package com.shrine.spiritoftheshrine.game

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap

private const val TILE = 16

private fun loadBitmap(context: Context, assetPath: String): Bitmap =
    context.assets.open(assetPath).use { BitmapFactory.decodeStream(it) }

private fun Bitmap.cropTile(col: Int, row: Int): ImageBitmap =
    Bitmap.createBitmap(this, col * TILE, row * TILE, TILE, TILE).asImageBitmap()

/**
 * Slime.png and Spirit.png are both 4x4 grids of 16px frames, same convention as the
 * player's Walk.png: column = direction (Down/Up/Left/Right), row = animation frame.
 * Neither creature has a distinct directional pose (they're round blobs/floating wisps),
 * so we just take column 0 (Down) across all 4 rows as a single idle/move loop reused for
 * every direction - that's the real 4-frame bounce/wobble animation. Taking row 0 across
 * columns instead (the original bug) pulled one frame each of the Down/Up/Left/Right poses
 * into what was meant to be a single animation loop, which read as the creature spinning.
 */
class EnemyAtlas(context: Context) {
    private val slimeFrames = run {
        val sheet = loadBitmap(context, "sprites/enemies/Slime.png")
        (0 until 4).map { sheet.cropTile(0, it) }
    }
    private val spiritFrames = run {
        val sheet = loadBitmap(context, "sprites/enemies/Spirit.png")
        (0 until 4).map { sheet.cropTile(0, it) }
    }

    fun frameFor(enemy: Enemy): ImageBitmap {
        val frames = when (enemy.type) {
            EnemyType.SLIME -> slimeFrames
            EnemyType.SPIRIT -> spiritFrames
        }
        return frames[enemy.animFrame]
    }
}
