package com.holenet.codge

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.*
import android.support.v4.content.ContextCompat
import android.view.SurfaceView
import kotlin.math.*

@SuppressLint("ViewConstructor")
class GameView(context: Context, private val outerRadius: Int): SurfaceView(context), Runnable {
    companion object {
        const val TICKS_PER_SECOND = 50
        const val SKIP_MILLIS = 1000 / TICKS_PER_SECOND
        const val MAX_FRAME_SKIP = 5
        const val MAX_BALLS_NUM = 8
        const val PREPARE_ANIM_TIME = GameActivity.UI_ANIM_TIME

        enum class GameMode {
            READY, PREPARING, PLAYING
        }
    }

    // game running
    @Volatile var running = false
    var gameMode = GameMode.READY; private set
    private var flagGameOver = false
    private var flagGameStart = false
    private var gameTicks = 0
    private var gameThread: Thread? = null
    private var firstPlay = true

    // input variables
    var startDirection: Direction? = null
    var toTurn: Boolean = false
    var toJumpOn: Boolean = false
    var toJumpOff: Boolean = false

    // models
    private val player = Player()
    private val balls = ArrayList<Ball>()

    // bitmaps
    private var backgroundBitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
    private var playerBitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)

    // drawing
    private var innerRadius = outerRadius * 0.9509259f
    private val paint = Paint().apply {
        textAlign = Paint.Align.CENTER
        style = Paint.Style.FILL
        typeface =  Typeface.MONOSPACE
    }
    private val backgroundColor = ContextCompat.getColor(context, R.color.background)

    // score
    private val pref = context.getSharedPreferences("score", 0)
    private val prefKeyBaseScore = context.getString(R.string.pref_best_score)
    private var bestScore = pref.getInt(prefKeyBaseScore, 0)
    private var score = 0

    // callback
    var onPrepare = { dir: Direction -> }
    var onGameOver = {}
    var onPlayerTurn = {dir: Direction -> }

    // calculate fps
    // NOTE: This is for development environment, should be erased on the production releases
    private var currentFPS = 0
    private var nextIndex = 0L
    private var nextFPS = 0
    private val paintFPS = Paint().apply {
        textAlign = Paint.Align.RIGHT
        color = 0xFF00AA11.toInt()
    }

    init {
        holder.setFormat(PixelFormat.RGBA_8888)
        loadBitmaps()
        initialize(Direction.STP)
        gameOver()
    }

    private fun loadBitmaps() {
        // background
        val opt = BitmapFactory.Options().apply { inSampleSize = 2 }
        val backgroundBitmapRaw = BitmapFactory.decodeResource(resources, R.drawable.background, opt)
        backgroundBitmap = Bitmap.createScaledBitmap(backgroundBitmapRaw, outerRadius * 2, (outerRadius * 2f / backgroundBitmapRaw.width * backgroundBitmapRaw.height).roundToInt(), true)
        backgroundBitmapRaw.recycle()

        // player
        val playerRadius = innerRadius * Player.RADIUS_SCALE
        val playerColorBase = 0xFF333333.toInt()
        val playerColorDeco = 0xFFFFCC33.toInt()
        playerBitmap = Bitmap.createBitmap((playerRadius * 2).roundToInt(), (playerRadius * 2).roundToInt(), Bitmap.Config.ARGB_8888)
        with (Canvas(playerBitmap)) {
            translate(playerRadius, playerRadius)
            scale(playerRadius, playerRadius)
            drawCircle(0f, 0f, 1f, Paint().apply { color = playerColorBase })
            val decoBitmap = BitmapFactory.decodeResource(resources, R.drawable.player_deco)
            drawBitmap(decoBitmap, Rect(0, 0, decoBitmap.width, decoBitmap.height), Rect(-1, -1, 1, 1), Paint().apply {
                colorFilter = PorterDuffColorFilter(playerColorDeco, PorterDuff.Mode.SRC_IN)
            })
            decoBitmap.recycle()
        }
    }

    private fun initialize(dir: Direction) {
        gameTicks = 0
        score = 0

        startDirection = null
        toTurn = false
        toJumpOn = false
        toJumpOff = false

        player.initialize(dir)

        balls.clear()
        balls.add(RevolvingBall(-90f, Direction.CCW))
        balls.add(RevolvingBall(-90f, Direction.CW))
    }

    fun updateColoring() {
        // TODO: change color/bitmaps for models
    }

    private fun processInput() {
        when (gameMode) {
            GameMode.READY -> {
                val dir = startDirection
                startDirection = null
                if (dir != null) {
                    prepare(dir)
                }
            }
            GameMode.PLAYING -> {
                val toTurn = toTurn
                this.toTurn = false
                if (toTurn) {
                    player.turn()
                    onPlayerTurn(player.dir)
                }

                val toJumpOff = toJumpOff
                this.toJumpOff = false
                if (toJumpOff) {
                    player.jumping = false
                }
                val toJumpOn = toJumpOn
                this.toJumpOn = false
                if (toJumpOn) {
                    player.jumping = true
                }
            }
        }
    }

    private var startDirectionTemp = Direction.STP
    private fun prepare(dir: Direction) {
        gameMode = GameMode.PREPARING
        onPrepare(dir)
        startDirectionTemp = dir

        player.anim = Model.LinearAnimator(player, Player(), PREPARE_ANIM_TIME / SKIP_MILLIS)
        val dstBall = RevolvingBall(-90f).apply { update() }
        for (ball in balls) {
            ball.anim = Model.LinearAnimator(ball, dstBall, PREPARE_ANIM_TIME / SKIP_MILLIS)
        }
    }

    private fun startPlay() {
        flagGameStart = false
        gameMode = GameMode.PLAYING

        initialize(startDirectionTemp)
        firstPlay = false
    }

    private fun gameOver() {
        flagGameOver = false
        gameMode = GameMode.READY
        player.isFree = true

        onGameOver()
        with(pref.edit()) {
            bestScore = max(bestScore, score)
            putInt(prefKeyBaseScore, bestScore)
            apply()
        }
    }

    override fun run() {
        var nextGameMillis = System.currentTimeMillis()

        while (running) {
            var loops = 0
            while (System.currentTimeMillis() > nextGameMillis && loops < MAX_FRAME_SKIP) {
                if (flagGameStart) {
                    startPlay()
                }
                if (flagGameOver)
                    gameOver()
                processInput()

                gameTicks++
                if (gameMode == GameMode.PLAYING)
                    score++
                update()

                nextGameMillis += SKIP_MILLIS
                loops++
            }

            draw()
        }
    }

    private fun update() {
        player.update()
        if (gameMode == GameMode.PREPARING && player.anim == null) {
            flagGameStart = true
        }

        fun addNewBall() {
            var count = 0
            while (true) {
                val theta = (Math.random() * 360 - 180).toFloat()
                if (theta diff player.theta < 110)
                    continue
                val ball = BouncingBall(theta, 0f)
                val vector = atan2(player.y - ball.y, player.x - ball.x).toDouble().toDegree()
                count++
                if ((theta inc 180f) diff vector < 10 && count < 10)
                    continue
                ball.vector = vector
                balls.add(ball)
                break
            }
        }

        if (gameMode == GameMode.PLAYING || gameMode == GameMode.READY) {
            if (gameTicks % 250 == 0) {
                addNewBall()
                if (balls.size > MAX_BALLS_NUM)
                    balls.removeAt(2)
            }
        }

        for (ball in balls) {
            ball.update()

            if (gameMode == GameMode.PLAYING && Model.intersects(player, ball)) {
                flagGameOver = true
                break
            }
        }

        for (ball in balls) {
            if (Model.intersects(player, ball)) {
                // update velocity
                val dx = player.x - ball.x
                val dy = player.y - ball.y
                val n = hypot(dx, dy)
                if (n == 0f) continue
                val ex = dx / n
                val ey = dy / n
                val a = Model.SPEED_LIMIT * 0.8f * E.toFloat().pow(-n / (player.r + ball.r) * E.toFloat())
                player.vx += a * ex
                player.vy += a * ey

               // update w
                val vx = player.vx - ball.vx
                val vy = player.vy - ball.vy
                val nn = n * n
                val vhx = vx - (dx * dx / nn * vx + dx * dy / nn * vy)
                val vhy = vy - (dx * dy / nn * vy + dy * dy / nn * vy)
                val aw = (hypot(vhx, vhy) / E.toFloat().pow(-n / (player.r + ball.r) * E.toFloat()) * 180 / PI).toFloat()
                val h = dy * vhx - dx * vhy
                player.w = 0.8f * player.w + 0.2f * aw * if (h > 0) -1 else 1
            }
        }
    }

    val Paint.textHeight get() = fontMetrics.descent - fontMetrics.ascent
    val timeFormat = "%.2f"
    val bestTimeFormat = "Best %.2f"
    private fun draw() {
        if (holder.surface.isValid) {
            with (holder.lockCanvas()) {
                // draw background
                drawBitmap(backgroundBitmap, 0f, 0f, null)

                save()
                translate(outerRadius.toFloat(), outerRadius.toFloat())

                if (!firstPlay) {
                    // draw time
                    val time = timeFormat.format(score * SKIP_MILLIS / 1000f)
                    paint.color = Color.BLACK
                    paint.textSize = innerRadius / 5
                    val timeHeight = paint.textHeight
                    drawText(time, 0f, +timeHeight / 2, paint)

                    // draw bestTime
                    val bestTime = (if (gameMode == GameMode.PLAYING) timeFormat else bestTimeFormat).format(bestScore * SKIP_MILLIS / 1000f)
                    paint.textSize = innerRadius / 10
                    drawText(bestTime, 0f, -timeHeight / 2, paint)
                }

                // draw player
                save()
                translate(innerRadius * player.x, innerRadius * player.y)
                rotate(player.angle)
                drawBitmap(playerBitmap, -playerBitmap.width / 2f, -playerBitmap.height / 2f, null)
                restore()

                // draw balls
                paint.color = 0xFFF46700.toInt()
                for (ball in balls) {
                    drawCircle(innerRadius * ball.x, innerRadius * ball.y, innerRadius * ball.r, paint)
                }

                restore()
                // draw fps
                // NOTE: This is for development environment, should be erased on the production releases
                val currentIndex = System.currentTimeMillis() / 1000
                if (nextIndex != currentIndex) {
                    nextIndex = currentIndex
                    currentFPS = nextFPS
                    nextFPS = 0
                }
                nextFPS++
                paintFPS.textSize = outerRadius / 6f
                drawText("$currentFPS", outerRadius * 1.95f, outerRadius * 2 - paintFPS.fontMetrics.bottom, paintFPS)

                holder.unlockCanvasAndPost(this)
            }
        }
    }

    fun onPause() {
        running = false
        gameThread?.join()
    }

    fun onResume() {
        running = true
        gameThread?.join()
        gameThread = Thread(this)
        gameThread?.start()
    }
}