package dev.astriavr.player

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.database.Cursor
import android.graphics.Color
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.drawable.ColorDrawable
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.view.Gravity
import android.view.DragEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.inputmethod.InputMethodManager
import android.os.SystemClock
import android.widget.*

/** Small previews and readable metadata, backed by a recycled, windowed database cursor. */
@SuppressLint("SetTextI18n")
class PlaylistPage(context: Context, private val store: PlaylistStore, private val covers: CoverRepository,
    private val play: (PlaylistStore.Entry, Boolean) -> Unit, add: () -> Unit, back: () -> Unit,
    private val reorder: (Long, Long, Boolean, (Boolean) -> Unit) -> Unit,
    private val remove: (PlaylistStore.Entry, (Boolean) -> Unit) -> Unit) : FrameLayout(context) {
    private val content = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
    private val count = label("", 12f, UiStyle.MUTED)
    private val search = EditText(context)
    private val viewPrefs = context.getSharedPreferences("playlist-view", Context.MODE_PRIVATE)
    private var gridMode = viewPrefs.getBoolean("covers_only", false)
    private lateinit var viewButton: ImageButton
    private val grid = GridView(context)
    private val titleGroup = LinearLayout(context).apply { gravity = Gravity.CENTER_VERTICAL }
    private var dragFrom = -1
    private var dragTo = -1
    private var reflowListener: ViewTreeObserver.OnPreDrawListener? = null
    private val insertionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = UiStyle.ACCENT; alpha = 90; style = Paint.Style.STROKE; strokeWidth = dp(1).toFloat()
    }
    private val list = object : ListView(context) {
        override fun dispatchDraw(canvas: Canvas) {
            super.dispatchDraw(canvas)
            if (dragging != null) getChildAt(dragTo - firstVisiblePosition)?.let { slot ->
                canvas.drawRoundRect(dp(2).toFloat(), slot.top + dp(2).toFloat(),
                    width - dp(2).toFloat(), slot.bottom - dp(2).toFloat(), dp(16).toFloat(), dp(16).toFloat(), insertionPaint)
            }
        }
    }
    private lateinit var searchButton: ImageButton
    private var searching = false
    private var queryText = ""
    private var overlay: FrameLayout? = null
    private var panel: View? = null
    private var closingActions = false
    private var pageGeneration = 0
    private var pageActive = false
    private var query: android.os.CancellationSignal? = null
    private val empty = label("", 15f, UiStyle.MUTED).apply {
        gravity = Gravity.CENTER; setPadding(dp(24), dp(20), dp(24), dp(20)); visibility = GONE
    }
    private data class DragItem(val owner: PlaylistPage, val id: Long)
    private var dragging: DragItem? = null
    private var dragY = Float.NaN
    private var refreshAfterDrag = false
    private var reorderPending = false
    private var blockPlayUntil = 0L
    private val autoScroll = object : Runnable {
        override fun run() {
            if (dragging == null || !dragY.isFinite() || !isShown) return
            val edge = dp(48).toFloat()
            val movement = when {
                dragY < edge -> -dp(7)
                dragY > list.height - edge -> dp(7)
                else -> 0
            }
            if (movement != 0 && list.canScrollList(if (movement < 0) -1 else 1)) {
                list.scrollListBy(movement); updateDropTarget(dragY); postOnAnimation(this)
            }
        }
    }
    private val notice = label("", 12f)
    private val hideNotice = Runnable { notice.animate().alpha(0f).setDuration(UiStyle.motionDuration(160)).withEndAction { notice.visibility = GONE }.start() }
    private val searchTask = Runnable { if (isShown) refresh(true) }
    private val changedEntries = android.util.LruCache<Long, PlaylistStore.Entry>(64)
    private val adapter = object : CursorAdapter(context, null, 0) {
        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View =
            super.getView(PlaylistDragOrder.originalPosition(position, dragFrom, dragTo), convertView, parent)
        override fun getItemId(position: Int): Long =
            super.getItemId(PlaylistDragOrder.originalPosition(position, dragFrom, dragTo))
        override fun getItem(position: Int): Any? =
            super.getItem(PlaylistDragOrder.originalPosition(position, dragFrom, dragTo))
        override fun getViewTypeCount() = 2
        override fun getItemViewType(position: Int) = if (gridMode) 1 else 0
        override fun newView(context: Context, cursor: Cursor, parent: ViewGroup): View = if (gridMode) CoverTile() else VideoRow()
        override fun bindView(view: View, context: Context, cursor: Cursor) {
            val original = PlaylistStore.read(cursor)
            val changed = changedEntries.get(original.id)
            val entry = if (changed != null && changed.revision > original.revision)
                original.copy(revision=changed.revision, coverSource=changed.coverSource) else original
            if (changed != null && original.revision >= changed.revision) changedEntries.remove(original.id)
            when (view) { is CoverTile -> view.bind(entry); is VideoRow -> view.bind(entry) }
        }
    }

    init {
        background = UiStyle.stars(context); isClickable = true; isFocusableInTouchMode = true
        content.setPadding(dp(18), dp(8), dp(18), dp(8))
        val header = LinearLayout(context).apply { gravity = Gravity.CENTER_VERTICAL }
        header.addView(UiStyle.icon(context,"return",AppText.BACK_TO_PLAYER.text(),Color.TRANSPARENT,back), LinearLayout.LayoutParams(dp(44),dp(40)).apply { marginEnd=dp(10) })
        val titleSlot = FrameLayout(context)
        titleGroup.addView(label(AppText.PLAYLIST.text(),18f,bold=true).apply {
            setSingleLine(); ellipsize=TextUtils.TruncateAt.END
        }, LinearLayout.LayoutParams(0,-2,1f))
        titleGroup.addView(count,LinearLayout.LayoutParams(-2,-2).apply { marginStart=dp(12) })
        titleSlot.addView(titleGroup,LayoutParams(-1,-1))
        search.apply {
            visibility=GONE; hint=AppText.SEARCH_VIDEO_NAMES.text(); setSingleLine(); setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP,15f)
            setTextColor(UiStyle.TEXT); setHintTextColor(UiStyle.MUTED); background=UiStyle.glass(context,UiStyle.SURFACE,10)
            setPadding(dp(4),0,dp(4),0); imeOptions=android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH
            setOnEditorActionListener { _, _, _ -> keyboard().hideSoftInputFromWindow(windowToken,0); true }
            addTextChangedListener(object:TextWatcher {
                override fun beforeTextChanged(s:CharSequence?,start:Int,count:Int,after:Int)=Unit
                override fun onTextChanged(s:CharSequence?,start:Int,before:Int,count:Int) {
                    queryText=s?.toString().orEmpty(); query?.cancel(); removeCallbacks(searchTask); postDelayed(searchTask,160)
                }
                override fun afterTextChanged(s:Editable?)=Unit
            })
        }
        titleSlot.addView(search,LayoutParams(-1,-1))
        header.addView(titleSlot,LinearLayout.LayoutParams(0,dp(40),1f))
        searchButton=UiStyle.icon(context,"search",AppText.SEARCH_VIDEOS.text(),Color.TRANSPARENT) { setSearching(!searching) }
        header.addView(searchButton,LinearLayout.LayoutParams(dp(44),dp(40)))
        viewButton=UiStyle.icon(context,"grid",AppText.SWITCH_TO_COVER_VIEW.text(),Color.TRANSPARENT) { toggleViewMode() }
        header.addView(viewButton,LinearLayout.LayoutParams(dp(44),dp(40)).apply { marginStart=dp(8) })
        updateViewButton()
        header.addView(UiStyle.icon(context,"add",AppText.ADD_VIDEOS.text(),Color.TRANSPARENT,add),LinearLayout.LayoutParams(dp(44),dp(40)).apply { marginStart=dp(8) })
        // Keep the 28dp icon artwork; only trim vertical space around it.
        for (index in 0 until header.childCount) (header.getChildAt(index) as? ImageButton)?.setPadding(dp(8),dp(6),dp(8),dp(6))
        content.addView(header,LinearLayout.LayoutParams(-1,dp(42)))
        content.addView(View(context).apply { setBackgroundColor(0xFF38517D.toInt()) },LinearLayout.LayoutParams(-1,dp(1)).apply { topMargin=dp(4); bottomMargin=dp(10) })
        val area=FrameLayout(context)
        list.apply {
            divider=ColorDrawable(Color.TRANSPARENT); dividerHeight=dp(8)
            setSelector(ColorDrawable(Color.TRANSPARENT)); cacheColorHint=Color.TRANSPARENT
            isVerticalScrollBarEnabled=false; clipToPadding=false; setPadding(0,0,0,dp(8))
            visibility=if(gridMode) GONE else VISIBLE
            if(!gridMode) adapter=this@PlaylistPage.adapter
        }
        area.addView(list,LayoutParams(-1,-1))
        grid.apply {
            numColumns=GridView.AUTO_FIT; columnWidth=dp(136); stretchMode=GridView.STRETCH_COLUMN_WIDTH
            horizontalSpacing=dp(8); verticalSpacing=dp(8); gravity=Gravity.CENTER
            setSelector(ColorDrawable(Color.TRANSPARENT)); cacheColorHint=Color.TRANSPARENT
            isVerticalScrollBarEnabled=false; clipToPadding=false; setPadding(0,0,0,dp(8))
            visibility=if(gridMode) VISIBLE else GONE
            if(gridMode) adapter=this@PlaylistPage.adapter
            setRecyclerListener { (it as? CoverTile)?.cancelCover() }
        }
        area.addView(grid,LayoutParams(-1,-1))
        area.addView(empty,LayoutParams(-1,-1))
        list.setRecyclerListener { (it as? VideoRow)?.cancelCover() }
        list.setOnDragListener { _, event -> handleDrag(event) }
        content.addView(area,LinearLayout.LayoutParams(-1,0,1f))
        addView(content,LayoutParams(-1,-1))
        notice.apply { gravity=Gravity.CENTER; background=UiStyle.shape(context,UiStyle.RAISED,14,UiStyle.LINE); setPadding(dp(18),dp(12),dp(18),dp(12)); elevation=dp(8).toFloat(); visibility=GONE }
        addView(notice,LayoutParams(-2,-2,Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply { bottomMargin=dp(12) })
    }
    /** Insets constrain the content and sheet, leaving the modal scrim edge-to-edge. */
    fun setContentPadding(left: Int, top: Int, right: Int, bottom: Int) {
        if (content.paddingLeft == left && content.paddingTop == top && content.paddingRight == right && content.paddingBottom == bottom) return
        content.setPadding(left,top,right,bottom)
        panel?.let { view ->
            view.layoutParams=(view.layoutParams as LayoutParams).apply {
                width=minOf(dp(340),(this@PlaylistPage.width-left-right-dp(12)).coerceAtLeast(1))
                leftMargin=left; topMargin=top; rightMargin=right; bottomMargin=bottom
            }
        }
    }
    fun present() {
        pageActive=true; pageGeneration++; animate().cancel(); translationY=0f; visibility=VISIBLE
        // Keep the full-screen background and header fixed; do not restore focus to a stale search field.
        requestFocus(); refresh(); UiStyle.appear(this,0f)
    }
    fun hidePage() {
        pageActive=false; query?.cancel(); removeCallbacks(searchTask)
        cancelVisibleCovers()
        if (dragging != null) list.cancelDragAndDrop()
        endDrag(false)
        val generation=++pageGeneration
        clearActions(); keyboard().hideSoftInputFromWindow(windowToken,0); search.clearFocus()
        animate().cancel()
        translationY=0f
        animate().alpha(0f).setDuration(UiStyle.motionDuration(170)).setInterpolator(UiStyle.easing).withEndAction {
            if(generation==pageGeneration) { visibility=GONE; release() }
        }.start()
    }
    fun handleBack():Boolean {
        if (dragging != null) { list.cancelDragAndDrop(); endDrag(true); return true }
        if(overlay!=null) { dismissActions(); return true }
        if(searching) { setSearching(false); return true }
        return false
    }
    private fun setSearching(value:Boolean) {
        searching=value
        titleGroup.animate().cancel(); search.animate().cancel()
        titleGroup.visibility=if(value) GONE else VISIBLE; search.visibility=if(value) VISIBLE else GONE
        searchButton.setImageDrawable(PlayerIcon(if(value) "close" else "search"))
        searchButton.contentDescription=if(value) AppText.CLOSE_SEARCH.text() else AppText.SEARCH_VIDEOS.text()
        searchButton.tooltipText=searchButton.contentDescription
        UiStyle.appear(if(value) search else titleGroup,0f)
        if(value) { search.requestFocus(); keyboard().showSoftInput(search,InputMethodManager.SHOW_IMPLICIT) }
        else { search.setText(""); search.clearFocus(); keyboard().hideSoftInputFromWindow(windowToken,0) }
    }
    private fun updateViewButton() {
        viewButton.setImageDrawable(PlayerIcon(if(gridMode) "list" else "grid"))
        viewButton.contentDescription=if(gridMode) AppText.SWITCH_TO_LIST_VIEW.text() else AppText.SWITCH_TO_COVER_VIEW.text()
        viewButton.tooltipText=viewButton.contentDescription
    }
    private fun toggleViewMode() {
        if (dragging != null || reorderPending || overlay != null) return
        val first=(if(gridMode) grid else list).firstVisiblePosition.coerceAtLeast(0)
        cancelVisibleCovers(); cancelReflow()
        // Detach the inactive adapter so hidden views cannot request or retain cover callbacks.
        list.adapter=null; grid.adapter=null
        gridMode=!gridMode
        viewPrefs.edit().putBoolean("covers_only",gridMode).apply()
        list.visibility=if(gridMode) GONE else VISIBLE
        grid.visibility=if(gridMode) VISIBLE else GONE
        val viewport:AbsListView=if(gridMode) grid else list
        viewport.adapter=adapter
        viewport.setSelection(first)
        updateViewButton()
    }
    private fun cancelVisibleCovers() {
        for(i in 0 until list.childCount) (list.getChildAt(i) as? VideoRow)?.cancelCover()
        for(i in 0 until grid.childCount) (grid.getChildAt(i) as? CoverTile)?.cancelCover()
    }
    fun refresh(resetScroll:Boolean=false, animateRows:Boolean=false) {
        if (!pageActive) return
        if (dragging != null || reorderPending) { refreshAfterDrag = true; return }
        query?.cancel()
        val text = queryText
        val viewport=if(gridMode) grid else list
        val first=viewport.firstVisiblePosition; val top=viewport.getChildAt(0)?.top ?: 0
        if (adapter.count == 0) { empty.text=AppText.LOADING_PLAYLIST.text(); empty.visibility=VISIBLE }
        query=store.queryAsync(text) { result ->
            query=null
            val cursor = result.getOrNull()
            if (!pageActive || text != queryText) { cursor?.close(); return@queryAsync }
            if (cursor == null) {
                if (adapter.count == 0) { empty.text=AppText.COULD_NOT_LOAD_THE_PLAYLIST_GO.text(); empty.visibility=VISIBLE }
                else Toast.makeText(context,AppText.COULD_NOT_LOAD_THE_PLAYLIST_TRY.text(),Toast.LENGTH_SHORT).show()
                dragFrom=-1; dragTo=-1; adapter.notifyDataSetChanged()
                return@queryAsync
            }
            // A query started just before a long press must not replace the drag's cursor.
            if (dragging != null || reorderPending) { cursor.close(); refreshAfterDrag=true; return@queryAsync }
            val apply = {
                dragFrom=-1; dragTo=-1
                count.text=cursor.count.toString(); adapter.changeCursor(cursor)
                empty.text=if(text.isBlank()) AppText.YOUR_PLAYLIST_IS_EMPTY_NTAP_AT.text() else AppText.NO_MATCHING_VIDEOS_NTRY_ANOTHER_NAME.text()
                empty.visibility=if(cursor.count==0) VISIBLE else GONE
            }
            if (animateRows && !gridMode) animateReflow(apply) else apply()
            val position=if(resetScroll) 0 else first.coerceIn(0,(cursor.count-1).coerceAtLeast(0))
            if(gridMode) grid.setSelection(position) else list.setSelectionFromTop(position,if(resetScroll) 0 else top)
        }
    }
    fun release() {
        pageActive=false; query?.cancel(); query=null
        cancelVisibleCovers()
        if (dragging != null) list.cancelDragAndDrop()
        endDrag(false)
        cancelReflow()
        pageGeneration++; animate().cancel(); clearActions(); removeCallbacks(searchTask); removeCallbacks(hideNotice)
        notice.animate().cancel(); notice.visibility=GONE; adapter.changeCursor(null); alpha=1f; translationY=0f
    }
    private fun notifyRemoved() {
        notice.animate().cancel(); removeCallbacks(hideNotice); notice.text=AppText.REMOVED_FROM_PLAYLIST_ORIGINAL_FILE_KEPT.text(); notice.visibility=VISIBLE
        UiStyle.appear(notice,dp(6).toFloat()); postDelayed(hideNotice,2400)
    }
    private fun startItemDrag(row: View, entry: PlaylistStore.Entry): Boolean {
        if (dragging != null || reorderPending || query != null || overlay != null || !row.isAttachedToWindow) return false
        val position = list.getPositionForView(row)
        if (position !in 0 until adapter.count) return false
        val item = DragItem(this, entry.id)
        dragFrom = position; dragTo = position
        dragging = item; blockPlayUntil = Long.MAX_VALUE
        val shadow = object : View.DragShadowBuilder(row) {
            override fun onProvideShadowMetrics(size: android.graphics.Point, touch: android.graphics.Point) {
                super.onProvideShadowMetrics(size, touch)
                // Keep the grabbed point under the finger instead of jumping to the row center.
                if (row is VideoRow) touch.set(row.dragTouchX.toInt().coerceIn(0, size.x), row.dragTouchY.toInt().coerceIn(0, size.y))
            }
        }
        val started = row.startDragAndDrop(null, shadow, item, 0)
        if (started) { row.alpha = 0f; list.invalidate(); keyboard().hideSoftInputFromWindow(windowToken, 0) }
        else endDrag(false)
        return started
    }
    private fun handleDrag(event: DragEvent): Boolean {
        val item = event.localState as? DragItem ?: return false
        if (item.owner !== this) return false
        when (event.action) {
            DragEvent.ACTION_DRAG_STARTED -> return dragging === item
            DragEvent.ACTION_DRAG_ENTERED, DragEvent.ACTION_DRAG_LOCATION -> {
                if (dragging !== item) return false
                dragY = event.y; updateDropTarget(dragY)
                removeCallbacks(autoScroll); postOnAnimation(autoScroll)
            }
            DragEvent.ACTION_DRAG_EXITED -> {
                dragY = Float.NaN
                removeCallbacks(autoScroll); list.invalidate()
            }
            DragEvent.ACTION_DROP -> {
                if (dragging !== item) return false
                updateDropTarget(event.y)
                val target = adapter.cursor?.let { if (it.moveToPosition(dragTo)) it.getLong(0) else -1L } ?: -1L
                if (target >= 0 && target != item.id) {
                    reorderPending = true
                    reorder(item.id, target, dragTo > dragFrom) { saved ->
                        reorderPending = false; refreshAfterDrag = false
                        if (isShown) {
                            refresh(animateRows = true)
                            if (!saved) Toast.makeText(context, AppText.COULD_NOT_SAVE_THE_ORDER_TRY.text(), Toast.LENGTH_SHORT).show()
                        } else { dragFrom = -1; dragTo = -1 }
                    }
                }
                endDrag(true)
                return true
            }
            DragEvent.ACTION_DRAG_ENDED -> if (dragging != null) endDrag(true)
        }
        return true
    }
    private fun updateDropTarget(y: Float) {
        if (dragging == null || list.childCount == 0 || list.height <= dp(4)) return
        var index = list.childCount - 1
        for (i in 0 until list.childCount) {
            if (y < list.getChildAt(i).bottom + dp(4)) { index = i; break }
        }
        val row = list.getChildAt(index)
        val position = list.firstVisiblePosition + index
        val center = row.top + row.height / 2f
        if (position != dragTo && ((position > dragTo && y >= center) || (position < dragTo && y <= center))) {
            animateReflow { dragTo = position; adapter.notifyDataSetChanged() }
        }
    }
    private fun cancelReflow() {
        reflowListener?.let { list.viewTreeObserver.removeOnPreDrawListener(it) }; reflowListener = null
    }
    /** FLIP animation: stable IDs follow their old visual coordinates into their new slots. */
    private fun animateReflow(change: () -> Unit) {
        cancelReflow()
        val first = list.firstVisiblePosition
        val top = list.getChildAt(0)?.top ?: 0
        val before = HashMap<Long, Float>()
        for (i in 0 until list.childCount) (list.getChildAt(i) as? VideoRow)?.let {
            before[it.boundId] = it.top + it.translationY
            it.animate().cancel()
        }
        change()
        // Stable-ID sync must not scroll the entire viewport to follow the moving item.
        if (adapter.count > 0) list.setSelectionFromTop(first.coerceAtMost(adapter.count - 1), top)
        val listener = object : ViewTreeObserver.OnPreDrawListener {
            override fun onPreDraw(): Boolean {
                list.viewTreeObserver.removeOnPreDrawListener(this)
                if (reflowListener === this) reflowListener = null
                for (i in 0 until list.childCount) (list.getChildAt(i) as? VideoRow)?.let { row ->
                    row.animate().cancel()
                    row.translationY = before[row.boundId]?.let { it - row.top } ?: 0f
                    row.alpha = if (row.boundId == dragging?.id) 0f else 1f
                    row.animate().translationY(0f).setDuration(UiStyle.motionDuration(180)).setInterpolator(UiStyle.easing).start()
                }
                list.invalidate()
                return true
            }
        }
        reflowListener = listener
        list.viewTreeObserver.addOnPreDrawListener(listener)
        list.requestLayout(); list.invalidate()
    }
    private fun endDrag(refresh: Boolean) {
        val active = dragging != null
        val draggedId = dragging?.id
        dragging = null; dragY = Float.NaN
        removeCallbacks(autoScroll)
        if (active && !reorderPending) {
            if (refresh) animateReflow { dragFrom = -1; dragTo = -1; adapter.notifyDataSetChanged() }
            else { dragFrom = -1; dragTo = -1 }
        }
        for (i in 0 until list.childCount) (list.getChildAt(i) as? VideoRow)?.let { row ->
            if (row.boundId == draggedId && reorderPending) {
                row.alpha = .3f
                row.animate().alpha(1f).setDuration(UiStyle.motionDuration(160)).start()
            } else row.alpha = 1f
        }
        if (active) blockPlayUntil = SystemClock.uptimeMillis() + 250
        list.invalidate()
        val pending = refreshAfterDrag; refreshAfterDrag = false
        if ((active || pending) && refresh) this.refresh()
    }
    private fun playEntry(entry: PlaylistStore.Entry) {
        if (dragging == null && !reorderPending && SystemClock.uptimeMillis() >= blockPlayUntil) play(entry, false)
    }
    /** Compact covers with duration; names and actions remain available on long press. */
    private inner class CoverTile:FrameLayout(context) {
        private val image=ImageView(context).apply { scaleType=ImageView.ScaleType.CENTER_CROP }
        private val duration=label("--:--",11f,Color.WHITE).apply {
            setPadding(dp(5),dp(2),dp(5),dp(2))
            background=UiStyle.shape(context,0xB3000000.toInt(),4)
            importantForAccessibility=IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        private var entry:PlaylistStore.Entry?=null
        private var boundKey=""
        private var displayedKey=""
        private var knownDuration=0L
        val boundId:Long get()=entry?.id ?: -1L
        private val retryCover=Runnable { entry?.let { if(pageActive && isShown) requestCover(it,boundKey) } }
        init {
            background=UiStyle.shape(context,UiStyle.RAISED,8); clipToOutline=true
            foreground=UiStyle.ripple(context,Color.TRANSPARENT,8)
            addView(image,LayoutParams(-1,-1))
            addView(duration,LayoutParams(-2,-2,Gravity.BOTTOM or Gravity.END).apply {
                marginEnd=dp(5); bottomMargin=dp(5)
            })
            isFocusable=true
            setOnClickListener { entry?.let(::playEntry) }
            setOnLongClickListener { entry?.let { openActions(it); true } ?: false }
        }
        override fun onMeasure(w:Int,h:Int) {
            val width=MeasureSpec.getSize(w)
            super.onMeasure(w,MeasureSpec.makeMeasureSpec(maxOf(dp(48),width*9/16),MeasureSpec.EXACTLY))
        }
        fun bind(value:PlaylistStore.Entry) {
            knownDuration=if(entry?.id==value.id) maxOf(knownDuration,value.duration) else value.duration
            entry=value.copy(duration=knownDuration)
            duration.text=if(knownDuration>0) UiStyle.duration(knownDuration) else "--:--"
            contentDescription=AppText.PLAY_DURATION_LONG_PRESS_FOR_VIDEO.text(value.name, duration.text)
            val key=covers.key(value)
            if(boundKey==key) return
            cancelCover(); boundKey=key
            val cached=covers.cached(value)
            if(cached!=null) { image.setImageBitmap(cached); displayedKey=key }
            else if(displayedKey!=key) { image.setImageDrawable(CoverPlaceholder()); displayedKey="" }
            requestCover(value,key)
        }
        private fun requestCover(value:PlaylistStore.Entry,key:String) {
            if(!pageActive || !gridMode || key.isEmpty()) return
            if(!covers.load(value,this) { bitmap,info ->
                if(boundKey==key && pageActive && gridMode) {
                    if(bitmap!=null && displayedKey!=key) { image.setImageBitmap(bitmap); displayedKey=key }
                    knownDuration=maxOf(knownDuration,info.duration)
                    entry=info.copy(duration=knownDuration)
                    duration.text=if(knownDuration>0) UiStyle.duration(knownDuration) else "--:--"
                    contentDescription=AppText.PLAY_DURATION_LONG_PRESS_FOR_VIDEO.text(info.name, duration.text)
                }
            }) { removeCallbacks(retryCover); postDelayed(retryCover,400) }
        }
        fun cancelCover() { covers.cancel(this); removeCallbacks(retryCover); boundKey="" }
        override fun onDetachedFromWindow() { cancelCover(); super.onDetachedFromWindow() }
        override fun onAttachedToWindow() { super.onAttachedToWindow(); entry?.let(::bind) }
    }

    private inner class VideoRow:LinearLayout(context) {
        private val image=ImageView(context).apply { scaleType=ImageView.ScaleType.FIT_XY; background=UiStyle.shape(context,UiStyle.RAISED,10); clipToOutline=true }
        private val title=label("",15f,bold=true).apply { maxLines=2; ellipsize=TextUtils.TruncateAt.END }
        private val metadata=label("",11f,UiStyle.MUTED).apply { setSingleLine(); ellipsize=TextUtils.TruncateAt.END }
        private val progress=ProgressBar(context,null,android.R.attr.progressBarStyleHorizontal).apply {
            max=1000; progressTintList=ColorStateList.valueOf(UiStyle.ACCENT); progressBackgroundTintList=ColorStateList.valueOf(UiStyle.LINE)
        }
        private var boundKey=""
        private var displayedKey=""
        private var loadedInfo: PlaylistStore.Entry? = null
        private var entry:PlaylistStore.Entry?=null
        private val retryCover = Runnable { entry?.let { if (pageActive && isShown) requestCover(it,boundKey) } }
        val boundId: Long get() = entry?.id ?: -1L
        var dragTouchX = 0f
            private set
        var dragTouchY = 0f
            private set
        init {
            orientation=HORIZONTAL; gravity=Gravity.CENTER_VERTICAL; setPadding(dp(12),dp(4),dp(8),dp(4))
            background=UiStyle.ripple(context,UiStyle.SURFACE,16)
            addView(image,LinearLayout.LayoutParams(dp(100),dp(56)).apply { marginEnd=dp(16) })
            val words=LinearLayout(context).apply { orientation=VERTICAL; gravity=Gravity.CENTER_VERTICAL }
            words.addView(title,LinearLayout.LayoutParams(-1,-2))
            words.addView(metadata,LinearLayout.LayoutParams(-1,-2).apply { topMargin=dp(5) })
            words.addView(progress,LinearLayout.LayoutParams(-1,dp(2)).apply { topMargin=dp(5) })
            addView(words,LinearLayout.LayoutParams(0,-1,1f))
            addView(UiStyle.icon(context,"play",AppText.RESUME_PLAYBACK.text(),Color.TRANSPARENT) { entry?.let(::playEntry) },LinearLayout.LayoutParams(dp(44),dp(44)).apply { marginStart=dp(12) })
            addView(UiStyle.icon(context,"more",AppText.VIDEO_ACTIONS.text(),Color.TRANSPARENT) { entry?.let { openActions(it) } },LinearLayout.LayoutParams(dp(40),dp(44)))
            image.setOnClickListener { entry?.let(::playEntry) }
            image.setOnLongClickListener { entry?.let { startItemDrag(this, it) } ?: false }
            setOnLongClickListener { entry?.let { startItemDrag(this, it) } ?: false }
        }
        override fun onMeasure(w:Int,h:Int) { super.onMeasure(w,MeasureSpec.makeMeasureSpec(dp(76),MeasureSpec.EXACTLY)) }
        override fun dispatchTouchEvent(event: MotionEvent): Boolean {
            if (event.actionMasked == MotionEvent.ACTION_DOWN || event.actionMasked == MotionEvent.ACTION_MOVE) {
                dragTouchX = event.x; dragTouchY = event.y
            }
            return super.dispatchTouchEvent(event)
        }
        fun bind(value:PlaylistStore.Entry) {
            if (entry?.id != value.id) { animate().cancel(); translationY = 0f }
            entry=value; title.text=value.name; image.contentDescription=AppText.PLAY_237.text(value.name)
            val info = loadedInfo?.takeIf { it.id == value.id && it.revision == value.revision && it.metadataLoaded }
            updateMetadata(if (info == null) value else value.copy(fileSize = info.fileSize, videoMime = info.videoMime,
                metadataLoaded = true, duration = maxOf(value.duration, info.duration)))
            alpha = if (dragging?.id == value.id) 0f else 1f
            val key=covers.key(value)
            if(boundKey==key) return
            cancelCover()
            boundKey=key; image.animate().cancel(); image.alpha=1f
            val cached=covers.cached(value)
            if(cached!=null) { image.setImageBitmap(cached); displayedKey=key }
            else if(displayedKey!=key) { image.setImageDrawable(CoverPlaceholder()); displayedKey="" }
            requestCover(value,key)
        }
        private fun updateMetadata(value:PlaylistStore.Entry) {
            val format=if(VideoProjection.isFlatCover(value.layout,value.projection)) AppText.STANDARD_VIDEO.text()
                else if(value.layout>=0 && value.projection>0) "${VideoProjection.label(value.projection)} ${arrayOf("SBS","TB",AppText.MONO.text())[value.layout.coerceIn(0,2)]}" else AppText.VIDEO.text()
            val status=if(value.position>0) AppText.RESUME_AT.text(UiStyle.duration(value.position)) else AppText.NOT_PLAYED.text()
            val fileInfo = if (value.metadataLoaded) "${VideoMetadata.size(value.fileSize)} · ${VideoMetadata.codec(value.videoMime)}" else AppText.LOADING_DETAILS.text()
            metadata.text = "${fileInfo} · ${status}" + if (value.duration > 0) " / ${UiStyle.duration(value.duration)}" else ""
            // Metadata must not consume long presses with an Android tooltip; the row owns dragging.
            contentDescription=AppText.LONG_PRESS_AND_DRAG_TO_REORDER.text(value.name, format, metadata.text)
            progress.progress=if(value.duration>0) (value.position*1000/value.duration).toInt().coerceIn(0,1000) else 0
        }
        private fun requestCover(value:PlaylistStore.Entry,key:String) {
            if(!pageActive || key.isEmpty()) return
            if(!covers.load(value, this) { bitmap, info ->
                if(boundKey==key) {
                    if(bitmap!=null && displayedKey!=key) {
                        image.setImageBitmap(bitmap)
                        displayedKey=key
                    }
                    loadedInfo = info; updateMetadata(info)
                }
            }) { removeCallbacks(retryCover); postDelayed(retryCover,400) }
        }
        fun cancelCover() { covers.cancel(this); removeCallbacks(retryCover); boundKey="" }
        override fun onDetachedFromWindow() { cancelCover(); super.onDetachedFromWindow() }
        override fun onAttachedToWindow() { super.onAttachedToWindow(); entry?.let(::bind) }
    }

    /** Same palette and motion as the page; no platform PopupMenu or mismatched theme. */
    private fun openActions(entry:PlaylistStore.Entry) {
        if (dragging != null || reorderPending || SystemClock.uptimeMillis() < blockPlayUntil) return
        clearActions(); keyboard().hideSoftInputFromWindow(windowToken,0)
        val layer=FrameLayout(context).apply { elevation=dp(16).toFloat() }; overlay=layer; closingActions=false
        layer.addView(View(context).apply { setBackgroundColor(0x9905080D.toInt()); setOnClickListener { dismissActions() } },LayoutParams(-1,-1))
        val sheet=LinearLayout(context).apply {
            orientation=LinearLayout.VERTICAL; setPadding(dp(18),dp(12),dp(18),dp(12)); background=UiStyle.glass(context,UiStyle.MODAL,22); isClickable=true
        }
        val heading=LinearLayout(context).apply { gravity=Gravity.CENTER_VERTICAL }
        heading.addView(label(AppText.VIDEO_ACTIONS.text(),17f,bold=true),LinearLayout.LayoutParams(0,dp(38),1f))
        heading.addView(UiStyle.icon(context,"close",AppText.CLOSE_ACTION_PANEL.text(),UiStyle.RAISED) { dismissActions() },LinearLayout.LayoutParams(dp(38),dp(38)))
        sheet.addView(heading)
        sheet.addView(label(entry.name,14f).apply { maxLines=2; ellipsize=TextUtils.TruncateAt.MIDDLE },LinearLayout.LayoutParams(-1,-2).apply { topMargin=dp(8); bottomMargin=dp(14) })
        sheet.addView(View(context).apply { setBackgroundColor(UiStyle.LINE) },LinearLayout.LayoutParams(-1,dp(1)).apply { bottomMargin=dp(6) })
        fun item(icon:String,title:String,detail:String,danger:Boolean=false,action:()->Unit) {
            val row=LinearLayout(context).apply { gravity=Gravity.CENTER_VERTICAL; setPadding(dp(8),0,dp(8),0); background=UiStyle.ripple(context,Color.TRANSPARENT,12); isFocusable=true; contentDescription="${title}，${detail}"; setOnClickListener { dismissActions(action) } }
            row.addView(ImageView(context).apply { setImageDrawable(PlayerIcon(icon)) },LinearLayout.LayoutParams(dp(26),dp(26)).apply { marginEnd=dp(14) })
            val words=LinearLayout(context).apply { orientation=LinearLayout.VERTICAL }
            words.addView(label(title,14f,if(danger) UiStyle.DANGER else UiStyle.TEXT,true))
            words.addView(label(detail,11f,UiStyle.MUTED),LinearLayout.LayoutParams(-1,-2).apply { topMargin=dp(4) })
            row.addView(words,LinearLayout.LayoutParams(-1,-2)); sheet.addView(row,LinearLayout.LayoutParams(-1,dp(54)))
        }
        item("restart",AppText.PLAY_FROM_START.text(),AppText.RETURN_TO_THE_BEGINNING.text()) { play(entry,true) }
        item("refresh",AppText.REFRESH_COVER.text(),if(VideoProjection.isFlatCover(entry.layout,entry.projection)) AppText.CAPTURE_A_NEW_VIDEO_FRAME.text() else AppText.CAPTURE_A_NEW_FORWARD_VIEW_FROM.text()) {
            store.submit({ store.refreshCover(entry.id); store.find(entry.uri) }) { result ->
                if (result.getOrNull() != null) covers.invalidate(entry)
                if (pageActive) {
                    val updated=result.getOrNull()
                    if (updated!=null) {
                        changedEntries.put(updated.id,updated)
                        for(i in 0 until list.childCount) (list.getChildAt(i) as? VideoRow)?.let { if(it.boundId==updated.id) it.bind(updated) }
                        for(i in 0 until grid.childCount) (grid.getChildAt(i) as? CoverTile)?.let { if(it.boundId==updated.id) it.bind(updated) }
                    }
                    else Toast.makeText(context,AppText.COULD_NOT_REFRESH_THE_COVER_TRY.text(),Toast.LENGTH_SHORT).show()
                }
            }
        }
        item("remove",AppText.REMOVE_FROM_PLAYLIST.text(),AppText.REMOVE_THE_ENTRY_AND_KEEP_THE.text(),true) {
            remove(entry) { success ->
                if (pageActive) {
                    if (success) { refresh(); notifyRemoved() }
                    else Toast.makeText(context,AppText.COULD_NOT_REMOVE_THE_ENTRY_TRY.text(),Toast.LENGTH_SHORT).show()
                }
            }
        }
        sheet.addView(View(context).apply { setBackgroundColor(UiStyle.LINE) },LinearLayout.LayoutParams(-1,dp(1)).apply { topMargin=dp(10); bottomMargin=dp(12) })
        sheet.addView(label(AppText.VIDEO_DETAILS.text(),14f,bold=true))
        val details=label(VideoMetadata.describe(entry),12f,UiStyle.MUTED).apply {
            setLineSpacing(dp(4).toFloat(),1f); setTextIsSelectable(true)
        }
        sheet.addView(details,LinearLayout.LayoutParams(-1,-2).apply { topMargin=dp(8) })
        covers.loadDetails(entry) { info ->
            if(pageActive && overlay===layer && !closingActions) details.text=VideoMetadata.describe(entry,info)
        }
        val scroll=ScrollView(context).apply { isFillViewport=true; isVerticalScrollBarEnabled=false; addView(sheet) }
        val available=(width-content.paddingLeft-content.paddingRight-dp(12)).coerceAtLeast(1)
        layer.addView(scroll,LayoutParams(minOf(dp(340),available),-1,Gravity.END).apply {
            leftMargin=content.paddingLeft; topMargin=content.paddingTop
            rightMargin=content.paddingRight; bottomMargin=content.paddingBottom
        })
        panel=scroll; addView(layer,LayoutParams(-1,-1)); content.importantForAccessibility=IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        layer.alpha=0f; scroll.translationX=dp(32).toFloat()
        layer.animate().alpha(1f).setDuration(UiStyle.motionDuration(180)).start()
        scroll.animate().translationX(0f).setDuration(UiStyle.motionDuration(240)).setInterpolator(UiStyle.easing).start()
        val paneTitle = AppText.VIDEO_ACTIONS_254.text(entry.name)
        if (android.os.Build.VERSION.SDK_INT >= 28) scroll.accessibilityPaneTitle = paneTitle
        else scroll.announceForAccessibility(paneTitle)
        scroll.requestFocus()
    }
    private fun dismissActions(action:(()->Unit)?=null) {
        val layer=overlay ?: return
        if(closingActions) return
        closingActions=true
        panel?.animate()?.translationX(dp(24).toFloat())?.setDuration(UiStyle.motionDuration(160))?.setInterpolator(UiStyle.easing)?.start()
        layer.animate().alpha(0f).setDuration(UiStyle.motionDuration(160)).withEndAction { if(overlay===layer) { clearActions(); action?.invoke() } }.start()
    }
    private fun clearActions() {
        covers.cancelDetails()
        overlay?.animate()?.cancel(); panel?.animate()?.cancel(); overlay?.let { removeView(it) }; overlay=null; panel=null; closingActions=false
        content.importantForAccessibility=IMPORTANT_FOR_ACCESSIBILITY_AUTO
    }
    private fun keyboard()=context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
    private fun dp(value:Int)=UiStyle.dp(context,value)
    private fun label(value:String,size:Float,color:Int=UiStyle.TEXT,bold:Boolean=false)=UiStyle.label(context,value,size,color,bold)
}
