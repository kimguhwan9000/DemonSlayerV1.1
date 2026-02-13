package com.example.androidgame01

import android.content.Context
import android.graphics.*
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.SoundPool
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import java.util.Random
import kotlin.math.sin

class GameView(context: Context) : SurfaceView(context), Runnable {
    private var gameThread: Thread? = null
    private var isPlaying = false
    private val paint = Paint().apply { isAntiAlias = false }
    private val holder: SurfaceHolder = getHolder()
    private val random = Random()

    // 사운드 관련
    private var mediaPlayer: MediaPlayer? = null
    private lateinit var soundPool: SoundPool
    private var shootSoundId: Int = 0

    // 리소스 비트맵
    private lateinit var playerBitmaps: Array<Bitmap>
    private lateinit var enemyBitmap: Bitmap
    private lateinit var bgBitmap: Bitmap
    private lateinit var coinBitmap: Bitmap
    private lateinit var blockBitmap: Bitmap
    private lateinit var boxBitmap: Bitmap
    private lateinit var mushroomBitmap: Bitmap
    private lateinit var potionBitmap: Bitmap

    // 물리 및 상태 변수
    private var charY = 0f; private var velocityY = 0f
    private val gravity = 4.2f; private val jumpPower = -95f
    private var groundY = 0f; private var isJumping = false
    private var worldX = 0f; private var cameraX = 0f; private val moveSpeed = 22f
    private var charSize = 140f; private var isBig = false; private var bigTimer = 0
    private var playerHp = 100f; private var playerMaxHp = 100f
    private var lastDirection = 1

    // 보스 및 방벽
    private var isBossBattle = false
    private var bossHp = 0f; private var bossMaxHp = 0f
    private var bossScreenX = 0f; private var bossScreenY = 0f
    private var bossSize = 300f; private var bossAttackCoolTime = 0
    private var barrierX = 1000000f; private var bossStepCount = 0

    // 아이템 보충 타이머
    private var itemSpawnTimer = 0
    private val SPAWN_INTERVAL = 1800

    // 리스트
    private val missiles = mutableListOf<Missile>()
    private val bossMissiles = mutableListOf<Missile>()
    private val coins = mutableListOf<PointF>(); private val blocks = mutableListOf<RectF>()
    private val itemBoxes = mutableListOf<RectF>(); private val mushrooms = mutableListOf<PointF>()
    private val potions = mutableListOf<PointF>(); private val enemies = mutableListOf<PointF>()

    private var score = 0; private var stageLevel = 1; private var stageDistance = 5000f
    private var curFrame = 0; private var aniTick = 0; private var lastMapGenX = 0f
    private var shakeTimer = 0; private var ultActive = false; private var ultTimer = 0
    private var attackCoolTime = 0; private var isAttackPressed = false
    private var isInitialized = false

    private val leftBtn = RectF(); private val rightBtn = RectF()
    private val jumpBtn = RectF(); private val attackBtn = RectF(); private val ultBtn = RectF()
    private var isLeftPressed = false; private var isRightPressed = false

    data class Missile(var x: Float, var y: Float, val dir: Int)

    init {
        loadResources()
        initSound()
        holder.addCallback(object : SurfaceHolder.Callback {
            override fun surfaceCreated(h: SurfaceHolder) { resume() }
            override fun surfaceChanged(h: SurfaceHolder, f: Int, w: Int, h1: Int) {}
            override fun surfaceDestroyed(h: SurfaceHolder) { pause() }
        })
    }

    private fun initSound() {
        // 배경음악 설정
        mediaPlayer = MediaPlayer.create(context, R.raw.bgm_theme3)
        mediaPlayer?.isLooping = true
        mediaPlayer?.setVolume(0.6f, 0.6f)

        // 효과음 설정
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        soundPool = SoundPool.Builder()
            .setMaxStreams(5)
            .setAudioAttributes(audioAttributes)
            .build()
        shootSoundId = soundPool.load(context, R.raw.enemy_die, 1)
    }

    private fun loadResources() {
        val res = context.resources
        val opt = BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.RGB_565 }
        val p1 = BitmapFactory.decodeResource(res, R.drawable.player_img, opt) ?: Bitmap.createBitmap(100,100,Bitmap.Config.ARGB_8888)
        playerBitmaps = arrayOf(Bitmap.createScaledBitmap(p1, 220, 220, true), Bitmap.createScaledBitmap(p1, 220, 220, true))
        enemyBitmap = Bitmap.createScaledBitmap(BitmapFactory.decodeResource(res, R.drawable.enemy_img, opt), 150, 150, true)
        bgBitmap = BitmapFactory.decodeResource(res, R.drawable.background_img, opt)
        coinBitmap = Bitmap.createScaledBitmap(BitmapFactory.decodeResource(res, R.drawable.coin_img, opt), 90, 90, true)
        blockBitmap = Bitmap.createScaledBitmap(BitmapFactory.decodeResource(res, R.drawable.block_img, opt), 85, 85, true)
        boxBitmap = Bitmap.createScaledBitmap(BitmapFactory.decodeResource(res, R.drawable.box_img, opt), 140, 140, true)
        mushroomBitmap = Bitmap.createScaledBitmap(BitmapFactory.decodeResource(res, R.drawable.mushroom_img, opt) ?: coinBitmap, 120, 120, true)
        potionBitmap = Bitmap.createScaledBitmap(BitmapFactory.decodeResource(res, R.drawable.potion_img, opt) ?: coinBitmap, 100, 100, true)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        groundY = h * 0.8f
        if (!isInitialized) { charY = groundY - charSize; resetGame(); isInitialized = true }
        setupButtons(w, h)
    }

    private fun setupButtons(w: Int, h: Int) {
        val bw = 200f; val bh = 150f; val pad = 60f
        leftBtn.set(pad, h-bh-pad, pad+bw, h-pad)
        rightBtn.set(pad+bw+50, h-bh-pad, pad+bw*2+50, h-pad)
        jumpBtn.set(w-bw-pad, h-bh-pad, w-pad, h-pad)
        attackBtn.set(w-bw-pad, h-bh*2-pad-30, w-pad, h-bh-pad-30)
        ultBtn.set(w-bw-pad, h-bh*3-pad-60, w-pad, h-bh*2-pad-60)
    }

    private fun resetGame() {
        playerHp = 100f; score = 0; worldX = 0f; cameraX = 0f; stageLevel = 1; isBossBattle = false; isBig = false; charSize = 140f
        blocks.clear(); coins.clear(); itemBoxes.clear(); mushrooms.clear(); potions.clear(); enemies.clear(); missiles.clear(); bossMissiles.clear()
        lastMapGenX = 0f; bossStepCount = 0; barrierX = 1000000f; itemSpawnTimer = 0
        generateMapSegment(0f, 6000f, true)
    }

    private fun generateMapSegment(start: Float, end: Float, isFirst: Boolean = false) {
        var x = start
        while (x < end) {
            val by = groundY - 220f - random.nextInt(150).toFloat()
            for (j in 0..2) blocks.add(RectF(x + j * 85f, by, x + j * 85f + 85f, by + 85f))
            if (!(isFirst && x < 1800f)) {
                if (random.nextInt(10) < 4) itemBoxes.add(RectF(x + 50f, by - 350f, x + 190f, by - 210f))
                else coins.add(PointF(x + 50f, by - 120f))
                enemies.add(PointF(x + 600f, groundY - 150f))
            }
            x += 1000f + random.nextInt(500)
        }
        lastMapGenX = end
    }

    override fun run() {
        while (isPlaying) { update(); draw(); control() }
    }

    private fun update() {
        if (width == 0) return
        if (worldX + width * 2 > lastMapGenX) generateMapSegment(lastMapGenX, lastMapGenX + 6000f)

        // 보스전 및 아이템 보충 로직
        val nextStage = (worldX / stageDistance).toInt() + 1
        if (nextStage > stageLevel && !isBossBattle) {
            stageLevel = nextStage
            if (stageLevel % 2 == 0) {
                isBossBattle = true; bossMaxHp = stageLevel * 1200f; bossHp = bossMaxHp
                barrierX = worldX + (width * 0.7f)
                bossScreenX = barrierX - 100f; bossScreenY = groundY - bossSize; bossStepCount = 0
            }
        }

        val validItemsCount = countValidItems()
        if (isBossBattle && validItemsCount == 0) {
            itemSpawnTimer++; if (itemSpawnTimer >= SPAWN_INTERVAL) {
                val spawnX = (worldX + random.nextInt(width) - 200f).coerceIn(worldX - 300f, barrierX - 150f)
                val spawnY = groundY - 150f - random.nextInt(250)
                val r = random.nextInt(10)
                if (r < 3) mushrooms.add(PointF(spawnX, spawnY)) else if (r < 7) potions.add(PointF(spawnX, spawnY)) else coins.add(PointF(spawnX, spawnY))
                itemSpawnTimer = 0
            }
        } else { itemSpawnTimer = 0 }

        // 캐릭터 이동 및 물리
        if (isLeftPressed) { worldX -= moveSpeed; lastDirection = -1 }
        if (isRightPressed) {
            val nextX = worldX + moveSpeed
            if (isBossBattle && nextX > barrierX - charSize) worldX = barrierX - charSize else worldX = nextX
            lastDirection = 1
        }
        if (worldX < 0) worldX = 0f
        if (isBig) { bigTimer--; charSize = 240f; if (bigTimer <= 0) { isBig = false; charSize = 140f } }
        velocityY += gravity; charY += velocityY
        if (isLeftPressed || isRightPressed) { aniTick++; if (aniTick % 5 == 0) curFrame = (curFrame + 1) % 2 } else curFrame = 0

        cameraX = (worldX - width * 0.3f).coerceAtLeast(0f)
        val pRect = RectF(worldX - cameraX + 35, charY, worldX - cameraX + charSize - 35, charY + charSize)

        // 보스 AI
        if (isBossBattle) {
            val bossSpeed = 4.5f + (stageLevel * 0.4f)
            if (bossScreenX > worldX + 100) bossScreenX -= bossSpeed else if (bossScreenX < worldX - 100) bossScreenX += bossSpeed
            if (bossScreenX > barrierX - bossSize) bossScreenX = barrierX - bossSize
            if (bossScreenY > charY - 40f) bossScreenY -= 3f else bossScreenY += 3f
            if (bossAttackCoolTime > 0) bossAttackCoolTime--
            else {
                val shootDir = if (bossScreenX > worldX) -1 else 1
                bossMissiles.add(Missile(bossScreenX - cameraX + bossSize/2, bossScreenY + bossSize/2, shootDir))
                bossAttackCoolTime = 50
            }
            val bRect = RectF(bossScreenX - cameraX, bossScreenY, bossScreenX - cameraX + bossSize, bossScreenY + bossSize)
            if (pRect.intersect(bRect)) {
                if (velocityY > 8f && charY < bRect.top + 100) {
                    bossHp -= (if (isBig) 400 else 200); velocityY = jumpPower / 1.4f; shakeTimer = 3; bossStepCount++
                    if (bossStepCount >= 3) { worldX -= 400f; velocityY = -35f; bossStepCount = 0; shakeTimer = 10 }
                } else if (!isBig) { playerHp -= 8f; worldX -= 150f; velocityY = -25f; bossStepCount = 0 }
            }
            if (bossHp <= 0) { isBossBattle = false; score += 15000; barrierX = 1000000f }
        }

        // 몬스터 AI
        val eIter = enemies.iterator()
        while (eIter.hasNext()) {
            val e = eIter.next(); val mSpeed = 6f + (stageLevel * 0.5f)
            if (e.x > worldX) e.x -= mSpeed else e.x += mSpeed
            val ex = e.x - cameraX
            if (pRect.intersect(RectF(ex, e.y, ex + 150, e.y + 150))) {
                if (velocityY > 5f && charY < e.y + 30) { eIter.remove(); velocityY = jumpPower/1.8f; score += 500 }
                else if (!isBig) { playerHp -= 15f; worldX -= 150f }
            } else if (ex < -1000) eIter.remove()
        }

        updateMissiles(pRect); updateCollisions(pRect)
        if (ultActive) { ultTimer--; if (ultTimer <= 0) ultActive = false }
        if (shakeTimer > 0) shakeTimer--; if (playerHp <= 0) resetGame()
    }

    private fun countValidItems(): Int {
        var count = 0
        mushrooms.forEach { if (it.x < barrierX) count++ }
        potions.forEach { if (it.x < barrierX) count++ }
        itemBoxes.forEach { if (it.left < barrierX) count++ }
        return count
    }

    private fun updateMissiles(pRect: RectF) {
        if (attackCoolTime > 0) attackCoolTime--
        if (isAttackPressed && attackCoolTime <= 0) {
            missiles.add(Missile(worldX - cameraX + charSize/2, charY + charSize/2, lastDirection))
            attackCoolTime = 14
            // 미사일 발사 사운드 재생
            soundPool.play(shootSoundId, 1f, 1f, 0, 0, 1f)
        }
        val mIter = missiles.iterator()
        while (mIter.hasNext()) {
            val m = mIter.next(); m.x += (48f * m.dir)
            if (m.x < -100 || m.x > width + 100) { mIter.remove(); continue }
            val mRect = RectF(m.x, m.y - 15, m.x + 60, m.y + 15)
            var hit = false
            for (i in enemies.indices.reversed()) {
                val e = enemies[i]
                if (mRect.intersect(RectF(e.x - cameraX, e.y, e.x - cameraX + 150, e.y + 150))) {
                    enemies.removeAt(i); score += 300; hit = true; break
                }
            }
            if (!hit && isBossBattle) {
                val bRect = RectF(bossScreenX - cameraX, bossScreenY, bossScreenX - cameraX + bossSize, bossScreenY + bossSize)
                if (mRect.intersect(bRect)) { bossHp -= 110f; hit = true; shakeTimer = 3 }
            }
            if (hit) mIter.remove()
        }
        val bmIter = bossMissiles.iterator()
        while (bmIter.hasNext()) {
            val bm = bmIter.next(); bm.x += 28f * bm.dir
            if (pRect.intersect(RectF(bm.x, bm.y - 25, bm.x + 85, bm.y + 25))) {
                if (!isBig) { playerHp -= 20f; worldX += (if(bm.dir > 0) 80f else -80f) }
                bmIter.remove()
            } else if (bm.x < -100 || bm.x > width + 100) bmIter.remove()
        }
    }

    private fun updateCollisions(pRect: RectF) {
        val boxIter = itemBoxes.iterator()
        while (boxIter.hasNext()) {
            val box = boxIter.next()
            if (velocityY < 0 && pRect.intersect(RectF(box.left - cameraX, box.top, box.right - cameraX, box.bottom))) {
                score += 200; velocityY = 25f
                val r = random.nextInt(10)
                if (r < 3) mushrooms.add(PointF(box.left, box.top - 140f))
                else if (r < 6) potions.add(PointF(box.left, box.top - 120f))
                else coins.add(PointF(box.left, box.top - 120f))
                boxIter.remove(); break
            }
        }
        mushrooms.removeAll { m ->
            if (pRect.intersect(RectF(m.x - cameraX, m.y, m.x - cameraX + 120, m.y + 120))) {
                isBig = true; bigTimer = 600; score += 2000; true
            } else false
        }
        potions.removeAll { p ->
            if (pRect.intersect(RectF(p.x - cameraX, p.y, p.x - cameraX + 100, p.y + 100))) {
                playerHp = (playerHp + 40f).coerceAtMost(playerMaxHp); true
            } else false
        }
        coins.removeAll { c -> if (pRect.intersect(RectF(c.x - cameraX, c.y, c.x - cameraX + 90, c.y + 90))) { score += 50; true } else false }
        for (block in blocks) {
            val bl = RectF(block.left - cameraX, block.top, block.right - cameraX, block.bottom)
            if (velocityY > 0 && pRect.intersect(bl) && charY + charSize * 0.8f < bl.bottom) {
                charY = block.top - charSize; velocityY = 0f; isJumping = false
            }
        }
        if (charY >= groundY - charSize) { charY = groundY - charSize; velocityY = 0f; isJumping = false }
    }

    private fun useUltimate() {
        score -= 5000; ultActive = true; ultTimer = 15; enemies.clear()
        if (isBossBattle) bossHp -= 1800f
    }

    private fun draw() {
        if (!holder.surface.isValid) return
        val canvas = holder.lockCanvas() ?: return
        if (shakeTimer > 0) canvas.translate((random.nextInt(3) - 1).toFloat(), (random.nextInt(3) - 1).toFloat())

        canvas.drawColor(Color.parseColor("#5C94FC"))
        val bgX = -(cameraX % bgBitmap.width); canvas.drawBitmap(bgBitmap, bgX, 0f, paint); canvas.drawBitmap(bgBitmap, bgX + bgBitmap.width, 0f, paint)

        if (isBossBattle) {
            paint.color = Color.argb(230, 255, 0, 0); canvas.drawRect(barrierX - cameraX, 0f, barrierX - cameraX + 35f, height.toFloat(), paint)
            val bx = bossScreenX - cameraX
            canvas.drawBitmap(enemyBitmap, null, RectF(bx, bossScreenY, bx + bossSize, bossScreenY + bossSize), paint)
            drawHPBar(canvas, bx, bossScreenY - 60, bossSize, bossHp, bossMaxHp, "BOSS")
        }

        blocks.toList().forEach { canvas.drawBitmap(blockBitmap, it.left - cameraX, it.top, paint) }
        itemBoxes.toList().forEach { canvas.drawBitmap(boxBitmap, it.left - cameraX, it.top, paint) }
        mushrooms.toList().forEach { canvas.drawBitmap(mushroomBitmap, it.x - cameraX, it.y, paint) }
        potions.toList().forEach { canvas.drawBitmap(potionBitmap, it.x - cameraX, it.y, paint) }
        coins.toList().forEach { canvas.drawBitmap(coinBitmap, it.x - cameraX, it.y, paint) }
        enemies.toList().forEach { canvas.drawBitmap(enemyBitmap, it.x - cameraX, it.y, paint) }

        missiles.toList().forEach { canvas.drawRoundRect(it.x, it.y-10, it.x+60, it.y+10, 10f, 10f, paint) }
        bossMissiles.toList().forEach { canvas.drawRoundRect(it.x, it.y-15, it.x+80, it.y+15, 12f, 12f, paint) }

        if (ultActive) { paint.color = Color.argb(150, 255, 255, 255); canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint) }

        canvas.save()
        val pX = worldX - cameraX
        if (isBig) paint.colorFilter = PorterDuffColorFilter(Color.parseColor("#66FF0000"), PorterDuff.Mode.SRC_ATOP)
        if (lastDirection == -1) canvas.scale(-1f, 1f, pX + charSize/2, charY + charSize/2)
        canvas.drawBitmap(playerBitmaps[curFrame], null, RectF(pX, charY, pX + charSize, charY + charSize), paint)
        paint.colorFilter = null; canvas.restore()

        drawHPBar(canvas, pX - 20, charY - 60, charSize + 40, playerHp, playerMaxHp, "PLAYER")
        drawUI(canvas); holder.unlockCanvasAndPost(canvas)
    }

    private fun drawHPBar(canvas: Canvas, x: Float, y: Float, w: Float, hp: Float, max: Float, label: String) {
        paint.color = Color.DKGRAY; canvas.drawRect(x, y, x + w, y + 25, paint)
        paint.color = if (label == "BOSS") Color.RED else Color.GREEN
        canvas.drawRect(x, y, x + (hp / max.coerceAtLeast(1f)) * w, y + 25, paint)
    }

    private fun drawUI(canvas: Canvas) {
        paint.color = Color.WHITE; paint.textSize = 55f; paint.isFakeBoldText = true
        canvas.drawText("SCORE: $score  LV: $stageLevel", 70f, 110f, paint)

        val validItemsCount = countValidItems()
        paint.textSize = 45f
        if (validItemsCount > 0) { canvas.drawText("ITEMS: $validItemsCount", width / 2f - 100f, 70f, paint) }
        else if (isBossBattle) {
            val timeLeft = ((SPAWN_INTERVAL - itemSpawnTimer) / 60) + 1
            paint.color = Color.YELLOW; canvas.drawText("SUPPLY IN: ${timeLeft}s", width / 2f - 130f, 70f, paint); paint.color = Color.WHITE
        }

        paint.color = Color.argb(210, 255, 255, 255)
        canvas.drawRoundRect(leftBtn, 25f, 25f, paint); canvas.drawRoundRect(rightBtn, 25f, 25f, paint)
        paint.color = Color.argb(210, 255, 100, 100); canvas.drawRoundRect(jumpBtn, 25f, 25f, paint)
        paint.color = if (isAttackPressed) Color.YELLOW else Color.argb(210, 100, 200, 255)
        canvas.drawRoundRect(attackBtn, 25f, 25f, paint)
        paint.color = if (score >= 5000) Color.argb(255, 255, 215, 0) else Color.argb(140, 100, 100, 100)
        canvas.drawRoundRect(ultBtn, 25f, 25f, paint)
        paint.color = Color.BLACK; paint.textSize = 55f
        canvas.drawText("◀", leftBtn.centerX()-28, leftBtn.centerY()+20, paint); canvas.drawText("▶", rightBtn.centerX()-28, rightBtn.centerY()+20, paint)
        paint.textSize = 35f
        canvas.drawText("JUMP", jumpBtn.centerX()-45, jumpBtn.centerY()+12, paint)
        canvas.drawText("ATTACK", attackBtn.centerX()-65, attackBtn.centerY()+12, paint)
        canvas.drawText("ULT", ultBtn.centerX()-35, ultBtn.centerY()+12, paint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val action = event.actionMasked
        var left = false; var right = false; var attack = false
        for (i in 0 until event.pointerCount) {
            val ex = event.getX(i); val ey = event.getY(i)
            if (leftBtn.contains(ex, ey)) left = true
            if (rightBtn.contains(ex, ey)) right = true
            if (attackBtn.contains(ex, ey)) attack = true
            if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_POINTER_DOWN) {
                if (i == event.actionIndex) {
                    if (jumpBtn.contains(ex, ey) && !isJumping) { velocityY = jumpPower; isJumping = true }
                    if (ultBtn.contains(ex, ey) && score >= 5000) useUltimate()
                }
            }
        }
        isLeftPressed = left; isRightPressed = right; isAttackPressed = attack
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_POINTER_UP) {
            if (event.pointerCount <= 1) { isLeftPressed = false; isRightPressed = false; isAttackPressed = false }
        }
        return true
    }

    private fun control() { try { Thread.sleep(17) } catch (e: Exception) {} }

    fun resume() {
        isPlaying = true
        gameThread = Thread(this)
        gameThread?.start()
        // 배경음악 재생
        mediaPlayer?.start()
    }

    fun pause() {
        isPlaying = false
        try { gameThread?.join() } catch (e: Exception) {}
        // 배경음악 일시정지
        mediaPlayer?.pause()
    }

    // 객체 소멸 시 리소스 해제
    fun release() {
        mediaPlayer?.release()
        mediaPlayer = null
        soundPool.release()
    }
}
