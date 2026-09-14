package com.cellular.rpc.domain.game

import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

/**
 * Section 2.16: Procedural 2D Maze Generator for Hardware Sensor Mini-Games.
 *
 * Implements randomized recursive backtracking (Depth-First Search) maze generation
 * with configurable grid dimensions, seed reproducibility for asynchronous multi-device challenges,
 * dead-end target ring placement, and hazard trap distribution.
 */
object ProceduralMazeGenerator {

    enum class MazeDifficulty(val gridSize: Int, val hazardRatio: Float, val targetCount: Int, val timeLimitSec: Float) {
        NOVICE(gridSize = 4, hazardRatio = 0.10f, targetCount = 3, timeLimitSec = 45f),
        INTERMEDIATE(gridSize = 6, hazardRatio = 0.20f, targetCount = 5, timeLimitSec = 60f),
        EXPERT(gridSize = 8, hazardRatio = 0.30f, targetCount = 8, timeLimitSec = 75f),
        MASTER(gridSize = 10, hazardRatio = 0.40f, targetCount = 12, timeLimitSec = 90f);

        companion object {
            fun fromString(str: String?): MazeDifficulty {
                return when (str?.lowercase()?.trim()) {
                    "novice", "easy", "tier1", "1" -> NOVICE
                    "expert", "hard", "tier3", "3" -> EXPERT
                    "master", "insane", "tier4", "4" -> MASTER
                    else -> INTERMEDIATE
                }
            }
        }
    }

    data class MazeLayout(
        val seed: Long,
        val difficulty: MazeDifficulty,
        val gridSize: Int,
        val ballSpawn: Vector2D,
        val targets: List<TargetRing>,
        val obstacles: List<Obstacle>,
        val exitPortal: TargetRing? = null
    )

    private data class Cell(
        val r: Int,
        val c: Int,
        var topWall: Boolean = true,
        var rightWall: Boolean = true,
        var bottomWall: Boolean = true,
        var leftWall: Boolean = true,
        var visited: Boolean = false
    )

    /**
     * Generates a fully playable, non-intersecting procedural maze scaled to the specified arena boundaries.
     *
     * @param arenaWidth Total pixel width of the arena canvas (e.g. 600f)
     * @param arenaHeight Total pixel height of the arena canvas (e.g. 600f)
     * @param difficulty Maze difficulty tier
     * @param customSeed Optional seed for deterministic reproduction
     */
    fun generateMaze(
        arenaWidth: Float = 600f,
        arenaHeight: Float = 600f,
        difficulty: MazeDifficulty = MazeDifficulty.INTERMEDIATE,
        customSeed: Long = System.currentTimeMillis()
    ): MazeLayout {
        val rand = Random(customSeed)
        val n = difficulty.gridSize
        val grid = Array(n) { r -> Array(n) { c -> Cell(r, c) } }

        // 1. Recursive Backtracking DFS to carve maze passages
        val stack = mutableListOf<Cell>()
        val startCell = grid[0][0]
        startCell.visited = true
        stack.add(startCell)

        val deadEnds = mutableListOf<Cell>()

        while (stack.isNotEmpty()) {
            val current = stack.last()
            val neighbors = getUnvisitedNeighbors(current, grid, n)

            if (neighbors.isNotEmpty()) {
                val next = neighbors[rand.nextInt(neighbors.size)]
                removeWallBetween(current, next)
                next.visited = true
                stack.add(next)
            } else {
                val popped = stack.removeAt(stack.size - 1)
                // If a cell has 3 intact walls (1 open passage), it's a dead end
                val wallCount = (if (popped.topWall) 1 else 0) +
                        (if (popped.rightWall) 1 else 0) +
                        (if (popped.bottomWall) 1 else 0) +
                        (if (popped.leftWall) 1 else 0)
                if (wallCount >= 3 && !(popped.r == 0 && popped.c == 0)) {
                    deadEnds.add(popped)
                }
            }
        }

        // 2. Compute geometry coordinates
        val cellW = arenaWidth / n
        val cellH = arenaHeight / n
        val wallThickness = 6f.coerceAtLeast(min(cellW, cellH) * 0.08f)

        val obstacles = mutableListOf<Obstacle>()

        for (r in 0 until n) {
            for (c in 0 until n) {
                val cell = grid[r][c]
                val x0 = c * cellW
                val y0 = r * cellH
                val x1 = (c + 1) * cellW
                val y1 = (r + 1) * cellH

                // Top wall (skip border r == 0 as arena edges handle boundary collisions)
                if (cell.topWall && r > 0) {
                    obstacles.add(
                        Obstacle(
                            id = "wall_t_${r}_$c",
                            left = x0,
                            top = y0 - wallThickness / 2f,
                            right = x1,
                            bottom = y0 + wallThickness / 2f,
                            isHazard = false,
                            colorHex = "#334155"
                        )
                    )
                }

                // Left wall (skip border c == 0)
                if (cell.leftWall && c > 0) {
                    obstacles.add(
                        Obstacle(
                            id = "wall_l_${r}_$c",
                            left = x0 - wallThickness / 2f,
                            top = y0,
                            right = x0 + wallThickness / 2f,
                            bottom = y1,
                            isHazard = false,
                            colorHex = "#334155"
                        )
                    )
                }
            }
        }

        // 3. Place Hazard Traps (Spikes/Thermal Pits) in open cell centers
        val maxHazards = ((n * n) * difficulty.hazardRatio).toInt().coerceAtLeast(1)
        var hazardCount = 0
        val hazardSize = min(cellW, cellH) * 0.35f

        for (r in 0 until n) {
            for (c in 0 until n) {
                if (r == 0 && c == 0) continue // Never trap spawn cell
                if (r == n - 1 && c == n - 1) continue // Never trap exit cell

                if (hazardCount < maxHazards && rand.nextFloat() < 0.28f) {
                    val centerX = c * cellW + cellW / 2f
                    val centerY = r * cellH + cellH / 2f
                    obstacles.add(
                        Obstacle(
                            id = "hazard_${r}_$c",
                            left = centerX - hazardSize / 2f,
                            top = centerY - hazardSize / 2f,
                            right = centerX + hazardSize / 2f,
                            bottom = centerY + hazardSize / 2f,
                            isHazard = true,
                            colorHex = "#FF5470"
                        )
                    )
                    hazardCount++
                }
            }
        }

        // 4. Place Target Rings across Dead Ends and Deep Maze Branches
        val targets = mutableListOf<TargetRing>()
        val targetColorPalette = listOf("#00E676", "#00E5FF", "#FFD166", "#B388FF", "#FF9100")
        val ringRadius = (min(cellW, cellH) * 0.24f).coerceIn(12f, 26f)

        val candidateCells = if (deadEnds.isNotEmpty()) {
            deadEnds.shuffled(rand).toMutableList()
        } else {
            mutableListOf()
        }

        // Fill remaining targets from all other cells if dead-ends < targetCount
        if (candidateCells.size < difficulty.targetCount) {
            for (r in 0 until n) {
                for (c in 0 until n) {
                    if (r == 0 && c == 0) continue
                    val cell = grid[r][c]
                    if (!candidateCells.contains(cell)) {
                        candidateCells.add(cell)
                    }
                }
            }
        }

        val selectedTargetCells = candidateCells.take(difficulty.targetCount)
        selectedTargetCells.forEachIndexed { index, cell ->
            val cx = cell.c * cellW + cellW / 2f
            val cy = cell.r * cellH + cellH / 2f
            val points = 10 + (index * 5)
            val color = targetColorPalette[index % targetColorPalette.size]

            targets.add(
                TargetRing(
                    id = "target_proc_$index",
                    pos = Vector2D(cx, cy),
                    radius = ringRadius,
                    points = points,
                    colorHex = color,
                    isCollected = false
                )
            )
        }

        // 5. Exit Portal Ring (at farthest corner cell [n-1, n-1])
        val exitCellX = (n - 1) * cellW + cellW / 2f
        val exitCellY = (n - 1) * cellH + cellH / 2f
        val exitPortal = TargetRing(
            id = "exit_portal_primary",
            pos = Vector2D(exitCellX, exitCellY),
            radius = ringRadius * 1.3f,
            points = 50,
            colorHex = "#00E5FF",
            isCollected = false
        )
        targets.add(exitPortal)

        // Spawn ball at center of cell (0,0)
        val spawnPos = Vector2D(cellW / 2f, cellH / 2f)

        return MazeLayout(
            seed = customSeed,
            difficulty = difficulty,
            gridSize = n,
            ballSpawn = spawnPos,
            targets = targets,
            obstacles = obstacles,
            exitPortal = exitPortal
        )
    }

    private fun getUnvisitedNeighbors(cell: Cell, grid: Array<Array<Cell>>, n: Int): List<Cell> {
        val neighbors = mutableListOf<Cell>()
        val (r, c) = cell.r to cell.c
        if (r > 0 && !grid[r - 1][c].visited) neighbors.add(grid[r - 1][c])
        if (r < n - 1 && !grid[r + 1][c].visited) neighbors.add(grid[r + 1][c])
        if (c > 0 && !grid[r][c - 1].visited) neighbors.add(grid[r][c - 1])
        if (c < n - 1 && !grid[r][c + 1].visited) neighbors.add(grid[r][c + 1])
        return neighbors
    }

    private fun removeWallBetween(c1: Cell, c2: Cell) {
        if (c1.r == c2.r) {
            if (c1.c < c2.c) {
                c1.rightWall = false
                c2.leftWall = false
            } else {
                c1.leftWall = false
                c2.rightWall = false
            }
        } else if (c1.c == c2.c) {
            if (c1.r < c2.r) {
                c1.bottomWall = false
                c2.topWall = false
            } else {
                c1.topWall = false
                c2.bottomWall = false
            }
        }
    }
}
