package com.holenet.codge

import android.animation.Animator
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.arch.lifecycle.Observer
import android.arch.lifecycle.ViewModelProviders
import android.content.Context
import android.graphics.*
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.os.Handler
import android.support.design.widget.FloatingActionButton
import android.support.v4.view.PagerAdapter
import android.support.v4.view.ViewPager
import android.support.v7.app.AlertDialog
import android.support.v7.widget.GridLayoutManager
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.view.*
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import com.holenet.codge.GameView.Companion.SKIP_MILLIS
import kotlinx.android.synthetic.main.activity_game.*
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt

class GameActivity : AppCompatActivity() {
    companion object {
        const val FADE_IN_TIME = 1500
        const val UI_ANIM_TIME = 500
    }
    var gameView: GameView? = null

    var customX = 0f
    var rightX = 0f
    var smallWidth = 0
    var bigWidth = 0
    var bigHeight = 0
    var entireWidth = 0

    private lateinit var recordViewModel: RecordViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_game)

        // fade in
        ValueAnimator.setFrameDelay(24)
        fLfade.setLayerType(View.LAYER_TYPE_HARDWARE, null)
        fLfade.postDelayed({
            fLfade.setLayerType(View.LAYER_TYPE_NONE, null)
        }, FADE_IN_TIME + 100L)
        with (ValueAnimator.ofInt(0, FADE_IN_TIME)) {
            duration = FADE_IN_TIME.toLong()
            addUpdateListener {
                val value = it.animatedFraction
                fLfade.alpha = 1 - value
            }
            start()
        }

        // record database
        recordViewModel = ViewModelProviders.of(this).get(RecordViewModel::class.java)

        // ranking records
        with (rVranking) {
            val linearLayoutManager = LinearLayoutManager(context)
            layoutManager = linearLayoutManager
            val recordAdapter = RecordRecyclerViewAdapter(context)
            adapter = recordAdapter
            overScrollMode = View.OVER_SCROLL_NEVER
            recordViewModel.allRecords.observe(this@GameActivity, Observer<List<Record>> {
                if (it != null) recordAdapter.records = it
            })
        }

        // UI handler
        val onPrepareHandler = Handler {
            (if (it.what == Direction.CW.rotation) bTccw else bTcw).visibility = View.INVISIBLE
            (if (it.what == Direction.CCW.rotation) bTccw else bTcw).visibility = View.VISIBLE
            changeGameMode(true)
            true
        }
        val onGameOverHandler = Handler {
            changeGameMode(false)
            true
        }
        val onPlayerTurnHandler = Handler {
            (if (it.what == Direction.CW.rotation) bTccw else bTcw).visibility = View.INVISIBLE
            (if (it.what == Direction.CCW.rotation) bTccw else bTcw).visibility = View.VISIBLE
            true
        }

        // init GameView
        fLgame.viewTreeObserver.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                fLgame.viewTreeObserver.removeOnGlobalLayoutListener(this)

                // save some properties for UI animation
                customX = bTcustom.x
                rightX = bTright.x
                smallWidth = bTranking.width
                bigWidth = cLcontrol.width / 2
                bigHeight = cLcontrol.height
                entireWidth = fLgame.width

                // customize view pager
                with (vPcustom) {
                    adapter = ViewPagerAdapter(this@GameActivity)
                    overScrollMode = View.OVER_SCROLL_NEVER
                    addOnPageChangeListener(object : ViewPager.OnPageChangeListener {
                        override fun onPageScrollStateChanged(state: Int) {}
                        override fun onPageScrolled(position: Int, positionOffset: Float, positionOffsetPixels: Int) {}
                        override fun onPageSelected(position: Int) {
                            gameView?.highlightedType = positionToType(position)
                        }
                    })
                }

                with(GameView(this@GameActivity, fLgame.width / 2, recordViewModel)) {
                    gameView = this
                    fLgame.addView(this)

                    // set callbacks
                    onPrepare = { dir ->
                        onPrepareHandler.sendEmptyMessage(dir.rotation)
                    }
                    onGameOver = {
                        onGameOverHandler.sendEmptyMessage(0)
                    }
                    onPlayerTurn = {dir ->
                        onPlayerTurnHandler.sendEmptyMessage(dir.rotation)
                    }

                    // buttons on ready
                    bTleft.setOnTouchListener { _, event ->
                        if (event.action == MotionEvent.ACTION_DOWN) {
                            if (gameMode == GameView.Companion.GameMode.READY) startDirection = Direction.CW
                        }
                        true
                    }
                    bTright.setOnTouchListener { _, event ->
                        if (event.action == MotionEvent.ACTION_DOWN) {
                            if (gameMode == GameView.Companion.GameMode.READY) startDirection = Direction.CCW
                        }
                        true
                    }

                    // buttons on play
                    bTccw.setOnTouchListener { _, event ->
                        if (event.action == MotionEvent.ACTION_DOWN && !isReplaying) {
                            toTurn = !toTurn
                        }
                        true
                    }
                    bTcw.setOnTouchListener { _, event ->
                        if (event.action == MotionEvent.ACTION_DOWN && !isReplaying) {
                            toTurn = !toTurn
                        }
                        true
                    }
                    bTjump.setOnTouchListener { _, event ->
                        if (event.action == MotionEvent.ACTION_DOWN && !isReplaying) {
                            if (toJumpOff) toJumpOff = false
                            toJumpOn = true
                        } else if (event.action == MotionEvent.ACTION_UP && !isReplaying) {
                            toJumpOff = true
                        }
                        true
                    }

                    // buttons custom
                    bTcustom.setOnClickListener {
                        changeCustomMode(true)
                    }
                    bTbackLeft.setOnClickListener {
                        changeCustomMode(false)
                    }

                    // buttons ranking
                    bTranking.setOnClickListener {
                        changeRankingMode(true)
                    }
                    bTbackRight.setOnClickListener {
                        changeRankingMode(false)
                    }
                    bTsort.setOnClickListener {
                        with (rVranking.adapter as RecordRecyclerViewAdapter) {
                            refresh((this.sortType + 1) % 2)
                        }
                    }

                    // start
                    onResume()
                }
            }
        })
    }

    fun turnOnHardwareAcceleration(vararg views: View) {
        for (view in views) view.setLayerType(View.LAYER_TYPE_HARDWARE, null)
    }
    fun turnOffHardwareAcceleration(vararg views: View) {
        for (view in views) view.setLayerType(View.LAYER_TYPE_NONE, null)
    }

    fun lockButtons(vararg buttons: View) {
        for (button in buttons) button.isEnabled = false
    }
    fun unlockButtons(vararg buttons: View) {
        for (button in buttons) button.isEnabled = true
    }

    private fun dismissTitle() {
        iVtitle.animate().translationY(-iVtitle.height.toFloat()).withLayer().setDuration(UI_ANIM_TIME.toLong()).start()
    }

    private var gameAnim: ValueAnimator? = null
    private var currentGameValue = 0f
    private fun changeGameMode(onPlay: Boolean) {
        if (gameAnim?.isRunning == true) gameAnim?.cancel()

        // title animation (only for the first play)
        if (gameView?.firstPlay == true && onPlay)
            dismissTitle()

        val views = arrayOf(bTranking, bTcustom, bTleft, bTccw, bTcw, bTright, bTjump)

        ValueAnimator.ofFloat(currentGameValue, if (onPlay) 1f else 0f).apply {
            duration = (UI_ANIM_TIME * abs(currentGameValue - if (onPlay) 1f else 0f)).toLong()
            addUpdateListener {
                val value = it.animatedValue as Float
                currentGameValue = value

                bTranking.x = -smallWidth * value
                bTcustom.x = customX + smallWidth * value
                bTleft.x = -bigWidth * value
                bTright.x = rightX + bigWidth * value

                val invertedValue = 1f - value
                bTccw.y = bigHeight * invertedValue
                bTcw.y = bigHeight * invertedValue
                bTjump.y = bigHeight * invertedValue
            }
            addListener(object : Animator.AnimatorListener {
                override fun onAnimationStart(animation: Animator?) { turnOnHardwareAcceleration(*views); lockButtons(bTranking, bTcustom) }
                override fun onAnimationEnd(animation: Animator?) { turnOffHardwareAcceleration(*views); unlockButtons(bTranking, bTcustom) }
                override fun onAnimationCancel(animation: Animator?) { onAnimationEnd(null) }
                override fun onAnimationRepeat(animation: Animator?) {}
            })
            gameAnim = this
        }.start()
    }

    private var customAnim: ValueAnimator? = null
    private var currentCustomValue = 0f
    private fun changeCustomMode(onCustom: Boolean) {
        if (customAnim?.isRunning == true) customAnim?.cancel()

        val views = arrayOf(bTranking, bTcustom, bTleft, bTright, bTbackLeft, cLcustom)

        ValueAnimator.ofFloat(currentCustomValue, if (onCustom) 1f else 0f).apply {
            duration = (UI_ANIM_TIME * abs(currentCustomValue - if (onCustom) 1f else 0f)).toLong()
            addUpdateListener {
                val value = it.animatedValue as Float
                currentCustomValue = value

                bTranking.x = -smallWidth * value
                bTcustom.x = customX - entireWidth * value
                bTleft.x = -bigWidth * value
                bTright.x = rightX - entireWidth * value

                val invertedValue = 1f - value
                bTbackLeft.x = (entireWidth + customX) * invertedValue
                cLcustom.x = entireWidth * invertedValue
            }
            addListener(object : Animator.AnimatorListener {
                override fun onAnimationStart(animation: Animator?) {
                    turnOnHardwareAcceleration(*views)
                    lockButtons(bTleft, bTright)
                    if (onCustom)
                        gameView?.highlightedType = positionToType(vPcustom.currentItem)
                    else
                        gameView?.highlightedType = null
                }
                override fun onAnimationEnd(animation: Animator?) {
                    turnOffHardwareAcceleration(*views)
                    unlockButtons(bTleft, bTright)
                }
                override fun onAnimationCancel(animation: Animator?) {
                    onAnimationEnd(null)
                }
                override fun onAnimationRepeat(animation: Animator?) {}
            })
            customAnim = this
        }.start()
    }

    private var rankingAnim: ValueAnimator? = null
    private var currentRankingValue = 0f
    private fun changeRankingMode(onRanking: Boolean, onEnd: (() -> Unit)? = null) {
        if (rankingAnim?.isRunning == true) rankingAnim?.cancel()

        val views = arrayOf(bTranking, bTcustom, bTleft, bTright, bTbackRight, bTsort, cLranking)

        ValueAnimator.ofFloat(currentRankingValue, if (onRanking) 1f else 0f).apply {
            duration = (UI_ANIM_TIME * abs(currentRankingValue - if (onRanking) 1f else 0f)).toLong()
            addUpdateListener {
                val value = it.animatedValue as Float
                currentRankingValue = value

                bTranking.x = entireWidth * value
                bTcustom.x = customX + smallWidth * value
                bTleft.x = entireWidth * value
                bTright.x = rightX + bigWidth * value
                fLfadeGame.alpha = value * 0.5f

                val invertedValue = 1f - value
                bTbackRight.x = customX - (customX + entireWidth) * invertedValue
                bTsort.x = - (customX + entireWidth) * invertedValue
                cLranking.x = -entireWidth * invertedValue
            }
            addListener(object : Animator.AnimatorListener {
                override fun onAnimationStart(animation: Animator?) {
                    turnOnHardwareAcceleration(*views)
                    lockButtons(bTleft, bTright)
                }
                override fun onAnimationEnd(animation: Animator?) {
                    turnOffHardwareAcceleration(*views)
                    unlockButtons(bTleft, bTright)
                    if (onRanking) {
                        (rVranking.adapter as RecordRecyclerViewAdapter).refresh()
                    }
                    if (onEnd != null) onEnd()
                }
                override fun onAnimationCancel(animation: Animator?) {
                    onAnimationEnd(null)
                }
                override fun onAnimationRepeat(animation: Animator?) {}
            })
            rankingAnim = this
        }.start()
    }

    override fun onPause() {
        super.onPause()
        gameView?.onPause()
    }

    override fun onResume() {
        super.onResume()
        gameView?.onResume()
    }

    override fun onBackPressed() {
        if (gameView?.gameMode == GameView.Companion.GameMode.PLAYING) {
            gameView?.toKillSelf = true
        } else {
            super.onBackPressed()
        }
    }

    fun positionToType(position: Int) = when (position) {
        0 -> CustomType.PlayerBaseColor
        1 -> CustomType.PlayerPatternColor
        2 -> CustomType.BallColor
        else -> CustomType.PlayerPatternShape
    }

    inner class ViewPagerAdapter(private val context: Context) : PagerAdapter() {
        private val columnsNum = 5
        private val eachWidth = ((vPcustom.width * 0.85f) / columnsNum).roundToInt()
        private val imageFrame = BitmapFactory.decodeResource(context.resources, R.drawable.item_frame)
        private val imageButtonPlus = BitmapFactory.decodeResource(context.resources, R.drawable.button_plus)

        init {
            CustomManager.load(context)
        }

        override fun instantiateItem(container: ViewGroup, position: Int): Any {
            val view = LayoutInflater.from(context).inflate(R.layout.fragment_picker, container, false)
            with (view.findViewById(R.id.rVpicker) as RecyclerView) {
                layoutManager = GridLayoutManager(context, columnsNum)
                adapter = CustomRecyclerViewAdapter(context, positionToType(position), eachWidth, imageFrame, imageButtonPlus)
                overScrollMode = View.OVER_SCROLL_NEVER
            }
            container.addView(view)
            return view
        }

        override fun destroyItem(container: ViewGroup, position: Int, view: Any) {
            container.removeView(view as View)
        }

        override fun isViewFromObject(view: View, `object`: Any): Boolean {
            return view == `object`
        }

        override fun getCount(): Int {
            return 3
        }
    }

    inner class CustomRecyclerViewAdapter(private val context: Context, val type: CustomType, private val eachWidth: Int, private val imageFrame: Bitmap, private val imageButtonPlus: Bitmap) : RecyclerView.Adapter<CustomRecyclerViewAdapter.ViewHolder>() {
        private val inflater = LayoutInflater.from(context)
        private val colors: MutableList<Int> = CustomManager.getColors(type)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            return ViewHolder(inflater.inflate(R.layout.item_picker, parent, false))
        }

        @SuppressLint("InflateParams")
        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.apply {
                val imageBitmap = Bitmap.createBitmap(eachWidth, eachWidth, Bitmap.Config.ARGB_8888)
                with (Canvas(imageBitmap)) {
                    scale(0.9f, 0.9f, eachWidth / 2f, eachWidth / 2f)
                    if (position == colors.size) {
                        drawBitmap(imageButtonPlus, Rect(0, 0, imageButtonPlus.width, imageButtonPlus.height), Rect(0, 0, eachWidth, eachWidth), null)
                    } else {
                        drawCircle(eachWidth / 2f, eachWidth / 2f, eachWidth / 2f, Paint().apply { color = colors[position] })
                        drawBitmap(imageFrame, Rect(0, 0, imageFrame.width, imageFrame.height), Rect(0, 0, eachWidth, eachWidth), null)
                    }
                }
                iBitem.setImageBitmap(imageBitmap)

                if (position == colors.size) {
                    iBitem.setOnClickListener {
                        AlertDialog.Builder(context).apply {
                            val view = layoutInflater.inflate(R.layout.dialog_color_picker, null)
                            val colorPickerView = view.findViewById<ColorPickerView>(R.id.colorPickerView)
                            setView(view)
                            setCancelable(false)
                            setPositiveButton("Add") { _, _ ->
                                CustomManager.addColor(type, colorPickerView.color)
                                CustomManager.updateCurrentColor(type, position)
                                notifyDataSetChanged()
                                gameView?.refreshCustomization(type)
                                CustomManager.save(context, type)
                            }
                            setNegativeButton("Cancel", null)
                        }.create().show()
                    }
                } else {
                    iBitem.setOnClickListener {
                        CustomManager.updateCurrentColor(type, position)
                        gameView?.refreshCustomization(type)
                        CustomManager.save(context, type)
                    }
                    iBitem.setOnLongClickListener {
                        AlertDialog.Builder(context).apply {
                            val view = layoutInflater.inflate(R.layout.dialog_color_picker, null)
                            val colorPickerView = view.findViewById<ColorPickerView>(R.id.colorPickerView)
                            colorPickerView.color = colors[position]
                            setView(view)
                            setCancelable(false)
                            setPositiveButton("Apply") { _, _ ->
                                CustomManager.changeColor(type, position, colorPickerView.color)
                                gameView?.refreshCustomization(type)
                                notifyDataSetChanged()
                                CustomManager.save(context, type)
                            }
                            setNeutralButton("Delete") { _, _ ->
                                CustomManager.deleteColor(type, position)
                                gameView?.refreshCustomization(type)
                                notifyDataSetChanged()
                                CustomManager.save(context, type)
                            }
                            setNegativeButton("Cancel", null)
                        }.create().show()
                        true
                    }
                }
            }
        }

        override fun getItemCount(): Int {
            return colors.size + 1
        }

        inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val iBitem: ImageButton = itemView.findViewById(R.id.iBitem)
        }
    }

    inner class RecordRecyclerViewAdapter(context: Context) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        private val inflater = LayoutInflater.from(context)
        var records: List<Record> = ArrayList()
        private var currentSize = 0
        var loading = false; private set

        val SORT_SCORE = 0
        val SORT_TIME = 1
        var sortType = context.getSharedPreferences("record", 0).getInt("sort_type", SORT_SCORE); private set

        override fun getItemViewType(position: Int): Int {
            return if (position < currentSize) 0 else -1
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            return if (viewType == 0)
                ItemViewHolder(inflater.inflate(R.layout.item_record, parent, false))
            else
                LoadViewHolder(inflater.inflate(R.layout.item_load_more, parent, false))
        }

        @SuppressLint("SimpleDateFormat")
        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            val record = records[position]
            if (holder is ItemViewHolder)
                holder.apply {
                    val time = "${GameView.timeFormat.format(record.score * SKIP_MILLIS / 1000f)}s"
                    tVscore.text = time
                    val calendar = Calendar.getInstance().apply { timeInMillis = record.recordedAtMillis }
                    tVtime.text = SimpleDateFormat("yyyy-MM-dd a hh:mm:ss").format(calendar.time)
                    tVrank.text = (position + 1).toString()
                    iBreplay.setOnClickListener {
                        changeRankingMode(false) {
                            gameView?.startReplay(record)
                        }
                    }
                }
            else if (holder is LoadViewHolder)
                holder.apply {
                    fABmore.setOnClickListener {
                        fABmore.visibility = View.GONE
                        pBloading.visibility = View.VISIBLE
                        loadMore {
                            fABmore.visibility = View.VISIBLE
                            pBloading.visibility = View.GONE
                        }
                    }
                    if (position == 0) {
                        fABmore.visibility = View.GONE
                        pBloading.visibility = View.VISIBLE
                        Handler { fABmore.performClick() }.sendEmptyMessageDelayed(0, 100)
                    }
                }
        }

        override fun getItemCount(): Int {
            return min(currentSize + 1, records.size)
        }

        fun refresh(sortType: Int = this.sortType) {
            loading = true
            if (this.sortType != sortType) {
                this.sortType = sortType
                applicationContext.getSharedPreferences("record", 0).edit().apply { putInt("sort_type", sortType) }.apply()
                currentSize = 0
            }
            records = when (sortType) {
                SORT_SCORE -> records.sortedByDescending(Record::score)
                SORT_TIME -> records.sortedByDescending(Record::recordedAtMillis)
                else -> records
            }
            notifyDataSetChanged()
            loading = false
        }

        private fun loadMore(onEnd: (() -> Unit)? = null) {
            loading = true
            currentSize += 10
            notifyDataSetChanged()
            loading = false
            if (onEnd != null) onEnd()
        }

        inner class ItemViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val tVscore: TextView = itemView.findViewById(R.id.tVscore)
            val tVtime: TextView = itemView.findViewById(R.id.tVtime)
            val tVrank: TextView = itemView.findViewById(R.id.tVrank)
            val iBreplay: ImageButton = itemView.findViewById(R.id.iBreplay)
            val iBdelete: ImageButton = itemView.findViewById(R.id.iBdelete)
        }

        inner class LoadViewHolder(loadView: View) : RecyclerView.ViewHolder(loadView) {
            val pBloading: ProgressBar = itemView.findViewById(R.id.pBloading)
            val fABmore: FloatingActionButton = itemView.findViewById(R.id.fABmore)
        }
    }
}
