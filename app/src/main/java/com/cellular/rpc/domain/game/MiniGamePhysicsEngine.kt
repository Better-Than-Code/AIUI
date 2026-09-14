package com.cellular.rpc.domain.game

import kotlin.math.sqrt

/**
 * Section 2.10: 2D Physics Vector & Simulation Primitives for Edge Mini-Games.
 */
data class Vector2D(val x: Float = 0f, val y: Float = 0f) {
    operator fun plus(other: Vector2D) = Vector2D(x + other.x, y + other.y)
    operator fun minus(other: Vector2D) = Vector2D(x - other.x, y - other.y)
    operator fun times(scalar: Float) = Vector2D(x * scalar, y * scalar)
    fun length(): Float = sqrt(x * x + y * y)
    fun distanceTo(other: Vector2D): Float = (this - other).length()
}

data class BallState(
    val pos: Vector2D = Vector2D(150f, 150f),
    val vel: Vector2D = Vector2D(0f, 0f),
    val radius: Float = 14f,
    val colorHex: String = "#00E5FF"
)

data class TargetRing(
    val id: String,
    val pos: Vector2D,
    val radius: Float = 24f,
    val points: Int = 10,
    val colorHex: String = "#00E676",
    val isCollected: Boolean = false
)

data class Obstacle(
    val id: String,
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    val isHazard: Boolean = false,
    val colorHex: String = "#FF5252"
)

data class GameEngineState(
    val ball: BallState = BallState(),
    val targets: List<TargetRing> = emptyList(),
    val obstacles: List<Obstacle> = emptyList(),
    val score: Int = 0,
    val lives: Int = 3,
    val timeRemainingSec: Float = 60f,
    val isGameOver: Boolean = false,
    val isLevelComplete: Boolean = false,
    val collisionCount: Int = 0
)

class MiniGamePhysicsEngine(
    var arenaWidth: Float = 320f,
    var arenaHeight: Float = 320f,
    val gravityFactor: Float = 220f,
    val damping: Float = 0.94f,
    val restitution: Float = 0.65f // Bounce elasticity
) {

    fun update(
        state: GameEngineState,
        tiltX: Float, // -10..10 from accelerometer
        tiltY: Float,
        dtSec: Float,
        onCollision: ((isHazard: Boolean) -> Unit)? = null,
        onCollectTarget: ((TargetRing) -> Unit)? = null
    ): GameEngineState {
        if (state.isGameOver || state.isLevelComplete) return state

        // 1. Calculate acceleration from tilt
        // On Android portrait: tiltX > 0 rolls left, tiltY > 0 rolls down
        val ax = -tiltX * gravityFactor
        val ay = tiltY * gravityFactor

        // 2. Symplectic Euler integration
        var newVx = (state.ball.vel.x + ax * dtSec) * damping
        var newVy = (state.ball.vel.y + ay * dtSec) * damping

        var newX = state.ball.pos.x + newVx * dtSec
        var newY = state.ball.pos.y + newVy * dtSec

        var collisionOccurred = false
        var hitHazard = false

        // 3. Arena Wall Collisions
        val r = state.ball.radius
        if (newX - r < 0f) {
            newX = r
            newVx = -newVx * restitution
            collisionOccurred = true
        } else if (newX + r > arenaWidth) {
            newX = arenaWidth - r
            newVx = -newVx * restitution
            collisionOccurred = true
        }

        if (newY - r < 0f) {
            newY = r
            newVy = -newVy * restitution
            collisionOccurred = true
        } else if (newY + r > arenaHeight) {
            newY = arenaHeight - r
            newVy = -newVy * restitution
            collisionOccurred = true
        }

        // 4. Obstacle / Wall Box Collisions
        state.obstacles.forEach { obs ->
            // Closest point on AABB to ball center
            val closestX = newX.coerceIn(obs.left, obs.right)
            val closestY = newY.coerceIn(obs.top, obs.bottom)
            val dist = Vector2D(newX, newY).distanceTo(Vector2D(closestX, closestY))

            if (dist < r) {
                collisionOccurred = true
                if (obs.isHazard) {
                    hitHazard = true
                }
                // Push back
                val nx = if (dist > 0.001f) (newX - closestX) / dist else 1f
                val ny = if (dist > 0.001f) (newY - closestY) / dist else 0f
                newX = closestX + nx * r
                newY = closestY + ny * r
                newVx = newVx * -restitution
                newVy = newVy * -restitution
            }
        }

        // 5. Target Collection Collisions
        var updatedScore = state.score
        var updatedTargets = state.targets.map { target ->
            if (!target.isCollected && Vector2D(newX, newY).distanceTo(target.pos) < (r + target.radius)) {
                updatedScore += target.points
                onCollectTarget?.invoke(target)
                target.copy(isCollected = true)
            } else {
                target
            }
        }

        if (collisionOccurred) {
            onCollision?.invoke(hitHazard)
        }

        val updatedLives = if (hitHazard) (state.lives - 1).coerceAtLeast(0) else state.lives
        val updatedTime = (state.timeRemainingSec - dtSec).coerceAtLeast(0f)
        val allCollected = updatedTargets.isNotEmpty() && updatedTargets.all { it.isCollected }
        val isOver = updatedLives <= 0 || (updatedTime <= 0f && !allCollected)

        return state.copy(
            ball = state.ball.copy(
                pos = Vector2D(newX, newY),
                vel = Vector2D(newVx, newVy)
            ),
            targets = updatedTargets,
            score = updatedScore,
            lives = updatedLives,
            timeRemainingSec = updatedTime,
            isGameOver = isOver,
            isLevelComplete = allCollected,
            collisionCount = if (collisionOccurred) state.collisionCount + 1 else state.collisionCount
        )
    }
}
