package com.jicay.rover.domain.usecase

import com.jicay.rover.domain.model.BoardSummary
import com.jicay.rover.domain.port.BoardPort

class ListBoardsUseCase(private val boardPort: BoardPort) {

    fun execute(limit: Int): List<BoardSummary> =
        if (limit < 1) emptyList() else boardPort.findRecent(limit)
}
