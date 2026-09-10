package com.jicay.rover.domain.usecase

import com.jicay.rover.domain.model.Board
import com.jicay.rover.domain.model.Position
import com.jicay.rover.domain.port.BoardPort

class CreateBoardUseCase(private val boardPort: BoardPort) {

    fun execute(id: String, width: Int, height: Int, obstacles: Set<Position> = emptySet()): Board {
        val board = Board(id = id, width = width, height = height, obstacles = obstacles)
        boardPort.save(board)
        return board
    }
}
