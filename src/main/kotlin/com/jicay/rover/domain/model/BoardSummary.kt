package com.jicay.rover.domain.model

data class BoardSummary(
    val id: String,
    val width: Int,
    val height: Int,
    val roverCount: Int,
    val obstacleCount: Int,
)
