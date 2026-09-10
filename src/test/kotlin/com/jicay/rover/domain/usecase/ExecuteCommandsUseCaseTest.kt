package com.jicay.rover.domain.usecase

import com.jicay.rover.domain.exception.BoardNotFoundException
import com.jicay.rover.domain.exception.InvalidCommandException
import com.jicay.rover.domain.exception.RoverNotFoundException
import com.jicay.rover.domain.model.Direction.E
import com.jicay.rover.domain.model.at
import com.jicay.rover.domain.model.board
import com.jicay.rover.domain.model.rover
import com.jicay.rover.domain.model.shouldBeAt
import com.jicay.rover.domain.model.shouldFace
import com.jicay.rover.domain.port.BoardPort
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.verify
import com.jicay.rover.domain.model.Direction.N

class ExecuteCommandsUseCaseTest : FunSpec({

    test("the sequence is applied and the board is saved once") {
        val boardPort = mockk<BoardPort>()
        every { boardPort.findById("board-1") } returns
            board(width = 10, height = 10, rovers = listOf(rover(0, 0, N)))
        justRun { boardPort.save(any()) }

        val result = ExecuteCommandsUseCase(boardPort).execute("board-1", "rover-1", "FFRFF")

        result.rover("rover-1") shouldBeAt at(2, 2)
        result.rover("rover-1") shouldFace E
        verify(exactly = 1) { boardPort.save(result) }
    }

    test("commands sent to an unknown board are rejected") {
        val boardPort = mockk<BoardPort>()
        every { boardPort.findById("unknown") } returns null

        shouldThrow<BoardNotFoundException> {
            ExecuteCommandsUseCase(boardPort).execute("unknown", "rover-1", "F")
        }

        verify(exactly = 0) { boardPort.save(any()) }
    }

    test("commands sent to an unknown rover are rejected") {
        val boardPort = mockk<BoardPort>()
        every { boardPort.findById("board-1") } returns board(rovers = listOf(rover(0, 0, N)))

        shouldThrow<RoverNotFoundException> {
            ExecuteCommandsUseCase(boardPort).execute("board-1", "ghost", "F")
        }

        verify(exactly = 0) { boardPort.save(any()) }
    }

    test("an invalid sequence is rejected and nothing is saved") {
        val boardPort = mockk<BoardPort>()
        every { boardPort.findById("board-1") } returns board(rovers = listOf(rover(0, 0, N)))

        shouldThrow<InvalidCommandException> {
            ExecuteCommandsUseCase(boardPort).execute("board-1", "rover-1", "FFXR")
        }

        verify(exactly = 0) { boardPort.save(any()) }
    }
})
