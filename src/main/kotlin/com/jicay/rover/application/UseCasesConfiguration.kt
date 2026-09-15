package com.jicay.rover.application

import com.jicay.rover.domain.port.BoardPort
import com.jicay.rover.domain.usecase.CreateBoardUseCase
import com.jicay.rover.domain.usecase.DeployRoverUseCase
import com.jicay.rover.domain.usecase.ExecuteCommandsUseCase
import com.jicay.rover.domain.usecase.GetBoardUseCase
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class UseCasesConfiguration {

    @Bean
    fun createBoardUseCase(boardPort: BoardPort) = CreateBoardUseCase(boardPort)

    @Bean
    fun deployRoverUseCase(boardPort: BoardPort) = DeployRoverUseCase(boardPort)

    @Bean
    fun executeCommandsUseCase(boardPort: BoardPort) = ExecuteCommandsUseCase(boardPort)

    @Bean
    fun getBoardUseCase(boardPort: BoardPort) = GetBoardUseCase(boardPort)
}
