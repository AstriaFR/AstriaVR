package dev.astriavr.player

import android.content.Context
import android.app.ActivityManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.Build
import android.os.SystemClock
import android.util.LruCache
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

/** A single low-priority decoder, bounded queue/cache; only visible covers are requested. */
class CoverRepository(context: Context, private val store: PlaylistStore) {
    private val app = context.applicationContext
    private val directory = File(app.cacheDir, "covers-v3").apply { mkdirs() }
    private val handler = Handler(Looper.getMainLooper())
    private val cache = object : LruCache<String, Bitmap>(
        (if (app.getSystemService(ActivityManager::class.java).isLowRamDevice) 4 else 8) * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.allocationByteCount
    }
    private val executor = ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS, ArrayBlockingQueue(24), { work ->
        Thread({ android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND); work.run() }, "AstriaVR-covers")
    })
    private class Request {
        val callbacks = java.util.IdentityHashMap<Any, (Bitmap?, PlaylistStore.Entry) -> Unit>()
        @Volatile var cancelled = false
        lateinit var task: Runnable
    }
    private val pending = mutableMapOf<String, Request>()
    private val detailsWorker = ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS, ArrayBlockingQueue(1), { work ->
        Thread({ android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND); work.run() }, "AstriaVR-details")
    }, ThreadPoolExecutor.DiscardOldestPolicy())
    private val detailsCache = LruCache<String, VideoMetadata.Info>(24)
    private var detailsGeneration = 0
    private val failed = LruCache<String, Long>(128)
    private val metadata = LruCache<String, PlaylistStore.Entry>(128)
    private val keys = LruCache<String, String>(256)
    private var writesUntilTrim = 0
    @Volatile private var closed = false

    init {
        executor.execute {
            runCatching {
                CoverCacheFiles.clearRetired(File(app.cacheDir, "covers-v2"))
                pruneObsoleteFiles()
            }
        }
    }

    fun key(entry: PlaylistStore.Entry): String {
        // Invalidate only unresolved covers affected by the ordinary-video detection fix.
        val recipe = if (entry.projection == 0 && entry.layout < 0) "auto-cover-v4" else "embedded-or-timed-right-v3"
        val identity = "${entry.uri}|${entry.name}|${entry.layout}|${entry.projection}|${entry.revision}|${entry.coverSource}|$recipe"
        return keys.get(identity) ?: MessageDigest.getInstance("SHA-256").digest(identity.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }.also { keys.put(identity, it) }
    }

    fun cached(entry: PlaylistStore.Entry): Bitmap? = cache.get(key(entry))

    /** Detail probing is on demand, separate from the cover and database queues. */
    fun loadDetails(entry: PlaylistStore.Entry, ready: (VideoMetadata.Info) -> Unit) {
        if (closed) return
        val token = ++detailsGeneration
        val identity = entry.uri // Cover revisions/projection changes do not change file details.
        detailsCache.get(identity)?.let { ready(it); return }
        detailsWorker.execute {
            if (closed) return@execute
            val info = VideoMetadata.read(app, Uri.parse(entry.uri))
            handler.post {
                if (!closed) {
                    detailsCache.put(identity, info)
                    if (token == detailsGeneration) ready(info)
                }
            }
        }
    }

    fun cancelDetails() { detailsGeneration++; detailsWorker.queue.clear() }

    fun load(entry: PlaylistStore.Entry, owner: Any, ready: (Bitmap?, PlaylistStore.Entry) -> Unit): Boolean {
        if (closed) return true
        val key = key(entry)
        val info = metadata.get(key)?.let { entry.copy(fileSize = it.fileSize, videoMime = it.videoMime,
            metadataLoaded = it.metadataLoaded, duration = maxOf(entry.duration, it.duration)) } ?: entry
        cache.get(key)?.let { ready(it, info); return true }
        failed.get(key)?.let { retryAt ->
            if (SystemClock.elapsedRealtime() < retryAt) { ready(null, info); return true }
            failed.remove(key)
        }
        pending[key]?.let { it.callbacks[owner] = ready; return true }
        val request = Request().apply { callbacks[owner] = ready }
        pending[key] = request
        request.task = Runnable {
            if (closed || request.cancelled) return@Runnable
            val bitmap = runCatching {
                val latest = store.find(entry.uri) ?: return@runCatching null
                if (this@CoverRepository.key(latest) != key) return@runCatching null
                if (!latest.metadataLoaded) {
                    val infoRead = VideoMetadata.read(app, Uri.parse(entry.uri))
                    detailsCache.put(entry.uri, infoRead)
                    if (!closed && !request.cancelled) {
                        store.updateMetadata(entry.id, infoRead.bytes, infoRead.mime)
                        if (infoRead.durationMs > 0) store.updateDuration(entry.id, infoRead.durationMs)
                    }
                }
                if (closed || request.cancelled) return@runCatching null
                val file = File(directory, "$key.webp")
                val disk = runCatching {
                    val bitmap = if (file.exists()) BitmapFactory.decodeFile(file.path) else null
                    if (bitmap != null) {
                        file.setLastModified(System.currentTimeMillis())
                        bitmap
                    } else {
                        file.delete()
                        null
                    }
                }.getOrNull()
                val decoded = disk ?: generate(entry)
                if (!closed && !request.cancelled && !file.exists() && decoded != null) runCatching {
                    val temporary = File.createTempFile("$key-", ".tmp", directory)
                    try {
                        @Suppress("DEPRECATION")
                        val format = if (Build.VERSION.SDK_INT >= 30) Bitmap.CompressFormat.WEBP_LOSSY else Bitmap.CompressFormat.WEBP
                        temporary.outputStream().use { check(decoded.compress(format, 80, it)) }
                        if (!closed && !request.cancelled && temporary.renameTo(file)) {
                            if (--writesUntilTrim <= 0) { trimDisk(); writesUntilTrim = 16 }
                        }
                    } finally { temporary.delete() }
                }
                decoded
            }.getOrNull()
            val updated = if (closed) entry else runCatching { store.find(entry.uri) }.getOrNull() ?: entry
            handler.post {
                if (!closed && pending[key] === request) {
                    pending.remove(key)
                    metadata.put(key, updated)
                    if (bitmap != null) cache.put(key, bitmap) else failed.put(key, SystemClock.elapsedRealtime() + 30_000)
                    request.callbacks.values.toList().forEach { it(bitmap, updated) }
                } else bitmap?.recycle()
            }
        }
        try {
            executor.execute(request.task)
        } catch (_: java.util.concurrent.RejectedExecutionException) {
            pending.remove(key)
            // Let still-visible cells retry after earlier decoding completes, without retaining a huge queue.
            return false
        }
        return true
    }

    /** UI-thread ownership prevents recycled or hidden rows retaining callbacks and queued decodes. */
    fun cancel(owner: Any) {
        val entries = pending.iterator()
        while (entries.hasNext()) {
            val request = entries.next().value
            request.callbacks.remove(owner)
            if (request.callbacks.isEmpty() && executor.remove(request.task)) {
                request.cancelled = true; entries.remove()
            }
            // A native decode already in progress must finish and populate the cache, not be discarded.
        }
    }

    fun trimMemory() { cache.evictAll(); failed.evictAll(); metadata.evictAll(); keys.evictAll(); detailsCache.evictAll() }

    /** Invalidate just the affected cover; never refresh unrelated rows when a video changes. */
    fun invalidate(entry: PlaylistStore.Entry, removed: Boolean = false) {
        if (closed) return
        val key = key(entry)
        cache.remove(key); failed.remove(key); metadata.remove(key)
        if (removed) detailsCache.remove(entry.uri)
        pending.remove(key)?.let { request ->
            request.cancelled = true
            request.callbacks.clear()
            executor.remove(request.task)
        }
        // Share the decoder worker so a cancelled in-flight encode cannot recreate the old file.
        try {
            executor.execute {
                File(directory, "$key.webp").delete()
            }
        } catch (_: java.util.concurrent.RejectedExecutionException) {
            // A full visible-cover queue takes priority; startup pruning will remove this orphan.
        }
    }

    private fun generate(entry: PlaylistStore.Entry): Bitmap? {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(app, Uri.parse(entry.uri))
            val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
            val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
            val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: entry.duration
            if (!closed) store.updateDuration(entry.id, duration)
            val selection = VideoProjection.resolve(entry.projection, entry.layout, 0, -1, entry.name, width, height, 1f)
            val ordinary = VideoProjection.isOrdinaryCover(entry.layout, entry.projection, entry.name, width, height)
            // Manual refresh stays on the original timed-frame route, including after app restarts.
            if (entry.coverSource == 0) {
                val embedded = runCatching { retriever.embeddedPicture?.let { decodeEmbedded(it, if (ordinary) 640 else 1536) } }.getOrNull()
                if (embedded != null) {
                    val picture = try { runCatching {
                        val imageRatio = embedded.width.toDouble() / embedded.height
                        val videoRatio = if (width > 0 && height > 0) width.toDouble() / height else 0.0
                        // A differently shaped poster is already a single image; avoid cutting it in half.
                        val sameFrameShape = videoRatio > 0 && kotlin.math.abs(imageRatio / videoRatio - 1.0) < .08
                        if (sameFrameShape && !ordinary && selection.projection in intArrayOf(
                                VideoProjection.FISHEYE_180, VideoProjection.STEREO_FISHEYE_180, VideoProjection.CUBEMAP, VideoProjection.EAC)) {
                            val input = IntArray(embedded.width * embedded.height)
                            embedded.getPixels(input, 0, embedded.width, 0, 0, embedded.width, embedded.height)
                            Bitmap.createBitmap(CoverProjection.render(input, embedded.width, embedded.height,
                                selection.layout, selection.projection, 320, 180), 320, 180, Bitmap.Config.ARGB_8888)
                        } else cropEmbedded(embedded, if (sameFrameShape && !ordinary) selection.layout else 2)
                    }.getOrNull() } finally { embedded.recycle() }
                    if (picture != null) return picture
                }
            }
            val frameLimit = if (ordinary) 640 else 1536
            val sampleTime = CoverProjection.sampleTimeUs(duration)
            val frame = (if (Build.VERSION.SDK_INT >= 27) {
                retriever.getScaledFrameAtTime(sampleTime,
                    MediaMetadataRetriever.OPTION_CLOSEST_SYNC, frameLimit, frameLimit)
            } else {
                // Android 8.0 has no scaled-frame API. Shrink before allocating projection pixels.
                retriever.getFrameAtTime(sampleTime, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)?.let { original ->
                    val edge = maxOf(original.width, original.height)
                    if (edge <= frameLimit) original else try {
                        Bitmap.createScaledBitmap(original,
                            (original.width.toLong() * frameLimit / edge).toInt().coerceAtLeast(1),
                            (original.height.toLong() * frameLimit / edge).toInt().coerceAtLeast(1), true)
                    } finally { original.recycle() }
                }
            }) ?: return null
            try {
                if (ordinary) return cropEmbedded(frame, 2)
                val input = IntArray(frame.width * frame.height)
                frame.getPixels(input, 0, frame.width, 0, 0, frame.width, frame.height)
                val pixels = CoverProjection.render(input, frame.width, frame.height, selection.layout, selection.projection, 320, 180)
                return Bitmap.createBitmap(pixels, 320, 180, Bitmap.Config.ARGB_8888)
            } finally { frame.recycle() }
        } finally { retriever.release() }
    }

    private fun decodeEmbedded(bytes: ByteArray, maxEdge: Int): Bitmap? {
        if (bytes.isEmpty() || bytes.size > 32 * 1024 * 1024) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > maxEdge) sample *= 2
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply {
            inSampleSize = sample; inPreferredConfig = Bitmap.Config.ARGB_8888
        })
    }

    private fun cropEmbedded(bitmap: Bitmap, layout: Int): Bitmap {
        val eye = Rect(if (layout == 0) bitmap.width / 2 else 0,
            if (layout == 1) bitmap.height / 2 else 0, bitmap.width, bitmap.height)
        val width = eye.width(); val height = eye.height()
        if (width * 9L > height * 16L) {
            val crop = (height * 16 / 9).coerceIn(1, width)
            eye.left += (width - crop) / 2; eye.right = eye.left + crop
        } else {
            val crop = (width * 9 / 16).coerceIn(1, height)
            eye.top += (height - crop) / 2; eye.bottom = eye.top + crop
        }
        return Bitmap.createBitmap(320, 180, Bitmap.Config.ARGB_8888).also {
            Canvas(it).drawBitmap(bitmap, eye, Rect(0, 0, 320, 180), Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
        }
    }

    private fun trimDisk() = CoverCacheFiles.trim(directory, null, CoverCacheFiles.MAX_BYTES,
        CoverCacheFiles.MAX_FILES, System.currentTimeMillis())

    private fun pruneObsoleteFiles() {
        val candidates = directory.listFiles()?.filter { it.extension in setOf("jpg", "webp") }
            ?.map { it.nameWithoutExtension }?.toHashSet() ?: return
        if (candidates.isEmpty()) { trimDisk(); return }
        val current = HashSet<String>()
        store.query().use { cursor ->
            while (!closed && current.size < candidates.size && cursor.moveToNext()) {
                val key = key(PlaylistStore.read(cursor))
                if (key in candidates) current.add(key)
            }
        }
        if (!closed) CoverCacheFiles.trim(directory, current, CoverCacheFiles.MAX_BYTES,
            CoverCacheFiles.MAX_FILES, System.currentTimeMillis())
    }
    fun close(onDrained: () -> Unit) {
        closed = true
        cancelDetails(); detailsWorker.shutdownNow()
        pending.values.forEach { it.cancelled = true }
        executor.queue.clear()
        pending.clear(); trimMemory(); handler.removeCallbacksAndMessages(null)
        // Native retrievers cannot be reliably interrupted. Close the DB only once they finish.
        executor.execute { onDrained() }
        executor.shutdown()
    }
}
