package com.holenet.codge

import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.os.Handler
import android.view.MotionEvent
import android.view.View
import android.view.ViewTreeObserver
import kotlinx.android.synthetic.main.activity_game.*

class GameActivity : AppCompatActivity() {
    var gameView: GameView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_game)

        val onStartPlayHandler = Handler {
            bTccw.visibility = View.VISIBLE
            bTcw.visibility = View.VISIBLE
            bTjump.visibility = View.VISIBLE

            bTleft.visibility = View.GONE
            bTright.visibility = View.GONE
            true
        }
        val onGameOverHandler = Handler {
            bTccw.visibility = View.GONE
            bTcw.visibility = View.GONE
            bTjump.visibility = View.GONE

            bTleft.visibility = View.VISIBLE
            bTright.visibility = View.VISIBLE
            true
        }
        val onPlayerTurnHandler = Handler {
            (if (it.what == Direction.CW.rotation) bTccw else bTcw).visibility = View.GONE
            (if (it.what == Direction.CCW.rotation) bTccw else bTcw).visibility = View.VISIBLE
            true
        }

        fLgame.viewTreeObserver.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                fLgame.viewTreeObserver.removeOnGlobalLayoutListener(this)
                with(GameView(this@GameActivity, fLgame.width / 2)) {
                    gameView = this
                    fLgame.addView(this)

                    // set callbacks
                    onStartPlay = {dir ->
                        onStartPlayHandler.sendEmptyMessage(dir.rotation)
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
                            if (startDirection == null) startDirection = Direction.CCW
                        }
                        true
                    }
                    bTright.setOnTouchListener { v, event ->
                        if (event.action == MotionEvent.ACTION_DOWN) {
                            if (startDirection == null) startDirection = Direction.CW
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
                            toJump = true
                        } else if (event.action == MotionEvent.ACTION_UP) {
                            toJump = false
                        }
                        true
                    }

                    // start
                    onResume()
                }
            }
        })
    }

    private fun changeMode(isGameOver: Boolean) {

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