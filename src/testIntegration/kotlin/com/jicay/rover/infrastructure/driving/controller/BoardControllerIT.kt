package com.jicay.rover.infrastructure.driving.controller

import com.jicay.rover.domain.exception.BoardNotFoundException
import com.jicay.rover.domain.exception.DuplicateRoverIdException
import com.jicay.rover.domain.exception.InvalidBoardDimensionsException
import com.jicay.rover.domain.exception.InvalidCommandException
import com.jicay.rover.domain.exception.RoverNotFoundException
import com.jicay.rover.domain.model.Board
import com.jicay.rover.domain.model.Direction
import com.jicay.rover.domain.model.Position
import com.jicay.rover.domain.model.Rover
import com.jicay.rover.domain.usecase.CreateBoardUseCase
import com.jicay.rover.domain.usecase.DeployRoverUseCase
import com.jicay.rover.domain.usecase.ExecuteCommandsUseCase
import com.jicay.rover.domain.usecase.GetBoardUseCase
import com.ninjasquad.springmockk.MockkBean
import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.core.spec.style.FunSpec
import io.kotest.extensions.spring.SpringExtension
import io.mockk.every
import io.mockk.slot
import io.mockk.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.http.MediaType
import org.springframework.test.json.JsonCompareMode
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import java.util.UUID

/**
 * Tests d'integration de la couche driving : on valide le cablage Spring MVC
 * (routage, (de)serialisation JSON, codes HTTP), pas les regles metier.
 * Les use cases sont mockes : le domaine est deja couvert par les tests unitaires.
 */
@WebMvcTest(BoardController::class)
class BoardControllerIT : FunSpec() {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockkBean
    private lateinit var createBoardUseCase: CreateBoardUseCase

    @MockkBean
    private lateinit var deployRoverUseCase: DeployRoverUseCase

    @MockkBean
    private lateinit var executeCommandsUseCase: ExecuteCommandsUseCase

    @MockkBean
    private lateinit var getBoardUseCase: GetBoardUseCase

    init {
        // Branche le TestContext Spring sur le cycle de vie Kotest : injection des champs
        // @Autowired / @MockkBean, et remise a zero des mocks entre chaque test.
        extension(SpringExtension())

        context("POST /boards") {

            test("creates a board and returns 201 with its state") {
                val generatedId = slot<String>()
                every { createBoardUseCase.execute(capture(generatedId), any(), any(), any()) } returns
                    Board(id = "board-1", width = 5, height = 4, obstacles = setOf(Position(1, 1)))

                mockMvc.post("/boards") {
                    contentType = MediaType.APPLICATION_JSON
                    content = """{"width":5,"height":4,"obstacles":[{"x":1,"y":1}]}"""
                }.andExpect {
                    status { isCreated() }
                    content {
                        json(
                            """
                            {
                              "id": "board-1",
                              "width": 5,
                              "height": 4,
                              "obstacles": [{"x":1,"y":1}],
                              "rovers": []
                            }
                            """.trimIndent(),
                            JsonCompareMode.STRICT,
                        )
                    }
                }

                verify(exactly = 1) {
                    createBoardUseCase.execute(any(), 5, 4, setOf(Position(1, 1)))
                }
                // L'identifiant est genere par la couche driving, pas par le domaine.
                shouldNotThrowAny { UUID.fromString(generatedId.captured) }
            }

            test("defaults obstacles to an empty set when the field is absent") {
                every { createBoardUseCase.execute(any(), any(), any(), any()) } returns
                    Board(id = "board-1", width = 2, height = 2)

                mockMvc.post("/boards") {
                    contentType = MediaType.APPLICATION_JSON
                    content = """{"width":2,"height":2}"""
                }.andExpect {
                    status { isCreated() }
                }

                verify(exactly = 1) { createBoardUseCase.execute(any(), 2, 2, emptySet()) }
            }

            test("returns 400 when a mandatory field is missing") {
                mockMvc.post("/boards") {
                    contentType = MediaType.APPLICATION_JSON
                    content = """{"width":5}"""
                }.andExpect {
                    status { isBadRequest() }
                }

                verify(exactly = 0) { createBoardUseCase.execute(any(), any(), any(), any()) }
            }

            test("returns 400 when the payload is not valid JSON") {
                mockMvc.post("/boards") {
                    contentType = MediaType.APPLICATION_JSON
                    content = "{ not json"
                }.andExpect {
                    status { isBadRequest() }
                }
            }

            test("returns 400 when the domain rejects the dimensions") {
                every { createBoardUseCase.execute(any(), any(), any(), any()) } throws
                    InvalidBoardDimensionsException(0, 4)

                mockMvc.post("/boards") {
                    contentType = MediaType.APPLICATION_JSON
                    content = """{"width":0,"height":4}"""
                }.andExpect {
                    status { isBadRequest() }
                }
            }
        }

        context("POST /boards/{boardId}/rovers") {

            test("deploys a rover and returns 201 with the board state") {
                every { deployRoverUseCase.execute(any(), any()) } returns
                    Board(
                        id = "board-1",
                        width = 5,
                        height = 4,
                        rovers = listOf(Rover("rover-1", Position(2, 3), Direction.E)),
                    )

                mockMvc.post("/boards/board-1/rovers") {
                    contentType = MediaType.APPLICATION_JSON
                    content = """{"id":"rover-1","x":2,"y":3,"direction":"E"}"""
                }.andExpect {
                    status { isCreated() }
                    content {
                        json(
                            """
                            {
                              "id": "board-1",
                              "width": 5,
                              "height": 4,
                              "obstacles": [],
                              "rovers": [{"id":"rover-1","x":2,"y":3,"direction":"E"}]
                            }
                            """.trimIndent(),
                            JsonCompareMode.STRICT,
                        )
                    }
                }

                verify(exactly = 1) {
                    deployRoverUseCase.execute("board-1", Rover("rover-1", Position(2, 3), Direction.E))
                }
            }

            test("returns 400 when the direction is unknown") {
                mockMvc.post("/boards/board-1/rovers") {
                    contentType = MediaType.APPLICATION_JSON
                    content = """{"id":"rover-1","x":2,"y":3,"direction":"X"}"""
                }.andExpect {
                    status { isBadRequest() }
                }

                verify(exactly = 0) { deployRoverUseCase.execute(any(), any()) }
            }

            test("returns 409 when the rover id is already used") {
                every { deployRoverUseCase.execute(any(), any()) } throws DuplicateRoverIdException("rover-1")

                mockMvc.post("/boards/board-1/rovers") {
                    contentType = MediaType.APPLICATION_JSON
                    content = """{"id":"rover-1","x":2,"y":3,"direction":"E"}"""
                }.andExpect {
                    status { isConflict() }
                }
            }
        }

        context("POST /boards/{boardId}/rovers/{roverId}/commands") {

            test("returns 200 with the board state after the moves") {
                every { executeCommandsUseCase.execute(any(), any(), any()) } returns
                    Board(
                        id = "board-1",
                        width = 5,
                        height = 4,
                        rovers = listOf(Rover("rover-1", Position(2, 2), Direction.S)),
                    )

                mockMvc.post("/boards/board-1/rovers/rover-1/commands") {
                    contentType = MediaType.APPLICATION_JSON
                    content = """{"commands":"FFRFF"}"""
                }.andExpect {
                    status { isOk() }
                    content {
                        json(
                            """
                            {
                              "id": "board-1",
                              "width": 5,
                              "height": 4,
                              "obstacles": [],
                              "rovers": [{"id":"rover-1","x":2,"y":2,"direction":"S"}]
                            }
                            """.trimIndent(),
                            JsonCompareMode.STRICT,
                        )
                    }
                }

                verify(exactly = 1) { executeCommandsUseCase.execute("board-1", "rover-1", "FFRFF") }
            }

            test("returns 400 when the commands field is missing") {
                mockMvc.post("/boards/board-1/rovers/rover-1/commands") {
                    contentType = MediaType.APPLICATION_JSON
                    content = "{}"
                }.andExpect {
                    status { isBadRequest() }
                }

                verify(exactly = 0) { executeCommandsUseCase.execute(any(), any(), any()) }
            }

            test("returns 400 when the domain rejects a command") {
                every { executeCommandsUseCase.execute(any(), any(), any()) } throws InvalidCommandException('Z')

                mockMvc.post("/boards/board-1/rovers/rover-1/commands") {
                    contentType = MediaType.APPLICATION_JSON
                    content = """{"commands":"FZ"}"""
                }.andExpect {
                    status { isBadRequest() }
                }
            }

            test("returns 404 when the rover is unknown") {
                every { executeCommandsUseCase.execute(any(), any(), any()) } throws RoverNotFoundException("ghost")

                mockMvc.post("/boards/board-1/rovers/ghost/commands") {
                    contentType = MediaType.APPLICATION_JSON
                    content = """{"commands":"F"}"""
                }.andExpect {
                    status { isNotFound() }
                }
            }
        }

        context("GET /boards/{boardId}") {

            test("returns 200 with the full board state") {
                every { getBoardUseCase.execute("board-1") } returns
                    Board(
                        id = "board-1",
                        width = 5,
                        height = 4,
                        obstacles = setOf(Position(0, 0), Position(3, 3)),
                        rovers = listOf(
                            Rover("rover-1", Position(1, 1), Direction.N),
                            Rover("rover-2", Position(2, 2), Direction.W),
                        ),
                    )

                mockMvc.get("/boards/board-1").andExpect {
                    status { isOk() }
                    content {
                        json(
                            """
                            {
                              "id": "board-1",
                              "width": 5,
                              "height": 4,
                              "obstacles": [{"x":0,"y":0},{"x":3,"y":3}],
                              "rovers": [
                                {"id":"rover-1","x":1,"y":1,"direction":"N"},
                                {"id":"rover-2","x":2,"y":2,"direction":"W"}
                              ]
                            }
                            """.trimIndent(),
                            JsonCompareMode.STRICT,
                        )
                    }
                }

                verify(exactly = 1) { getBoardUseCase.execute("board-1") }
            }

            test("returns 404 when the board does not exist") {
                every { getBoardUseCase.execute("unknown") } throws BoardNotFoundException("unknown")

                mockMvc.get("/boards/unknown").andExpect {
                    status { isNotFound() }
                }
            }
        }
    }
}
