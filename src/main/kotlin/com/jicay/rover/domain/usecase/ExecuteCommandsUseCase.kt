package com.jicay.rover.domain.usecase

import com.jicay.rover.domain.exception.BoardNotFoundException
import com.jicay.rover.domain.model.Board
import com.jicay.rover.domain.model.Command
import com.jicay.rover.domain.port.BoardPort

class ExecuteCommandsUseCase(private val boardPort: BoardPort) {

    fun execute(boardId: String, roverId: String, commands: String): Board {
        val board = boardPort.findById(boardId) ?: throw BoardNotFoundException(boardId)
        val updated = board.executeCommands(roverId, Command.parse(commands))
        boardPort.save(updated)
        return updated
    }
}
