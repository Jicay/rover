package com.jicay.rover.infrastructure.driven.postgres

import com.jicay.rover.domain.model.Board
import com.jicay.rover.domain.model.BoardSummary
import com.jicay.rover.domain.model.Direction
import com.jicay.rover.domain.model.Position
import com.jicay.rover.domain.model.Rover
import io.kotest.core.extensions.install
import io.kotest.core.spec.style.FunSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.extensions.testcontainers.TestContainerProjectExtension
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.postgresql.PostgreSQLContainer

@SpringBootTest
@ActiveProfiles("integration-test")
class BoardDAOIT : FunSpec() {

    @Autowired
    private lateinit var boardDAO: BoardDAO

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    init {
        install(TestContainerProjectExtension(postgres))
        extension(SpringExtension())

        beforeTest {
            jdbcTemplate.execute("TRUNCATE TABLE obstacles, rovers, boards RESTART IDENTITY CASCADE")
        }

        test("saves a board with its obstacles and rovers, then reads it back identical") {
            val board = Board(
                id = "board-1",
                width = 5,
                height = 4,
                obstacles = setOf(Position(1, 1), Position(3, 2)),
                rovers = listOf(
                    Rover("rover-1", Position(0, 0), Direction.N),
                    Rover("rover-2", Position(2, 3), Direction.E),
                ),
            )

            boardDAO.save(board)

            boardDAO.findById("board-1") shouldBe board

            countOf("boards") shouldBe 1
            countOf("obstacles") shouldBe 2
            countOf("rovers") shouldBe 2
            rowsOf("SELECT width, height FROM boards WHERE id = 'board-1'") shouldBe listOf(listOf(5, 4))
            rowsOf("SELECT x, y FROM obstacles WHERE board_id = 'board-1' ORDER BY x, y") shouldBe
                listOf(listOf(1, 1), listOf(3, 2))
            rowsOf(
                "SELECT rover_id, x, y, direction FROM rovers WHERE board_id = 'board-1' ORDER BY rover_id",
            ) shouldBe listOf(
                listOf("rover-1", 0, 0, "N"),
                listOf("rover-2", 2, 3, "E"),
            )
        }

        test("returns null when the board does not exist") {
            boardDAO.save(Board(id = "board-1", width = 2, height = 2))

            boardDAO.findById("unknown").shouldBeNull()

            countOf("boards") shouldBe 1
        }

        test("saving the same board twice does not duplicate any row") {
            val board = Board(
                id = "board-1",
                width = 5,
                height = 4,
                obstacles = setOf(Position(1, 1), Position(3, 2)),
                rovers = listOf(
                    Rover("rover-1", Position(0, 0), Direction.N),
                    Rover("rover-2", Position(2, 3), Direction.E),
                ),
            )

            boardDAO.save(board)
            boardDAO.save(board)

            boardDAO.findById("board-1") shouldBe board

            countOf("boards") shouldBe 1
            countOf("obstacles") shouldBe 2
            countOf("rovers") shouldBe 2
        }

        test("replaces the previous state when the board is saved again after a move") {
            val board = Board(
                id = "board-1",
                width = 5,
                height = 4,
                obstacles = setOf(Position(1, 1)),
                rovers = listOf(Rover("rover-1", Position(0, 0), Direction.N)),
            )
            boardDAO.save(board)

            val moved = board.copy(rovers = listOf(Rover("rover-1", Position(0, 1), Direction.E)))
            boardDAO.save(moved)

            boardDAO.findById("board-1") shouldBe moved

            countOf("rovers") shouldBe 1
            rowsOf("SELECT x, y, direction FROM rovers WHERE board_id = 'board-1'") shouldBe
                listOf(listOf(0, 1, "E"))
        }

        test("round-trips a board without any rover nor obstacle") {
            val board = Board(id = "board-1", width = 3, height = 3)

            boardDAO.save(board)

            boardDAO.findById("board-1") shouldBe board

            countOf("boards") shouldBe 1
            countOf("obstacles") shouldBe 0
            countOf("rovers") shouldBe 0
        }

        test("round-trips the four directions") {
            val board = Board(
                id = "board-1",
                width = 4,
                height = 1,
                rovers = listOf(
                    Rover("rover-N", Position(0, 0), Direction.N),
                    Rover("rover-E", Position(1, 0), Direction.E),
                    Rover("rover-S", Position(2, 0), Direction.S),
                    Rover("rover-W", Position(3, 0), Direction.W),
                ),
            )

            boardDAO.save(board)

            boardDAO.findById("board-1") shouldBe board

            rowsOf("SELECT direction FROM rovers WHERE board_id = 'board-1' ORDER BY x") shouldBe
                listOf(listOf("N"), listOf("E"), listOf("S"), listOf("W"))
        }

        test("recent boards come back newest first, counted, without loading the aggregates") {
            boardDAO.save(Board(id = "board-1", width = 3, height = 3))
            boardDAO.save(
                Board(
                    id = "board-2",
                    width = 5,
                    height = 4,
                    obstacles = setOf(Position(1, 1), Position(2, 2)),
                    rovers = listOf(Rover("rover-1", Position(0, 0), Direction.N)),
                ),
            )
            boardDAO.save(Board(id = "board-3", width = 2, height = 2))

            boardDAO.findRecent(10) shouldBe listOf(
                BoardSummary(id = "board-3", width = 2, height = 2, roverCount = 0, obstacleCount = 0),
                BoardSummary(id = "board-2", width = 5, height = 4, roverCount = 1, obstacleCount = 2),
                BoardSummary(id = "board-1", width = 3, height = 3, roverCount = 0, obstacleCount = 0),
            )
        }

        test("the limit caps the listing to the newest boards") {
            boardDAO.save(Board(id = "board-1", width = 3, height = 3))
            boardDAO.save(Board(id = "board-2", width = 4, height = 4))
            boardDAO.save(Board(id = "board-3", width = 5, height = 5))

            boardDAO.findRecent(2).map { it.id } shouldBe listOf("board-3", "board-2")
        }

        test("saving a board twice keeps its original place in the listing") {
            boardDAO.save(Board(id = "board-1", width = 3, height = 3))
            boardDAO.save(Board(id = "board-2", width = 4, height = 4))

            boardDAO.save(Board(id = "board-1", width = 3, height = 3, obstacles = setOf(Position(0, 0))))

            boardDAO.findRecent(10).map { it.id } shouldBe listOf("board-2", "board-1")
        }
    }

    private fun countOf(table: String): Int =
        jdbcTemplate.queryForObject("SELECT count(*) FROM $table", Int::class.java)!!

    private fun rowsOf(sql: String): List<List<Any?>> =
        jdbcTemplate.query(sql) { rs, _ ->
            (1..rs.metaData.columnCount).map { rs.getObject(it) }
        }

    companion object {
        private val postgres = PostgreSQLContainer("postgres:18-alpine")

        @JvmStatic
        @DynamicPropertySource
        fun datasourceProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url") { postgres.jdbcUrl }
            registry.add("spring.datasource.username") { postgres.username }
            registry.add("spring.datasource.password") { postgres.password }
        }
    }
}
