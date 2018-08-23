package com.holenet.codge

import android.animation.Animator
import android.animation.ValueAnimator
import android.content.Intent
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import kotlinx.android.synthetic.main.activity_loader.*
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos

class LoaderActivity : AppCompatActivity() {
    companion object {
        const val ANIM_TIME = 3000
        const val FADE_TIME = 1100
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_loader)

        // start some loading tasks

        ValueAnimator.setFrameDelay(24)
        with (ValueAnimator.ofInt(0, ANIM_TIME)) {
            duration = ANIM_TIME.toLong()
            addUpdateListener {
                val time = it.animatedValue as Int
                val symTime = ANIM_TIME / 2 - abs(time - ANIM_TIME / 2)
                iVlogo.alpha = if (symTime < FADE_TIME) 0.5f - 0.5f * cos(symTime * PI / FADE_TIME).toFloat() else 1f
                val scale = if (time < ANIM_TIME / 2) 1f - 0.03f * (1 + cos(symTime * 2 * PI / ANIM_TIME)).toFloat() else 1f
                iVlogo.scaleX = scale
                iVlogo.scaleY = scale
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
