package com.shrine.spiritoftheshrine.game

import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.random.Random

private const val PLAYER_SPEED_TILES_PER_SEC = 4.5f
private const val PLAYER_HALF_SIZE = 0.3f
private const val ATTACK_REACH = 0.65f
private const val ATTACK_HALF_SIZE = 0.45f
private const val NPC_INTERACT_RADIUS = 1.3f
private const val POTION_PICKUP_RADIUS = 0.8f
private const val KEY_PICKUP_RADIUS = 0.8f
private const val GATE_UNLOCK_RADIUS = 1.5f

data class TileRect(val rowMin: Float, val rowMax: Float, val colMin: Float, val colMax: Float)

enum class SoundEvent { SWORD_SWING, ENEMY_HIT, PLAYER_HURT }

class PotionPickup(val row: Float, val col: Float)
class KeyPickup(val row: Float, val col: Float)

private data class EnemySpec(
    val patrolSpeed: Float,
    val chaseSpeed: Float,
    val detectRadius: Float,
    val attackRadius: Float,
    val loseRadius: Float,
    val attackCooldown: Float,
    val contactDamage: Int,
    val maxHealth: Int,
    val halfSize: Float = 0.3f,
)

private val ENEMY_SPECS = mapOf(
    EnemyType.SLIME to EnemySpec(
        patrolSpeed = 0.8f, chaseSpeed = 1.6f,
        detectRadius = 4f, attackRadius = 0.65f, loseRadius = 6f,
        attackCooldown = 1.2f, contactDamage = 1, maxHealth = 3,
    ),
    EnemyType.SPIRIT to EnemySpec(
        patrolSpeed = 1.0f, chaseSpeed = 2.0f,
        detectRadius = 5f, attackRadius = 0.65f, loseRadius = 6.5f,
        attackCooldown = 1.0f, contactDamage = 1, maxHealth = 3,
    ),
    // Bigger, tougher, hits harder - guards the temple's boss room.
    EnemyType.BOSS to EnemySpec(
        patrolSpeed = 0.6f, chaseSpeed = 1.4f,
        detectRadius = 4.5f, attackRadius = 0.9f, loseRadius = 7f,
        attackCooldown = 1.0f, contactDamage = 2, maxHealth = 9,
        halfSize = 0.6f,
    ),
)

class GameEngine(private val tileMap: TileMap) {
    val player: Player = run {
        val spawn = tileMap.spawnPoints.first { it.marker == MarkerType.PLAYER_SPAWN }
        Player(spawn.row + 0.5f, spawn.col + 0.5f)
    }

    val enemies: MutableList<Enemy> = tileMap.spawnPoints.mapNotNull { spawn ->
        val type = when (spawn.marker) {
            MarkerType.SLIME_SPAWN -> EnemyType.SLIME
            MarkerType.SPIRIT_SPAWN -> EnemyType.SPIRIT
            MarkerType.BOSS_SPAWN -> EnemyType.BOSS
            else -> return@mapNotNull null
        }
        Enemy(type, spawn.row + 0.5f, spawn.col + 0.5f, ENEMY_SPECS.getValue(type).maxHealth)
    }.toMutableList()

    val npcs: List<Npc> = tileMap.spawnPoints.mapNotNull { spawn ->
        when (spawn.marker) {
            MarkerType.NPC_SPAWN -> Npc(spawn.row + 0.5f, spawn.col + 0.5f, NpcKind.VILLAGER, VillagerDialogue.lines())
            MarkerType.ELDER_SPAWN -> Npc(spawn.row + 0.5f, spawn.col + 0.5f, NpcKind.ELDER, ElderDialogue.lines())
            else -> null
        }
    }

    val potionPickups: MutableList<PotionPickup> = tileMap.spawnPoints
        .filter { it.marker == MarkerType.POTION_PICKUP }
        .map { PotionPickup(it.row + 0.5f, it.col + 0.5f) }
        .toMutableList()

    val keyPickups: MutableList<KeyPickup> = tileMap.spawnPoints
        .filter { it.marker == MarkerType.KEY_PICKUP }
        .map { KeyPickup(it.row + 0.5f, it.col + 0.5f) }
        .toMutableList()

    /** Every TEMPLE_GATE tile, scanned once - the gate unlocks when the player reaches any of them with the key. */
    private val gateTiles: List<Pair<Int, Int>> = (0 until tileMap.height).flatMap { row ->
        (0 until tileMap.width).mapNotNull { col ->
            (row to col).takeIf { tileMap.tileAt(row, col) == TileType.TEMPLE_GATE }
        }
    }

    var isGateOpen: Boolean = false
        private set

    /** Sound effects triggered this frame - the caller drains and plays these, then clears the
     * list. Kept here rather than in GameScreen so the "did a hit actually land" logic (which
     * already has to account for invulnerability / one-hit-per-swing) lives in one place. */
    val pendingSounds: MutableList<SoundEvent> = mutableListOf()

    var dialogueNpc: Npc? = null
        private set
    var dialogueLineIndex: Int = 0
        private set
    val isTalking: Boolean get() = dialogueNpc != null

    /** Nearest NPC within talking range of the player, or null if none is close enough. */
    fun nearbyNpc(): Npc? = npcs
        .minByOrNull { hypot(it.row - player.row, it.col - player.col) }
        ?.takeIf { hypot(it.row - player.row, it.col - player.col) <= NPC_INTERACT_RADIUS }

    fun startDialogue() {
        if (isTalking) return
        dialogueNpc = nearbyNpc() ?: return
        dialogueLineIndex = 0
    }

    /** Shows the next line, or ends the conversation once the last line has been read. */
    fun advanceDialogue() {
        val npc = dialogueNpc ?: return
        if (dialogueLineIndex < npc.dialogue.lastIndex) {
            dialogueLineIndex++
        } else {
            dialogueNpc = null
            dialogueLineIndex = 0
        }
    }

    /** The line currently on screen, or null when nobody's talking. */
    fun currentDialogueLine(): DialogueLine? = dialogueNpc?.dialogue?.getOrNull(dialogueLineIndex)

    fun isLastDialogueLine(): Boolean = dialogueNpc?.let { dialogueLineIndex >= it.dialogue.lastIndex } ?: true

    /** Nearest potion pickup within reach of the player, or null if none is close enough. */
    fun nearbyPotionPickup(): PotionPickup? = potionPickups
        .minByOrNull { hypot(it.row - player.row, it.col - player.col) }
        ?.takeIf { hypot(it.row - player.row, it.col - player.col) <= POTION_PICKUP_RADIUS }

    fun pickUpPotion() {
        val pickup = nearbyPotionPickup() ?: return
        potionPickups.remove(pickup)
        player.addPotion()
    }

    /** Nearest key pickup within reach of the player, or null if none is close enough. */
    fun nearbyKeyPickup(): KeyPickup? = keyPickups
        .minByOrNull { hypot(it.row - player.row, it.col - player.col) }
        ?.takeIf { hypot(it.row - player.row, it.col - player.col) <= KEY_PICKUP_RADIUS }

    fun pickUpKey() {
        val pickup = nearbyKeyPickup() ?: return
        keyPickups.remove(pickup)
        player.addKey()
    }

    /** dx/dy are the joystick's normalized input, each in [-1, 1]. [paused] freezes the world
     * while a full-screen UI overlay (dialogue, inventory) is open on top of it. */
    fun update(dt: Float, dx: Float, dy: Float, attackPressed: Boolean, paused: Boolean = false) {
        if (player.isDead || isTalking || paused) return

        if (attackPressed && player.tryStartAttack()) pendingSounds.add(SoundEvent.SWORD_SWING)
        player.tickCombatTimers(dt)

        if (!isGateOpen && player.hasKey) {
            val nearGate = gateTiles.any { (row, col) ->
                hypot(player.row - (row + 0.5f), player.col - (col + 0.5f)) <= GATE_UNLOCK_RADIUS
            }
            if (nearGate) {
                isGateOpen = true
                player.consumeKey()
            }
        }

        val moving = !player.isAttacking && (dx != 0f || dy != 0f)
        if (moving) {
            player.updateFacing(dx, dy)
            val newCol = player.col + dx * PLAYER_SPEED_TILES_PER_SEC * dt
            if (canOccupy(player.row, newCol, PLAYER_HALF_SIZE)) player.col = newCol
            val newRow = player.row + dy * PLAYER_SPEED_TILES_PER_SEC * dt
            if (canOccupy(newRow, player.col, PLAYER_HALF_SIZE)) player.row = newRow
        }
        player.advanceAnimation(dt, moving)

        val hitbox = attackHitbox()
        for (enemy in enemies) {
            if (enemy.isDead) continue
            updateEnemyAI(enemy, dt)
            val enemyHalfSize = ENEMY_SPECS.getValue(enemy.type).halfSize
            if (hitbox != null && rectOverlapsBox(hitbox, enemy.row, enemy.col, enemyHalfSize)) {
                if (enemy.takeDamage(1, player.attackSeq)) pendingSounds.add(SoundEvent.ENEMY_HIT)
            }
        }
        enemies.removeAll { it.isDead }
    }

    /** Tile-space rectangle of the active sword hitbox, or null when not attacking. */
    fun attackHitbox(): TileRect? {
        if (!player.isAttacking) return null
        val (centerRow, centerCol) = when (player.facing) {
            Direction.DOWN -> (player.row + ATTACK_REACH) to player.col
            Direction.UP -> (player.row - ATTACK_REACH) to player.col
            Direction.LEFT -> player.row to (player.col - ATTACK_REACH)
            Direction.RIGHT -> player.row to (player.col + ATTACK_REACH)
        }
        return TileRect(
            rowMin = centerRow - ATTACK_HALF_SIZE,
            rowMax = centerRow + ATTACK_HALF_SIZE,
            colMin = centerCol - ATTACK_HALF_SIZE,
            colMax = centerCol + ATTACK_HALF_SIZE,
        )
    }

    private fun updateEnemyAI(enemy: Enemy, dt: Float) {
        val spec = ENEMY_SPECS.getValue(enemy.type)
        val dRow = player.row - enemy.row
        val dCol = player.col - enemy.col
        val dist = hypot(dRow, dCol)

        enemy.state = when (enemy.state) {
            EnemyBehaviorState.PATROL ->
                if (dist <= spec.detectRadius) EnemyBehaviorState.CHASE else EnemyBehaviorState.PATROL
            EnemyBehaviorState.CHASE -> when {
                dist <= spec.attackRadius -> EnemyBehaviorState.ATTACK
                dist > spec.loseRadius -> EnemyBehaviorState.PATROL
                else -> EnemyBehaviorState.CHASE
            }
            EnemyBehaviorState.ATTACK -> when {
                dist > spec.loseRadius -> EnemyBehaviorState.PATROL
                dist > spec.attackRadius -> EnemyBehaviorState.CHASE
                else -> EnemyBehaviorState.ATTACK
            }
        }

        when (enemy.state) {
            EnemyBehaviorState.PATROL -> patrolStep(enemy, dt, spec)
            EnemyBehaviorState.CHASE -> {
                if (dist > 0.01f) {
                    val moveRow = dRow / dist * spec.chaseSpeed * dt
                    val moveCol = dCol / dist * spec.chaseSpeed * dt
                    tryMoveEnemy(enemy, enemy.row + moveRow, enemy.col + moveCol, spec.halfSize)
                }
            }
            EnemyBehaviorState.ATTACK -> {
                if (enemy.attackCooldown <= 0f) {
                    if (player.takeDamage(spec.contactDamage)) pendingSounds.add(SoundEvent.PLAYER_HURT)
                    enemy.attackCooldown = spec.attackCooldown
                }
            }
        }
        enemy.advanceAnimation(dt)
    }

    private fun patrolStep(enemy: Enemy, dt: Float, spec: EnemySpec) {
        enemy.patrolWaitTimer -= dt
        val distToTarget = hypot(enemy.patrolTargetRow - enemy.row, enemy.patrolTargetCol - enemy.col)
        if (distToTarget < 0.15f || enemy.patrolWaitTimer <= 0f) {
            val angle = Random.nextFloat() * (2f * Math.PI.toFloat())
            val radius = 1f + Random.nextFloat() * 2f
            enemy.patrolTargetRow = enemy.spawnRow + sin(angle) * radius
            enemy.patrolTargetCol = enemy.spawnCol + cos(angle) * radius
            enemy.patrolWaitTimer = 1.5f + Random.nextFloat() * 1.5f
        } else {
            val dRow = enemy.patrolTargetRow - enemy.row
            val dCol = enemy.patrolTargetCol - enemy.col
            val d = hypot(dRow, dCol)
            if (d > 0.01f) {
                tryMoveEnemy(
                    enemy,
                    enemy.row + dRow / d * spec.patrolSpeed * dt,
                    enemy.col + dCol / d * spec.patrolSpeed * dt,
                    spec.halfSize,
                )
            }
        }
    }

    private fun tryMoveEnemy(enemy: Enemy, targetRow: Float, targetCol: Float, halfSize: Float) {
        if (canOccupy(enemy.row, targetCol, halfSize)) enemy.col = targetCol
        if (canOccupy(targetRow, enemy.col, halfSize)) enemy.row = targetRow
    }

    private fun rectOverlapsBox(rect: TileRect, row: Float, col: Float, halfSize: Float): Boolean =
        rect.colMax >= col - halfSize && rect.colMin <= col + halfSize &&
            rect.rowMax >= row - halfSize && rect.rowMin <= row + halfSize

    private fun canOccupy(row: Float, col: Float, halfSize: Float): Boolean {
        val corners = listOf(
            (row - halfSize) to (col - halfSize),
            (row - halfSize) to (col + halfSize),
            (row + halfSize) to (col - halfSize),
            (row + halfSize) to (col + halfSize),
        )
        return corners.all { (r, c) -> isPassable(floor(r).toInt(), floor(c).toInt()) }
    }

    /** Same as [TileMap.isWalkable], except the temple gate opens up once [isGateOpen]. */
    private fun isPassable(row: Int, col: Int): Boolean {
        val type = tileMap.tileAt(row, col)
        if (type == TileType.TEMPLE_GATE) return isGateOpen
        return type.walkable
    }
}
