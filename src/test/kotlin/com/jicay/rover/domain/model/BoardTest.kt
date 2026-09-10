package com.jicay.rover.domain.model

import com.jicay.rover.domain.exception.DuplicateRoverIdException
import com.jicay.rover.domain.exception.InvalidBoardDimensionsException
import com.jicay.rover.domain.exception.PositionAlreadyOccupiedException
import com.jicay.rover.domain.exception.PositionOutOfBoardException
import com.jicay.rover.domain.exception.RoverNotFoundException
import com.jicay.rover.domain.model.Direction.E
import com.jicay.rover.domain.model.Direction.N
import com.jicay.rover.domain.model.Direction.S
import com.jicay.rover.domain.model.Direction.W
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.element
import io.kotest.property.arbitrary.enum
import io.kotest.property.arbitrary.list
import io.kotest.property.checkAll

private val anyCommands = Arb.list(Arb.enum<Command>(), 0..20)
private val turnCommands = Arb.list(Arb.element(listOf(Command.L, Command.R)), 1..20)
private val moveCommands = Arb.list(Arb.element(listOf(Command.F, Command.B)), 1..20)

class BoardTest : FunSpec({

    context("creation") {
        test("a board with width 0 is rejected") {
            shouldThrow<InvalidBoardDimensionsException> { board(width = 0) }
        }

        test("a board with height 0 is rejected") {
            shouldThrow<InvalidBoardDimensionsException> { board(height = 0) }
        }

        test("a board with a negative width is rejected") {
            shouldThrow<InvalidBoardDimensionsException> { board(width = -1) }
        }

        test("a board keeps its identifier") {
            board(id = "board-42").id shouldBe "board-42"
        }

        test("a 1x1 board is the smallest valid board") {
            val smallest = board(width = 1, height = 1)
            smallest.width shouldBe 1
            smallest.height shouldBe 1
        }

        test("an obstacle outside the board is rejected") {
            shouldThrow<PositionOutOfBoardException> { board(obstacles = setOf(at(5, 0))) }
        }

        test("a rover outside the board is rejected") {
            shouldThrow<PositionOutOfBoardException> { board(rovers = listOf(rover(0, 5, N))) }
        }

        test("two rovers on the same cell are rejected") {
            shouldThrow<PositionAlreadyOccupiedException> {
                board(rovers = listOf(rover(1, 1, N, "a"), rover(1, 1, S, "b")))
            }
        }

        test("a rover standing on an obstacle is rejected") {
            shouldThrow<PositionAlreadyOccupiedException> {
                board(obstacles = setOf(at(1, 1)), rovers = listOf(rover(1, 1, N)))
            }
        }

        test("two rovers sharing an id are rejected") {
            shouldThrow<DuplicateRoverIdException> {
                board(rovers = listOf(rover(0, 0, N, "same"), rover(1, 1, N, "same")))
            }
        }
    }

    context("cells") {
        test("a cell inside the bounds belongs to the board") {
            board().contains(at(4, 4)) shouldBe true
        }

        test("a cell beyond the bounds does not belong to the board") {
            board().contains(at(5, 4)) shouldBe false
            board().contains(at(4, 5)) shouldBe false
            board().contains(at(-1, 0)) shouldBe false
            board().contains(at(0, -1)) shouldBe false
        }

        test("a cell holding an obstacle is not free") {
            board(obstacles = setOf(at(2, 2))).isFree(at(2, 2)) shouldBe false
        }

        test("a cell holding a rover is not free") {
            board(rovers = listOf(rover(2, 2, N))).isFree(at(2, 2)) shouldBe false
        }

        test("an empty cell inside the board is free") {
            board().isFree(at(2, 2)) shouldBe true
        }
    }

    context("deploying a rover") {
        test("a rover is added to the board") {
            val updated = board().deployRover(rover(1, 2, E))
            updated.rovers shouldBe listOf(rover(1, 2, E))
        }

        test("deploying outside the board is rejected") {
            shouldThrow<PositionOutOfBoardException> { board().deployRover(rover(5, 0, N)) }
        }

        test("deploying onto another rover is rejected") {
            val occupied = board(rovers = listOf(rover(1, 1, N, "a")))
            shouldThrow<PositionAlreadyOccupiedException> { occupied.deployRover(rover(1, 1, S, "b")) }
        }

        test("deploying onto an obstacle is rejected") {
            val withObstacle = board(obstacles = setOf(at(1, 1)))
            shouldThrow<PositionAlreadyOccupiedException> { withObstacle.deployRover(rover(1, 1, N)) }
        }

        test("deploying a rover with an already used id is rejected") {
            val occupied = board(rovers = listOf(rover(1, 1, N, "a")))
            shouldThrow<DuplicateRoverIdException> { occupied.deployRover(rover(3, 3, N, "a")) }
        }
    }

    context("looking up a rover") {
        test("a deployed rover is found by its id") {
            board(rovers = listOf(rover(1, 1, N, "a"))).rover("a") shouldBeAt at(1, 1)
        }

        test("an unknown rover id is rejected") {
            shouldThrow<RoverNotFoundException> { board().rover("ghost") }
        }
    }

    context("moving a rover") {
        test("F moves the rover forward") {
            val moved = board(rovers = listOf(rover(2, 2, N))).moveRover("rover-1", Command.F)
            moved.rover("rover-1") shouldBeAt at(2, 3)
        }

        test("B moves the rover backward") {
            val moved = board(rovers = listOf(rover(2, 2, N))).moveRover("rover-1", Command.B)
            moved.rover("rover-1") shouldBeAt at(2, 1)
        }

        test("L turns the rover left") {
            val turned = board(rovers = listOf(rover(2, 2, N))).moveRover("rover-1", Command.L)
            turned.rover("rover-1") shouldFace W
        }

        test("R turns the rover right") {
            val turned = board(rovers = listOf(rover(2, 2, N))).moveRover("rover-1", Command.R)
            turned.rover("rover-1") shouldFace E
        }

        test("moving an unknown rover is rejected") {
            shouldThrow<RoverNotFoundException> { board().moveRover("ghost", Command.F) }
        }

        test("the north wall blocks the rover") {
            val blocked = board(rovers = listOf(rover(2, 4, N))).moveRover("rover-1", Command.F)
            blocked.rover("rover-1") shouldBeAt at(2, 4)
        }

        test("the east wall blocks the rover") {
            val blocked = board(rovers = listOf(rover(4, 2, E))).moveRover("rover-1", Command.F)
            blocked.rover("rover-1") shouldBeAt at(4, 2)
        }

        test("the south wall blocks the rover") {
            val blocked = board(rovers = listOf(rover(2, 0, S))).moveRover("rover-1", Command.F)
            blocked.rover("rover-1") shouldBeAt at(2, 0)
        }

        test("the west wall blocks the rover") {
            val blocked = board(rovers = listOf(rover(0, 2, W))).moveRover("rover-1", Command.F)
            blocked.rover("rover-1") shouldBeAt at(0, 2)
        }

        test("an obstacle blocks the rover") {
            val blocked = board(obstacles = setOf(at(2, 3)), rovers = listOf(rover(2, 2, N)))
                .moveRover("rover-1", Command.F)
            blocked.rover("rover-1") shouldBeAt at(2, 2)
        }

        test("another rover blocks the rover") {
            val blocked = board(rovers = listOf(rover(2, 2, N, "a"), rover(2, 3, S, "b")))
                .moveRover("a", Command.F)
            blocked.rover("a") shouldBeAt at(2, 2)
        }

        test("moving one rover leaves the others untouched") {
            val moved = board(rovers = listOf(rover(0, 0, N, "a"), rover(4, 4, S, "b")))
                .moveRover("a", Command.F)
            moved.rover("b") shouldBeAt at(4, 4)
            moved.rover("b") shouldFace S
        }
    }

    context("executing a sequence") {
        test("FFRFF from the origin facing N reaches (2,2) facing E") {
            val result = board(width = 10, height = 10, rovers = listOf(rover(0, 0, N)))
                .executeCommands("rover-1", Command.parse("FFRFF"))
            result.rover("rover-1") shouldBeAt at(2, 2)
            result.rover("rover-1") shouldFace E
        }

        test("an empty sequence leaves the board unchanged") {
            val initial = board(rovers = listOf(rover(2, 2, N)))
            initial.executeCommands("rover-1", emptyList()) shouldBe initial
        }

        test("a blocked rover can turn and leave in another direction") {
            val result = board(obstacles = setOf(at(2, 3)), rovers = listOf(rover(2, 2, N)))
                .executeCommands("rover-1", Command.parse("FRF"))
            result.rover("rover-1") shouldBeAt at(3, 2)
            result.rover("rover-1") shouldFace E
        }

        test("the sequence carries on after a blocked move") {
            val result = board(rovers = listOf(rover(2, 4, N)))
                .executeCommands("rover-1", Command.parse("FFB"))
            result.rover("rover-1") shouldBeAt at(2, 3)
        }
    }

    context("invariants") {
        val crowded = board(
            obstacles = setOf(at(2, 2), at(3, 1)),
            rovers = listOf(rover(0, 0, N, "a"), rover(4, 4, S, "b")),
        )

        test("any command sequence keeps every rover on a free cell of the board") {
            checkAll(anyCommands) { commands ->
                crowded.executeCommands("a", commands).shouldHoldItsInvariants()
            }
        }

        test("turning never changes the position of any rover") {
            checkAll(turnCommands) { commands ->
                val result = crowded.executeCommands("a", commands)
                result.rovers.map { it.position } shouldBe crowded.rovers.map { it.position }
            }
        }

        test("moving never changes the direction of any rover") {
            checkAll(moveCommands) { commands ->
                val result = crowded.executeCommands("a", commands)
                result.rovers.map { it.direction } shouldBe crowded.rovers.map { it.direction }
            }
        }

        test("LLLL leaves the whole board unchanged") {
            crowded.executeCommands("a", Command.parse("LLLL")) shouldBe crowded
        }

        test("executing two sequences equals executing their concatenation") {
            checkAll(anyCommands, anyCommands) { first, second ->
                crowded.executeCommands("a", first).executeCommands("a", second) shouldBe
                    crowded.executeCommands("a", first + second)
            }
        }
    }
})
