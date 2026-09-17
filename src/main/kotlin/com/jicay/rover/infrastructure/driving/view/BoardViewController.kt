package com.jicay.rover.infrastructure.driving.view

import com.jicay.rover.domain.exception.RoverDomainException
import com.jicay.rover.domain.model.Position
import com.jicay.rover.domain.model.Rover
import com.jicay.rover.domain.usecase.CreateBoardUseCase
import com.jicay.rover.domain.usecase.DeployRoverUseCase
import com.jicay.rover.domain.usecase.ExecuteCommandsUseCase
import com.jicay.rover.domain.usecase.GetBoardUseCase
import com.jicay.rover.domain.usecase.ListBoardsUseCase
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.ModelAttribute
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.servlet.ModelAndView
import org.springframework.web.servlet.mvc.support.RedirectAttributes
import java.util.UUID

@Controller
class BoardViewController(
    private val createBoardUseCase: CreateBoardUseCase,
    private val deployRoverUseCase: DeployRoverUseCase,
    private val executeCommandsUseCase: ExecuteCommandsUseCase,
    private val getBoardUseCase: GetBoardUseCase,
    private val listBoardsUseCase: ListBoardsUseCase,
) {

    @GetMapping("/")
    fun index(): ModelAndView = ModelAndView(
        "index",
        "boards",
        listBoardsUseCase.execute(RECENT_BOARDS).map(RecentBoardView::from),
    )

    @GetMapping("/ui/boards/{boardId}")
    fun showBoard(
        @PathVariable boardId: String,
        model: Model,
        redirectAttributes: RedirectAttributes,
    ): String = orElseRedirect("redirect:/", redirectAttributes) {
        model.addAttribute("board", BoardView.from(getBoardUseCase.execute(boardId)))
        "board"
    }

    @PostMapping("/ui/boards")
    fun createBoard(
        @ModelAttribute form: CreateBoardForm,
        redirectAttributes: RedirectAttributes,
    ): String = orElseRedirect("redirect:/", redirectAttributes) {
        val board = createBoardUseCase.execute(
            id = UUID.randomUUID().toString(),
            width = form.width,
            height = form.height,
            obstacles = parseObstacles(form.obstacles),
        )
        "redirect:/ui/boards/${board.id}"
    }

    @PostMapping("/ui/boards/{boardId}/rovers")
    fun deployRover(
        @PathVariable boardId: String,
        @ModelAttribute form: DeployRoverForm,
        redirectAttributes: RedirectAttributes,
    ): String = orElseRedirect("redirect:/ui/boards/$boardId", redirectAttributes) {
        deployRoverUseCase.execute(
            boardId = boardId,
            rover = Rover(
                id = form.id,
                position = Position(x = form.x, y = form.y),
                direction = form.direction,
            ),
        )
        "redirect:/ui/boards/$boardId"
    }

    @PostMapping("/ui/boards/{boardId}/commands")
    fun sendCommands(
        @PathVariable boardId: String,
        @ModelAttribute form: SendCommandsForm,
        redirectAttributes: RedirectAttributes,
    ): String = orElseRedirect("redirect:/ui/boards/$boardId", redirectAttributes) {
        executeCommandsUseCase.execute(boardId = boardId, roverId = form.roverId, commands = form.commands)
        "redirect:/ui/boards/$boardId"
    }

    private fun orElseRedirect(
        fallback: String,
        redirectAttributes: RedirectAttributes,
        action: () -> String,
    ): String = try {
        action()
    } catch (exception: RoverDomainException) {
        redirectAttributes.addFlashAttribute("error", exception.message.orEmpty())
        fallback
    }

    private fun parseObstacles(input: String): Set<Position> =
        OBSTACLE_PATTERN.findAll(input)
            .map { Position(x = it.groupValues[1].toInt(), y = it.groupValues[2].toInt()) }
            .toSet()

    private companion object {
        const val RECENT_BOARDS = 12

        val OBSTACLE_PATTERN = Regex("""(-?\d+)\s*,\s*(-?\d+)""")
    }
}
