package com.shrine.spiritoftheshrine.game

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap

private const val TILE = 16

private fun loadBitmap(context: Context, assetPath: String): Bitmap =
    context.assets.open(assetPath).use { BitmapFactory.decodeStream(it) }

private fun Bitmap.cropTile(col: Int, row: Int, w: Int = TILE, h: Int = TILE): ImageBitmap =
    Bitmap.createBitmap(this, col * TILE, row * TILE, w, h).asImageBitmap()

/** 3x3 autotile block: [col][row], (1,1) is the fully-surrounded center tile. */
private fun blobFrom(bitmap: Bitmap, originCol: Int, originRow: Int): Array<Array<ImageBitmap>> =
    Array(3) { c -> Array(3) { r -> bitmap.cropTile(originCol + c, originRow + r) } }

/**
 * Given whether each neighbor is the *same* region as the tile being drawn, picks the
 * (col, row) offset within a 3x3 blob block. Corner variants take priority over edges.
 */
fun blobIndex(sameUp: Boolean, sameDown: Boolean, sameLeft: Boolean, sameRight: Boolean): Pair<Int, Int> {
    val exposedUp = !sameUp
    val exposedDown = !sameDown
    val exposedLeft = !sameLeft
    val exposedRight = !sameRight
    return when {
        exposedUp && exposedLeft -> 0 to 0
        exposedUp && exposedRight -> 2 to 0
        exposedDown && exposedLeft -> 0 to 2
        exposedDown && exposedRight -> 2 to 2
        exposedUp -> 1 to 0
        exposedDown -> 1 to 2
        exposedLeft -> 0 to 1
        exposedRight -> 2 to 1
        else -> 1 to 1
    }
}

/**
 * Hand-picked cells from the Ninja Adventure Asset Pack (CC0, pixel-boy). Coordinates were
 * found by overlaying a 16px grid on each sheet and reading off column/row by eye - see the
 * conversation history for the exact sheets inspected. Floor-type tiles use a real 3x3 blob
 * autotile (grass/village/dungeon/temple ground blend at their edges); walls, the gate and
 * trees are single representative sprites (no corner blending - see project notes).
 */
class TileAtlas(context: Context) {
    private val fieldSheet = loadBitmap(context, "tilesets/TilesetField.png")
    private val reliefSheet = loadBitmap(context, "tilesets/TilesetRelief.png")
    private val houseSheet = loadBitmap(context, "tilesets/TilesetHouse.png")
    private val natureSheet = loadBitmap(context, "tilesets/TilesetNature.png")
    private val waterSheet = loadBitmap(context, "tilesets/TilesetWater.png")
    private val waterRippleSheet = loadBitmap(context, "tilesets/WaterRipples.png")

    val grass: ImageBitmap = fieldSheet.cropTile(1, 4)
    val villageFloorBlob: Array<Array<ImageBitmap>> = blobFrom(fieldSheet, originCol = 0, originRow = 0)
    val dungeonFloorBlob: Array<Array<ImageBitmap>> = blobFrom(fieldSheet, originCol = 0, originRow = 6)
    val templeFloorBlob: Array<Array<ImageBitmap>> = blobFrom(fieldSheet, originCol = 0, originRow = 12)
    // Same sheet, a previously-unused peach-colored block - works as a sandy beach blob with
    // the same ready-made autotile edges as the other floor types.
    val sandBlob: Array<Array<ImageBitmap>> = blobFrom(fieldSheet, originCol = 0, originRow = 9)
    // This is one cell of a decorative pond graphic (sand ring + foam edge around open water),
    // reused here as the ocean's blob block - its "water meets land" edge art works just as
    // well for a coastline as it does for a pond shore.
    val waterBlob: Array<Array<ImageBitmap>> = blobFrom(waterSheet, originCol = 13, originRow = 0)
    // A 4-frame ripple animation, used only for fully-surrounded "open water" tiles - the
    // shore-blending edge/corner tiles above stay static since there's no animated set with
    // matching sand/grass edges.
    val waterRipple: List<ImageBitmap> = (0 until 4).map { waterRippleSheet.cropTile(it, 0) }

    val dungeonWall: ImageBitmap = reliefSheet.cropTile(5, 1)
    val templeWall: ImageBitmap = reliefSheet.cropTile(5, 6)
    val houseWall: ImageBitmap = houseSheet.cropTile(0, 3)
    val templeGate: ImageBitmap = houseSheet.cropTile(9, 3)
    // TilesetNature.png's icons aren't laid out on a clean non-overlapping grid - adjacent
    // trees' bounding boxes overlap each other, so a naive 48x48 grid-cell crop pulled in a
    // sliver of the neighboring pine tree along with the intended bush (that stray wedge is
    // what read as a "cropped fir tree" glued onto every tree on the map). 32x32 from the same
    // origin stays inside the bush's own silhouette - confirmed by dumping the alpha channel
    // and checking column-by-column where the neighbor's pixels actually start.
    val tree: ImageBitmap = natureSheet.cropTile(0, 0, w = 32, h = 32)
    // A tall segmented bamboo stalk, three tiles high - blocks movement the same way TREE does.
    val bamboo: ImageBitmap = natureSheet.cropTile(11, 8, w = 16, h = 48)
    // Hand-drawn (not from the pack - it has no torii asset): a mossy vermilion shrine gate,
    // the landmark on the path from the beach to the village. Non-blocking, walked under.
    val torii: ImageBitmap = loadBitmap(context, "sprites/landmarks/Torii.png").asImageBitmap()
    // Shipwreck debris scattered on the beach - a real crate from the pack plus two hand-drawn
    // pieces (plank, barrel) for variety. Picked per-marker by position so it stays deterministic.
    val debris: List<ImageBitmap> = listOf(
        loadBitmap(context, "sprites/landmarks/DebrisCrate.png").asImageBitmap(),
        loadBitmap(context, "sprites/landmarks/DebrisPlank.png").asImageBitmap(),
        loadBitmap(context, "sprites/landmarks/DebrisBarrel.png").asImageBitmap(),
    )
}
