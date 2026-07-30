package com.shrine.spiritoftheshrine.game

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.SoundPool

/**
 * Background music (starts once the opening cutscene ends, loops for the rest of the session),
 * a looping ambient track for the cutscene itself, plus short one-shot SFX for combat events.
 * SoundPool handles the SFX - they're tiny local WAVs, loaded async, safe to "play" even before
 * loading finishes (SoundPool just no-ops that call). Music/ambient use MediaPlayer with
 * prepareAsync so the (larger) files don't block the composition that creates this - each
 * tracks its own "primed" flag so a start request arriving before prepare finishes is honored
 * as soon as it does, instead of throwing or getting silently dropped.
 */
class GameAudio(context: Context) {
    private val soundPool = SoundPool.Builder()
        .setMaxStreams(4)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )
        .build()

    private val attackSoundId = loadSfx(context, "audio/sfx/Attack.wav")
    private val enemyHitSoundId = loadSfx(context, "audio/sfx/EnemyHit.wav")
    private val playerHurtSoundId = loadSfx(context, "audio/sfx/PlayerHurt.wav")

    private var musicPrimed = false
    private var musicRequested = false
    private val music = MediaPlayer().apply {
        val fd = context.assets.openFd("audio/music/Theme.ogg")
        setDataSource(fd.fileDescriptor, fd.startOffset, fd.length)
        fd.close()
        isLooping = true
        setVolume(0.5f, 0.5f)
        setOnPreparedListener {
            musicPrimed = true
            if (musicRequested) it.start()
        }
        prepareAsync()
    }

    private var ambientPrimed = false
    private var ambientRequested = false
    private val ambient = MediaPlayer().apply {
        val fd = context.assets.openFd("audio/ambient/Storm.wav")
        setDataSource(fd.fileDescriptor, fd.startOffset, fd.length)
        fd.close()
        isLooping = true
        setVolume(0.7f, 0.7f)
        setOnPreparedListener {
            ambientPrimed = true
            if (ambientRequested) it.start()
        }
        prepareAsync()
    }

    private fun loadSfx(context: Context, assetPath: String): Int =
        context.assets.openFd(assetPath).use { soundPool.load(it, 1) }

    fun play(event: SoundEvent) {
        val soundId = when (event) {
            SoundEvent.SWORD_SWING -> attackSoundId
            SoundEvent.ENEMY_HIT -> enemyHitSoundId
            SoundEvent.PLAYER_HURT -> playerHurtSoundId
        }
        soundPool.play(soundId, 1f, 1f, 0, 0, 1f)
    }

    /** Sea/storm loop for the opening cutscene. */
    fun startAmbient() {
        ambientRequested = true
        if (ambientPrimed && !ambient.isPlaying) ambient.start()
    }

    fun stopAmbient() {
        ambientRequested = false
        if (ambient.isPlaying) ambient.pause()
    }

    /** Starts the looping background music - called once the opening cutscene ends. */
    fun startMusic() {
        musicRequested = true
        if (musicPrimed && !music.isPlaying) music.start()
    }

    /** Call from onPause/onStop so nothing keeps playing while the app is backgrounded. */
    fun pauseMusic() {
        if (music.isPlaying) music.pause()
        if (ambient.isPlaying) ambient.pause()
    }

    fun resumeMusic() {
        if (musicRequested && musicPrimed && !music.isPlaying) music.start()
        if (ambientRequested && ambientPrimed && !ambient.isPlaying) ambient.start()
    }

    fun release() {
        soundPool.release()
        music.stop()
        music.release()
        ambient.stop()
        ambient.release()
    }
}
