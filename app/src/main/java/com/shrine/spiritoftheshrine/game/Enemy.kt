package com.shrine.spiritoftheshrine.game

enum class EnemyType { SLIME, SPIRIT }
enum class EnemyBehaviorState { PATROL, CHASE, ATTACK }

// Only frames 0-1 of the sheet are used (see animFrame) - frames 2-3 lean the sprite
// asymmetrically left/right, which read as the creature spinning when cycled through.
private const val ANIM_FPS = 3f
private const val HIT_FLASH_DURATION = 0.15f

/** Plain mutable state, mutated once per frame by [GameEngine] - same pattern as [Player]. */
class Enemy(
    val type: EnemyType,
    val spawnRow: Float,
    val spawnCol: Float,
    maxHealth: Int,
) {
    var row: Float = spawnRow
    var col: Float = spawnCol
    var health: Int = maxHealth
        private set
    var state: EnemyBehaviorState = EnemyBehaviorState.PATROL
    var attackCooldown: Float = 0f
    private var hitFlashTimer: Float = 0f
    private var animTime: Float = 0f
    private var lastHitByAttackSeq: Int = -1

    var patrolTargetRow: Float = spawnRow
    var patrolTargetCol: Float = spawnCol
    var patrolWaitTimer: Float = 0f

    val isDead: Boolean get() = health <= 0
    val isFlashing: Boolean get() = hitFlashTimer > 0f
    val animFrame: Int get() = (animTime * ANIM_FPS).toInt() % 2

    fun advanceAnimation(dt: Float) {
        animTime += dt
        if (attackCooldown > 0f) attackCooldown = (attackCooldown - dt).coerceAtLeast(0f)
        if (hitFlashTimer > 0f) hitFlashTimer = (hitFlashTimer - dt).coerceAtLeast(0f)
    }

    /** [attackSeq] identifies which sword swing this is, so one swing can't hit the same enemy twice. */
    fun takeDamage(amount: Int, attackSeq: Int) {
        if (isDead || lastHitByAttackSeq == attackSeq) return
        lastHitByAttackSeq = attackSeq
        health = (health - amount).coerceAtLeast(0)
        hitFlashTimer = HIT_FLASH_DURATION
    }
}
