package com.shrine.spiritoftheshrine.game

enum class Direction { DOWN, UP, LEFT, RIGHT }

private const val WALK_FPS = 8f
const val ATTACK_DURATION = 0.35f
const val INVULN_DURATION = 1f
const val MAX_HEALTH = 6

/** Plain mutable state (not Compose state) - GameEngine mutates it once per frame, the
 * screen redraws by bumping a single frame-tick counter rather than observing every field. */
class Player(startRow: Float, startCol: Float) {
    var row: Float = startRow
    var col: Float = startCol
    var facing: Direction = Direction.DOWN
        private set
    var moving: Boolean = false
        private set
    private var animTime: Float = 0f

    var health: Int = MAX_HEALTH
        private set
    var attackSeq: Int = 0
        private set
    private var attackTimer: Float = 0f
    private var invulnTimer: Float = 0f

    val walkFrame: Int get() = (animTime * WALK_FPS).toInt() % 4
    val isAttacking: Boolean get() = attackTimer > 0f
    val isInvulnerable: Boolean get() = invulnTimer > 0f
    /** Toggles a few times a second while invulnerable, for a hit-flash blink effect. */
    val isFlashHidden: Boolean get() = isInvulnerable && (invulnTimer * 10).toInt() % 2 == 0
    val isDead: Boolean get() = health <= 0

    fun updateFacing(dx: Float, dy: Float) {
        facing = if (kotlin.math.abs(dx) > kotlin.math.abs(dy)) {
            if (dx > 0) Direction.RIGHT else Direction.LEFT
        } else {
            if (dy > 0) Direction.DOWN else Direction.UP
        }
    }

    fun advanceAnimation(dt: Float, isMoving: Boolean) {
        moving = isMoving
        animTime = if (isMoving) animTime + dt else 0f
    }

    fun tryStartAttack(): Boolean {
        if (isAttacking) return false
        attackTimer = ATTACK_DURATION
        attackSeq++
        return true
    }

    fun tickCombatTimers(dt: Float) {
        if (attackTimer > 0f) attackTimer = (attackTimer - dt).coerceAtLeast(0f)
        if (invulnTimer > 0f) invulnTimer = (invulnTimer - dt).coerceAtLeast(0f)
    }

    fun takeDamage(amount: Int) {
        if (isInvulnerable || isDead) return
        health = (health - amount).coerceAtLeast(0)
        invulnTimer = INVULN_DURATION
    }
}
