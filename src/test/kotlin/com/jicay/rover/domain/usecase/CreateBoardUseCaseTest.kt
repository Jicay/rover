package com.jicay.rover.domain.usecase

import com.jicay.rover.domain.exception.InvalidBoardDimensionsException
import com.jicay.rover.domain.model.Board
import com.jicay.rover.domain.model.at
import com.jicay.rover.domain.port.BoardPort
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.verify

class CreateBoardUseCaseTest : FunSpec({

    test("a created board is returned and saved") {
        val boardPort = mockk<BoardPort>()
        justRun { boardPort.save(any()) }

        val result = CreateBoardUseCase(boardPort).execute("board-1", 5, 4)

        result shouldBe Board(id = "board-1", width = 5, height = 4)
        verify(exactly = 1) { boardPort.save(result) }
    }

    test("a board is created with its obstacles") {
        val boardPort = mockk<BoardPort>()
        justRun { boardPort.save(any()) }

        val result = CreateBoardUseCase(boardPort).execute("board-1", 5, 5, setOf(at(1, 1)))

        result.obstacles shouldBe setOf(at(1, 1))
    }

    test("an invalid board is not saved") {
        val boardPort = mockk<BoardPort>()

        shouldThrow<InvalidBoardDimensionsException> {
            CreateBoardUseCase(boardPort).execute("board-1", 0, 5)
        }

        verify(exactly = 0) { boardPort.save(any()) }
    }
})
