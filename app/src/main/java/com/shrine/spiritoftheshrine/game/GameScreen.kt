package com.shrine.spiritoftheshrine.game

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import android.os.Build
import android.view.View
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlinx.coroutines.delay

/** Tiles visible across the shorter screen dimension - sets the zoom level. */
private const val VISIBLE_TILES = 16f

@Composable
fun GameScreen() {
    val context = LocalContext.current
    // Two standalone maps, not one continuous world: the player starts on the beach and steps
    // through its LOCATION_EXIT onto the village map (see the transition check in the game-loop
    // LaunchedEffect below). There's currently no exit back the other way.
    var tileMap by remember { mutableStateOf(TileMap.loadBeach()) }
    val atlas = remember { TileAtlas(context) }
    val playerAtlas = remember { PlayerAtlas(context) }
    val enemyAtlas = remember { EnemyAtlas(context) }
    val npcAtlas = remember { NpcAtlas(context) }
    val uiAtlas = remember { UiAtlas(context) }
    val cutsceneAtlas = remember { CutsceneAtlas(context) }
    val gameAudio = remember { GameAudio(context) }
    var engine by remember { mutableStateOf(GameEngine(tileMap)) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> gameAudio.pauseMusic()
                Lifecycle.Event.ON_RESUME -> gameAudio.resumeMusic()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            gameAudio.release()
        }
    }

    val inputVector = remember { mutableStateOf(0f to 0f) }
    var attackRequested by remember { mutableStateOf(false) }
    var isInventoryOpen by remember { mutableStateOf(false) }
    var introPhase by remember { mutableStateOf(IntroPhase.SHIP) }
    var cutsceneLineIndex by remember { mutableStateOf(0) }
    var fadeInAlpha by remember { mutableStateOf(1f) }

    LaunchedEffect(introPhase) {
        if (introPhase != IntroPhase.FADE_IN) return@LaunchedEffect
        var elapsed = 0f
        var lastFrameNanos = withFrameNanos { it }
        while (elapsed < FADE_IN_DURATION_S) {
            withFrameNanos { nowNanos ->
                val dt = ((nowNanos - lastFrameNanos) / 1_000_000_000.0).toFloat()
                lastFrameNanos = nowNanos
                elapsed += dt
                fadeInAlpha = (1f - elapsed / FADE_IN_DURATION_S).coerceIn(0f, 1f)
            }
        }
        fadeInAlpha = 0f
        introPhase = IntroPhase.SITTING
    }

    LaunchedEffect(introPhase) {
        if (introPhase != IntroPhase.SITTING) return@LaunchedEffect
        delay((SITTING_DURATION_S * 1000).toLong())
        introPhase = IntroPhase.DONE
    }
    var frameTick by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) { gameAudio.startAmbient() }

    LaunchedEffect(Unit) {
        var lastFrameNanos = withFrameNanos { it }
        while (true) {
            withFrameNanos { nowNanos ->
                val dt = ((nowNanos - lastFrameNanos) / 1_000_000_000.0).toFloat().coerceAtMost(1f / 20f)
                lastFrameNanos = nowNanos
                val (dx, dy) = inputVector.value
                val worldFrozen = isInventoryOpen || introPhase != IntroPhase.DONE
                engine.update(dt, dx, dy, attackRequested, paused = worldFrozen)
                attackRequested = false
                for (event in engine.pendingSounds) gameAudio.play(event)
                engine.pendingSounds.clear()
                if (!worldFrozen && engine.reachedExitToVillage()) {
                    val nextMap = TileMap.loadVillage()
                    val carryOver = engine.player
                    tileMap = nextMap
                    engine = GameEngine(nextMap, carryOver)
                    gameAudio.startMusic()
                } else if (!worldFrozen && engine.reachedExitToBeach()) {
                    val nextMap = TileMap.loadBeach()
                    val carryOver = engine.player
                    tileMap = nextMap
                    engine = GameEngine(nextMap, carryOver, spawnMarker = MarkerType.BEACH_RETURN_SPAWN)
                    // Music keeps playing once it's started - only the beach's very first visit
                    // (before the village is ever reached) is silent.
                }
                frameTick++
            }
        }
    }

    // Reading frameTick here recomposes the whole screen every frame, which is what keeps
    // the HUD hearts and the death screen in sync with engine state. The Canvas below *also*
    // reads it internally for its own draw-phase invalidation, but that alone only kept the
    // Canvas itself fresh - HeartsHud and the death-screen check live outside the Canvas, so
    // without this read they only ever updated on the next touch-driven recomposition (a tap
    // or joystick drag), which is why hearts looked frozen and the death screen never showed.
    @Suppress("UNUSED_EXPRESSION")
    frameTick

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            // Reading frameTick here (inside the draw phase) is what actually makes this
            // redraw every frame. Reading it up in the composable body instead relied on a
            // full recomposition of GameScreen to regenerate this lambda, which turned out to
            // be unreliable - the canvas would only refresh in response to touch-driven state
            // changes (like the joystick), so enemies visibly only moved while the player did.
            @Suppress("UNUSED_EXPRESSION")
            frameTick
            val cameraRow = engine.player.row.coerceIn(VISIBLE_TILES / 2f, tileMap.height - VISIBLE_TILES / 2f)
            val cameraCol = engine.player.col.coerceIn(VISIBLE_TILES / 2f, tileMap.width - VISIBLE_TILES / 2f)
            val tilePx = size.minDimension / VISIBLE_TILES
            val originX = size.width / 2f - cameraCol * tilePx
            val originY = size.height / 2f - cameraRow * tilePx

            val firstCol = max(0, (cameraCol - VISIBLE_TILES).toInt())
            val lastCol = min(tileMap.width - 1, (cameraCol + VISIBLE_TILES).toInt())
            val firstRow = max(0, (cameraRow - VISIBLE_TILES).toInt())
            val lastRow = min(tileMap.height - 1, (cameraRow + VISIBLE_TILES).toInt())

            val waterRippleFrame = (frameTick / 8) % atlas.waterRipple.size

            fun sameRegion(type: TileType, row: Int, col: Int) = tileMap.tileAt(row, col) == type

            fun drawBlob(block: Array<Array<ImageBitmap>>, type: TileType, row: Int, col: Int, x: Float, y: Float) {
                val (bc, br) = blobIndex(
                    sameUp = sameRegion(type, row - 1, col),
                    sameDown = sameRegion(type, row + 1, col),
                    sameLeft = sameRegion(type, row, col - 1),
                    sameRight = sameRegion(type, row, col + 1),
                )
                if (type == TileType.WATER && bc == 1 && br == 1) {
                    // Fully-surrounded open water - animate it instead of the static center tile.
                    drawTileImage(atlas.waterRipple[waterRippleFrame], x, y, tilePx)
                } else {
                    drawTileImage(block[bc][br], x, y, tilePx)
                }
            }

            for (row in firstRow..lastRow) {
                for (col in firstCol..lastCol) {
                    val x = originX + col * tilePx
                    val y = originY + row * tilePx
                    when (val type = tileMap.tileAt(row, col)) {
                        TileType.VILLAGE_FLOOR -> drawBlob(atlas.villageFloorBlob, type, row, col, x, y)
                        TileType.DUNGEON_FLOOR -> drawBlob(atlas.dungeonFloorBlob, type, row, col, x, y)
                        TileType.TEMPLE_FLOOR -> drawBlob(atlas.templeFloorBlob, type, row, col, x, y)
                        TileType.SAND -> drawBlob(atlas.sandBlob, type, row, col, x, y)
                        TileType.WATER -> drawBlob(atlas.waterBlob, type, row, col, x, y)
                        TileType.DUNGEON_WALL -> drawTileImage(atlas.dungeonWall, x, y, tilePx)
                        TileType.TEMPLE_WALL -> drawTileImage(atlas.templeWall, x, y, tilePx)
                        TileType.TEMPLE_GATE -> if (engine.isGateOpen) {
                            // Just the fully-surrounded blob cell, not a blended edge - these
                            // three tiles sit in an isolated row so autotiling against their
                            // still-TEMPLE_GATE neighbors would draw a seam between each of them.
                            drawTileImage(atlas.templeFloorBlob[1][1], x, y, tilePx)
                        } else {
                            drawTileImage(atlas.templeGate, x, y, tilePx)
                        }
                        TileType.HOUSE -> drawTileImage(atlas.houseWall, x, y, tilePx)
                        TileType.GRASS, TileType.TREE, TileType.PALM, TileType.BAMBOO -> drawTileImage(atlas.grass, x, y, tilePx)
                    }
                }
            }

            for (row in firstRow..lastRow) {
                for (col in firstCol..lastCol) {
                    if (tileMap.tileAt(row, col) != TileType.TREE) continue
                    val treeSize = tilePx * 1.6f
                    val cx = originX + (col + 0.5f) * tilePx
                    val bottomY = originY + (row + 1) * tilePx
                    drawImage(
                        image = atlas.tree,
                        dstOffset = IntOffset((cx - treeSize / 2f).roundToInt(), (bottomY - treeSize).roundToInt()),
                        dstSize = IntSize(treeSize.roundToInt(), treeSize.roundToInt()),
                        filterQuality = FilterQuality.None,
                    )
                }
            }

            for (row in firstRow..lastRow) {
                for (col in firstCol..lastCol) {
                    if (tileMap.tileAt(row, col) != TileType.PALM) continue
                    val palmSize = tilePx * 1.6f
                    val cx = originX + (col + 0.5f) * tilePx
                    val bottomY = originY + (row + 1) * tilePx
                    drawImage(
                        image = atlas.palm,
                        dstOffset = IntOffset((cx - palmSize / 2f).roundToInt(), (bottomY - palmSize).roundToInt()),
                        dstSize = IntSize(palmSize.roundToInt(), palmSize.roundToInt()),
                        filterQuality = FilterQuality.None,
                    )
                }
            }

            for (row in firstRow..lastRow) {
                for (col in firstCol..lastCol) {
                    if (tileMap.tileAt(row, col) != TileType.BAMBOO) continue
                    val bambooWidth = tilePx
                    val bambooHeight = tilePx * 3f
                    val cx = originX + (col + 0.5f) * tilePx
                    val bottomY = originY + (row + 1) * tilePx
                    drawImage(
                        image = atlas.bamboo,
                        dstOffset = IntOffset((cx - bambooWidth / 2f).roundToInt(), (bottomY - bambooHeight).roundToInt()),
                        dstSize = IntSize(bambooWidth.roundToInt(), bambooHeight.roundToInt()),
                        filterQuality = FilterQuality.None,
                    )
                }
            }

            for (spawn in tileMap.spawnPoints) {
                if (spawn.marker != MarkerType.TORII_LANDMARK) continue
                val toriiWidth = tilePx * 2f
                val toriiHeight = tilePx * 2.75f
                val cx = originX + (spawn.col + 0.5f) * tilePx
                val bottomY = originY + (spawn.row + 1) * tilePx
                drawImage(
                    image = atlas.torii,
                    dstOffset = IntOffset((cx - toriiWidth / 2f).roundToInt(), (bottomY - toriiHeight).roundToInt()),
                    dstSize = IntSize(toriiWidth.roundToInt(), toriiHeight.roundToInt()),
                    filterQuality = FilterQuality.None,
                )
            }

            for (spawn in tileMap.spawnPoints) {
                if (spawn.marker != MarkerType.SHIPWRECK_DEBRIS) continue
                val debrisImage = atlas.debris[(spawn.row * 31 + spawn.col) % atlas.debris.size]
                val debrisSize = tilePx * 0.8f
                val cx = originX + (spawn.col + 0.5f) * tilePx
                val bottomY = originY + (spawn.row + 1) * tilePx
                drawImage(
                    image = debrisImage,
                    dstOffset = IntOffset((cx - debrisSize / 2f).roundToInt(), (bottomY - debrisSize).roundToInt()),
                    dstSize = IntSize(debrisSize.roundToInt(), debrisSize.roundToInt()),
                    filterQuality = FilterQuality.None,
                )
            }

            for (npc in engine.npcs) {
                val npcSize = tilePx * 1.1f
                val cx = originX + (npc.col + 0.5f) * tilePx
                val cy = originY + (npc.row + 0.5f) * tilePx
                drawImage(
                    image = npcAtlas.spriteFor(npc),
                    dstOffset = IntOffset((cx - npcSize / 2f).roundToInt(), (cy - npcSize / 2f).roundToInt()),
                    dstSize = IntSize(npcSize.roundToInt(), npcSize.roundToInt()),
                    filterQuality = FilterQuality.None,
                )
            }

            for (pickup in engine.potionPickups) {
                val pickupSize = tilePx * 0.7f
                val cx = originX + (pickup.col + 0.5f) * tilePx
                val cy = originY + (pickup.row + 0.5f) * tilePx
                drawImage(
                    image = uiAtlas.potion,
                    dstOffset = IntOffset((cx - pickupSize / 2f).roundToInt(), (cy - pickupSize / 2f).roundToInt()),
                    dstSize = IntSize(pickupSize.roundToInt(), pickupSize.roundToInt()),
                    filterQuality = FilterQuality.None,
                )
            }

            for (pickup in engine.keyPickups) {
                val pickupSize = tilePx * 0.7f
                val cx = originX + (pickup.col + 0.5f) * tilePx
                val cy = originY + (pickup.row + 0.5f) * tilePx
                drawImage(
                    image = uiAtlas.key,
                    dstOffset = IntOffset((cx - pickupSize / 2f).roundToInt(), (cy - pickupSize / 2f).roundToInt()),
                    dstSize = IntSize(pickupSize.roundToInt(), pickupSize.roundToInt()),
                    filterQuality = FilterQuality.None,
                )
            }

            for (enemy in engine.enemies) {
                val enemySize = if (enemy.type == EnemyType.BOSS) tilePx * 2.4f else tilePx * 1.1f
                val cx = originX + (enemy.col + 0.5f) * tilePx
                val cy = originY + (enemy.row + 0.5f) * tilePx
                drawImage(
                    image = enemyAtlas.frameFor(enemy),
                    dstOffset = IntOffset((cx - enemySize / 2f).roundToInt(), (cy - enemySize / 2f).roundToInt()),
                    dstSize = IntSize(enemySize.roundToInt(), enemySize.roundToInt()),
                    filterQuality = FilterQuality.None,
                )
                if (enemy.isFlashing) {
                    drawCircle(color = Color.White.copy(alpha = 0.6f), radius = enemySize * 0.5f, center = Offset(cx, cy))
                }
            }

            // Only drawn for a brief moment right when the punch actually connects with an
            // enemy - not for the whole swing (attackHitbox() is still used for collision).
            engine.activeHitFlash()?.let { box ->
                drawRect(
                    color = Color(0x99FF3B30),
                    topLeft = Offset(originX + box.colMin * tilePx, originY + box.rowMin * tilePx),
                    size = androidx.compose.ui.geometry.Size(
                        (box.colMax - box.colMin) * tilePx,
                        (box.rowMax - box.rowMin) * tilePx,
                    ),
                )
            }

            if (!engine.player.isFlashHidden) {
                val playerCx = originX + engine.player.col * tilePx
                if (introPhase == IntroPhase.FADE_IN || introPhase == IntroPhase.SITTING) {
                    // Washed ashore, not standing yet - a wider, center-anchored pose instead of
                    // the normal bottom-anchored standing sprite. Lying during the fade-in reveal,
                    // then sitting up as the following beat, before a normal idle frame takes over.
                    val wakeImage = if (introPhase == IntroPhase.FADE_IN) playerAtlas.lyingDown else playerAtlas.sittingUp
                    val wakeWidth = tilePx * 2.1f
                    val wakeHeight = wakeWidth * (wakeImage.height.toFloat() / wakeImage.width.toFloat())
                    val playerCy = originY + (engine.player.row + 0.5f) * tilePx
                    drawImage(
                        image = wakeImage,
                        dstOffset = IntOffset((playerCx - wakeWidth / 2f).roundToInt(), (playerCy - wakeHeight / 2f).roundToInt()),
                        dstSize = IntSize(wakeWidth.roundToInt(), wakeHeight.roundToInt()),
                        filterQuality = FilterQuality.None,
                    )
                } else {
                    // Full-body custom art, not the pack's square bust sprites - size by height
                    // and derive width from each pose's own aspect ratio instead of assuming square.
                    val frame = playerAtlas.frameFor(engine.player)
                    val playerHeight = tilePx * 1.5f
                    val playerWidth = playerHeight * (frame.width.toFloat() / frame.height.toFloat())
                    val playerBottomY = originY + (engine.player.row + 0.5f) * tilePx
                    drawImage(
                        image = frame,
                        dstOffset = IntOffset((playerCx - playerWidth / 2f).roundToInt(), (playerBottomY - playerHeight).roundToInt()),
                        dstSize = IntSize(playerWidth.roundToInt(), playerHeight.roundToInt()),
                        filterQuality = FilterQuality.None,
                    )
                }
            }
        }

        if (introPhase == IntroPhase.DONE) {
            HeartsHud(
                healthPoints = engine.player.health,
                heartIcon = uiAtlas.heart,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(16.dp),
            )

            VirtualJoystick(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(24.dp),
                onChange = { dx, dy -> inputVector.value = dx to dy },
            )

            AttackButton(
                icon = uiAtlas.fist,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(24.dp),
                onTap = { attackRequested = true },
            )

            if (!engine.isTalking && !isInventoryOpen && !engine.player.isDead) {
                InventoryButton(
                    icon = uiAtlas.bag,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 116.dp, bottom = 24.dp),
                    onTap = { isInventoryOpen = true },
                )
            }

            if (!engine.isTalking && !isInventoryOpen && engine.nearbyNpc() != null) {
                TalkButton(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 24.dp, bottom = 120.dp),
                    onTap = { engine.startDialogue() },
                )
            }

            if (!engine.isTalking && !isInventoryOpen && engine.nearbyPotionPickup() != null) {
                PickupButton(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 116.dp, bottom = 120.dp),
                    onTap = { engine.pickUpPotion() },
                )
            }

            if (!engine.isTalking && !isInventoryOpen && engine.nearbyKeyPickup() != null) {
                PickupButton(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 116.dp, bottom = 120.dp),
                    onTap = { engine.pickUpKey() },
                )
            }

            engine.currentDialogueLine()?.let { line ->
                DialogueBox(
                    line = line,
                    isLastLine = engine.isLastDialogueLine(),
                    villagerSprite = npcAtlas.villagerSprite,
                    elderSprite = npcAtlas.elderSprite,
                    playerSprite = playerAtlas.idle.getValue(Direction.DOWN),
                    onAdvance = { engine.advanceDialogue() },
                )
            }

            if (isInventoryOpen) {
                InventoryPanel(
                    potionIcon = uiAtlas.potion,
                    potionCount = engine.player.potionCount,
                    onUsePotion = { engine.player.usePotion() },
                    keyIcon = uiAtlas.key,
                    hasKey = engine.player.hasKey,
                    onClose = { isInventoryOpen = false },
                )
            }

            if (engine.player.isDead) {
                DeathScreen(onRestart = { engine = GameEngine(tileMap) })
            }
        } else if (introPhase == IntroPhase.FADE_IN) {
            // World and player are already visible underneath (drawn unconditionally by the
            // Canvas above) - this is just the black cutscene background dissolving away. HUD
            // stays hidden until IntroPhase.DONE so it doesn't pop in mid-fade.
            Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = fadeInAlpha)))
        }

        if (introPhase == IntroPhase.SHIP) {
            ShipCutscene(
                waterFrames = atlas.waterRipple,
                boat = cutsceneAtlas.boat,
                onStormStart = { gameAudio.playThunder() },
                onCrash = { gameAudio.playShipCrash() },
                onFinished = { introPhase = IntroPhase.TEXT },
            )
        } else if (introPhase == IntroPhase.TEXT) {
            CutsceneOverlay(
                lineIndex = cutsceneLineIndex,
                onAdvance = {
                    val lines = PrologueCutscene.lines()
                    if (cutsceneLineIndex < lines.lastIndex) {
                        cutsceneLineIndex++
                    } else {
                        // No music on the beach - it only starts once the village exit is reached.
                        gameAudio.stopAmbient()
                        introPhase = IntroPhase.FADE_IN
                    }
                },
            )
        }
    }
}

private const val FADE_IN_DURATION_S = 1.5f
private const val SITTING_DURATION_S = 1.6f

private enum class IntroPhase { SHIP, TEXT, FADE_IN, SITTING, DONE }

/**
 * Opening scripted sequence - no HUD, no portraits, just black screen and tap-to-advance
 * narration lines (same interaction as [DialogueBox]) over the looping storm ambient. Follows
 * [ShipCutscene], which already showed the sailing/storm/crash beats visually, so this only
 * needs the one line that has to be heard rather than seen. Ends by fading into the world with
 * the player already standing on the beach.
 */
private object PrologueCutscene {
    private val linesRu = listOf(
        "\"Ты обещал... что больше никогда не вернёшься.\"",
    )
    private val linesEn = listOf(
        "\"You promised... you'd never come back.\"",
    )
    fun lines(): List<String> = if (Locale.getDefault().language == "ru") linesRu else linesEn
}

private const val SHIP_SAIL_DURATION_S = 2.5f
private const val SHIP_STORM_DURATION_S = 2.5f
private const val SHIP_CRASH_FLASH_DURATION_S = 0.6f

/**
 * A short, non-interactive scene that plays before the text cutscene: the boat sails a tiled
 * water background, a storm rolls in (screen darkens, shakes, flickers with lightning), then a
 * crash flash cuts to black - which is also [CutsceneOverlay]'s background, so the handoff is
 * seamless. Timing is driven by two independent effects: a per-frame loop for smooth animation
 * (bob/shake/darken) and a delay-based one for the two one-shot triggers (crash sound, done).
 */
@Composable
private fun ShipCutscene(
    waterFrames: List<ImageBitmap>,
    boat: ImageBitmap,
    onStormStart: () -> Unit,
    onCrash: () -> Unit,
    onFinished: () -> Unit,
) {
    var elapsed by remember { mutableStateOf(0f) }
    val totalDuration = SHIP_SAIL_DURATION_S + SHIP_STORM_DURATION_S + SHIP_CRASH_FLASH_DURATION_S

    LaunchedEffect(Unit) {
        var lastFrameNanos = withFrameNanos { it }
        while (true) {
            withFrameNanos { nowNanos ->
                val dt = ((nowNanos - lastFrameNanos) / 1_000_000_000.0).toFloat().coerceAtMost(1f / 20f)
                lastFrameNanos = nowNanos
                elapsed = (elapsed + dt).coerceAtMost(totalDuration)
            }
        }
    }

    LaunchedEffect(Unit) {
        delay((SHIP_SAIL_DURATION_S * 1000).toLong())
        onStormStart()
        delay((SHIP_STORM_DURATION_S * 1000).toLong())
        onCrash()
        delay((SHIP_CRASH_FLASH_DURATION_S * 1000).toLong())
        onFinished()
    }

    Canvas(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        val stormProgress = ((elapsed - SHIP_SAIL_DURATION_S) / SHIP_STORM_DURATION_S).coerceIn(0f, 1f)

        val waterTileSize = size.height / 6f
        val waterFrame = waterFrames[((elapsed * 6f).toInt()) % waterFrames.size]
        var ty = 0f
        while (ty < size.height) {
            var tx = 0f
            while (tx < size.width) {
                drawImage(
                    image = waterFrame,
                    dstOffset = IntOffset(tx.roundToInt(), ty.roundToInt()),
                    dstSize = IntSize(waterTileSize.roundToInt(), waterTileSize.roundToInt()),
                    filterQuality = FilterQuality.None,
                )
                tx += waterTileSize
            }
            ty += waterTileSize
        }

        val shakeMagnitude = stormProgress * 14f
        val shakeX = sin(elapsed * 37f) * shakeMagnitude
        val shakeY = cos(elapsed * 29f) * shakeMagnitude
        val bob = sin(elapsed * 3f) * 6f
        val boatWidth = size.width * 0.4f
        val boatHeight = boatWidth * (boat.height.toFloat() / boat.width.toFloat())
        val boatCx = size.width / 2f + shakeX
        val boatCy = size.height / 2f + bob + shakeY
        drawImage(
            image = boat,
            dstOffset = IntOffset((boatCx - boatWidth / 2f).roundToInt(), (boatCy - boatHeight / 2f).roundToInt()),
            dstSize = IntSize(boatWidth.roundToInt(), boatHeight.roundToInt()),
            filterQuality = FilterQuality.None,
        )

        if (stormProgress > 0f) {
            drawRect(Color(0xFF060C28).copy(alpha = stormProgress * 0.55f))
            val lightning = sin(elapsed * 13f)
            if (lightning > 0.96f) {
                drawRect(Color.White.copy(alpha = (lightning - 0.96f) / 0.04f * 0.5f))
            }
        }

        if (elapsed > SHIP_SAIL_DURATION_S + SHIP_STORM_DURATION_S) {
            val flashT = ((elapsed - (SHIP_SAIL_DURATION_S + SHIP_STORM_DURATION_S)) / SHIP_CRASH_FLASH_DURATION_S)
                .coerceIn(0f, 1f)
            drawRect(Color.White.copy(alpha = (1f - flashT) * 0.9f))
            drawRect(Color.Black.copy(alpha = flashT))
        }
    }
}

@Composable
private fun CutsceneOverlay(lineIndex: Int, onAdvance: () -> Unit) {
    val lines = PrologueCutscene.lines()
    val line = lines.getOrElse(lineIndex) { "" }
    val isLastLine = lineIndex >= lines.lastIndex
    val isRussian = Locale.getDefault().language == "ru"
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onAdvance,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
            Text(
                text = line,
                color = Color.White,
                fontSize = 20.sp,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = if (isLastLine) {
                    if (isRussian) "(тап, чтобы очнуться)" else "(tap to wake)"
                } else {
                    if (isRussian) "(тап, чтобы продолжить)" else "(tap to continue)"
                },
                color = Color.White.copy(alpha = 0.5f),
                fontSize = 12.sp,
            )
        }
    }
}

@Composable
private fun HeartsHud(healthPoints: Int, heartIcon: ImageBitmap, modifier: Modifier = Modifier) {
    Row(modifier = modifier) {
        repeat(HEART_COUNT) { index ->
            // Each heart covers 2 health points: fully lit if both remain, half-lit (left
            // half of the icon drawn at full opacity) if only one point remains, dim if none.
            val pointsInThisHeart = (healthPoints - index * 2).coerceIn(0, 2)
            Canvas(
                modifier = Modifier
                    .size(28.dp)
                    .padding(2.dp)
            ) {
                drawImage(
                    image = heartIcon,
                    dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
                    alpha = 0.25f,
                    filterQuality = FilterQuality.None,
                )
                if (pointsInThisHeart > 0) {
                    val litWidth = if (pointsInThisHeart == 2) size.width else size.width / 2f
                    clipRect(left = 0f, top = 0f, right = litWidth, bottom = size.height) {
                        drawImage(
                            image = heartIcon,
                            dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
                            filterQuality = FilterQuality.None,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DeathScreen(onRestart: () -> Unit) {
    val isRussian = Locale.getDefault().language == "ru"
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {}, // swallow touches so the joystick/attack button underneath can't be used
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = if (isRussian) "Вы мертвы" else "You are dead",
                color = Color.White,
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(24.dp))
            Button(onClick = onRestart) {
                Text(if (isRussian) "Начать заново" else "Restart")
            }
        }
    }
}

@Composable
private fun TalkButton(modifier: Modifier = Modifier, onTap: () -> Unit) {
    val isRussian = Locale.getDefault().language == "ru"
    val view = LocalView.current
    Box(
        modifier = modifier
            .size(64.dp)
            .excludeFromSystemGestures(view)
            .clip(CircleShape)
            .background(Color(0xFFE8B33D).copy(alpha = 0.85f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onTap,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = if (isRussian) "Говорить" else "Talk",
            color = Color.Black,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun PickupButton(modifier: Modifier = Modifier, onTap: () -> Unit) {
    val isRussian = Locale.getDefault().language == "ru"
    val view = LocalView.current
    Box(
        modifier = modifier
            .size(56.dp)
            .excludeFromSystemGestures(view)
            .clip(CircleShape)
            .background(Color(0xFF3DBF6E).copy(alpha = 0.85f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onTap,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = if (isRussian) "Взять" else "Take",
            color = Color.Black,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun InventoryButton(icon: ImageBitmap, modifier: Modifier = Modifier, onTap: () -> Unit) {
    val view = LocalView.current
    Box(
        modifier = modifier
            .size(56.dp)
            .excludeFromSystemGestures(view)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.25f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onTap,
            ),
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.foundation.Image(
            bitmap = icon,
            contentDescription = null,
            filterQuality = FilterQuality.None,
            modifier = Modifier.size(28.dp),
        )
    }
}

@Composable
private fun InventorySlot(
    icon: ImageBitmap,
    label: String,
    modifier: Modifier = Modifier,
    onTap: (() -> Unit)? = null,
) {
    Box(
        modifier = modifier
            .size(64.dp)
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
            .background(Color.White.copy(alpha = 0.12f))
            .let {
                if (onTap != null) {
                    it.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onTap,
                    )
                } else it
            },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            androidx.compose.foundation.Image(
                bitmap = icon,
                contentDescription = null,
                filterQuality = FilterQuality.None,
                modifier = Modifier.size(28.dp),
            )
            Text(text = label, color = Color.White, fontSize = 12.sp)
        }
    }
}

@Composable
private fun InventoryPanel(
    potionIcon: ImageBitmap,
    potionCount: Int,
    onUsePotion: () -> Unit,
    keyIcon: ImageBitmap,
    hasKey: Boolean,
    onClose: () -> Unit,
) {
    val isRussian = Locale.getDefault().language == "ru"
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.6f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {}, // swallow touches so controls underneath can't be used
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .background(Color(0xFF2B2B2B))
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = if (isRussian) "Инвентарь" else "Inventory",
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(16.dp))
            if (potionCount <= 0 && !hasKey) {
                Text(
                    text = if (isRussian) "Пусто" else "Empty",
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 14.sp,
                )
            } else {
                Row {
                    if (potionCount > 0) {
                        InventorySlot(icon = potionIcon, label = "x$potionCount", onTap = onUsePotion)
                    }
                    if (hasKey) {
                        if (potionCount > 0) Spacer(modifier = Modifier.width(12.dp))
                        InventorySlot(icon = keyIcon, label = if (isRussian) "ключ" else "key")
                    }
                }
                if (potionCount > 0) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = if (isRussian) "(тап по зелью, чтобы выпить)" else "(tap the potion to drink)",
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 11.sp,
                    )
                }
            }
            Spacer(modifier = Modifier.height(20.dp))
            Button(onClick = onClose) {
                Text(if (isRussian) "Закрыть" else "Close")
            }
        }
    }
}

@Composable
private fun DialogueBox(
    line: DialogueLine,
    isLastLine: Boolean,
    villagerSprite: ImageBitmap,
    elderSprite: ImageBitmap,
    playerSprite: ImageBitmap,
    onAdvance: () -> Unit,
) {
    val isRussian = Locale.getDefault().language == "ru"
    val portrait = when (line.speaker) {
        Speaker.VILLAGER -> villagerSprite
        Speaker.ELDER -> elderSprite
        Speaker.PLAYER -> playerSprite
    }
    val speakerLabel = when (line.speaker) {
        Speaker.VILLAGER -> null
        Speaker.ELDER -> if (isRussian) "Старейшина" else "Elder"
        Speaker.PLAYER -> if (isRussian) "Ты" else "You"
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onAdvance,
            ),
    ) {
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(16.dp)
                .background(Color.Black.copy(alpha = 0.85f))
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            androidx.compose.foundation.Image(
                bitmap = portrait,
                contentDescription = null,
                filterQuality = FilterQuality.None,
                modifier = Modifier.size(48.dp),
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                if (speakerLabel != null) {
                    Text(text = speakerLabel, color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(2.dp))
                }
                Text(text = line.text(), color = Color.White, fontSize = 18.sp)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = if (isLastLine) {
                        if (isRussian) "(тап, чтобы закрыть)" else "(tap to close)"
                    } else {
                        if (isRussian) "(тап, чтобы продолжить)" else "(tap to continue)"
                    },
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 12.sp,
                )
            }
        }
    }
}

@Composable
private fun AttackButton(icon: ImageBitmap, modifier: Modifier = Modifier, onTap: () -> Unit) {
    val view = LocalView.current
    Box(
        modifier = modifier
            .size(84.dp)
            .excludeFromSystemGestures(view)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.25f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onTap,
            ),
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.foundation.Image(
            bitmap = icon,
            contentDescription = null,
            filterQuality = FilterQuality.None,
            modifier = Modifier.size(40.dp),
        )
    }
}

/**
 * On-screen controls sitting near the left/right screen edges (in landscape) fall inside
 * Android's edge-swipe-back gesture zone. A single-finger tap that starts there can get
 * eaten by the system gesture detector instead of reaching this composable - which is
 * exactly why the attack button only worked while a second finger was already down on the
 * joystick. This tells the OS to leave the button's area alone.
 */
fun Modifier.excludeFromSystemGestures(view: View): Modifier =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        // GameScreen recomposes ~60x/sec (the game loop), so this fires every frame too.
        // Re-setting systemGestureExclusionRects that often - even to the same value - was
        // enough to make Android cancel the in-progress tap gesture, which is what caused
        // the regression below. Only touch the platform API when the rect actually changed.
        this.onGloballyPositioned { coordinates ->
            val bounds = coordinates.boundsInWindow()
            val rect = android.graphics.Rect(
                bounds.left.roundToInt(),
                bounds.top.roundToInt(),
                bounds.right.roundToInt(),
                bounds.bottom.roundToInt(),
            )
            if (view.systemGestureExclusionRects != listOf(rect)) {
                view.systemGestureExclusionRects = listOf(rect)
            }
        }
    } else {
        this
    }

private fun DrawScope.drawTileImage(image: ImageBitmap, x: Float, y: Float, tilePx: Float) {
    drawImage(
        image = image,
        dstOffset = IntOffset(x.roundToInt(), y.roundToInt()),
        dstSize = IntSize((tilePx + 1f).roundToInt(), (tilePx + 1f).roundToInt()),
        filterQuality = FilterQuality.None,
    )
}
