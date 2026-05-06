package com.jicay.rover

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class RoverApplication

fun main(args: Array<String>) {
	runApplication<RoverApplication>(*args)
}
