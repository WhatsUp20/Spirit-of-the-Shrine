package com.shrine.spiritoftheshrine.game

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap

/** Assets used only by the opening ship/storm cutscene - not part of the playable world. */
class CutsceneAtlas(context: Context) {
    val boat: ImageBitmap = context.assets.open("sprites/cutscene/Boat.png")
        .use { BitmapFactory.decodeStream(it) }.asImageBitmap()
}
