package com.jicay.rover.infrastructure.driving.view

import com.jicay.rover.domain.model.Board
import com.jicay.rover.domain.model.Direction
import com.jicay.rover.domain.model.Position

data class CellView(
    val x: Int,
    val y: Int,
    val roverId: String?,
    val direction: Direction?,
    val colorIndex: Int?,
    val obstacle: Boolean,
)

data class UnitView(
    val id: String,
    val x: Int,
    val y: Int,
    val direction: Direction,
    val heading: String,
    val colorIndex: Int,
)

data class BoardView(
    val id: String,
    val width: Int,
    val height: Int,
    val rows: List<List<CellView>>,
    val units: List<UnitView>,
) {
    companion object {
        private const val UNIT_COLOR_COUNT = 6

        fun from(board: Board): BoardView {
            val units = board.rovers.mapIndexed { index, rover ->
                UnitView(
                    id = rover.id,
                    x = rover.position.x,
                    y = rover.position.y,
                    direction = rover.direction,
                    heading = headingOf(rover.direction),
                    colorIndex = index % UNIT_COLOR_COUNT,
                )
            }
            val unitsByPosition = units.associateBy { Position(x = it.x, y = it.y) }
            return BoardView(
                id = board.id,
                width = board.width,
                height = board.height,
                rows = (board.height - 1 downTo 0).map { y ->
                    (0 until board.width).map { x ->
                        val position = Position(x = x, y = y)
                        val unit = unitsByPosition[position]
                        CellView(
                            x = x,
                            y = y,
                            roverId = unit?.id,
                            direction = unit?.direction,
                            colorIndex = unit?.colorIndex,
                            obstacle = position in board.obstacles,
                        )
                    }
                },
                units = units,
            )
        }

        private fun headingOf(direction: Direction): String = when (direction) {
            Direction.N -> "Nord"
            Direction.E -> "Est"
            Direction.S -> "Sud"
            Direction.W -> "Ouest"
        }
    }
}
