package com.jicay.rover.domain.usecase

import com.jicay.rover.domain.model.BoardSummary
import com.jicay.rover.domain.port.BoardPort
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify

class ListBoardsUseCaseTest : FunSpec({

    test("the most recent boards are returned as the port gives them") {
        val boardPort = mockk<BoardPort>()
        val expected = listOf(
            BoardSummary(id = "board-2", width = 5, height = 4, roverCount = 2, obstacleCount = 1),
            BoardSummary(id = "board-1", width = 3, height = 3, roverCount = 0, obstacleCount = 0),
        )
        every { boardPort.findRecent(12) } returns expected

        ListBoardsUseCase(boardPort).execute(12) shouldBe expected
    }

    test("a limit below one asks the port for nothing") {
        val boardPort = mockk<BoardPort>()

        ListBoardsUseCase(boardPort).execute(0) shouldBe emptyList()

        verify(exactly = 0) { boardPort.findRecent(any()) }
    }
})
