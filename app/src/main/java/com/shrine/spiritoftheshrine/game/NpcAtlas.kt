package com.shrine.spiritoftheshrine.game

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap

private const val TILE = 16

/** NPCs are stationary, so we only ever need the down-facing idle pose (column 0 of Idle.png). */
class NpcAtlas(context: Context) {
    val sprite: ImageBitmap = run {
        val sheet = context.assets.open("sprites/npc/Idle.png").use { BitmapFactory.decodeStream(it) }
        android.graphics.Bitmap.createBitmap(sheet, 0, 0, TILE, TILE).asImageBitmap()
    }
}
