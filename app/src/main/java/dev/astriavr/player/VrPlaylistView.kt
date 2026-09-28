package dev.astriavr.player

import android.animation.ValueAnimator
import android.content.Context
import android.database.Cursor
import android.graphics.*
import android.text.TextUtils
import android.view.View
import kotlin.math.abs

/** Fixed dp HUD, centered and clipped to the right eye. Optical placement stays physical. */
class VrPlaylistView(context: Context, private val store: PlaylistStore, private val covers: CoverRepository,
    dismiss: () -> Unit) : View(context) {
    var settings = RenderSettings()
        set(value) { field=value; opticalLayout=value.opticalLayout(width,height); invalidate() }
    private var opticalLayout=settings.opticalLayout(0,0)
    private var cursor:Cursor?=null
    private var selected=0
    private var displayCount=0
    private var loading=false
    private var loadFailed=false
    private var generation=0
    private var cards=emptyList<Pair<Int,PlaylistStore.Entry>>()
    private val images=mutableMapOf<Long,Bitmap?>()
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val text=android.text.TextPaint(Paint.ANTI_ALIAS_FLAG)
    private val clip=Path()
    private val cardClip=Path()
    private val cardRect=RectF()
    private val placeholder=CoverPlaceholder()
    private val starPoints=floatArrayOf(8f,8f,37f,5f,177f,9f,191f,64f,8f,86f,172f,91f)
    private var motionOffset=0f
    private var movement:ValueAnimator?=null
    private val retryCovers = Runnable {
        if(isShown) cards.sortedBy { abs(it.first) }.forEach { (_,entry) ->
            if(!images.containsKey(entry.id)) requestCover(entry,generation)
        }
    }
    init {
        visibility=GONE; isClickable=true
        contentDescription=AppText.RIGHT_EYE_PLAYLIST_LEFT_RIGHT_TO.text()
        setOnClickListener { dismiss() }
    }
    fun show(uri:String?) {
        animate().cancel(); movement?.cancel(); motionOffset=0f
        covers.cancel(this); removeCallbacks(retryCovers)
        cursor?.close(); cursor=null; cards=emptyList(); images.clear(); displayCount=0
        val token=++generation
        loading=true; loadFailed=false; visibility=VISIBLE; alpha=0f; invalidate()
        store.submit({
            val data=store.query()
            try { data.count; data to store.indexOf(uri) }
            catch(error:Exception) { data.close(); throw error }
        }) { result ->
            val data=result.getOrNull()
            if(token!=generation || visibility!=VISIBLE) { data?.first?.close(); return@submit }
            loading=false; loadFailed=result.isFailure
            cursor=data?.first; displayCount=cursor?.count ?: 0
            selected=(data?.second ?: 0).coerceIn(0,maxOf(0,displayCount-1))
            if(cursor!=null) loadCards() else invalidate()
        }
        animate().alpha(1f).setDuration(UiStyle.motionDuration(160)).setInterpolator(UiStyle.easing).start()
    }
    fun close(immediate:Boolean=false) {
        val token=++generation
        covers.cancel(this); removeCallbacks(retryCovers)
        movement?.cancel(); movement=null; animate().cancel(); cursor?.close(); cursor=null
        if(immediate) { cards=emptyList(); images.clear(); visibility=GONE; alpha=1f; return }
        animate().alpha(0f).setDuration(UiStyle.motionDuration(130)).withEndAction {
            if(token==generation) { cards=emptyList(); images.clear(); visibility=GONE }
        }.start()
    }
    fun move(direction:Int) {
        val count=cursor?.count ?: return
        if(count==0) return
        val next=(selected+direction).coerceIn(0,count-1)
        if(next==selected) return
        movement?.cancel()
        val start=motionOffset+(next-selected)
        selected=next; motionOffset=start; loadCards()
        movement=ValueAnimator.ofFloat(start,0f).apply {
            duration=UiStyle.motionDuration(160); interpolator=UiStyle.easing
            addUpdateListener { motionOffset=it.animatedValue as Float; invalidate() }; start()
        }
    }
    fun selection():PlaylistStore.Entry?=cursor?.let { if(it.moveToPosition(selected)) PlaylistStore.read(it) else null }
    private fun loadCards() {
        val data=cursor ?: return
        covers.cancel(this); removeCallbacks(retryCovers)
        val token=++generation
        cards=(-2..2).mapNotNull { offset -> if(data.moveToPosition(selected+offset)) offset to PlaylistStore.read(data) else null }
        val ids=cards.map { it.second.id }.toSet(); images.keys.retainAll(ids)
        cards.sortedBy { abs(it.first) }.forEach { (_,entry) -> if(!images.containsKey(entry.id)) requestCover(entry,token) }
        invalidate()
    }
    private fun requestCover(entry:PlaylistStore.Entry,token:Int) {
        if(!covers.load(entry,this) { bitmap, _ -> if(token==generation && visibility==VISIBLE) { images[entry.id]=bitmap; invalidate() } }) {
            removeCallbacks(retryCovers); postDelayed(retryCovers,400)
        }
    }
    override fun onSizeChanged(w:Int,h:Int,oldw:Int,oldh:Int) { opticalLayout=settings.opticalLayout(w,h) }
    override fun onDraw(canvas:Canvas) {
        if(settings.phoneMode) return
        val layout=opticalLayout
        val x=layout.rightX.toFloat(); val y=height-layout.y-layout.eyeHeight.toFloat()
        val w=layout.eyeWidth.toFloat(); val h=layout.eyeHeight.toFloat()
        if(w<=0 || h<=0) return
        canvas.save()
        clip.reset(); clip.addOval(x,y,x+w,y+h,Path.Direction.CW); canvas.clipPath(clip)
        val density=resources.displayMetrics.density
        canvas.translate(x+w/2-100*density,y+h/2-48*density); canvas.scale(density,density)
        paint.color=0x180D1630; paint.style=Paint.Style.FILL
        canvas.drawRoundRect(0f,0f,200f,96f,10f,10f,paint)
        paint.color=0x48C4D2F6; paint.strokeWidth=.65f; paint.strokeCap=Paint.Cap.ROUND
        canvas.drawPoints(starPoints,paint)
        text.textAlign=Paint.Align.CENTER; text.color=0xBFFFFFFF.toInt(); text.textSize=8.5f
        text.setShadowLayer(.8f,0f,.45f,0xB3000000.toInt())
        canvas.drawText(AppText.PLAYLIST.text(),100f,13f,text)
        if(cards.isEmpty()) {
            text.textSize=8f
            canvas.drawText(if(loading) AppText.LOADING_PLAYLIST.text() else if(loadFailed) AppText.COULD_NOT_LOAD_PLAYLIST.text() else AppText.PLAYLIST_IS_EMPTY.text(),100f,40f,text)
            if(!loading) {
                val hint = if(loadFailed) AppText.CLOSE_AND_TRY_AGAIN.text() else AppText.ADD_VIDEOS_ON_THE_PLAYLIST_PAGE.text()
                val originalSize = text.textSize
                text.textSize *= minOf(1f, 184f / text.measureText(hint).coerceAtLeast(1f))
                canvas.drawText(hint,100f,55f,text)
                text.textSize = originalSize
            }
        }
        canvas.save(); canvas.clipRect(8f,18f,192f,59f)
        for((offset,entry) in cards) {
            val position=offset+motionOffset
            val focus=(1f-abs(position)).coerceIn(0f,1f)
            val cw=48f+10f*focus; val ch=cw*9/16
            val cx=100f+position*64f; val cy=39f
            if(cx+cw/2<8 || cx-cw/2>192) continue
            val rect=cardRect.apply { set(cx-cw/2,cy-ch/2,cx+cw/2,cy+ch/2) }
            paint.style=Paint.Style.FILL; paint.color=0x24202A36
            canvas.drawRoundRect(rect,3f,3f,paint)
            canvas.save(); cardClip.reset(); cardClip.addRoundRect(rect,3f,3f,Path.Direction.CW); canvas.clipPath(cardClip)
            val opacity=(204+28*focus).toInt(); val bitmap=images[entry.id]
            paint.alpha=opacity
            if(bitmap!=null) canvas.drawBitmap(bitmap,null,rect,paint)
            else {
                canvas.saveLayerAlpha(rect,opacity); canvas.translate(rect.left,rect.top)
                placeholder.setBounds(0,0,rect.width().toInt(),rect.height().toInt()); placeholder.draw(canvas); canvas.restore()
            }
            canvas.restore()
            if(focus>0) {
                paint.style=Paint.Style.STROKE; paint.color=UiStyle.ACCENT; paint.alpha=(201*focus).toInt(); paint.strokeWidth=.85f
                canvas.drawRoundRect(rect,3f,3f,paint)
            }
            paint.alpha=255; paint.style=Paint.Style.FILL
        }
        canvas.restore()
        cards.firstOrNull { it.first==0 }?.let { (_,entry) ->
            text.color=0xD9FFFFFF.toInt(); text.textSize=8f
            canvas.drawText(TextUtils.ellipsize(entry.name,text,180f,TextUtils.TruncateAt.MIDDLE).toString(),100f,69f,text)
        }
        text.color=0xBFFFFFFF.toInt(); text.textSize=7f
        canvas.drawText(if(displayCount>0) AppText.SELECT_PLAY.text(selected+1, displayCount) else AppText.MENU_TO_EXIT.text(),100f,81f,text)
        if(displayCount>0) { text.textSize=6.5f; canvas.drawText(AppText.MENU_CLOSE.text(),100f,91f,text) }
        canvas.restore()
    }
}
