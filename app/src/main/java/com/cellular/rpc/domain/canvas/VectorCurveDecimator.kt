package com.cellular.rpc.domain.canvas

import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Point representation for 2D vector strokes.
 */
data class VectorPoint(val x: Float, val y: Float)

/**
 * Vector Curve Decimator (Ramer-Douglas-Peucker Algorithm).
 * Decimates high-frequency (120Hz) touch coordinate streams from Canvas drawing
 * by 70%-85%, preserving visual fidelity of curves while shrinking vector state
 * payloads to fit over offline cellular SMS/MMS pipes.
 */
object VectorCurveDecimator {

    /**
     * Decimates a sequence of stroke points using the Ramer-Douglas-Peucker algorithm.
     * @param points Raw touch coordinate list.
     * @param epsilon Perpendicular distance threshold in pixels (default 1.5f gives near-perfect visual match).
     * @return Decimated point list.
     */
    fun decimate(points: List<VectorPoint>, epsilon: Float = 1.5f): List<VectorPoint> {
        if (points.size <= 2) return points

        var maxDistance = 0f
        var maxIndex = 0

        val start = points.first()
        val end = points.last()

        for (i in 1 until points.size - 1) {
            val dist = perpendicularDistance(points[i], start, end)
            if (dist > maxDistance) {
                maxDistance = dist
                maxIndex = i
            }
        }

        return if (maxDistance > epsilon) {
            val leftPoints = decimate(points.subList(0, maxIndex + 1), epsilon)
            val rightPoints = decimate(points.subList(maxIndex, points.size), epsilon)
            // Combine results avoiding duplicate midpoint
            leftPoints.dropLast(1) + rightPoints
        } else {
            listOf(start, end)
        }
    }

    /**
     * Calculates the perpendicular distance from point p to the line segment between start and end.
     */
    private fun perpendicularDistance(p: VectorPoint, start: VectorPoint, end: VectorPoint): Float {
        val dx = end.x - start.x
        val dy = end.y - start.y

        val mag = sqrt((dx * dx + dy * dy).toDouble()).toFloat()
        if (mag == 0f) {
            val px = p.x - start.x
            val py = p.y - start.y
            return sqrt((px * px + py * py).toDouble()).toFloat()
        }

        val u = ((p.x - start.x) * dx + (p.y - start.y) * dy) / (mag * mag)
        val clampedU = u.coerceIn(0f, 1f)

        val nearestX = start.x + clampedU * dx
        val nearestY = start.y + clampedU * dy

        val distX = p.x - nearestX
        val distY = p.y - nearestY

        return sqrt((distX * distX + distY * distY).toDouble()).toFloat()
    }

    /**
     * Decimates raw stroke list encoded as Maps (e.g. `[{"x": 10.5, "y": 20.3}, ...]`).
     */
    fun decimateStrokeMaps(rawPoints: List<Map<String, Any?>>, epsilon: Float = 1.5f): List<Map<String, Any?>> {
        if (rawPoints.size <= 2) return rawPoints

        val vectorPoints = rawPoints.mapNotNull { map ->
            val x = (map["x"] as? Number)?.toFloat()
            val y = (map["y"] as? Number)?.toFloat()
            if (x != null && y != null) VectorPoint(x, y) else null
        }

        if (vectorPoints.size <= 2) return rawPoints

        val decimated = decimate(vectorPoints, epsilon)
        return decimated.map { pt ->
            mapOf("x" to pt.x, "y" to pt.y)
        }
    }
}
