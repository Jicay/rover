package com.jicay.rover.infrastructure.driving.dto

import com.jicay.rover.domain.model.Board
import com.jicay.rover.domain.model.Direction
import com.jicay.rover.domain.model.Position
import com.jicay.rover.domain.model.Rover

data class PositionDto(val x: Int, val y: Int) {

    fun toDomain(): Position = Position(x = x, y = y)

    companion object {
        fun from(position: Position) = PositionDto(x = position.x, y = position.y)
    }
}

data class CreateBoardRequest(
    val width: Int,
    val height: Int,
    val obstacles: List<PositionDto> = emptyList(),
)

data class DeployRoverRequest(
    val id: String,
    val x: Int,
    val y: Int,
    val direction: Direction,
) {
    fun toDomain(): Rover = Rover(id = id, position = Position(x = x, y = y), direction = direction)
}

data class ExecuteCommandsRequest(val commands: String)

data class RoverResponse(val id: String, val x: Int, val y: Int, val direction: Direction) {

    companion object {
        fun from(rover: Rover) = RoverResponse(
            id = rover.id,
            x = rover.position.x,
            y = rover.position.y,
            direction = rover.direction,
        )
    }
}

data class BoardResponse(
    val id: String,
    val width: Int,
    val height: Int,
    val obstacles: List<PositionDto>,
    val rovers: List<RoverResponse>,
) {
    companion object {
        fun from(board: Board) = BoardResponse(
            id = board.id,
            width = board.width,
            height = board.height,
            obstacles = board.obstacles.map(PositionDto::from),
            rovers = board.rovers.map(RoverResponse::from),
        )
    }
}

data class ErrorResponse(val message: String)
