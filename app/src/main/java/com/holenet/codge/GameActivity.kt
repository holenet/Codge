package com.holenet.codge

import android.animation.Animator
import android.animation.ValueAnimator
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.os.Handler
import android.view.MotionEvent
import android.view.View
import android.view.ViewTreeObserver
import kotlinx.android.synthetic.main.activity_game.*
import kotlin.math.abs

class GameActivity : AppCompatActivity() {
    companion object {
        const val FADE_IN_TIME = 1500
        const val UI_ANIM_TIME = 500
    }
    var gameView: GameView? = null

    var costumX = 0f
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
                costumX = bTcostum.x
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
                val invertedValue = 1f - value

                bTscore.x = -smallWidth * value
                bTcostum.x = costumX + smallWidth * value
                bTleft.x = -bigWidth * value
                bTright.x = rightX + bigWidth * value

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
                val invertedValue = 1f - value

                bTscore.x = -smallWidth * value
                bTcostum.x = costumX - entireWidth * value
                bTleft.x = -bigWidth * value
                bTright.x = rightX - entireWidth * value
            }
            addListener(object : Animator.AnimatorListener {
                override fun onAnimationStart(animation: Animator?) { turnOnHardwareAcceleration(*views); lockButtons(bTleft, bTright) }
                override fun onAnimationEnd(animation: Animator?) { turnOffHardwareAcceleration(*views); unlockButtons(bTleft, bTright) }
                override fun onAnimationCancel(animation: Animator?) { turnOffHardwareAcceleration(*views); unlockButtons(bTleft, bTright) }
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
}
