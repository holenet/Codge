package com.holenet.codge

import android.animation.Animator
import android.animation.ValueAnimator
import android.content.Intent
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import kotlinx.android.synthetic.main.activity_loader.*
import kotlin.math.abs

class LoaderActivity : AppCompatActivity() {
    companion object {
        const val ANIM_TIME = 4000
        const val FADE_TIME = 1200
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_loader)

        // start some loading tasks

        ValueAnimator.setFrameDelay(24)
        with (ValueAnimator.ofInt(0, ANIM_TIME)) {
            duration = ANIM_TIME.toLong()
            addUpdateListener {
                val value = it.animatedValue as Int
                iVlogo.alpha = (ANIM_TIME / 2f - abs(value - ANIM_TIME / 2f)) / FADE_TIME
            }
            addListener(object : Animator.AnimatorListener {
                override fun onAnimationEnd(animation: Animator?) {
                    onCompleteLoad()
                }
                override fun onAnimationRepeat(animation: Animator?) {}
                override fun onAnimationCancel(animation: Animator?) {}
                override fun onAnimationStart(animation: Animator?) {}
            })
            start()
        }
    }

    private fun onCompleteLoad() {
        val intent = Intent(this, GameActivity::class.java)
        startActivityForResult(intent, 0)
        overridePendingTransition(0, 0)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        finish()
    }
}
