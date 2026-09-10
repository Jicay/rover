package com.jicay.rover.domain.model

data class Rover(val id: String, val position: Position, val direction: Direction) {

    fun forwardTarget(): Position = position.translate(direction.dx, direction.dy)

    fun backwardTarget(): Position = position.translate(-direction.dx, -direction.dy)

    fun turnLeft(): Rover = copy(direction = direction.turnLeft())

    fun turnRight(): Rover = copy(direction = direction.turnRight())

    fun movedTo(target: Position): Rover = copy(position = target)
}
