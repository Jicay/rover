package com.jicay.rover.domain.port

import com.jicay.rover.domain.model.Board

interface BoardPort {
    fun save(board: Board)
    fun findById(id: String): Board?
}
