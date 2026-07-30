package com.shrine.spiritoftheshrine.game

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap

private const val TILE = 16

/** NPCs are stationary, so we only ever need the down-facing idle pose (column 0 of Idle.png). */
class NpcAtlas(context: Context) {
    val villagerSprite: ImageBitmap = loadDownFrame(context, "sprites/npc/Idle.png")
    val elderSprite: ImageBitmap = loadDownFrame(context, "sprites/npc/ElderIdle.png")

    fun spriteFor(npc: Npc): ImageBitmap = when (npc.kind) {
        NpcKind.VILLAGER -> villagerSprite
        NpcKind.ELDER -> elderSprite
    }

    private fun loadDownFrame(context: Context, assetPath: String): ImageBitmap {
        val sheet = context.assets.open(assetPath).use { BitmapFactory.decodeStream(it) }
        return android.graphics.Bitmap.createBitmap(sheet, 0, 0, TILE, TILE).asImageBitmap()
    }
}
