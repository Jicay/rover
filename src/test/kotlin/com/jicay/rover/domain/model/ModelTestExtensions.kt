package com.jicay.rover.domain.model

import io.kotest.matchers.shouldBe

fun rover(x: Int, y: Int, facing: Direction, id: String = "rover-1") =
    Rover(id = id, position = Position(x, y), direction = facing)

fun at(x: Int, y: Int) = Position(x, y)

fun board(
    width: Int = 5,
    height: Int = 5,
    id: String = "board-1",
    obstacles: Set<Position> = emptySet(),
    rovers: List<Rover> = emptyList(),
) = Board(id = id, width = width, height = height, obstacles = obstacles, rovers = rovers)

infix fun Rover.shouldBeAt(expected: Position) = position shouldBe expected

infix fun Rover.shouldFace(expected: Direction) = direction shouldBe expected

fun Board.shouldHoldItsInvariants() {
    rovers.forEach { rover ->
        contains(rover.position) shouldBe true
        (rover.position in obstacles) shouldBe false
    }
    rovers.map { it.position }.distinct().size shouldBe rovers.size
}
