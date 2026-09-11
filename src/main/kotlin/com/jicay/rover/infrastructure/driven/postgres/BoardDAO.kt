package com.jicay.rover.infrastructure.driven.postgres

import com.jicay.rover.domain.model.Board
import com.jicay.rover.domain.model.Direction
import com.jicay.rover.domain.model.Position
import com.jicay.rover.domain.model.Rover
import com.jicay.rover.domain.port.BoardPort
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional

/**
 * Adapter driven : implementation Postgres du [BoardPort].
 *
 * Le SQL est ecrit a la main (pas de JPA, pas d'ORM) : c'est ici, et nulle part ailleurs,
 * que le modele relationnel rencontre l'agregat. Le domaine ignore tout de cette classe.
 */
@Repository
class BoardDAO(private val jdbcTemplate: NamedParameterJdbcTemplate) : BoardPort {

    /**
     * Ecrit l'agregat entier. L'operation est idempotente : la ligne `boards` est upsertee,
     * les rovers et les obstacles du plateau sont supprimes puis reinseres. Sauvegarder deux
     * fois le meme plateau laisse donc exactement les memes lignes en base.
     *
     * Le tout dans une seule transaction : a aucun moment un lecteur concurrent ne voit
     * un plateau ampute de ses rovers.
     */
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

    /**
     * Reconstruit l'agregat complet, ou `null` si le plateau n'existe pas.
     * Les rovers sont relus dans leur ordre de deploiement (cf. colonne `created_at`),
     * pour que l'aller-retour restitue une liste identique a celle sauvegardee.
     */
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
