package com.jicay.rover.domain.usecase

import com.jicay.rover.domain.exception.BoardNotFoundException
import com.jicay.rover.domain.exception.PositionAlreadyOccupiedException
import com.jicay.rover.domain.model.Direction.N
import com.jicay.rover.domain.model.board
import com.jicay.rover.domain.model.rover
import com.jicay.rover.domain.port.BoardPort
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.verify

class DeployRoverUseCaseTest : FunSpec({

    test("the rover is added to the board and the board is saved") {
        val boardPort = mockk<BoardPort>()
        every { boardPort.findById("board-1") } returns board()
        justRun { boardPort.save(any()) }

        val result = DeployRoverUseCase(boardPort).execute("board-1", rover(1, 2, N))

        result.rovers shouldBe listOf(rover(1, 2, N))
        verify(exactly = 1) { boardPort.save(result) }
    }

    test("deploying on an unknown board is rejected") {
        val boardPort = mockk<BoardPort>()
        every { boardPort.findById("unknown") } returns null

        shouldThrow<BoardNotFoundException> {
            DeployRoverUseCase(boardPort).execute("unknown", rover(1, 2, N))
        }

        verify(exactly = 0) { boardPort.save(any()) }
    }

    test("a rejected deployment is not saved") {
        val boardPort = mockk<BoardPort>()
        every { boardPort.findById("board-1") } returns board(rovers = listOf(rover(1, 1, N, "a")))

        shouldThrow<PositionAlreadyOccupiedException> {
            DeployRoverUseCase(boardPort).execute("board-1", rover(1, 1, N, "b"))
        }

        verify(exactly = 0) { boardPort.save(any()) }
    }
})
