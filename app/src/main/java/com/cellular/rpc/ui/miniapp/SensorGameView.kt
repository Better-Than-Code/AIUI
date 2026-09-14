package com.cellular.rpc.ui.miniapp

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cellular.rpc.domain.game.*
import com.cellular.rpc.domain.sensor.HardwareSensorEngine
import com.example.ui.theme.*
import kotlinx.coroutines.isActive

/**
 * Section 2.10 & 2.16: Native 60-120Hz Hardware Sensor 2D Mini-Game Canvas Component.
 * 
 * Embeds directly into SDUI Mini-Apps as node type "sensor_game", "physics_game", or "tilt_maze".
 * Connects directly to HardwareSensorEngine for low-latency accelerometer tilt and haptics,
 * featuring interactive tilt physics, procedural DFS maze generation, multi-tier difficulty progression,
 * target ring collections, hazard traps, and fallback manual touch drag controls.
 */
@Composable
fun SensorGameView(
    modifier: Modifier = Modifier,
    gameId: String = "sensor_maze_v1",
    arenaHeightDp: Int = 280,
    initialLives: Int = 3,
    timeLimitSec: Float = 45f,
    mazeDifficulty: String = "intermediate",
    isProcedural: Boolean = true,
    mazeSeed: Long? = null,
    onScoreChanged: ((score: Int, isGameOver: Boolean, isLevelComplete: Boolean) -> Unit)? = null,
    onGameStateExport: ((Map<String, Any?>) -> Unit)? = null
) {
    val context = LocalContext.current
    val sensorEngine = remember { HardwareSensorEngine(context) }
    val sensorTelemetry by sensorEngine.telemetry.collectAsState()

    var currentTier by remember { mutableStateOf(ProceduralMazeGenerator.MazeDifficulty.fromString(mazeDifficulty)) }
    var currentSeed by remember { mutableLongStateOf(mazeSeed ?: System.currentTimeMillis()) }
    var currentStage by remember { mutableIntStateOf(1) }

    var manualTiltX by remember { mutableFloatStateOf(0f) }
    var manualTiltY by remember { mutableFloatStateOf(0f) }
    var isManualControlActive by remember { mutableStateOf(false) }

    val physicsEngine = remember {
        MiniGamePhysicsEngine(
            arenaWidth = 600f,
            arenaHeight = 600f,
            gravityFactor = 240f
        )
    }

    fun buildInitialLayout(tier: ProceduralMazeGenerator.MazeDifficulty, seed: Long): GameEngineState {
        if (!isProcedural) {
            val defaultTargets = listOf(
                TargetRing("t1", Vector2D(120f, 120f), radius = 22f, points = 10, colorHex = "#00E676"),
                TargetRing("t2", Vector2D(480f, 120f), radius = 22f, points = 15, colorHex = "#00E5FF"),
                TargetRing("t3", Vector2D(300f, 300f), radius = 26f, points = 25, colorHex = "#FFD166"),
                TargetRing("t4", Vector2D(140f, 480f), radius = 22f, points = 10, colorHex = "#00E676"),
                TargetRing("t5", Vector2D(480f, 480f), radius = 22f, points = 20, colorHex = "#B388FF")
            )
            val defaultObstacles = listOf(
                Obstacle("obs_h1", 200f, 180f, 400f, 210f, isHazard = false, colorHex = "#334155"),
                Obstacle("obs_h2", 200f, 390f, 400f, 420f, isHazard = false, colorHex = "#334155"),
                Obstacle("obs_v1", 120f, 240f, 150f, 360f, isHazard = false, colorHex = "#334155"),
                Obstacle("obs_v2", 450f, 240f, 480f, 360f, isHazard = false, colorHex = "#334155"),
                Obstacle("hazard_1", 280f, 140f, 320f, 180f, isHazard = true, colorHex = "#FF5470"),
                Obstacle("hazard_2", 280f, 420f, 320f, 460f, isHazard = true, colorHex = "#FF5470")
            )
            return GameEngineState(
                ball = BallState(pos = Vector2D(80f, 300f), radius = 16f, colorHex = "#00E5FF"),
                targets = defaultTargets,
                obstacles = defaultObstacles,
                lives = initialLives,
                timeRemainingSec = timeLimitSec
            )
        }

        val layout = ProceduralMazeGenerator.generateMaze(
            arenaWidth = 600f,
            arenaHeight = 600f,
            difficulty = tier,
            customSeed = seed
        )

        return GameEngineState(
            ball = BallState(pos = layout.ballSpawn, radius = 14f, colorHex = "#00E5FF"),
            targets = layout.targets,
            obstacles = layout.obstacles,
            lives = initialLives,
            timeRemainingSec = tier.timeLimitSec
        )
    }

    var gameState by remember(gameId, currentSeed, currentTier) {
        mutableStateOf(buildInitialLayout(currentTier, currentSeed))
    }

    // Lifecycle Sensor Management
    DisposableEffect(Unit) {
        sensorEngine.start()
        onDispose {
            sensorEngine.stop()
        }
    }

    // 60fps Game Simulation Loop
    LaunchedEffect(gameState.isGameOver, gameState.isLevelComplete) {
        if (gameState.isGameOver || gameState.isLevelComplete) return@LaunchedEffect

        var lastFrameTime = withFrameNanos { it }
        while (isActive && !gameState.isGameOver && !gameState.isLevelComplete) {
            val frameTime = withFrameNanos { it }
            val dtSec = ((frameTime - lastFrameTime) / 1_000_000_000f).coerceIn(0.001f, 0.05f)
            lastFrameTime = frameTime

            val activeTiltX = if (isManualControlActive) manualTiltX else sensorTelemetry.accelX
            val activeTiltY = if (isManualControlActive) manualTiltY else sensorTelemetry.accelY

            gameState = physicsEngine.update(
                state = gameState,
                tiltX = activeTiltX,
                tiltY = activeTiltY,
                dtSec = dtSec,
                onCollision = { isHazard ->
                    if (isHazard) {
                        sensorEngine.triggerHapticFeedback(HardwareSensorEngine.HapticIntensity.HEAVY)
                    } else {
                        sensorEngine.triggerHapticFeedback(HardwareSensorEngine.HapticIntensity.LIGHT)
                    }
                },
                onCollectTarget = { target ->
                    sensorEngine.triggerHapticFeedback(HardwareSensorEngine.HapticIntensity.MEDIUM)
                }
            )

            onScoreChanged?.invoke(gameState.score, gameState.isGameOver, gameState.isLevelComplete)
            onGameStateExport?.invoke(
                mapOf(
                    "score" to gameState.score,
                    "lives" to gameState.lives,
                    "timeRemaining" to gameState.timeRemainingSec.toInt(),
                    "isGameOver" to gameState.isGameOver,
                    "isLevelComplete" to gameState.isLevelComplete
                )
            )
        }
    }

    fun restartGame(nextLevel: Boolean = false) {
        if (nextLevel) {
            currentStage += 1
            currentSeed += 101L
            // Advance tier dynamically
            currentTier = when (currentTier) {
                ProceduralMazeGenerator.MazeDifficulty.NOVICE -> ProceduralMazeGenerator.MazeDifficulty.INTERMEDIATE
                ProceduralMazeGenerator.MazeDifficulty.INTERMEDIATE -> ProceduralMazeGenerator.MazeDifficulty.EXPERT
                ProceduralMazeGenerator.MazeDifficulty.EXPERT -> ProceduralMazeGenerator.MazeDifficulty.MASTER
                ProceduralMazeGenerator.MazeDifficulty.MASTER -> ProceduralMazeGenerator.MazeDifficulty.MASTER
            }
        } else {
            currentStage = 1
            currentSeed = mazeSeed ?: System.currentTimeMillis()
            currentTier = ProceduralMazeGenerator.MazeDifficulty.fromString(mazeDifficulty)
        }
        gameState = buildInitialLayout(currentTier, currentSeed).copy(
            score = if (nextLevel) gameState.score + 50 else 0
        )
        manualTiltX = 0f
        manualTiltY = 0f
        isManualControlActive = false
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .testTag("sensor_game_view"),
        color = DarkNavyBackground,
        border = BorderStroke(1.5.dp, DarkNavyBorder)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // HUD Status Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Score, Stage & Lives
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = CyanPrimary.copy(alpha = 0.15f),
                        border = BorderStroke(1.dp, CyanPrimary.copy(alpha = 0.4f))
                    ) {
                        Text(
                            text = "STG $currentStage • ${gameState.score} PTS",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = CyanPrimary,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                        )
                    }

                    // Difficulty Tag
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = when (currentTier) {
                            ProceduralMazeGenerator.MazeDifficulty.NOVICE -> SignalGreen.copy(alpha = 0.2f)
                            ProceduralMazeGenerator.MazeDifficulty.INTERMEDIATE -> CyanPrimary.copy(alpha = 0.2f)
                            ProceduralMazeGenerator.MazeDifficulty.EXPERT -> SignalAmber.copy(alpha = 0.2f)
                            ProceduralMazeGenerator.MazeDifficulty.MASTER -> SignalRed.copy(alpha = 0.2f)
                        }
                    ) {
                        Text(
                            text = currentTier.name,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = when (currentTier) {
                                ProceduralMazeGenerator.MazeDifficulty.NOVICE -> SignalGreen
                                ProceduralMazeGenerator.MazeDifficulty.INTERMEDIATE -> CyanPrimary
                                ProceduralMazeGenerator.MazeDifficulty.EXPERT -> SignalAmber
                                ProceduralMazeGenerator.MazeDifficulty.MASTER -> SignalRed
                            },
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                        )
                    }

                    // Lives hearts/dots
                    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        repeat(initialLives) { idx ->
                            Icon(
                                imageVector = if (idx < gameState.lives) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                contentDescription = null,
                                tint = if (idx < gameState.lives) SignalRed else Color.Gray,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }

                // Sensor indicator badge & Timer
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = if (sensorTelemetry.isPhysicalSensorActive) SignalGreen.copy(alpha = 0.15f) else SignalAmber.copy(alpha = 0.15f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(if (sensorTelemetry.isPhysicalSensorActive) SignalGreen else SignalAmber)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (sensorTelemetry.isPhysicalSensorActive) "HARDWARE" else "SIM TILT",
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (sensorTelemetry.isPhysicalSensorActive) SignalGreen else SignalAmber
                            )
                        }
                    }

                    Text(
                        text = "${gameState.timeRemainingSec.toInt()}s",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = if (gameState.timeRemainingSec < 10f) SignalRed else Color.White
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 2D Game Arena Canvas
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(arenaHeightDp.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF070B14))
                    .border(1.dp, CyanPrimary.copy(alpha = 0.25f), RoundedCornerShape(12.dp))
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { isManualControlActive = true },
                            onDragEnd = {
                                manualTiltX = 0f
                                manualTiltY = 0f
                                isManualControlActive = false
                            },
                            onDragCancel = {
                                manualTiltX = 0f
                                manualTiltY = 0f
                                isManualControlActive = false
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                isManualControlActive = true
                                manualTiltX = (manualTiltX - dragAmount.x * 0.1f).coerceIn(-8f, 8f)
                                manualTiltY = (manualTiltY + dragAmount.y * 0.1f).coerceIn(-8f, 8f)
                                sensorEngine.simulateTilt(manualTiltX, manualTiltY)
                            }
                        )
                    }
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    physicsEngine.arenaWidth = size.width
                    physicsEngine.arenaHeight = size.height

                    // Grid Background lines
                    val step = 40.dp.toPx()
                    var x = 0f
                    while (x < size.width) {
                        drawLine(
                            color = Color(0xFF131D35),
                            start = Offset(x, 0f),
                            end = Offset(x, size.height),
                            strokeWidth = 1f
                        )
                        x += step
                    }
                    var y = 0f
                    while (y < size.height) {
                        drawLine(
                            color = Color(0xFF131D35),
                            start = Offset(0f, y),
                            end = Offset(size.width, y),
                            strokeWidth = 1f
                        )
                        y += step
                    }

                    // 1. Draw Obstacles & Hazards
                    gameState.obstacles.forEach { obs ->
                        val obsColor = if (obs.isHazard) SignalRed else Color(0xFF334155)
                        drawRoundRect(
                            color = obsColor,
                            topLeft = Offset(obs.left, obs.top),
                            size = Size(obs.right - obs.left, obs.bottom - obs.top),
                            cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx())
                        )
                        if (obs.isHazard) {
                            drawRoundRect(
                                color = SignalRed.copy(alpha = 0.4f),
                                topLeft = Offset(obs.left - 2f, obs.top - 2f),
                                size = Size((obs.right - obs.left) + 4f, (obs.bottom - obs.top) + 4f),
                                cornerRadius = CornerRadius(8.dp.toPx(), 8.dp.toPx()),
                                style = Stroke(width = 2f)
                            )
                        }
                    }

                    // 2. Draw Target Rings
                    gameState.targets.forEach { target ->
                        if (!target.isCollected) {
                            val ringColor = Color(android.graphics.Color.parseColor(target.colorHex))
                            // Glow background
                            drawCircle(
                                color = ringColor.copy(alpha = 0.2f),
                                radius = target.radius + 6f,
                                center = Offset(target.pos.x, target.pos.y)
                            )
                            // Outer ring
                            drawCircle(
                                color = ringColor,
                                radius = target.radius,
                                center = Offset(target.pos.x, target.pos.y),
                                style = Stroke(width = 3.dp.toPx())
                            )
                            // Core point
                            drawCircle(
                                color = ringColor,
                                radius = 4.dp.toPx(),
                                center = Offset(target.pos.x, target.pos.y)
                            )
                        }
                    }

                    // 3. Draw Physics Player Ball
                    val ballPos = Offset(gameState.ball.pos.x, gameState.ball.pos.y)
                    val ballColor = Color(android.graphics.Color.parseColor(gameState.ball.colorHex))

                    // Pulse glow halo
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(ballColor.copy(alpha = 0.6f), Color.Transparent),
                            center = ballPos,
                            radius = gameState.ball.radius * 2.5f
                        ),
                        radius = gameState.ball.radius * 2.5f,
                        center = ballPos
                    )

                    // Solid ball core
                    drawCircle(
                        color = ballColor,
                        radius = gameState.ball.radius,
                        center = ballPos
                    )
                    drawCircle(
                        color = Color.White,
                        radius = gameState.ball.radius * 0.35f,
                        center = ballPos - Offset(gameState.ball.radius * 0.3f, gameState.ball.radius * 0.3f)
                    )
                }

                // Overlay: Game Over / Victory Screen
                if (gameState.isGameOver || gameState.isLevelComplete) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = Color.Black.copy(alpha = 0.75f)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = if (gameState.isLevelComplete) "STAGE CLEARED! 🌟" else "GAME OVER",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = if (gameState.isLevelComplete) SignalGreen else SignalRed
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Final Score: ${gameState.score} pts",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.height(14.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (gameState.isLevelComplete) {
                                    Button(
                                        onClick = { restartGame(nextLevel = true) },
                                        colors = ButtonDefaults.buttonColors(containerColor = SignalGreen),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Next Stage", color = Color.Black, fontWeight = FontWeight.Bold)
                                    }
                                }
                                Button(
                                    onClick = { restartGame(nextLevel = false) },
                                    colors = ButtonDefaults.buttonColors(containerColor = CyanPrimary),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Icon(Icons.Default.Refresh, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(if (gameState.isLevelComplete) "Replay" else "Try Again", color = Color.Black, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Bottom Diagnostics & Interactive Controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Tilt device or drag arena to guide probe",
                    fontSize = 10.sp,
                    color = Color.Gray
                )

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    IconButton(
                        onClick = { restartGame() },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "Restart", tint = Color.LightGray, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
    }
}
