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

class RoverTest : FunSpec({

    test("forward target facing N is one cell up") {
        rover(2, 2, facing = N).forwardTarget() shouldBe at(2, 3)
    }

    test("forward target facing S is one cell down") {
        rover(2, 2, facing = S).forwardTarget() shouldBe at(2, 1)
    }

    test("forward target facing E is one cell right") {
        rover(2, 2, facing = E).forwardTarget() shouldBe at(3, 2)
    }

    test("forward target facing W is one cell left") {
        rover(2, 2, facing = W).forwardTarget() shouldBe at(1, 2)
    }

    test("backward target facing N is one cell down") {
        rover(2, 2, facing = N).backwardTarget() shouldBe at(2, 1)
    }

    test("backward target facing S is one cell up") {
        rover(2, 2, facing = S).backwardTarget() shouldBe at(2, 3)
    }

    test("backward target facing E is one cell left") {
        rover(2, 2, facing = E).backwardTarget() shouldBe at(1, 2)
    }

    test("backward target facing W is one cell right") {
        rover(2, 2, facing = W).backwardTarget() shouldBe at(3, 2)
    }

    test("turning left changes the direction but not the position") {
        val turned = rover(2, 2, facing = N).turnLeft()
        turned shouldFace W
        turned shouldBeAt at(2, 2)
    }

    test("turning right changes the direction but not the position") {
        val turned = rover(2, 2, facing = N).turnRight()
        turned shouldFace E
        turned shouldBeAt at(2, 2)
    }

    test("moving changes the position but not the direction") {
        val moved = rover(2, 2, facing = N).movedTo(at(4, 1))
        moved shouldBeAt at(4, 1)
        moved shouldFace N
    }

    test("forward and backward targets are always opposite") {
        checkAll(Arb.enum<Direction>()) { direction ->
            val subject = rover(5, 5, facing = direction)
            subject.forwardTarget().translate(-direction.dx, -direction.dy) shouldBe subject.position
            subject.backwardTarget().translate(direction.dx, direction.dy) shouldBe subject.position
        }
    }
})
