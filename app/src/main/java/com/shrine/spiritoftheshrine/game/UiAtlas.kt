package com.shrine.spiritoftheshrine.game

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap

/** Small HUD/UI icons - heart for the health bar, sword for the attack button. */
class UiAtlas(context: Context) {
    val heart: ImageBitmap = context.assets.open("sprites/items/Heart.png")
        .use { BitmapFactory.decodeStream(it) }.asImageBitmap()
    val sword: ImageBitmap = context.assets.open("sprites/items/Sword.png")
        .use { BitmapFactory.decodeStream(it) }.asImageBitmap()
}
