package com.cellular.rpc.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.CyanPrimary
import com.example.ui.theme.DarkNavyBorder
import com.example.ui.theme.DarkNavySurface

/**
 * Reusable M3 shimmer brush for cellular skeleton cards.
 * Provides a high-contrast sweeping light shimmer matching cellular terminal aesthetics.
 */
@Composable
fun rememberShimmerBrush(
    targetValue: Float = 1200f,
    durationMillis: Int = 1300
): Brush {
    val transition = rememberInfiniteTransition(label = "CellularShimmerTransition")
    val translateAnimation by transition.animateFloat(
        initialValue = 0f,
        targetValue = targetValue,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = durationMillis, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "CellularShimmerTranslate"
    )

    val shimmerColors = listOf(
        DarkNavySurface,
        Color(0xFF1E2A47),
        Color(0xFF2C3E6B).copy(alpha = 0.85f),
        Color(0xFF1E2A47),
        DarkNavySurface
    )

    return Brush.linearGradient(
        colors = shimmerColors,
        start = Offset(x = translateAnimation - 400f, y = 0f),
        end = Offset(x = translateAnimation + 400f, y = 300f)
    )
}

/**
 * Reusable animated glowing border for in-flight cellular streams.
 */
@Composable
fun rememberPulsingBorder(
    glowColor: Color = CyanPrimary,
    minAlpha: Float = 0.35f,
    maxAlpha: Float = 0.85f,
    durationMillis: Int = 1600
): BorderStroke {
    val transition = rememberInfiniteTransition(label = "PulsingBorderTransition")
    val alpha by transition.animateFloat(
        initialValue = minAlpha,
        targetValue = maxAlpha,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = durationMillis, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "PulsingBorderAlpha"
    )

    return BorderStroke(1.dp, glowColor.copy(alpha = alpha))
}

/**
 * FEAT-05 & FEAT-05 Refinement: Named Inline Loading Skeleton & Shimmer Outline.
 * Dynamically displays the specific item name being loaded (e.g., Loading Market Ticker...,
 * Loading Margin Calc..., Loading News Wire...) with glowing container contours,
 * header badge, content block contours, and action button outlines.
 */
@Composable
fun DynamicNamedSkeletonCard(
    label: String,
    targetType: String = "blueprint",
    iconEmoji: String = "✨",
    modifier: Modifier = Modifier
) {
    val shimmerBrush = rememberShimmerBrush()
    val pulsingBorder = rememberPulsingBorder()

    // Title label normalization
    val displayLabel = if (label.startsWith("Loading", ignoreCase = true)) {
        label
    } else {
        "Loading $label..."
    }

    val transition = rememberInfiniteTransition(label = "SkeletonDotTransition")
    val dotAlpha by transition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "DotPulse"
    )

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("inline_loading_skeleton_${targetType}"),
        color = DarkNavySurface.copy(alpha = 0.92f),
        shape = RoundedCornerShape(16.dp),
        border = pulsingBorder
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            // Header Row: Emoji badge, dynamic named label, and sync pill
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f, fill = false)
                ) {
                    // Emoji / Icon Container with subtle glow
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF1E2A47).copy(alpha = 0.8f))
                            .border(0.5.dp, CyanPrimary.copy(alpha = 0.5f), RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = iconEmoji.ifBlank { "✨" },
                            fontSize = 16.sp
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Column {
                        Text(
                            text = displayLabel,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "Awaiting cellular PDU • SDUI v2.1.0",
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 10.sp,
                            color = CyanPrimary.copy(alpha = 0.85f),
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                // In-Flight Status Pill
                Row(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(CyanPrimary.copy(alpha = 0.12f))
                        .border(0.5.dp, CyanPrimary.copy(alpha = 0.4f), CircleShape)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(CyanPrimary.copy(alpha = dotAlpha))
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = "STREAMING",
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = CyanPrimary,
                        letterSpacing = 0.5.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Primary Content Block Contour (Simulates graph / hero section)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(shimmerBrush)
                    .border(0.5.dp, DarkNavyBorder.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Secondary Content Line 1 (85% width)
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.85f)
                    .height(12.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(shimmerBrush)
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Secondary Content Line 2 (55% width)
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.55f)
                    .height(12.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(shimmerBrush)
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Action Button Contours (Pill shapes simulating interactive buttons)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .width(100.dp)
                        .height(30.dp)
                        .clip(RoundedCornerShape(15.dp))
                        .background(shimmerBrush)
                        .border(0.5.dp, DarkNavyBorder.copy(alpha = 0.5f), RoundedCornerShape(15.dp))
                )
                Box(
                    modifier = Modifier
                        .width(75.dp)
                        .height(30.dp)
                        .clip(RoundedCornerShape(15.dp))
                        .background(shimmerBrush)
                        .border(0.5.dp, DarkNavyBorder.copy(alpha = 0.5f), RoundedCornerShape(15.dp))
                )
            }
        }
    }
}
