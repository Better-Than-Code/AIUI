package com.cellular.rpc.ui.chat.animation

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import org.json.JSONObject
import kotlin.random.Random

/**
 * Standardized Animation Preset Configuration conforming to Google Doc Section 2.14.
 */
data class AnimationPresetConfig(
    val presetId: String = "cloud",
    val durationMs: Int = 320,
    val dampingRatio: Float = 0.75f,
    val stiffness: Float = 380f,
    val shaderType: String = "none",
    val hapticCue: String = "click_light"
) {
    fun toJson(): String {
        return JSONObject().apply {
            put("preset_id", presetId)
            put("duration_ms", durationMs)
            put("spring_spec", JSONObject().apply {
                put("damping_ratio", dampingRatio.toDouble())
                put("stiffness", stiffness.toDouble())
            })
            put("shader_effect", JSONObject().apply {
                put("type", shaderType)
            })
            put("haptic_cue", hapticCue)
        }.toString()
    }

    companion object {
        val CLOUD = AnimationPresetConfig(
            presetId = "cloud",
            durationMs = 350,
            dampingRatio = 0.8f,
            stiffness = 300f,
            shaderType = "vapor_condense"
        )
        val ORIGAMI_FOLD = AnimationPresetConfig(
            presetId = "origami_fold",
            durationMs = 380,
            dampingRatio = 0.65f,
            stiffness = 420f,
            shaderType = "crease_snap"
        )
        val NEON_STRIKE = AnimationPresetConfig(
            presetId = "neon_strike",
            durationMs = 300,
            dampingRatio = 0.9f,
            stiffness = 500f,
            shaderType = "glow_pulse"
        )
        val SPRING_DETENT = AnimationPresetConfig(
            presetId = "spring_detent",
            durationMs = 320,
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium,
            shaderType = "jelly_overshoot"
        )
        val PARTICLE_DISSOLVE = AnimationPresetConfig(
            presetId = "particle_dissolve",
            durationMs = 400,
            dampingRatio = 0.85f,
            stiffness = 250f,
            shaderType = "ash_smoke"
        )
        val LIQUID_MORPH = AnimationPresetConfig(
            presetId = "liquid_morph",
            durationMs = 360,
            dampingRatio = 0.7f,
            stiffness = 350f,
            shaderType = "metaball_merge"
        )

        fun fromPresetId(id: String): AnimationPresetConfig {
            return when (id.lowercase().trim()) {
                "origami", "origami_fold" -> ORIGAMI_FOLD
                "neon", "neon_strike" -> NEON_STRIKE
                "spring", "spring_detent" -> SPRING_DETENT
                "dissolve", "particle_dissolve" -> PARTICLE_DISSOLVE
                "liquid", "liquid_morph" -> LIQUID_MORPH
                else -> CLOUD
            }
        }
    }
}

/**
 * Bubble Animation Wrapper implementing Section 2.12 / 2.14 reveal dynamics.
 */
@Composable
fun PluggableBubbleContainer(
    preset: AnimationPresetConfig,
    isOutgoing: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    var isAppeared by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        isAppeared = true
    }

    val transition = updateTransition(targetState = isAppeared, label = "BubbleReveal_${preset.presetId}")

    val alphaAnim by transition.animateFloat(
        transitionSpec = {
            tween(durationMillis = preset.durationMs, easing = FastOutSlowInEasing)
        },
        label = "Alpha"
    ) { appeared -> if (appeared) 1f else 0f }

    val scaleAnim by transition.animateFloat(
        transitionSpec = {
            spring(
                dampingRatio = preset.dampingRatio,
                stiffness = preset.stiffness
            )
        },
        label = "Scale"
    ) { appeared ->
        if (appeared) 1f else when (preset.presetId) {
            "origami_fold" -> 0.82f
            "spring_detent" -> 0.70f
            "liquid_morph" -> 0.78f
            else -> 0.90f
        }
    }

    val neonGlowAlpha by transition.animateFloat(
        transitionSpec = {
            keyframes {
                durationMillis = preset.durationMs
                0.0f at 0
                0.9f at (preset.durationMs / 3)
                0.3f at (preset.durationMs * 2 / 3)
                0.0f at preset.durationMs
            }
        },
        label = "NeonGlow"
    ) { 0f }

    Box(
        modifier = modifier
            .alpha(alphaAnim)
            .scale(scaleAnim)
            .drawBehind {
                if (preset.presetId == "neon_strike" && neonGlowAlpha > 0.05f) {
                    val strokeColor = if (isOutgoing) Color(0xFF00E5FF) else Color(0xFF00E676)
                    drawRoundRect(
                        color = strokeColor.copy(alpha = neonGlowAlpha),
                        size = size,
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(18.dp.toPx(), 18.dp.toPx()),
                        style = Stroke(width = 3.dp.toPx())
                    )
                }
            }
    ) {
        content()
    }
}
