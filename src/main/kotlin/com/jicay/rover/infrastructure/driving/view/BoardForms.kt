package com.jicay.rover.infrastructure.driving.view

import com.jicay.rover.domain.model.Direction

data class CreateBoardForm(val width: Int, val height: Int, val obstacles: String)

data class DeployRoverForm(val id: String, val x: Int, val y: Int, val direction: Direction)

data class SendCommandsForm(val roverId: String, val commands: String)
