package com.holenet.codge

import android.animation.Animator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.os.Handler
import android.support.v4.view.PagerAdapter
import android.support.v4.view.ViewPager
import android.support.v7.widget.GridLayoutManager
import android.support.v7.widget.RecyclerView
import android.view.*
import android.widget.ImageButton
import kotlinx.android.synthetic.main.activity_game.*
import kotlin.math.abs
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
                customX = bTcostum.x
                rightX = bTright.x
                smallWidth = bTscore.width
                bigWidth = cLcontrol.width / 2
                bigHeight = cLcontrol.height
                entireWidth = fLgame.width

                with(GameView(this@GameActivity, fLgame.width / 2)) {
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
                    bTleft.setOnTouchListener { v, event ->
                        if (event.action == MotionEvent.ACTION_DOWN) {
                            if (gameMode == GameView.Companion.GameMode.READY) startDirection = Direction.CW
                        }
                        true
                    }
                    bTright.setOnTouchListener { v, event ->
                        if (event.action == MotionEvent.ACTION_DOWN) {
                            if (gameMode == GameView.Companion.GameMode.READY) startDirection = Direction.CCW
                        }
                        true
                    }

                    // buttons on play
                    bTccw.setOnTouchListener { v, event ->
                        if (event.action == MotionEvent.ACTION_DOWN) {
                            toTurn = !toTurn
                        }
                        true
                    }
                    bTcw.setOnTouchListener { v, event ->
                        if (event.action == MotionEvent.ACTION_DOWN) {
                            toTurn = !toTurn
                        }
                        true
                    }
                    bTjump.setOnTouchListener { v, event ->
                        if (event.action == MotionEvent.ACTION_DOWN) {
                            if (toJumpOff) toJumpOff = false
                            toJumpOn = true
                        } else if (event.action == MotionEvent.ACTION_UP) {
                            toJumpOff = true
                        }
                        true
                    }

                    // buttons custom
                    bTcostum.setOnClickListener {
                        changeCustomMode(true)
                    }
                    bTback.setOnClickListener {
                        changeCustomMode(false)
                    }

                    // start
                    onResume()
                }

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

    private var gameAnim: ValueAnimator? = null
    private var currentGameValue = 0f
    private fun changeGameMode(onPlay: Boolean) {
        if (gameAnim?.isRunning == true) gameAnim?.cancel()

        // title animation (only for the first play)
        if (gameView?.firstPlay == true && onPlay)
            iVtitle.animate().translationY(-iVtitle.height.toFloat()).withLayer().setDuration(UI_ANIM_TIME.toLong()).start()

        val views = arrayOf(bTscore, bTcostum, bTleft, bTccw, bTcw, bTright, bTjump)

        ValueAnimator.ofFloat(currentGameValue, if (onPlay) 1f else 0f).apply {
            duration = (UI_ANIM_TIME * abs(currentGameValue - if (onPlay) 1f else 0f)).toLong()
            addUpdateListener {
                val value = it.animatedValue as Float
                currentGameValue = value

                bTscore.x = -smallWidth * value
                bTcostum.x = customX + smallWidth * value
                bTleft.x = -bigWidth * value
                bTright.x = rightX + bigWidth * value

                val invertedValue = 1f - value
                bTccw.y = bigHeight * invertedValue
                bTcw.y = bigHeight * invertedValue
                bTjump.y = bigHeight * invertedValue
            }
            addListener(object : Animator.AnimatorListener {
                override fun onAnimationStart(animation: Animator?) { turnOnHardwareAcceleration(*views); lockButtons(bTscore, bTcostum) }
                override fun onAnimationEnd(animation: Animator?) { turnOffHardwareAcceleration(*views); unlockButtons(bTscore, bTcostum) }
                override fun onAnimationCancel(animation: Animator?) { turnOffHardwareAcceleration(*views); unlockButtons(bTscore, bTcostum) }
                override fun onAnimationRepeat(animation: Animator?) {}
            })
            gameAnim = this
        }.start()
    }

    private var customAnim: ValueAnimator? = null
    private var currentCustomValue = 0f
    private fun changeCustomMode(onCustom: Boolean) {
        if (customAnim?.isRunning == true) customAnim?.cancel()

        val views = arrayOf(bTscore, bTcostum, bTleft, bTright)

        ValueAnimator.ofFloat(currentCustomValue, if (onCustom) 1f else 0f).apply {
            duration = (UI_ANIM_TIME * abs(currentCustomValue - if (onCustom) 1f else 0f)).toLong()
            addUpdateListener {
                val value = it.animatedValue as Float
                currentCustomValue = value

                bTscore.x = -smallWidth * value
                bTcostum.x = customX - entireWidth * value
                bTleft.x = -bigWidth * value
                bTright.x = rightX - entireWidth * value

                val invertedValue = 1f - value
                bTback.x = (entireWidth + customX) * invertedValue
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
                override fun onAnimationEnd(animation: Animator?) { turnOffHardwareAcceleration(*views); unlockButtons(bTleft, bTright); }
                override fun onAnimationCancel(animation: Animator?) { onAnimationEnd(null) }
                override fun onAnimationRepeat(animation: Animator?) {}
            })
            customAnim = this
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

        override fun instantiateItem(container: ViewGroup, position: Int): Any {
            val view = LayoutInflater.from(context).inflate(R.layout.fragment_picker, container, false)
            with (view.findViewById(R.id.rVpicker) as RecyclerView) {
                layoutManager = GridLayoutManager(context, columnsNum)
                adapter = RecyclerViewAdapter(context, positionToType(position), eachWidth, imageFrame)
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

    inner class RecyclerViewAdapter(private val context: Context, val type: CustomType, private val eachWidth: Int, private val imageFrame: Bitmap) : RecyclerView.Adapter<RecyclerViewAdapter.ViewHolder>() {
        private val inflater = LayoutInflater.from(context)
        private val colors: MutableList<Int> = CustomManager.getColors(type)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            return ViewHolder(inflater.inflate(R.layout.item_picker, parent, false))
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.apply {
                val imageBitmap = Bitmap.createBitmap(eachWidth, eachWidth, Bitmap.Config.ARGB_8888)
                with (Canvas(imageBitmap)) {
                    scale(0.9f, 0.9f, eachWidth / 2f, eachWidth / 2f)
                    drawCircle(eachWidth / 2f, eachWidth / 2f, eachWidth / 2f, Paint().apply { color = colors[position] })
                    drawBitmap(imageFrame, Rect(0, 0, imageFrame.width, imageFrame.height), Rect(0, 0, eachWidth, eachWidth), null)
                }
                iBitem.setImageBitmap(imageBitmap)
                iBitem.setOnClickListener {
                    CustomManager.updateCurrentColor(type, position)
                    gameView?.refreshCustomization(type)
                    CustomManager.save(context, type)
                }
            }
        }

        override fun getItemCount(): Int {
            return colors.size
        }

        inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val iBitem : ImageButton = itemView.findViewById(R.id.iBitem)
        }
    }
}
