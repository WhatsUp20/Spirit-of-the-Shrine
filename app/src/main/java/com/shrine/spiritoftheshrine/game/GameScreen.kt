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
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Tiles visible across the shorter screen dimension - sets the zoom level. */
private const val VISIBLE_TILES = 16f

@Composable
fun GameScreen() {
    val context = LocalContext.current
    val tileMap = remember { TileMap.load() }
    val atlas = remember { TileAtlas(context) }
    val playerAtlas = remember { PlayerAtlas(context) }
    val enemyAtlas = remember { EnemyAtlas(context) }
    val npcAtlas = remember { NpcAtlas(context) }
    val uiAtlas = remember { UiAtlas(context) }
    val gameAudio = remember { GameAudio(context) }
    var engine by remember { mutableStateOf(GameEngine(tileMap)) }

    DisposableEffect(Unit) {
        onDispose { gameAudio.release() }
    }

    val inputVector = remember { mutableStateOf(0f to 0f) }
    var attackRequested by remember { mutableStateOf(false) }
    var isInventoryOpen by remember { mutableStateOf(false) }
    var frameTick by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        var lastFrameNanos = withFrameNanos { it }
        while (true) {
            withFrameNanos { nowNanos ->
                val dt = ((nowNanos - lastFrameNanos) / 1_000_000_000.0).toFloat().coerceAtMost(1f / 20f)
                lastFrameNanos = nowNanos
                val (dx, dy) = inputVector.value
                engine.update(dt, dx, dy, attackRequested, paused = isInventoryOpen)
                attackRequested = false
                for (event in engine.pendingSounds) gameAudio.play(event)
                engine.pendingSounds.clear()
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

            fun sameRegion(type: TileType, row: Int, col: Int) = tileMap.tileAt(row, col) == type

            fun drawBlob(block: Array<Array<ImageBitmap>>, type: TileType, row: Int, col: Int, x: Float, y: Float) {
                val (bc, br) = blobIndex(
                    sameUp = sameRegion(type, row - 1, col),
                    sameDown = sameRegion(type, row + 1, col),
                    sameLeft = sameRegion(type, row, col - 1),
                    sameRight = sameRegion(type, row, col + 1),
                )
                drawTileImage(block[bc][br], x, y, tilePx)
            }

            for (row in firstRow..lastRow) {
                for (col in firstCol..lastCol) {
                    val x = originX + col * tilePx
                    val y = originY + row * tilePx
                    when (val type = tileMap.tileAt(row, col)) {
                        TileType.VILLAGE_FLOOR -> drawBlob(atlas.villageFloorBlob, type, row, col, x, y)
                        TileType.DUNGEON_FLOOR -> drawBlob(atlas.dungeonFloorBlob, type, row, col, x, y)
                        TileType.TEMPLE_FLOOR -> drawBlob(atlas.templeFloorBlob, type, row, col, x, y)
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
                        TileType.GRASS, TileType.TREE -> drawTileImage(atlas.grass, x, y, tilePx)
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

            for (npc in engine.npcs) {
                val npcSize = tilePx * 1.1f
                val cx = originX + (npc.col + 0.5f) * tilePx
                val cy = originY + (npc.row + 0.5f) * tilePx
                drawImage(
                    image = npcAtlas.sprite,
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

            // Sword hitbox: drawn as a visible translucent rectangle so it can be checked
            // by eye for now - M3's enemies will be the ones that actually read this box.
            engine.attackHitbox()?.let { box ->
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
                val playerSize = tilePx * 1.3f
                val playerCx = originX + engine.player.col * tilePx
                val playerBottomY = originY + (engine.player.row + 0.5f) * tilePx
                drawImage(
                    image = playerAtlas.frameFor(engine.player),
                    dstOffset = IntOffset((playerCx - playerSize / 2f).roundToInt(), (playerBottomY - playerSize).roundToInt()),
                    dstSize = IntSize(playerSize.roundToInt(), playerSize.roundToInt()),
                    filterQuality = FilterQuality.None,
                )
            }
        }

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
            icon = uiAtlas.sword,
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

        if (engine.isTalking) {
            DialogueBox(
                npcSprite = npcAtlas.sprite,
                lineIndex = engine.dialogueLineIndex,
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
private fun DialogueBox(npcSprite: ImageBitmap, lineIndex: Int, onAdvance: () -> Unit) {
    val lines = NpcDialogue.lines()
    val line = lines.getOrElse(lineIndex) { "" }
    val isLastLine = lineIndex >= lines.lastIndex
    val isRussian = Locale.getDefault().language == "ru"
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
                bitmap = npcSprite,
                contentDescription = null,
                filterQuality = FilterQuality.None,
                modifier = Modifier.size(48.dp),
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(text = line, color = Color.White, fontSize = 18.sp)
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
