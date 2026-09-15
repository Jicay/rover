package com.jicay.rover.architecture

import com.lemonappdev.konsist.api.Konsist
import com.lemonappdev.konsist.api.architecture.KoArchitectureCreator.assertArchitecture
import com.lemonappdev.konsist.api.architecture.Layer
import com.lemonappdev.konsist.api.ext.list.withPackage
import com.lemonappdev.konsist.api.ext.list.withParentInterfaceNamed
import com.lemonappdev.konsist.api.ext.list.withoutPackage
import com.lemonappdev.konsist.api.verify.assertEmpty
import com.lemonappdev.konsist.api.verify.assertFalse
import com.lemonappdev.konsist.api.verify.assertNotEmpty
import com.lemonappdev.konsist.api.verify.assertTrue
import io.kotest.core.spec.style.FunSpec

class HexagonalArchitectureTest : FunSpec({

    val production = Konsist.scopeFromProduction()

    val bannedInDomain = listOf("org.springframework", "jakarta", "tools.jackson", "io.cucumber")

    val allowedInDomain = listOf("com.jicay.rover.domain", "java", "kotlin", "kotlinx", "org.jetbrains.annotations")

    test("the scope actually sees the production sources, so no rule can pass vacuously") {
        listOf(
            "com.jicay.rover.domain.usecase",
            "com.jicay.rover.application",
            "com.jicay.rover.infrastructure.driving.controller",
            "com.jicay.rover.infrastructure.driven.postgres",
        ).forEach { packageName ->
            production.classes().withPackage(packageName)
                .assertNotEmpty(testName = "le scope contient $packageName")
        }
    }

    test("the domain stays plain Kotlin, so it can be tested without any framework") {
        production
            .files
            .withPackage("..domain..")
            .assertFalse(
                testName = "le domaine ne depend d'aucun framework",
                additionalMessage = "le domaine se teste en millisecondes sans contexte Spring, " +
                    "sans serialisation et sans Cucumber : le jour ou l'un de ces frameworks y " +
                    "entre, on ne peut plus en changer sans reecrire les regles metier",
            ) { file ->
                file.imports.any { import -> bannedInDomain.any { import.name.startsWith("$it.") } }
            }
    }

    test("the domain imports nothing but itself and the standard library") {
        production
            .files
            .withPackage("..domain..")
            .assertTrue(
                testName = "le domaine n'importe que lui-meme et la bibliotheque standard",
                additionalMessage = "un import qui n'est ni du domaine ni de la bibliotheque " +
                    "standard est une dependance de plus a porter dans le coeur metier",
            ) { file ->
                file.imports.all { import -> allowedInDomain.any { import.name.startsWith("$it.") } }
            }
    }

    test("each layer only talks to the layers the hexagon allows it to talk to") {
        production.assertArchitecture(
            testName = "chaque couche ne parle qu'aux couches que l'hexagone autorise",
        ) {
            val domain = Layer("Domain", "com.jicay.rover.domain..")
            val application = Layer("Application", "com.jicay.rover.application..")
            val driving = Layer("Driving", "com.jicay.rover.infrastructure.driving..")
            val driven = Layer("Driven", "com.jicay.rover.infrastructure.driven..")

            domain.dependsOnNothing()

            application.dependsOn(domain)
            application.doesNotDependOn(driving, driven)

            driving.dependsOn(domain)
            driving.doesNotDependOn(driven)

            driven.dependsOn(domain)
            driven.doesNotDependOn(driving)
        }
    }

    test("ports stay interfaces, so the domain never knows which adapter answers") {
        production.interfaces().withPackage("..domain.port..")
            .assertNotEmpty(testName = "le package des ports contient au moins une interface")
        production
            .classes()
            .withPackage("..domain.port..")
            .assertEmpty(
                testName = "les ports sont des interfaces",
                additionalMessage = "un port est un contrat : si c'etait une classe concrete, le " +
                    "domaine dependrait d'une implementation et les use cases ne seraient plus mockables",
            )
    }

    test("only the driving side handles DTOs, so the wire format never leaks inward") {
        production
            .files
            .withoutPackage("..infrastructure.driving..")
            .assertFalse(
                testName = "seul le driving manipule les DTO",
                additionalMessage = "les DTO decrivent le contrat HTTP, pas le metier : les laisser " +
                    "filer vers le domaine ou vers le driven ferait dependre le coeur de la forme du JSON",
            ) { file ->
                file.hasImport { it.name.startsWith("com.jicay.rover.infrastructure.driving.dto.") }
            }
    }

    test("every port implementation lives on the driven side, where the outside world belongs") {
        val portNames = production.interfaces().withPackage("..domain.port..").map { it.name }

        production
            .classes()
            .withParentInterfaceNamed(portNames)
            .assertTrue(
                testName = "les implementations de port vivent dans le driven",
                additionalMessage = "une implementation de port parle a un systeme externe (ici " +
                    "Postgres) : sa place est dans l'adapter driven, jamais dans le domaine ni " +
                    "dans le controller",
            ) { it.resideInPackage("..infrastructure.driven..") }
    }
})
