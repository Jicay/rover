package com.jicay.rover.domain.model

import com.jicay.rover.domain.exception.InvalidCommandException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.enum
import io.kotest.property.arbitrary.list
import io.kotest.property.checkAll

class CommandTest : FunSpec({

    test("each command character maps to its command") {
        Command.from('F') shouldBe Command.F
        Command.from('B') shouldBe Command.B
        Command.from('L') shouldBe Command.L
        Command.from('R') shouldBe Command.R
    }

    test("an unknown character is rejected") {
        shouldThrow<InvalidCommandException> { Command.from('X') }
    }

    test("a lowercase character is rejected") {
        shouldThrow<InvalidCommandException> { Command.from('f') }
    }

    test("a sequence is parsed in order") {
        Command.parse("FBLR") shouldBe listOf(Command.F, Command.B, Command.L, Command.R)
    }

    test("an empty sequence parses to no command") {
        Command.parse("") shouldBe emptyList()
    }

    test("a sequence containing an unknown character is rejected") {
        shouldThrow<InvalidCommandException> { Command.parse("FFXR") }
    }

    test("parsing the rendering of any command list gives back that list") {
        checkAll(Arb.list(Arb.enum<Command>(), 0..20)) { commands ->
            Command.parse(commands.joinToString("") { it.name }) shouldBe commands
        }
    }
})
