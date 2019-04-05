package com.holenet.codge

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.*
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.GLU
import android.os.Handler
import android.os.Looper
import android.support.v4.content.ContextCompat
import android.view.MotionEvent
import android.widget.Toast
import java.util.*
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.*

@SuppressLint("ViewConstructor")
class GameView(context: Context, private val outerRadius: Int, private val recordViewModel: RecordViewModel): GLSurfaceView(context), Runnable, GLSurfaceView.Renderer {
    companion object {
        const val TICKS_PER_SECOND = 50
        const val SKIP_MILLIS = 1000 / TICKS_PER_SECOND
        const val MAX_FRAME_SKIP = 5
        const val MAX_BALLS_NUM = 8
        const val PREPARE_ANIM_TIME = GameActivity.UI_ANIM_TIME

        enum class GameMode {
            READY, PREPARING, PLAYING
        }

        const val timeFormat = "%.2f"
        const val bestTimeFormat = "Best %.2f"
    }

    // game running
    @Volatile var running = false
    var gameMode = GameMode.READY; private set
    private var flagGameOver = false
    private var flagGameStart = false
    private var gameTicks = 0
    private var gameThread: Thread? = null
    var firstPlay = true; private set
    private var isPaused = false
    private var random = Random()

    // input variables
    var startDirection: Direction? = null
    var toTurn: Boolean = false
    var toJumpOn: Boolean = false
    var toJumpOff: Boolean = false
    var toKillSelf: Boolean = false

    // models
    private val player = Player()
    private val balls = ArrayList<Ball>()

    // bitmaps
    private var backgroundBitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
    private var playerBitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
    private var playerBaseHighlightBitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
    private var playerPatternHighlightBitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
    private var ballBitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
    private var ballHighlightBitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)

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
    var onPlayerTurn = { dir: Direction -> }

    // hidden spinning
    private var haveTurned = false
    private var spinningDirection = Direction.STP
    private var spinningSpeed = 0f
    private var canvasRotation = 0f

    // customization highlighting
    var highlightedType: CustomType? = null

    // game record and replay
    var record: Record? = null
    var isReplaying = false
    var nextInputIndex = 0

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
        initializeGame(Direction.STP)
        gameOver()
    }

    override fun onSurfaceCreated(gl: GL10, config: EGLConfig) {
        gl.glClearColor(0.5f, 0.5f, 0.5f, 1f)
        gl.glHint(GL10.GL_PERSPECTIVE_CORRECTION_HINT, GL10.GL_FASTEST)
        gl.glShadeModel(GL10.GL_FLAT)
        gl.glDisable(GL10.GL_DEPTH_TEST)
        gl.glEnable(GL10.GL_BLEND)
        gl.glBlendFunc(GL10.GL_ONE, GL10.GL_ONE_MINUS_SRC_ALPHA)

        gl.glViewport(0, 0, width, height)
        gl.glMatrixMode(GL10.GL_PROJECTION)
        gl.glLoadIdentity()
        gl.glEnable(GL10.GL_BLEND)
        gl.glBlendFunc(GL10.GL_SRC_ALPHA, GL10.GL_ONE_MINUS_SRC_ALPHA)
        gl.glShadeModel(GL10.GL_FLAT)
        gl.glEnable(GL10.GL_TEXTURE_2D)

        GLU.gluOrtho2D(gl, 0f, surfaceWidth.toFloat(), surfaceHeight.toFloat(), 0f)
    }

    var surfaceWidth: Int = -1
    var surfaceHeight: Int = -1
    override fun onSurfaceChanged(gl: GL10, width: Int, height: Int) {
        surfaceWidth = width
        surfaceHeight = height

        gl.glViewport(0, 0, width, height)
        gl.glLoadIdentity()
        GLU.gluOrtho2D(gl, 0f, width.toFloat(), height.toFloat(), 0f)
    }

    override fun onDrawFrame(gl: GL10) {
    }

    private fun loadBitmaps() {
        CustomManager.load(context)
        loadCustomBackground()
        loadCustomPlayer()
        loadCustomBall()
    }
    private fun loadCustomBackground() {
        val opt = BitmapFactory.Options().apply { inSampleSize = 2 }
        val backgroundBitmapRaw = BitmapFactory.decodeResource(resources, R.drawable.background, opt)
        backgroundBitmap = Bitmap.createScaledBitmap(backgroundBitmapRaw, outerRadius * 2, (outerRadius * 2f / backgroundBitmapRaw.width * backgroundBitmapRaw.height).roundToInt(), true)
        backgroundBitmapRaw.recycle()
    }
    private fun loadCustomPlayer() {
        val playerRadius = innerRadius * Player.RADIUS_SCALE
        val playerBaseColor = CustomManager.getCurrentColor(CustomType.PlayerBaseColor)
        val playerPatternColor = CustomManager.getCurrentColor(CustomType.PlayerPatternColor)
        val patternBitmap = BitmapFactory.decodeResource(resources, R.drawable.player_pattern)
        playerBitmap = Bitmap.createBitmap((playerRadius * 2).roundToInt(), (playerRadius * 2).roundToInt(), Bitmap.Config.ARGB_8888)
        with (Canvas(playerBitmap)) {
            translate(playerRadius, playerRadius)
            scale(playerRadius, playerRadius)
            drawCircle(0f, 0f, 1f, Paint().apply { color = playerBaseColor })
            drawBitmap(patternBitmap, Rect(0, 0, patternBitmap.width, patternBitmap.height), Rect(-1, -1, 1, 1), Paint(Paint.ANTI_ALIAS_FLAG).apply {
                colorFilter = PorterDuffColorFilter(playerPatternColor, PorterDuff.Mode.SRC_IN)
            })
            patternBitmap.recycle()
        }
        with (BitmapFactory.decodeResource(resources, R.drawable.player_base_highlight)) {
            val baseHighlightSize = playerBitmap.width * this.width / patternBitmap.width
            playerBaseHighlightBitmap = Bitmap.createScaledBitmap(this, baseHighlightSize, baseHighlightSize, true)
            recycle()
        }
        with (BitmapFactory.decodeResource(resources, R.drawable.player_pattern_highlight)) {
            val patternHighlightSize = playerBitmap.width * this.width / patternBitmap.width
            playerPatternHighlightBitmap = Bitmap.createScaledBitmap(this, patternHighlightSize, patternHighlightSize, true)
            recycle()
        }
    }
    private fun loadCustomBall() {
        val ballRadius = innerRadius * Ball.RADIUS_SCALE
        val ballColor = CustomManager.getCurrentColor(CustomType.BallColor)
        val ballShapeBitmap = BitmapFactory.decodeResource(resources, R.drawable.ball)
        ballBitmap = Bitmap.createBitmap((ballRadius * 2).roundToInt(), (ballRadius * 2).roundToInt(), Bitmap.Config.ARGB_8888)
        with (Canvas(ballBitmap)) {
            translate(ballRadius, ballRadius)
            scale(ballRadius, ballRadius)
            drawBitmap(ballShapeBitmap, Rect(0, 0, ballShapeBitmap.width, ballShapeBitmap.height), Rect(-1, -1, 1, 1), Paint(Paint.ANTI_ALIAS_FLAG).apply {
                colorFilter = PorterDuffColorFilter(ballColor, PorterDuff.Mode.SRC_IN)
            })
            ballShapeBitmap.recycle()
        }
        with (BitmapFactory.decodeResource(resources, R.drawable.ball_highlight)) {
            val highlightSize = ballBitmap.width * this.width / ballShapeBitmap.width
            ballHighlightBitmap = Bitmap.createScaledBitmap(this, highlightSize, highlightSize, true)
            recycle()
        }
    }

    fun refreshCustomization(type: CustomType) {
        when (type) {
            CustomType.PlayerBaseColor -> loadCustomPlayer()
            CustomType.PlayerPatternColor -> loadCustomPlayer()
            CustomType.BallColor -> loadCustomBall()
        }
    }

    private fun initializeGame(dir: Direction) {
        gameTicks = 0
        score = 0

        startDirection = null
        toTurn = false
        toJumpOn = false
        toJumpOff = false
        toKillSelf = false

        player.initialize(dir)

        balls.clear()
        balls.add(RevolvingBall(-90f, Direction.CCW))
        balls.add(RevolvingBall(-90f, Direction.CW))

        haveTurned = false
        spinningDirection = Direction.STP
        canvasRotation = 0f

        if (dir != Direction.STP && !isReplaying) {
            val seed = System.currentTimeMillis()
            record = Record(recordedAtMillis = System.currentTimeMillis(), seed = seed, firstDirection = dir)
            random = Random(seed)
        }
    }

    private fun injectFakeInput() {
        val record = record ?: return
        while (nextInputIndex < record.inputList.size && record.inputList[nextInputIndex].first == gameTicks) {
            when (record.inputList[nextInputIndex].second) {
                Input.TURN -> toTurn = true
                Input.JUMP_ON -> toJumpOn = true
                Input.JUMP_OFF -> toJumpOff = true
                Input.KILL_SELF -> toKillSelf = true
            }
            nextInputIndex++
        }
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
                    haveTurned = true
                    if (!isReplaying) record?.inputList?.add(Pair(gameTicks, Input.TURN))
                }

                val toJumpOn = toJumpOn
                this.toJumpOn = false
                if (toJumpOn) {
                    player.jumping = true
                    if (!isReplaying) record?.inputList?.add(Pair(gameTicks, Input.JUMP_ON))
                } else {
                    val toJumpOff = toJumpOff
                    this.toJumpOff = false
                    if (toJumpOff) {
                        player.jumping = false
                        if (!isReplaying) record?.inputList?.add(Pair(gameTicks, Input.JUMP_OFF))
                    }
                }
                val toKillSelf = toKillSelf
                this.toKillSelf = false
                if (toKillSelf) {
                    flagGameOver = true
                    if (!isReplaying) record?.inputList?.add(Pair(gameTicks, Input.KILL_SELF))
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

        initializeGame(startDirectionTemp)
        firstPlay = false
    }

    private fun gameOver() {
        flagGameOver = false
        gameMode = GameMode.READY
        player.isFree = true

        onGameOver()

        if (!isReplaying) {
            with(pref.edit()) {
                bestScore = max(bestScore, score)
                putInt(prefKeyBaseScore, bestScore)
                apply()
            }

            val record = record
            this.record = null
            if (record != null) {
                recordViewModel.insert(record.apply { score = this@GameView.score })
            }
        } else {
            isReplaying = false
            record = null
            nextInputIndex = 0
        }
    }

    override fun run() {
        var nextGameMillis = System.currentTimeMillis()
        var lastDrawTick = gameTicks - 1

        while (running) {
            var loops = 0
            while (System.currentTimeMillis() > nextGameMillis && loops < MAX_FRAME_SKIP) {
                if (flagGameStart)
                    startPlay()
                if (flagGameOver)
                    gameOver()

                if (isReplaying) injectFakeInput()
                processInput()

                if ((gameMode == GameMode.PLAYING && !isReplaying) || !isPaused) {
                    gameTicks++
                    update()
                    if (gameMode == GameMode.PLAYING)
                        score++
                }

                nextGameMillis += SKIP_MILLIS
                loops++
            }

            if (lastDrawTick != gameTicks) {
                lastDrawTick = gameTicks
//                draw()
            }
        }
    }

    private fun update() {
        // spinning
        if (gameMode == GameMode.PLAYING && gameTicks == 30 * 1000 / SKIP_MILLIS && !haveTurned) {
            Handler(Looper.getMainLooper()).post { Toast.makeText(context, "Do You Love Spin?", Toast.LENGTH_LONG).show(); }
            spinningDirection = player.dir
            spinningSpeed = spinningDirection.rotation * 1.8f
        }

        // player update
        player.update()
        if (gameMode == GameMode.PREPARING && player.anim == null) {
            flagGameStart = true
        }

        fun addNewBall() {
            var count = 0
            while (true) {
                val theta = (random.nextDouble() * 360 - 180).toFloat()
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

        // create new ball
        if (gameMode == GameMode.PLAYING || gameMode == GameMode.READY) {
            if (gameTicks == 250 || gameTicks % 500 == 0) {
                addNewBall()
                if (balls.size > MAX_BALLS_NUM)
                    balls.removeAt(2)
            }
        }

        // ball update
        for (ball in balls) {
            ball.update()

            if (gameMode == GameMode.PLAYING && Model.intersects(player, ball)) {
                flagGameOver = true
                break
            }
        }

        // free physics
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
    private fun draw() {
        if (holder.surface.isValid) {
            with (holder.lockCanvas()) {
                // draw background
                drawColor(backgroundColor)
                drawBitmap(backgroundBitmap, 0f, 0f, null)

                save()
                translate(outerRadius.toFloat(), outerRadius.toFloat())

                // spinning
                if (spinningDirection != Direction.STP) {
                    if (random.nextDouble() < 0.03) {
                        spinningDirection = if (spinningDirection == Direction.CCW) Direction.CW else Direction.CCW
                    } else {
                        spinningSpeed += spinningDirection.rotation * 0.05f
                        if (abs(spinningSpeed) > 1.8f) {
                            spinningSpeed = spinningSpeed.sign * 1.8f
                        }
                    }
                    canvasRotation = canvasRotation inc spinningSpeed
                    rotate(canvasRotation)
                }

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
                if (highlightedType == CustomType.PlayerBaseColor)
                    drawBitmap(playerBaseHighlightBitmap, -playerBaseHighlightBitmap.width / 2f, -playerBaseHighlightBitmap.height / 2f, null)
                if (highlightedType == CustomType.PlayerPatternColor || highlightedType == CustomType.PlayerPatternShape)
                    drawBitmap(playerPatternHighlightBitmap, -playerPatternHighlightBitmap.width / 2f, -playerPatternHighlightBitmap.height / 2f, null)
                restore()

                // draw balls
                for (ball in balls) {
                    drawBitmap(ballBitmap, innerRadius * ball.x - ballBitmap.width / 2f, innerRadius * ball.y - ballBitmap.height / 2f, null)
                    if (highlightedType == CustomType.BallColor)
                        drawBitmap(ballHighlightBitmap, innerRadius * ball.x - ballHighlightBitmap.width / 2f, innerRadius * ball.y - ballHighlightBitmap.height / 2f, null)
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

    fun startReplay(record: Record) {
        this.record = record
        isReplaying = true
        random = Random(record.seed)
        startDirection = record.firstDirection
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_DOWN) {
            isPaused = true
        } else if (event.action == MotionEvent.ACTION_UP) {
            isPaused = false
        }
        return true
    }

    override fun onPause() {
        running = false
        gameThread?.join()
    }

    override fun onResume() {
        running = true
        gameThread?.join()
        gameThread = Thread(this)
        gameThread?.start()
    }
}