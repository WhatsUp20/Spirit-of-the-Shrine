package com.shrine.spiritoftheshrine.game

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap

private const val TILE = 16

private fun loadBitmap(context: Context, assetPath: String): Bitmap =
    context.assets.open(assetPath).use { BitmapFactory.decodeStream(it) }

private fun Bitmap.cropTile(col: Int, row: Int): Bitmap =
    Bitmap.createBitmap(this, col * TILE, row * TILE, TILE, TILE)

private fun Bitmap.flippedHorizontally(): Bitmap {
    val matrix = Matrix().apply { preScale(-1f, 1f) }
    return Bitmap.createBitmap(this, 0, 0, width, height, matrix, true)
}

/**
 * Slices the Knight "SeparateAnim" sheets. Idle.png is 4 columns = Down/Up/Left/Right, one
 * frame each - all four directions are drawn distinctly there. Walk.png only has unique art
 * for Down (row 0) and Left (row 1); rows 2-3 turned out to be exact duplicates when compared
 * pixel-for-pixel. So Right-walk is Left-walk mirrored here, and Up-walk reuses Down-walk -
 * a deliberate simplification flagged to the user rather than something we quietly do.
 */
class PlayerAtlas(context: Context) {
    val idle: Map<Direction, ImageBitmap>
    val walk: Map<Direction, List<ImageBitmap>>
    val attack: Map<Direction, ImageBitmap>

    init {
        val idleSheet = loadBitmap(context, "sprites/player/Idle.png")
        val walkSheet = loadBitmap(context, "sprites/player/Walk.png")
        val attackSheet = loadBitmap(context, "sprites/player/Attack.png")

        idle = mapOf(
            Direction.DOWN to idleSheet.cropTile(0, 0).asImageBitmap(),
            Direction.UP to idleSheet.cropTile(1, 0).asImageBitmap(),
            Direction.LEFT to idleSheet.cropTile(2, 0).asImageBitmap(),
            Direction.RIGHT to idleSheet.cropTile(3, 0).asImageBitmap(),
        )

        val downWalk = (0 until 4).map { walkSheet.cropTile(it, 0) }
        val leftWalk = (0 until 4).map { walkSheet.cropTile(it, 1) }
        val rightWalk = leftWalk.map { it.flippedHorizontally() }

        walk = mapOf(
            Direction.DOWN to downWalk.map { it.asImageBitmap() },
            Direction.UP to downWalk.map { it.asImageBitmap() },
            Direction.LEFT to leftWalk.map { it.asImageBitmap() },
            Direction.RIGHT to rightWalk.map { it.asImageBitmap() },
        )

        // Attack.png follows the same 4-column layout as Idle.png (Down/Up/Left/Right), all real art.
        attack = mapOf(
            Direction.DOWN to attackSheet.cropTile(0, 0).asImageBitmap(),
            Direction.UP to attackSheet.cropTile(1, 0).asImageBitmap(),
            Direction.LEFT to attackSheet.cropTile(2, 0).asImageBitmap(),
            Direction.RIGHT to attackSheet.cropTile(3, 0).asImageBitmap(),
        )
    }

    fun frameFor(player: Player): ImageBitmap = when {
        player.isAttacking -> attack.getValue(player.facing)
        player.moving -> walk.getValue(player.facing)[player.walkFrame]
        else -> idle.getValue(player.facing)
    }
}
