package com.jicay.rover.domain.model

import com.jicay.rover.domain.exception.InvalidCommandException

enum class Command {
    F, B, L, R;

    companion object {
        fun from(char: Char): Command =
            entries.firstOrNull { it.name.single() == char } ?: throw InvalidCommandException(char)

        fun parse(sequence: String): List<Command> = sequence.map { from(it) }
    }
}
