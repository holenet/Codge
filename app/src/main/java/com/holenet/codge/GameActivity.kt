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
            changeMode(true)
            true
        }
        val onGameOverHandler = Handler {
            changeMode(false)
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
                colorX = bTcolor.x
                rightX = bTright.x

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
                            if (toJumpOn) toJumpOn = false
                            toJumpOff = true
                        }
                        true
                    }

                    // start
                    onResume()
                }
            }
        })
    }

    var anim: ValueAnimator? = null
    private var currentValue = 0f
    var colorX = 0f
    var rightX = 0f
    private fun changeMode(onPlay: Boolean) {
        // for hardware acceleration
        fun setLayerType(type: Int) {
            bTscore.setLayerType(type, null)
            bTcolor.setLayerType(type, null)
            bTleft.setLayerType(type, null)
            bTccw.setLayerType(type, null)
            bTcw.setLayerType(type, null)
            bTright.setLayerType(type, null)
            bTjump.setLayerType(type, null)
        }

        // basic animation setting
        val animTime = UI_ANIM_TIME
        if (anim?.isRunning == true) anim?.cancel()
        ValueAnimator.setFrameDelay(24)

        if (gameView?.firstPlay == true && onPlay)
            iVtitle.animate().translationY(-iVtitle.height.toFloat()).withLayer().setDuration(animTime.toLong()).start()

        // save some properties
        val smallWidth = bTscore.width
        val bigWidth = cLcontrol.width / 2
        val bigHeight = cLcontrol.height

        // hardware acceleration turn on
        setLayerType(View.LAYER_TYPE_HARDWARE)

        with (ValueAnimator.ofFloat(currentValue, if (onPlay) 1f else 0f)) {
            duration = (animTime * abs(currentValue - if (onPlay) 1f else 0f)).toLong()
            addUpdateListener {
                val value = it.animatedValue as Float
                currentValue = value
                val invertedValue = 1f - value
                bTscore.x = -smallWidth * value
                bTcolor.x = colorX + smallWidth * value
                bTleft.x = -bigWidth * value
                bTccw.y = bigHeight * invertedValue
                bTcw.y = bigHeight * invertedValue
                bTright.x = rightX + bigWidth * value
                bTjump.y = bigHeight * invertedValue
            }
            // hardware acceleration turn off
            addListener(object : Animator.AnimatorListener {
                override fun onAnimationRepeat(animation: Animator?) {}
                override fun onAnimationEnd(animation: Animator?) { setLayerType(View.LAYER_TYPE_NONE) }
                override fun onAnimationCancel(animation: Animator?) { setLayerType(View.LAYER_TYPE_NONE) }
                override fun onAnimationStart(animation: Animator?) {}
            })
            start()
            anim = this
        }
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
