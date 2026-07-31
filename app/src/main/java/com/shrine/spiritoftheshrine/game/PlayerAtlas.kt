package com.shrine.spiritoftheshrine.game

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap

private fun loadBitmap(context: Context, assetPath: String): Bitmap =
    context.assets.open(assetPath).use { BitmapFactory.decodeStream(it) }

private fun Bitmap.flippedHorizontally(): Bitmap {
    val matrix = Matrix().apply { preScale(-1f, 1f) }
    return Bitmap.createBitmap(this, 0, 0, width, height, matrix, true)
}

/**
 * Custom player character (user-supplied reference art, cropped/cleaned up by hand - not from
 * the Ninja Adventure pack, so none of the pack's grid conventions apply here). Every pose is
 * its own standalone image with its own aspect ratio (this is full-body art, unlike the pack's
 * bust-only characters) - Right is always Left mirrored at load time, so only Down/Up/Left need
 * source art for each pose set (idle, walk-step, attack frame 1, attack frame 2).
 */
class PlayerAtlas(context: Context) {
    val idle: Map<Direction, ImageBitmap>
    val walk: Map<Direction, List<ImageBitmap>>
    val attack: Map<Direction, List<ImageBitmap>>

    // Hand-drawn cutouts from the user's reference art (top-down view, unlike the standing
    // poses above) - used only for the post-cutscene waking-up beat: lying, then sitting up,
    // then a normal idle frame takes over.
    val lyingDown: ImageBitmap = loadBitmap(context, "sprites/player/LyingDown.png").asImageBitmap()
    val sittingUp: ImageBitmap = loadBitmap(context, "sprites/player/SittingUp.png").asImageBitmap()

    init {
        fun directional(prefix: String, suffix: String = ""): Map<Direction, ImageBitmap> {
            val down = loadBitmap(context, "sprites/player/${prefix}Down$suffix.png")
            val up = loadBitmap(context, "sprites/player/${prefix}Up$suffix.png")
            val left = loadBitmap(context, "sprites/player/${prefix}Left$suffix.png")
            return mapOf(
                Direction.DOWN to down.asImageBitmap(),
                Direction.UP to up.asImageBitmap(),
                Direction.LEFT to left.asImageBitmap(),
                Direction.RIGHT to left.flippedHorizontally().asImageBitmap(),
            )
        }

        idle = directional("")
        val walkStep = directional("Walk")
        val attack1 = directional("Attack", "1")
        val attack2 = directional("Attack", "2")

        // Two-frame walk cycle: standing pose alternating with the mid-stride pose.
        walk = Direction.entries.associateWith { d -> listOf(idle.getValue(d), walkStep.getValue(d), idle.getValue(d), walkStep.getValue(d)) }
        attack = Direction.entries.associateWith { d -> listOf(attack1.getValue(d), attack2.getValue(d)) }
    }

    fun frameFor(player: Player): ImageBitmap = when {
        player.isAttacking -> attack.getValue(player.facing)[if (player.attackProgress < 0.5f) 0 else 1]
        player.moving -> walk.getValue(player.facing)[player.walkFrame]
        else -> idle.getValue(player.facing)
    }
}
