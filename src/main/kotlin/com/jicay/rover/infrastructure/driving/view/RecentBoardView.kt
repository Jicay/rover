package com.jicay.rover.infrastructure.driving.view

import com.jicay.rover.domain.model.BoardSummary

data class RecentBoardView(
    val id: String,
    val size: String,
    val rovers: String,
    val obstacles: String,
) {
    companion object {
        fun from(summary: BoardSummary) = RecentBoardView(
            id = summary.id,
            size = "${summary.width} × ${summary.height}",
            rovers = plural(summary.roverCount, "rover"),
            obstacles = plural(summary.obstacleCount, "rocher"),
        )

        private fun plural(count: Int, noun: String) = if (count > 1) "$count ${noun}s" else "$count $noun"
    }
}
