package com.jicay.rover.domain.model

data class Position(val x: Int, val y: Int) {
    fun translate(dx: Int, dy: Int) = Position(x + dx, y + dy)
}
