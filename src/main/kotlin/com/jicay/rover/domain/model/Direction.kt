package com.jicay.rover.domain.model

enum class Direction(val dx: Int, val dy: Int) {
    N(0, 1),
    E(1, 0),
    S(0, -1),
    W(-1, 0);

    fun turnLeft(): Direction = when (this) {
        N -> W
        W -> S
        S -> E
        E -> N
    }

    fun turnRight(): Direction = when (this) {
        N -> E
        E -> S
        S -> W
        W -> N
    }
}
