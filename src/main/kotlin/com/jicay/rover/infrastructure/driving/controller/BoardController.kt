package com.jicay.rover.infrastructure.driving.controller

import com.jicay.rover.domain.exception.BoardNotFoundException
import com.jicay.rover.domain.exception.DuplicateRoverIdException
import com.jicay.rover.domain.exception.InvalidBoardDimensionsException
import com.jicay.rover.domain.exception.InvalidCommandException
import com.jicay.rover.domain.exception.PositionAlreadyOccupiedException
import com.jicay.rover.domain.exception.PositionOutOfBoardException
import com.jicay.rover.domain.exception.RoverDomainException
import com.jicay.rover.domain.exception.RoverNotFoundException
import com.jicay.rover.domain.model.Board
import com.jicay.rover.domain.usecase.CreateBoardUseCase
import com.jicay.rover.domain.usecase.DeployRoverUseCase
import com.jicay.rover.domain.usecase.ExecuteCommandsUseCase
import com.jicay.rover.domain.usecase.GetBoardUseCase
import com.jicay.rover.infrastructure.driving.dto.BoardResponse
import com.jicay.rover.infrastructure.driving.dto.CreateBoardRequest
import com.jicay.rover.infrastructure.driving.dto.DeployRoverRequest
import com.jicay.rover.infrastructure.driving.dto.ErrorResponse
import com.jicay.rover.infrastructure.driving.dto.ExecuteCommandsRequest
import com.jicay.rover.infrastructure.driving.dto.PositionDto
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/boards")
class BoardController(
    private val createBoardUseCase: CreateBoardUseCase,
    private val deployRoverUseCase: DeployRoverUseCase,
    private val executeCommandsUseCase: ExecuteCommandsUseCase,
    private val getBoardUseCase: GetBoardUseCase,
) {

    @PostMapping
    fun createBoard(@RequestBody request: CreateBoardRequest): ResponseEntity<Any> =
        respond(HttpStatus.CREATED) {
            createBoardUseCase.execute(
                id = UUID.randomUUID().toString(),
                width = request.width,
                height = request.height,
                obstacles = request.obstacles.map(PositionDto::toDomain).toSet(),
            )
        }

    @PostMapping("/{boardId}/rovers")
    fun deployRover(
        @PathVariable boardId: String,
        @RequestBody request: DeployRoverRequest,
    ): ResponseEntity<Any> =
        respond(HttpStatus.CREATED) {
            deployRoverUseCase.execute(boardId = boardId, rover = request.toDomain())
        }

    @PostMapping("/{boardId}/rovers/{roverId}/commands")
    fun executeCommands(
        @PathVariable boardId: String,
        @PathVariable roverId: String,
        @RequestBody request: ExecuteCommandsRequest,
    ): ResponseEntity<Any> =
        respond(HttpStatus.OK) {
            executeCommandsUseCase.execute(boardId = boardId, roverId = roverId, commands = request.commands)
        }

    @GetMapping("/{boardId}")
    fun getBoard(@PathVariable boardId: String): ResponseEntity<Any> =
        respond(HttpStatus.OK) { getBoardUseCase.execute(boardId) }

    private fun respond(successStatus: HttpStatus, action: () -> Board): ResponseEntity<Any> =
        try {
            ResponseEntity.status(successStatus).body(BoardResponse.from(action()))
        } catch (exception: RoverDomainException) {
            ResponseEntity.status(statusOf(exception)).body(ErrorResponse(exception.message.orEmpty()))
        }

    private fun statusOf(exception: RoverDomainException): HttpStatus = when (exception) {
        is InvalidBoardDimensionsException,
        is InvalidCommandException,
        is PositionOutOfBoardException,
            -> HttpStatus.BAD_REQUEST

        is PositionAlreadyOccupiedException,
        is DuplicateRoverIdException,
            -> HttpStatus.CONFLICT

        is BoardNotFoundException,
        is RoverNotFoundException,
            -> HttpStatus.NOT_FOUND
    }
}
