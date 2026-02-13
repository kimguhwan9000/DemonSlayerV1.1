package com.example.androidgame01

import android.content.Context
import android.graphics.*
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.SoundPool
import android.util.Log
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import java.util.*
import kotlin.math.*

class GameView(context: Context) : SurfaceView(context), Runnable {
    private var gameThread: Thread? = null
    private var isPlaying = false
    private var isGameOver = false
    private val paint = Paint()
    private val holder: SurfaceHolder = getHolder()

    private lateinit var playerBitmap: Bitmap
    private lateinit var enemyBitmap: Bitmap
    private lateinit var itemBitmap: Bitmap
    private lateinit var bulletBitmap: Bitmap
    private lateinit var bgBitmap: Bitmap

    private var soundPool: SoundPool
    private var itemSoundId = 0; private var hitSoundId = 0; private var dieSoundId = 0
    private var isSoundLoaded = false
    private var mediaPlayer: MediaPlayer? = null

    private var currentThemeIndex = -1
    private val themeBgRes = arrayOf(R.drawable.bg_theme1, R.drawable.bg_theme2, R.drawable.bg_theme3)
    private val themeBgmRes = arrayOf(R.raw.bgm_theme1, R.raw.bgm_theme2, R.raw.bgm_theme3)

    private var charX = 100f; private var charY = 100f
    private var targetX = 100f; private var targetY = 100f
    private var moveSpeed = 12f; private var attackPower = 50f; private val charSize = 120f
    private var score = 0; private var stage = 1; private var mp = 0f; private val maxMp = 100f

    // [추가] 플레이어 체력 변수
    private var playerMaxHp = 100f
    private var playerHp = 100f

    private var itemCooldown = 180; private val maxItemCooldown = 600
    private var isItemActive = false
    private var invincTimer = 0; private var freezeTimer = 0; private var shakeTime = 0
    private var messageText = ""; private var messageTimer = 0

    private var enemyX = 400f; private var enemyY = 400f
    private var enemySpeedX = 10f; private var enemySpeedY = 10f
    private var currentEnemySize = 110f
    private var enemyMaxHp = 100f; private var enemyHp = 100f
    private var isBossStage = false

    private var bulletX = -5000f; private var bulletY = -5000f
    private var isBulletActive = false
    private val bulletSpeed = 45f; private val bulletSize = 70f

    private var itemX = -2000f; private var itemY = -2000f
    private val itemSize = 80f

    private val attackBtnRect = RectF()
    private val skillBtnRect = RectF()
    private val shopAtkBtnRect = RectF()
    private val shopSpdBtnRect = RectF()
    private val random = Random()

    init {
        val res = context.resources
        try {
            playerBitmap = Bitmap.createScaledBitmap(BitmapFactory.decodeResource(res, R.drawable.player_img), charSize.toInt(), charSize.toInt(), true)
            enemyBitmap = BitmapFactory.decodeResource(res, R.drawable.enemy_img)
            itemBitmap = Bitmap.createScaledBitmap(BitmapFactory.decodeResource(res, R.drawable.item_img), itemSize.toInt(), itemSize.toInt(), true)
            bulletBitmap = Bitmap.createScaledBitmap(BitmapFactory.decodeResource(res, R.drawable.bullet_img), bulletSize.toInt(), bulletSize.toInt(), true)
        } catch (e: Exception) { Log.e("GameView", "이미지 로드 에러") }

        val audioAttributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
        soundPool = SoundPool.Builder().setMaxStreams(5).setAudioAttributes(audioAttributes).build()
        soundPool.setOnLoadCompleteListener { _, _, status -> if (status == 0) isSoundLoaded = true }
        itemSoundId = soundPool.load(context, R.raw.item_sound, 1); hitSoundId = soundPool.load(context, R.raw.hit_sound, 1); dieSoundId = soundPool.load(context, R.raw.enemy_die, 1)
    }

    private fun setupStage() {
        if (width == 0) return
        val newThemeIndex = ((stage - 1) / 10) % themeBgRes.size
        if (newThemeIndex != currentThemeIndex) {
            currentThemeIndex = newThemeIndex
            bgBitmap = BitmapFactory.decodeResource(context.resources, themeBgRes[currentThemeIndex])
            mediaPlayer?.release()
            mediaPlayer = MediaPlayer.create(context, themeBgmRes[currentThemeIndex])
            mediaPlayer?.isLooping = true
            if (isPlaying) mediaPlayer?.start()
        }
        isBossStage = (stage % 5 == 0)
        currentEnemySize = if (isBossStage) 380f else 110f
        enemyMaxHp = if (isBossStage) 600f + (stage * 60f) else 100f + (stage * 20f)
        enemyHp = enemyMaxHp
        enemyX = width / 2f; enemyY = 250f
        enemySpeedX = (10f + (stage * 0.5f)) * (if(random.nextBoolean()) 1f else -1f)
        enemySpeedY = 10f + (stage * 0.5f)
    }

    private fun useUltimate() {
        if (mp >= maxMp) {
            mp = 0f; shakeTime = 25; enemyHp -= 250f
            messageText = "필살기 사용!!"; messageTimer = 40
            if (isSoundLoaded) soundPool.play(dieSoundId, 1f, 1f, 1, 0, 0.7f)
        }
    }

    override fun run() {
        while (isPlaying) {
            if (!isGameOver) update()
            draw()
            try { Thread.sleep(17) } catch (e: Exception) {}
        }
    }

    private fun update() {
        if (width == 0) return
        if (shakeTime > 0) shakeTime--
        if (invincTimer > 0) invincTimer--; if (freezeTimer > 0) freezeTimer--
        if (messageTimer > 0) messageTimer--

        if (!isItemActive) {
            itemCooldown--
            if (itemCooldown <= 0) {
                isItemActive = true
                itemX = random.nextInt((width - 200).coerceAtLeast(1)).toFloat() + 100f
                itemY = random.nextInt((height - 700).coerceAtLeast(1)).toFloat() + 200f
            }
        }

        if (abs(targetX - charX) > moveSpeed) charX += if (targetX > charX) moveSpeed else -moveSpeed
        if (abs(targetY - charY) > moveSpeed) charY += if (targetY > charY) moveSpeed else -moveSpeed

        if (isBulletActive) {
            bulletX += bulletSpeed
            if (bulletX > width + 100) isBulletActive = false
        }

        if (freezeTimer <= 0) {
            enemyX += enemySpeedX; enemyY += enemySpeedY
            if (enemyX <= 0) { enemyX = 0f; enemySpeedX = abs(enemySpeedX) }
            else if (enemyX >= width - currentEnemySize) { enemyX = width - currentEnemySize; enemySpeedX = -abs(enemySpeedX) }
            if (enemyY <= 150) { enemyY = 150f; enemySpeedY = abs(enemySpeedY) }
            else if (enemyY >= height - 450) { enemyY = height - 450f; enemySpeedY = -abs(enemySpeedY) }
        }

        val playerRect = RectF(charX, charY, charX + charSize, charY + charSize)
        val enemyRect = RectF(enemyX, enemyY, enemyX + currentEnemySize, enemyY + currentEnemySize)

        if (isItemActive && playerRect.intersect(RectF(itemX, itemY, itemX + itemSize, itemY + itemSize))) {
            isItemActive = false; itemCooldown = maxItemCooldown
            if (isSoundLoaded) soundPool.play(itemSoundId, 1f, 1f, 0, 0, 1f)
            when(random.nextInt(5)) {
                0 -> { invincTimer = 300; messageText = "무적 모드!"; messageTimer = 50 }
                1 -> { freezeTimer = 200; messageText = "시간 정지!"; messageTimer = 50 }
                2 -> { moveSpeed += 3f; messageText = "스피드 UP!"; messageTimer = 50 }
                3 -> { attackPower += 15f; messageText = "공격력 UP!"; messageTimer = 50 }
                4 -> { playerHp = (playerHp + 30f).coerceAtMost(playerMaxHp); messageText = "체력 회복!"; messageTimer = 50 }
            }
        }

        // [수정] 플레이어 체력 차감 로직
        if (playerRect.intersect(enemyRect) && invincTimer <= 0) {
            playerHp -= 20f; if (isSoundLoaded) soundPool.play(hitSoundId, 1f, 1f, 0, 0, 1f)
            enemyX = width.toFloat()
            if (playerHp <= 0) { playerHp = 0f; isGameOver = true }
        }

        if (isBulletActive && RectF(bulletX, bulletY, bulletX + bulletSize, bulletY + bulletSize).intersect(enemyRect)) {
            isBulletActive = false; enemyHp -= attackPower; mp = (mp + 8f).coerceAtMost(maxMp)
            if (enemyHp <= 0) {
                stage++; score += 100; setupStage()
                if (isSoundLoaded) soundPool.play(dieSoundId, 1f, 1f, 0, 0, 1f)
            }
        }
    }

    private fun draw() {
        if (holder.surface.isValid) {
            val canvas = holder.lockCanvas()
            canvas.save()
            if (shakeTime > 0) canvas.translate((random.nextInt(40) - 20).toFloat(), (random.nextInt(40) - 20).toFloat())

            if (::bgBitmap.isInitialized) canvas.drawBitmap(bgBitmap, null, Rect(0, 0, width, height), null)
            else canvas.drawColor(Color.parseColor("#D5E2C5"))

            if (isItemActive) canvas.drawBitmap(itemBitmap, itemX, itemY, paint)
            canvas.drawBitmap(enemyBitmap, null, RectF(enemyX, enemyY, enemyX + currentEnemySize, enemyY + currentEnemySize), paint)
            canvas.drawBitmap(playerBitmap, charX, charY, paint)
            if (isBulletActive) canvas.drawBitmap(bulletBitmap, bulletX, bulletY, paint)

            // [추가] 플레이어 머리 위 체력바
            paint.color = Color.DKGRAY; canvas.drawRect(charX, charY - 25f, charX + charSize, charY - 10f, paint)
            paint.color = Color.GREEN; canvas.drawRect(charX, charY - 25f, charX + (playerHp / playerMaxHp * charSize), charY - 10f, paint)

            drawUI(canvas)
            canvas.restore()

            if (messageTimer > 0) {
                paint.color = Color.YELLOW; paint.textSize = 80f; paint.textAlign = Paint.Align.CENTER
                canvas.drawText(messageText, width / 2f, height / 2f - 100f, paint)
                paint.textAlign = Paint.Align.LEFT
            }

            if (isGameOver) {
                paint.color = Color.argb(230, 0, 0, 0); canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
                paint.color = Color.RED; paint.textSize = 100f; paint.textAlign = Paint.Align.CENTER
                canvas.drawText("GAME OVER", width / 2f, height / 2f, paint)
                paint.textAlign = Paint.Align.LEFT
            }
            holder.unlockCanvasAndPost(canvas)
        }
    }

    private fun drawUI(canvas: Canvas) {
        paint.color = Color.WHITE; paint.textSize = 45f; paint.isFakeBoldText = true
        canvas.drawText("STAGE $stage  SCORE $score", 50f, 60f, paint)

        // [추가] 상단 플레이어 HP 숫자 표시
        paint.color = Color.GREEN; canvas.drawText("HP: ${playerHp.toInt()} / ${playerMaxHp.toInt()}", 50f, 110f, paint)

        paint.color = Color.CYAN; canvas.drawRect(50f, 130f, 50f + (mp/maxMp * 300f), 150f, paint)

        // 적 HP바
        paint.color = Color.DKGRAY; canvas.drawRect(enemyX, enemyY - 25f, enemyX + currentEnemySize, enemyY - 10f, paint)
        paint.color = Color.RED; canvas.drawRect(enemyX, enemyY - 25f, enemyX + (enemyHp/enemyMaxHp * currentEnemySize), enemyY - 10f, paint)

        val uiBaseY = height - 250f
        skillBtnRect.set(width - 350f, uiBaseY - 180f, width - 50f, uiBaseY - 20f)
        paint.color = if (mp >= maxMp) Color.YELLOW else Color.GRAY
        canvas.drawRoundRect(skillBtnRect, 30f, 30f, paint)
        paint.color = Color.BLACK; paint.textSize = 45f; canvas.drawText("ULTIMATE", width - 300f, uiBaseY - 80f, paint)

        attackBtnRect.set(width - 350f, uiBaseY, width - 50f, uiBaseY + 180f)
        paint.color = Color.argb(220, 255, 50, 50); canvas.drawRoundRect(attackBtnRect, 30f, 30f, paint)
        paint.color = Color.WHITE; paint.textSize = 50f; canvas.drawText("ATTACK", width - 290f, uiBaseY + 105f, paint)

        shopAtkBtnRect.set(50f, uiBaseY, 350f, uiBaseY + 85f)
        paint.color = Color.parseColor("#FFA500"); canvas.drawRoundRect(shopAtkBtnRect, 15f, 15f, paint)
        paint.color = Color.BLACK; paint.textSize = 35f; canvas.drawText("ATK UP (50pt)", 85f, uiBaseY + 55f, paint)

        shopSpdBtnRect.set(50f, uiBaseY + 95f, 350f, uiBaseY + 180f)
        paint.color = Color.parseColor("#87CEEB"); canvas.drawRoundRect(shopSpdBtnRect, 15f, 15f, paint)
        paint.color = Color.BLACK; canvas.drawText("SPD UP (30pt)", 85f, uiBaseY + 150f, paint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action != MotionEvent.ACTION_DOWN) return true
        val tx = event.x; val ty = event.y
        if (isGameOver) {
            score = 0; stage = 1; playerHp = 100f; setupStage(); mp = 0f; isGameOver = false
            return true
        }

        when {
            skillBtnRect.contains(tx, ty) -> useUltimate()
            attackBtnRect.contains(tx, ty) -> {
                if (!isBulletActive) {
                    bulletX = charX + charSize; bulletY = charY + charSize/3
                    isBulletActive = true
                    if (isSoundLoaded) soundPool.play(hitSoundId, 0.5f, 0.5f, 0, 0, 1.2f)
                }
            }
            shopAtkBtnRect.contains(tx, ty) && score >= 50 -> { score -= 50; attackPower += 20f; messageText = "공격력 강화!"; messageTimer = 40 }
            shopSpdBtnRect.contains(tx, ty) && score >= 30 -> { score -= 30; moveSpeed += 2f; messageText = "이동속도 강화!"; messageTimer = 40 }
            else -> { targetX = tx - charSize/2; targetY = ty - charSize/2 }
        }
        return true
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) { super.onSizeChanged(w, h, oldw, oldh); setupStage() }
    fun resume() { isPlaying = true; gameThread = Thread(this); gameThread?.start(); mediaPlayer?.start() }
    fun pause() { isPlaying = false; mediaPlayer?.pause(); try { gameThread?.join() } catch (e: Exception) {} }
}
