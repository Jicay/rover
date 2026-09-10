package com.jicay.rover.domain.exception

import com.jicay.rover.domain.model.Position

sealed class RoverDomainException(message: String) : RuntimeException(message)

class InvalidBoardDimensionsException(width: Int, height: Int) :
    RoverDomainException("Board dimensions must be at least 1x1, got ${width}x$height")

class InvalidCommandException(command: Char) :
    RoverDomainException("Unknown command '$command', expected one of F, B, L, R")

class PositionOutOfBoardException(position: Position) :
    RoverDomainException("Position (${position.x}, ${position.y}) is outside the board")

class PositionAlreadyOccupiedException(position: Position) :
    RoverDomainException("Position (${position.x}, ${position.y}) is already occupied")

class DuplicateRoverIdException(roverId: String) :
    RoverDomainException("A rover with id '$roverId' is already deployed on this board")

class BoardNotFoundException(boardId: String) :
    RoverDomainException("No board found with id '$boardId'")

class RoverNotFoundException(roverId: String) :
    RoverDomainException("No rover found with id '$roverId'")
