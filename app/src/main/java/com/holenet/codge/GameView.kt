package com.holenet.codge

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.*
import android.support.v4.content.ContextCompat
import android.view.SurfaceView
import kotlin.math.atan2
import kotlin.math.max
import kotlin.math.roundToInt

@SuppressLint("ViewConstructor")
class GameView(context: Context, private val outerRadius: Int): SurfaceView(context), Runnable {
    companion object {
        const val TICKS_PER_SECOND = 50
        const val SKIP_MILLIS = 1000 / TICKS_PER_SECOND
        const val MAX_FRAME_SKIP = 5
        const val MAX_BALLS_NUM = 8

        enum class GameMode {
            READY, PREPARING, PLAYING, GAME_OVER
        }
    }

    // game running
    @Volatile var running = false
    var gameMode = GameMode.READY; private set
    private var flagGameOver = false
    private var gameTicks = 0
    private var gameThread: Thread? = null
    private var firstPlay = true

    // input variables
    var startDirection: Direction? = null
    var toTurn: Boolean = false
    var toJump: Boolean = false

    // models
    private val player = Player()
    private val balls = ArrayList<Ball>()

    // bitmaps
    private var playerBitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)

    // drawing
    private var innerRadius = outerRadius.toFloat()
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

    // callback
    var onStartPlay = {dir: Direction -> }
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
        loadBitmaps()
        initialize(Direction.STP)
    }

    private fun loadBitmaps() {
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
        }
    }

    private fun initialize(dir: Direction) {
        gameTicks = 0

        startDirection = null
        toTurn = false
        toJump = false

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
                    onStartPlay(dir)
                    gameMode = GameMode.PLAYING
                    initialize(dir)
                    firstPlay = false
                }
            }
            GameMode.PLAYING -> {
                val toTurn = toTurn
                this.toTurn = false
                if (toTurn) {
                    player.turn()
                    onPlayerTurn(player.dir)
                }

                player.jumping = toJump
            }
        }
    }

    private fun gameOver() {
        flagGameOver = false

        onGameOver()
        with(pref.edit()) {
            putInt(prefKeyBaseScore, bestScore)
            apply()
        }
        gameMode = GameMode.READY
    }

    override fun run() {
        var nextGameMillis = System.currentTimeMillis()

        while (running) {
            var loops = 0
            while (System.currentTimeMillis() > nextGameMillis && loops < MAX_FRAME_SKIP) {
                if (flagGameOver)
                    gameOver()
                processInput()

                if (gameMode == GameMode.PLAYING) {
                    gameTicks++
                    bestScore = max(bestScore, gameTicks)
                }
                update()

                nextGameMillis += SKIP_MILLIS
                loops++
            }

            draw()
        }
    }

    private fun update() {
        if (gameMode == GameMode.PLAYING) {
            player.update()
            while (balls.size < MAX_BALLS_NUM && balls.size - 2 < gameTicks / 250) {
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
        }

        for (ball in balls) {
            ball.update()

            if (gameMode == GameMode.PLAYING && Model.intersects(player, ball)) {
                flagGameOver = true
                return
            }
        }
    }

    val Paint.textHeight get() = fontMetrics.descent - fontMetrics.ascent
    private fun draw() {
        holder.setFormat(PixelFormat.RGBA_8888)
        if (holder.surface.isValid) {
            with (holder.lockCanvas()) {
                drawColor(backgroundColor)

                save()
                translate(outerRadius.toFloat(), outerRadius.toFloat())
                paint.color = 0xFFF2F2F2.toInt()
                drawCircle(0f, 0f, innerRadius, paint)

                if (!firstPlay) {
                    // draw time
                    val time = "%.2f".format(gameTicks * SKIP_MILLIS / 1000f)
                    paint.color = Color.BLACK
                    paint.textSize = innerRadius / 5
                    val timeHeight = paint.textHeight
                    drawText(time, 0f, 0f, paint)

                    // draw bestTime
                    var bestTime = "%.2f".format(bestScore * SKIP_MILLIS / 1000f)
                    if (gameMode != GameMode.PLAYING)
                        bestTime = "Best $bestTime"
                    paint.textSize = innerRadius / 10
                    drawText(bestTime, 0f, -timeHeight, paint)
                }

                // draw player
                save()
                translate(innerRadius * player.x, innerRadius * player.y)
                rotate(-player.theta / Player.RADIUS_SCALE)
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
                drawText("$currentFPS", outerRadius * 1.95f, outerRadius * 2 - paint.fontMetrics.bottom, paintFPS)

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