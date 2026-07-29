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
 * Slime.png and Spirit.png are both 4x4 grids of 16px frames. Neither creature has a
 * distinct left/right/up/down pose (they're round blobs/floating wisps), so unlike the
 * player we just take row 0 as a single 4-frame idle/move loop reused for every direction.
 */
class EnemyAtlas(context: Context) {
    private val slimeFrames = run {
        val sheet = loadBitmap(context, "sprites/enemies/Slime.png")
        (0 until 4).map { sheet.cropTile(it, 0) }
    }
    private val spiritFrames = run {
        val sheet = loadBitmap(context, "sprites/enemies/Spirit.png")
        (0 until 4).map { sheet.cropTile(it, 0) }
    }

    fun frameFor(enemy: Enemy): ImageBitmap {
        val frames = when (enemy.type) {
            EnemyType.SLIME -> slimeFrames
            EnemyType.SPIRIT -> spiritFrames
        }
        return frames[enemy.animFrame]
    }
}
