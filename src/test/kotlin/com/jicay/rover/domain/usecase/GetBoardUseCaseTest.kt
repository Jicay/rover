package com.jicay.rover.domain.usecase

import com.jicay.rover.domain.exception.BoardNotFoundException
import com.jicay.rover.domain.model.Direction.N
import com.jicay.rover.domain.model.board
import com.jicay.rover.domain.model.rover
import com.jicay.rover.domain.port.BoardPort
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk

class GetBoardUseCaseTest : FunSpec({

    test("an existing board is returned with its rovers and obstacles") {
        val boardPort = mockk<BoardPort>()
        val expected = board(rovers = listOf(rover(3, 2, N)))
        every { boardPort.findById("board-1") } returns expected

        GetBoardUseCase(boardPort).execute("board-1") shouldBe expected
    }

    test("an unknown board id is rejected") {
        val boardPort = mockk<BoardPort>()
        every { boardPort.findById("unknown") } returns null

        shouldThrow<BoardNotFoundException> { GetBoardUseCase(boardPort).execute("unknown") }
    }
})
