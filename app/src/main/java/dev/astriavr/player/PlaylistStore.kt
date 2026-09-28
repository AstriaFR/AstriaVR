package dev.astriavr.player

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.os.CancellationSignal
import android.os.Handler
import android.os.Looper
import java.util.concurrent.Executors

/** No item limit. A windowed SQLite cursor backs both views; playback updates never reorder it. */
class PlaylistStore(context: Context) : SQLiteOpenHelper(context.applicationContext, "playlist.db", null, 2) {
    private val handler = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor { work ->
        Thread({ android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND); work.run() }, "AstriaVR-database")
    }

    /** Serial writes also act as a barrier for the next list query. */
    fun <T> submit(work: () -> T, ready: (Result<T>) -> Unit = {}) {
        worker.execute {
            val result = runCatching(work)
            handler.post { ready(result) }
        }
    }

    fun queryAsync(search: String = "", ready: (Result<Cursor>) -> Unit): CancellationSignal {
        val cancellation = CancellationSignal()
        worker.execute {
            val result = runCatching {
                cancellation.throwIfCanceled()
                val cursor = query(search, cancellation)
                try { cursor.count; cancellation.throwIfCanceled(); cursor }
                catch (error: Exception) { cursor.close(); throw error }
            }
            handler.post {
                if (cancellation.isCanceled) result.getOrNull()?.close() else ready(result)
            }
        }
        return cancellation
    }

    /** Call only after document import and cover decoding have drained. Never block the UI. */
    fun closeWhenIdle() { worker.execute { close() }; worker.shutdown() }
    data class Entry(val id: Long, val uri: String, val name: String, val position: Long,
        val duration: Long, val layout: Int, val projection: Int, val revision: Int,
        val order: Long, val fileSize: Long, val videoMime: String, val metadataLoaded: Boolean, val coverSource: Int)

    init { setWriteAheadLoggingEnabled(true) }
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE videos (_id INTEGER PRIMARY KEY AUTOINCREMENT, uri TEXT NOT NULL UNIQUE, name TEXT NOT NULL, position INTEGER NOT NULL DEFAULT 0, duration INTEGER NOT NULL DEFAULT 0, layout INTEGER NOT NULL DEFAULT -1, projection INTEGER NOT NULL DEFAULT 0, revision INTEGER NOT NULL DEFAULT 0, sort_order INTEGER NOT NULL DEFAULT 0, file_size INTEGER NOT NULL DEFAULT -1, video_mime TEXT NOT NULL DEFAULT '', metadata_loaded INTEGER NOT NULL DEFAULT 0, cover_source INTEGER NOT NULL DEFAULT 0)")
        createOrderIndex(db)
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE videos ADD COLUMN sort_order INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE videos ADD COLUMN file_size INTEGER NOT NULL DEFAULT -1")
            db.execSQL("ALTER TABLE videos ADD COLUMN video_mime TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE videos ADD COLUMN metadata_loaded INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE videos ADD COLUMN cover_source INTEGER NOT NULL DEFAULT 0")
            // Preserve the previous newest-first order and manually refreshed thumbnail choices.
            db.execSQL("UPDATE videos SET sort_order=-_id, cover_source=CASE WHEN revision>0 THEN 1 ELSE 0 END")
            createOrderIndex(db)
        }
    }
    private fun createOrderIndex(db: SQLiteDatabase) {
        db.execSQL("CREATE INDEX IF NOT EXISTS videos_order ON videos(sort_order ASC, _id DESC)")
    }

    fun add(uri: String, name: String): Entry {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.execSQL("INSERT OR IGNORE INTO videos(uri,name,sort_order) SELECT ?,?,COALESCE(MIN(sort_order),0)-1 FROM videos", arrayOf(uri, name))
            val result = find(uri)!!
            db.setTransactionSuccessful()
            return result
        } finally { db.endTransaction() }
    }
    fun find(uri: String): Entry? = readableDatabase.query("videos", null, "uri=?", arrayOf(uri), null, null, null).use {
        if (it.moveToFirst()) read(it) else null
    }
    fun query(search: String = "", cancellation: CancellationSignal? = null): Cursor {
        val text = search.trim().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
        return readableDatabase.query(false, "videos", null, if (text.isEmpty()) null else "name LIKE ? ESCAPE '\\'",
            if (text.isEmpty()) null else arrayOf("%$text%"), null, null, "sort_order ASC, _id DESC", null, cancellation)
    }
    /** Same order as the visible list, including drag sorting; wrap at either end. Worker only. */
    fun adjacent(uri: String, direction: Int): Entry? {
        val current = find(uri)
        val forward = direction > 0
        val order = if (forward) "sort_order ASC, _id DESC" else "sort_order DESC, _id ASC"
        fun first(selection: String?, args: Array<String>?): Entry? =
            readableDatabase.query("videos", null, selection, args, null, null, order, "1").use {
                if (it.moveToFirst()) read(it) else null
            }
        if (current != null) {
            val selection = if (forward) "sort_order>? OR (sort_order=? AND _id<?)"
                else "sort_order<? OR (sort_order=? AND _id>?)"
            first(selection, arrayOf(current.order.toString(), current.order.toString(), current.id.toString()))
                ?.let { return it }
        }
        return first(null, null)
    }
    fun save(uri: String, name: String, position: Long, duration: Long) {
        val knownDuration = if (duration > 0) duration else find(uri)?.duration ?: 0
        writableDatabase.update("videos", ContentValues().apply {
            put("name", name); put("position", PlaybackPosition.resume(position, knownDuration))
            if (duration > 0) put("duration", duration)
        }, "uri=?", arrayOf(uri))
    }
    fun setProjection(uri: String, layout: Int, projection: Int) {
        writableDatabase.update("videos", ContentValues().apply { put("layout", layout); put("projection", projection) },
            "uri=? AND (layout!=? OR projection!=?)", arrayOf(uri, layout.toString(), projection.toString()))
    }
    fun refreshCover(id: Long) { writableDatabase.execSQL("UPDATE videos SET revision=revision+1, cover_source=1 WHERE _id=?", arrayOf(id)) }
    fun updateMetadata(id: Long, fileSize: Long, videoMime: String) {
        writableDatabase.update("videos", ContentValues().apply {
            if (fileSize >= 0) put("file_size", fileSize)
            if (videoMime.isNotEmpty()) put("video_mime", videoMime)
            put("metadata_loaded", 1)
        }, "_id=?", arrayOf(id.toString()))
    }
    /** Persist one drop atomically. Unseen rows retain their relative order, even in filtered lists. */
    fun moveRelative(id: Long, targetId: Long, after: Boolean): Boolean {
        if (id == targetId) return false
        val db = writableDatabase
        db.beginTransaction()
        try {
            fun orderOf(key: Long): Long? = db.rawQuery("SELECT sort_order FROM videos WHERE _id=?", arrayOf(key.toString())).use {
                if (it.moveToFirst()) it.getLong(0) else null
            }
            val from = orderOf(id) ?: return false
            val target = orderOf(targetId) ?: return false
            val destination: Long
            if (from < target) {
                destination = if (after) target else target - 1
                db.execSQL("UPDATE videos SET sort_order=sort_order-1 WHERE sort_order>? AND sort_order<=?", arrayOf(from, destination))
            } else {
                destination = if (after) target + 1 else target
                db.execSQL("UPDATE videos SET sort_order=sort_order+1 WHERE sort_order>=? AND sort_order<?", arrayOf(destination, from))
            }
            db.execSQL("UPDATE videos SET sort_order=? WHERE _id=?", arrayOf(destination, id))
            db.setTransactionSuccessful()
            return from != destination
        } finally { db.endTransaction() }
    }
    fun updateDuration(id: Long, duration: Long) {
        if (duration > 0) writableDatabase.update("videos", ContentValues().apply { put("duration", duration) }, "_id=?", arrayOf(id.toString()))
    }
    fun indexOf(uri: String?): Int {
        val entry = uri?.let { find(it) } ?: return 0
        return readableDatabase.rawQuery("SELECT COUNT(*) FROM videos WHERE sort_order<? OR (sort_order=? AND _id>?)", arrayOf(entry.order.toString(), entry.order.toString(), entry.id.toString())).use {
            if (it.moveToFirst()) it.getInt(0) else 0
        }
    }
    fun remove(id: Long) { writableDatabase.delete("videos", "_id=?", arrayOf(id.toString())) }

    companion object {
        fun read(cursor: Cursor) = Entry(cursor.getLong(0), cursor.getString(1), cursor.getString(2),
            cursor.getLong(3), cursor.getLong(4), cursor.getInt(5), cursor.getInt(6), cursor.getInt(7),
            cursor.getLong(8), cursor.getLong(9), cursor.getString(10), cursor.getInt(11) != 0, cursor.getInt(12))
    }
}
