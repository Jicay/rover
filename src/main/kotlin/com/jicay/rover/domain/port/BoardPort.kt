package com.jicay.rover.domain.port

import com.jicay.rover.domain.model.Board
import com.jicay.rover.domain.model.BoardSummary

interface BoardPort {
    fun save(board: Board)
    fun findById(id: String): Board?
    fun findRecent(limit: Int): List<BoardSummary>
}
