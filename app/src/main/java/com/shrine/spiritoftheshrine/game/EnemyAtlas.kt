package com.shrine.spiritoftheshrine.game

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap

private const val TILE = 16
private const val BOSS_TILE = 82

private fun loadBitmap(context: Context, assetPath: String): Bitmap =
    context.assets.open(assetPath).use { BitmapFactory.decodeStream(it) }

private fun Bitmap.cropTile(col: Int, row: Int, tile: Int = TILE): ImageBitmap =
    Bitmap.createBitmap(this, col * tile, row * tile, tile, tile).asImageBitmap()

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

    // Boss.png (Tengu) is a single row of 6 frames at 82px each - direction doesn't apply here
    // either (the boss doesn't move much), so just the first 4 as an idle loop, same as above.
    private val bossFrames = run {
        val sheet = loadBitmap(context, "sprites/enemies/Boss.png")
        (0 until 4).map { sheet.cropTile(it, 0, BOSS_TILE) }
    }

    fun frameFor(enemy: Enemy): ImageBitmap {
        val frames = when (enemy.type) {
            EnemyType.SLIME -> slimeFrames
            EnemyType.SPIRIT -> spiritFrames
            EnemyType.BOSS -> bossFrames
        }
        return frames[enemy.animFrame]
    }
}
