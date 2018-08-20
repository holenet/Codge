package com.holenet.codge

import android.util.Log
import kotlin.math.*

enum class Direction(val rotation: Int) {
    STP(0), CCW(-1), CW(1)
}

fun Double.toDegree() = (this * 180 / PI).toFloat()
fun Float.toRadian() = toDouble() * PI / 180
infix fun Float.inc(f: Float): Float {
    var result = this + f
    while (result >= 180f)
        result -= 360f
    while (result < -180f)
        result += 360f
    return result
}
infix fun Float.diff(f: Float): Float {
    val delta = abs(this inc -f)
    return min(delta, 360f - delta)
}

interface Model {
    class LinearAnimator {
        private var model: Model?
        private val sx: Float
        private val sy: Float
        private val dx: Float
        private val dy: Float
        private val step: Int
        private var current: Int = 0

        constructor(start: Model, end: Model, step: Int) {
            model = start
            sx = start.x
            sy = start.y
            dx = end.x - sx
            dy = end.y - sy
            this.step = step
        }

        fun update() {
            current++
            val value = (0.5f - 0.5f * cos(current * PI / step)).toFloat()
            model?.x = sx + dx * value
            model?.y = sy + dy * value

            if (current >= step) {
                model?.anim = null
                model = null
            }
        }
    }

    companion object {
        const val SPEED_LIMIT = 0.033f

        fun intersects(m1: Model, m2: Model) = hypot(m1.x - m2.x, m1.y - m2.y) < m1.r + m2.r
    }

    val r: Float
    var x: Float
    var y: Float
    val vx: Float
    val vy: Float
    var anim: LinearAnimator?

    fun update()
}

class Player : Model {
    companion object {
        const val RADIUS_SCALE = 0.1f
        const val ACC_DEFAULT = 0.0017f
        const val VERTICAL_SPEED_LIMIT = 0.03f
        const val VERTICAL_ACC_DEFAULT = 0.0027f

        enum class JumpMode {
            LANDED, JUMPING, FALLING
        }
    }

    var theta = 90f
    var speed = 0f
    var dir = Direction.STP

    override var r: Float = RADIUS_SCALE
    override var x: Float = 0f
        get() = if (isFree) field else ((1 - RADIUS_SCALE - height) * cos(theta.toRadian())).toFloat()
    override var y: Float = 0f
        get() = if (isFree) field else ((1 - RADIUS_SCALE - height) * sin(theta.toRadian())).toFloat()
    override var vx: Float = 0f
    override var vy: Float = 0f
    override var anim: Model.LinearAnimator? = null
    var isFree: Boolean = false
        set(value) {
            if (field == value) {
                return
            }
            if (value) {
                val thetaRadian = theta.toRadian()
                vx = (-speed * sin(thetaRadian) -verticalSpeed * cos(thetaRadian)).toFloat()
                vy = (speed * cos(thetaRadian) -verticalSpeed * sin(thetaRadian)).toFloat()
                x = x
                y = y
            } else {

            }
            field = value
        }

    var jumping = false
    var jumpMode = JumpMode.LANDED
    var verticalSpeed = 0f
    var height = 0f

    fun initialize(dir: Direction = Direction.STP) {
        theta = 90f
        speed = 0f
        this.dir = dir
        jumping = false
        jumpMode = JumpMode.LANDED
        verticalSpeed = 0f
        height = 0f
        anim = null
        isFree = false
    }

    override fun update() {
        if (anim != null) {
            anim?.update()
            return
        }

        // Update speed
        speed += ACC_DEFAULT * dir.rotation
        if (speed > Model.SPEED_LIMIT)
            speed = Model.SPEED_LIMIT
        else if (speed < -Model.SPEED_LIMIT)
            speed = -Model.SPEED_LIMIT

        // Update theta
        theta = theta inc (speed / (1 - RADIUS_SCALE) * 180 / PI).toFloat()

        // Update height
        when (jumpMode) {
            JumpMode.LANDED -> {
                if (jumping) {
                    jumpMode = JumpMode.JUMPING
                }
            }
            JumpMode.JUMPING -> {
                verticalSpeed= VERTICAL_SPEED_LIMIT
                height += verticalSpeed
                if (!jumping)
                    jumpMode = JumpMode.FALLING
            }
            JumpMode.FALLING -> {
                verticalSpeed -= VERTICAL_ACC_DEFAULT
                height += verticalSpeed
            }
        }
        // Flipping
        if (height > 1 - r) {
            theta = theta inc 180f
            height = 2 - 2 * r - height
            verticalSpeed *= -1
            jumpMode = JumpMode.FALLING
        }
        // Landing
        if (height < 0f) {
            height = 0f
            verticalSpeed = 0f
            jumpMode = JumpMode.LANDED
        }
    }

    fun turn() {
        dir = when (dir) {
            Direction.CCW -> Direction.CW
            Direction.CW -> Direction.CCW
            else -> Direction.STP
        }
    }
}

interface Ball : Model {
    companion object {
        const val RADIUS_SCALE = 0.02f
    }

    override val r: Float
        get() = RADIUS_SCALE
}

class RevolvingBall(var theta: Float, dir: Direction = Direction.STP) : Ball {
    var speed: Float = Model.SPEED_LIMIT * dir.rotation

    override var x: Float = 0f
    override var y: Float = 0f
    override val vx: Float
        get() = (-speed * sin(theta.toRadian())).toFloat()
    override val vy: Float
        get() = (speed * cos(theta.toRadian())).toFloat()
    override var anim: Model.LinearAnimator? = null

    override fun update() {
        if (anim != null) {
            anim?.update()
            return
        }

        theta = theta inc (speed / (1 - Player.RADIUS_SCALE) * 180 / PI).toFloat()
        x = ((1 - Player.RADIUS_SCALE) * cos(theta.toRadian())).toFloat()
        y = ((1 - Player.RADIUS_SCALE) * sin(theta.toRadian())).toFloat()
    }
}

class BouncingBall(theta: Float, var vector: Float = theta inc -180f) : Ball {
    var speed: Float = Model.SPEED_LIMIT

    override var x: Float = (1 - r) * cos(theta.toRadian()).toFloat()
    override var y: Float = (1 - r) * sin(theta.toRadian()).toFloat()
    override val vx: Float
        get() = (speed * cos(vector.toRadian())).toFloat()
    override val vy: Float
        get() = (speed * sin(vector.toRadian())).toFloat()
    override var anim: Model.LinearAnimator? = null

    override fun update() {
        if (anim != null) {
            anim?.update()
            return
        }

        x += vx
        y += vy

        if (1 - r < hypot(x, y)) {
            reflect()
        }
    }

    private fun reflect() {
        val v1 = arrayOf(cos(vector.toRadian()), sin(vector.toRadian()))
        val b = x * v1[0] + y * v1[1]
        val c = x * x + y * y - (1 - r) * (1 - r)
        val t = -b + sqrt(b * b - c)
        if (t >= 0)
            return
        x += (t * v1[0]).toFloat()
        y += (t * v1[1]).toFloat()
        val n = x * x + y * y
        val v2 = arrayOf(
                (2 * x * x / n - 1) * -v1[0] + (2 * x * y / n) * -v1[1],
                (2 * x * y / n) * -v1[0] + (2 * y * y / n - 1) * -v1[1]
        )
        x += (-t * v2[0]).toFloat()
        y += (-t * v2[1]).toFloat()
        vector = atan2(v2[1], v2[0]).toDegree()
    }
}