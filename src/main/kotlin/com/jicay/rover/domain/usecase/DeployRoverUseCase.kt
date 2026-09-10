package com.jicay.rover.domain.usecase

import com.jicay.rover.domain.exception.BoardNotFoundException
import com.jicay.rover.domain.model.Board
import com.jicay.rover.domain.model.Rover
import com.jicay.rover.domain.port.BoardPort

class DeployRoverUseCase(private val boardPort: BoardPort) {

    fun execute(boardId: String, rover: Rover): Board {
        val board = boardPort.findById(boardId) ?: throw BoardNotFoundException(boardId)
        val updated = board.deployRover(rover)
        boardPort.save(updated)
        return updated
    }
}
