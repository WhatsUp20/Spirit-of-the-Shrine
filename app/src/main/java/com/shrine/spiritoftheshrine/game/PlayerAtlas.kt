package com.shrine.spiritoftheshrine.game

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap

private const val TILE = 16

private fun loadBitmap(context: Context, assetPath: String): Bitmap =
    context.assets.open(assetPath).use { BitmapFactory.decodeStream(it) }

private fun Bitmap.cropTile(col: Int, row: Int): Bitmap =
    Bitmap.createBitmap(this, col * TILE, row * TILE, TILE, TILE)

/**
 * Slices the Knight "SeparateAnim" sheets. Idle.png and Attack.png are 4 columns =
 * Down/Up/Left/Right, one frame each. Walk.png is a 4x4 grid where the COLUMN is the
 * direction (same Down/Up/Left/Right order as Idle.png - row 0 is pixel-identical to
 * Idle.png's 4 frames, confirmed by diff) and the ROW is the animation frame - not the
 * other way around. Reading it as "row = direction, column = frame" (the original bug
 * here) pulled one frame each of Down/Up/Left/Right into what was meant to be a single
 * direction's walk cycle, which is why the character looked like it was spinning in
 * place while moving.
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

        walk = mapOf(
            Direction.DOWN to (0 until 4).map { walkSheet.cropTile(0, it).asImageBitmap() },
            Direction.UP to (0 until 4).map { walkSheet.cropTile(1, it).asImageBitmap() },
            Direction.LEFT to (0 until 4).map { walkSheet.cropTile(2, it).asImageBitmap() },
            Direction.RIGHT to (0 until 4).map { walkSheet.cropTile(3, it).asImageBitmap() },
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
