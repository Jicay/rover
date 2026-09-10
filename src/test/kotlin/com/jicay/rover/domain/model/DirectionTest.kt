package com.jicay.rover.domain.model

import com.jicay.rover.domain.model.Direction.E
import com.jicay.rover.domain.model.Direction.N
import com.jicay.rover.domain.model.Direction.S
import com.jicay.rover.domain.model.Direction.W
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.enum
import io.kotest.property.checkAll

class DirectionTest : FunSpec({

    test("N moves along the positive y axis") {
        N.dx shouldBe 0
        N.dy shouldBe 1
    }

    test("E moves along the positive x axis") {
        E.dx shouldBe 1
        E.dy shouldBe 0
    }

    test("S moves along the negative y axis") {
        S.dx shouldBe 0
        S.dy shouldBe -1
    }

    test("W moves along the negative x axis") {
        W.dx shouldBe -1
        W.dy shouldBe 0
    }

    test("N turnLeft should be W") { N.turnLeft() shouldBe W }
    test("W turnLeft should be S") { W.turnLeft() shouldBe S }
    test("S turnLeft should be E") { S.turnLeft() shouldBe E }
    test("E turnLeft should be N") { E.turnLeft() shouldBe N }

    test("N turnRight should be E") { N.turnRight() shouldBe E }
    test("E turnRight should be S") { E.turnRight() shouldBe S }
    test("S turnRight should be W") { S.turnRight() shouldBe W }
    test("W turnRight should be N") { W.turnRight() shouldBe N }

    test("LLLL always returns to original direction") {
        checkAll(Arb.enum<Direction>()) { direction ->
            direction.turnLeft().turnLeft().turnLeft().turnLeft() shouldBe direction
        }
    }

    test("RRRR always returns to original direction") {
        checkAll(Arb.enum<Direction>()) { direction ->
            direction.turnRight().turnRight().turnRight().turnRight() shouldBe direction
        }
    }

    test("turnRight is the inverse of turnLeft") {
        checkAll(Arb.enum<Direction>()) { direction ->
            direction.turnLeft().turnRight() shouldBe direction
            direction.turnRight().turnLeft() shouldBe direction
        }
    }
})
