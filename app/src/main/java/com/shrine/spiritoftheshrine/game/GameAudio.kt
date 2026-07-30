package com.shrine.spiritoftheshrine.game

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.SoundPool

/**
 * Background music (looped for the whole session) plus short one-shot SFX for combat events.
 * SoundPool handles the SFX - they're tiny local WAVs, loaded async, safe to "play" even before
 * loading finishes (SoundPool just no-ops that call). Music uses MediaPlayer with prepareAsync
 * so the ~1MB ogg doesn't block the composition that creates this.
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

    private val music = MediaPlayer().apply {
        val fd = context.assets.openFd("audio/music/Theme.ogg")
        setDataSource(fd.fileDescriptor, fd.startOffset, fd.length)
        fd.close()
        isLooping = true
        setVolume(0.5f, 0.5f)
        setOnPreparedListener { it.start() }
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

    fun release() {
        soundPool.release()
        music.stop()
        music.release()
    }
}
