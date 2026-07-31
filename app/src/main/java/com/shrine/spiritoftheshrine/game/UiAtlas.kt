package com.shrine.spiritoftheshrine.game

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap

/** Small HUD/UI icons - heart for the health bar, fist for the attack button, bag for the
 * inventory button, potion for the world pickup and the inventory slot. */
class UiAtlas(context: Context) {
    val heart: ImageBitmap = context.assets.open("sprites/items/Heart.png")
        .use { BitmapFactory.decodeStream(it) }.asImageBitmap()
    val fist: ImageBitmap = context.assets.open("sprites/items/Fist.png")
        .use { BitmapFactory.decodeStream(it) }.asImageBitmap()
    val bag: ImageBitmap = context.assets.open("sprites/items/Bag.png")
        .use { BitmapFactory.decodeStream(it) }.asImageBitmap()
    val potion: ImageBitmap = context.assets.open("sprites/items/Potion.png")
        .use { BitmapFactory.decodeStream(it) }.asImageBitmap()
    val key: ImageBitmap = context.assets.open("sprites/items/Key.png")
        .use { BitmapFactory.decodeStream(it) }.asImageBitmap()
}
