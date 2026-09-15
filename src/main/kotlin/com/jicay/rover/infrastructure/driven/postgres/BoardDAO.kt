package com.jicay.rover.infrastructure.driven.postgres

import com.jicay.rover.domain.model.Board
import com.jicay.rover.domain.model.Direction
import com.jicay.rover.domain.model.Position
import com.jicay.rover.domain.model.Rover
import com.jicay.rover.domain.port.BoardPort
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional

@Repository
class BoardDAO(private val jdbcTemplate: NamedParameterJdbcTemplate) : BoardPort {

    @Transactional
    override fun save(board: Board) {
        jdbcTemplate.update(
            """
            INSERT INTO boards (id, width, height)
            VALUES (:id, :width, :height)
            ON CONFLICT (id) DO UPDATE SET width = EXCLUDED.width, height = EXCLUDED.height
            """.trimIndent(),
            mapOf("id" to board.id, "width" to board.width, "height" to board.height),
        )

        val boardIdParam = mapOf("boardId" to board.id)
        jdbcTemplate.update("DELETE FROM rovers WHERE board_id = :boardId", boardIdParam)
        jdbcTemplate.update("DELETE FROM obstacles WHERE board_id = :boardId", boardIdParam)

        board.obstacles.forEach { obstacle ->
            jdbcTemplate.update(
                "INSERT INTO obstacles (board_id, x, y) VALUES (:boardId, :x, :y)",
                mapOf("boardId" to board.id, "x" to obstacle.x, "y" to obstacle.y),
            )
        }

        board.rovers.forEach { rover ->
            jdbcTemplate.update(
                """
                INSERT INTO rovers (board_id, rover_id, x, y, direction)
                VALUES (:boardId, :roverId, :x, :y, :direction)
                """.trimIndent(),
                mapOf(
                    "boardId" to board.id,
                    "roverId" to rover.id,
                    "x" to rover.position.x,
                    "y" to rover.position.y,
                    "direction" to rover.direction.name,
                ),
            )
        }
    }

    @Transactional(readOnly = true)
    override fun findById(id: String): Board? {
        val idParam = mapOf("id" to id)

        val dimensions = jdbcTemplate.query(
            "SELECT width, height FROM boards WHERE id = :id",
            idParam,
        ) { rs, _ -> rs.getInt("width") to rs.getInt("height") }.firstOrNull() ?: return null

        val obstacles = jdbcTemplate.query(
            "SELECT x, y FROM obstacles WHERE board_id = :id",
            idParam,
        ) { rs, _ -> Position(rs.getInt("x"), rs.getInt("y")) }.toSet()

        val rovers = jdbcTemplate.query(
            """
            SELECT rover_id, x, y, direction
            FROM rovers
            WHERE board_id = :id
            ORDER BY created_at, rover_id
            """.trimIndent(),
            idParam,
        ) { rs, _ ->
            Rover(
                id = rs.getString("rover_id"),
                position = Position(rs.getInt("x"), rs.getInt("y")),
                direction = Direction.valueOf(rs.getString("direction")),
            )
        }

        val (width, height) = dimensions
        return Board(id = id, width = width, height = height, obstacles = obstacles, rovers = rovers)
    }
}
