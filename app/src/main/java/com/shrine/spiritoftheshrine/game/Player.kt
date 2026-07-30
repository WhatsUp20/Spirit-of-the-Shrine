package com.shrine.spiritoftheshrine.game

enum class Direction { DOWN, UP, LEFT, RIGHT }

private const val WALK_FPS = 8f
private const val FACING_CHANGE_COOLDOWN = 0.12f
const val ATTACK_DURATION = 0.35f
const val INVULN_DURATION = 1f

/** Hearts shown in the HUD. Health itself is tracked in half-heart points (2 per heart) -
 * one enemy touch costs 1 point (half a heart), so it takes two touches to lose a full heart. */
const val HEART_COUNT = 6
const val MAX_HEALTH = HEART_COUNT * 2

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
    private var facingChangeCooldown: Float = 0f

    val walkFrame: Int get() = (animTime * WALK_FPS).toInt() % 4
    val isAttacking: Boolean get() = attackTimer > 0f
    val isInvulnerable: Boolean get() = invulnTimer > 0f
    /** Toggles a few times a second while invulnerable, for a hit-flash blink effect. */
    val isFlashHidden: Boolean get() = isInvulnerable && (invulnTimer * 10).toInt() % 2 == 0
    val isDead: Boolean get() = health <= 0

    fun updateFacing(dx: Float, dy: Float, dt: Float) {
        if (facingChangeCooldown > 0f) {
            facingChangeCooldown = (facingChangeCooldown - dt).coerceAtLeast(0f)
            return
        }
        val absDx = kotlin.math.abs(dx)
        val absDy = kotlin.math.abs(dy)
        // Two layers against flicker: one axis must clearly dominate before we even consider
        // switching (holding the joystick near a diagonal makes dx/dy wobble across the
        // tie-break line every frame otherwise), and once we do switch, a short cooldown blocks
        // switching back immediately. Without both, the sprite flipped between its front and
        // side poses fast enough to look like the character was spinning in place.
        val newFacing = when {
            absDx > absDy * 1.3f -> if (dx > 0) Direction.RIGHT else Direction.LEFT
            absDy > absDx * 1.3f -> if (dy > 0) Direction.DOWN else Direction.UP
            else -> facing
        }
        if (newFacing != facing) {
            facing = newFacing
            facingChangeCooldown = FACING_CHANGE_COOLDOWN
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
