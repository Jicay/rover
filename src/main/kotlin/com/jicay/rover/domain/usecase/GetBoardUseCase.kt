package com.jicay.rover.domain.usecase

import com.jicay.rover.domain.exception.BoardNotFoundException
import com.jicay.rover.domain.model.Board
import com.jicay.rover.domain.port.BoardPort

class GetBoardUseCase(private val boardPort: BoardPort) {

    fun execute(boardId: String): Board =
        boardPort.findById(boardId) ?: throw BoardNotFoundException(boardId)
}
