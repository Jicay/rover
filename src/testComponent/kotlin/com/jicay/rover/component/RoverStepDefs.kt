package com.jicay.rover.component

import io.cucumber.java.ParameterType
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.restassured.RestAssured
import io.restassured.http.ContentType
import io.restassured.path.json.JsonPath
import io.restassured.response.Response
import io.restassured.specification.RequestSpecification
import org.springframework.boot.test.web.server.LocalServerPort

class RoverStepDefs {

    @LocalServerPort
    private var port: Int = 0

    private lateinit var boardId: String
    private lateinit var lastResponse: Response

    @ParameterType("North|East|South|West")
    fun orientation(label: String): String = label.first().toString()

    @Given("a board of {int} cells by {int}")
    fun aBoard(width: Int, height: Int) = createBoard(width, height, emptyList())

    @Given("a board of {int} cells by {int} with a rock at \\({int}, {int}\\)")
    fun aBoardWithARock(width: Int, height: Int, x: Int, y: Int) =
        createBoard(width, height, listOf(x to y))

    @Given("a rover {string} landed at \\({int}, {int}\\) facing {orientation}")
    fun aRoverLandedAt(roverId: String, x: Int, y: Int, direction: String) {
        deploy(roverId, x, y, direction).statusCode shouldBe 201
    }

    @When("I send the sequence {string} to rover {string}")
    fun iSendTheSequence(commands: String, roverId: String) {
        lastResponse = request()
            .body("""{"commands":"$commands"}""")
            .post("/boards/$boardId/rovers/$roverId/commands")
        lastResponse.statusCode shouldBe 200
    }

    @When("I try to land rover {string} at \\({int}, {int}\\) facing {orientation}")
    fun iTryToLandRover(roverId: String, x: Int, y: Int, direction: String) {
        lastResponse = deploy(roverId, x, y, direction)
    }

    @Then("rover {string} is at \\({int}, {int}\\) facing {orientation}")
    fun roverIsAt(roverId: String, x: Int, y: Int, direction: String) {
        val rover = readBoard().getList<Map<String, Any>>("rovers").single { it["id"] == roverId }
        rover["x"] shouldBe x
        rover["y"] shouldBe y
        rover["direction"] shouldBe direction
    }

    @Then("the deployment is rejected because the cell is already taken")
    fun theDeploymentIsRejected() {
        lastResponse.statusCode shouldBe 409
    }

    @Then("the board holds only one rover")
    fun theBoardHoldsOnlyOneRover() {
        readBoard().getList<Any>("rovers") shouldHaveSize 1
    }

    private fun createBoard(width: Int, height: Int, obstacles: List<Pair<Int, Int>>) {
        val obstaclesJson = obstacles.joinToString(",") { (x, y) -> """{"x":$x,"y":$y}""" }
        val response = request()
            .body("""{"width":$width,"height":$height,"obstacles":[$obstaclesJson]}""")
            .post("/boards")
        response.statusCode shouldBe 201
        boardId = response.jsonPath().getString("id")
    }

    private fun deploy(roverId: String, x: Int, y: Int, direction: String): Response =
        request()
            .body("""{"id":"$roverId","x":$x,"y":$y,"direction":"$direction"}""")
            .post("/boards/$boardId/rovers")

    private fun readBoard(): JsonPath {
        val response = request().get("/boards/$boardId")
        response.statusCode shouldBe 200
        return response.jsonPath()
    }

    private fun request(): RequestSpecification =
        RestAssured.given().port(port).contentType(ContentType.JSON)
}
