package com.jicay.rover.domain.model

import com.jicay.rover.domain.exception.DuplicateRoverIdException
import com.jicay.rover.domain.exception.InvalidBoardDimensionsException
import com.jicay.rover.domain.exception.PositionAlreadyOccupiedException
import com.jicay.rover.domain.exception.PositionOutOfBoardException
import com.jicay.rover.domain.exception.RoverNotFoundException

data class Board(
    val id: String,
    val width: Int,
    val height: Int,
    val obstacles: Set<Position> = emptySet(),
    val rovers: List<Rover> = emptyList(),
) {
    init {
        if (width < 1 || height < 1) throw InvalidBoardDimensionsException(width, height)

        obstacles.forEach { obstacle ->
            if (!contains(obstacle)) throw PositionOutOfBoardException(obstacle)
        }

        rovers.groupBy { it.id }
            .entries
            .firstOrNull { it.value.size > 1 }
            ?.let { throw DuplicateRoverIdException(it.key) }

        val taken = mutableSetOf<Position>()
        rovers.forEach { rover ->
            if (!contains(rover.position)) throw PositionOutOfBoardException(rover.position)
            if (rover.position in obstacles || !taken.add(rover.position)) {
                throw PositionAlreadyOccupiedException(rover.position)
            }
        }
    }

    fun contains(position: Position): Boolean =
        position.x in 0 until width && position.y in 0 until height

    fun isFree(position: Position): Boolean =
        contains(position) && position !in obstacles && rovers.none { it.position == position }

    fun rover(roverId: String): Rover =
        rovers.firstOrNull { it.id == roverId } ?: throw RoverNotFoundException(roverId)

    fun deployRover(rover: Rover): Board {
        if (rovers.any { it.id == rover.id }) throw DuplicateRoverIdException(rover.id)
        if (!contains(rover.position)) throw PositionOutOfBoardException(rover.position)
        if (!isFree(rover.position)) throw PositionAlreadyOccupiedException(rover.position)
        return copy(rovers = rovers + rover)
    }

    fun moveRover(roverId: String, command: Command): Board {
        val current = rover(roverId)
        val updated = when (command) {
            Command.L -> current.turnLeft()
            Command.R -> current.turnRight()
            Command.F -> moveIfFree(current, current.forwardTarget())
            Command.B -> moveIfFree(current, current.backwardTarget())
        }
        return copy(rovers = rovers.map { if (it.id == roverId) updated else it })
    }

    fun executeCommands(roverId: String, commands: List<Command>): Board =
        commands.fold(this) { board, command -> board.moveRover(roverId, command) }

    private fun moveIfFree(rover: Rover, target: Position): Rover =
        if (isFree(target)) rover.movedTo(target) else rover
}
