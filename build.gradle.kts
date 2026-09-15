import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("org.springframework.boot") version "4.0.6"
    id("io.spring.dependency-management") version "1.1.7"
    kotlin("jvm") version "2.3.20"
    kotlin("plugin.spring") version "2.3.20"
    id("info.solidsoft.pitest") version "1.19.0"
    jacoco
}

group = "com.jicay"
version = "0.0.1-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

repositories {
    mavenCentral()
}

extra["junit-jupiter.version"] = "5.14.2"
configurations.all {
    resolutionStrategy.eachDependency {
        if (requested.group == "org.junit.platform") {
            useVersion("1.14.2")
        }
    }
}

val kotestVersion = "6.1.11"

val cucumberVersion = "7.34.8"

val restAssuredVersion = "6.0.1"

val konsistVersion = "0.17.3"

dependencies {
    implementation("org.springframework.boot:spring-boot-starter")
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("tools.jackson.module:jackson-module-kotlin")

    implementation("org.springframework.boot:spring-boot-starter-jdbc")
    implementation("org.springframework.boot:spring-boot-starter-liquibase")
    implementation("org.liquibase:liquibase-core")
    runtimeOnly("org.postgresql:postgresql")

    testImplementation("io.mockk:mockk:1.14.9")
    testImplementation("io.kotest:kotest-assertions-core:$kotestVersion")
    testImplementation("io.kotest:kotest-property:$kotestVersion")
    testImplementation("io.kotest:kotest-runner-junit5:$kotestVersion")
}

testing {
    suites {
        register<JvmTestSuite>("testIntegration") {
            useJUnitJupiter("5.14.2")
            dependencies {
                implementation(project())
                implementation("org.springframework.boot:spring-boot-starter-webmvc-test") {
                    exclude(group = "org.mockito", module = "mockito-core")
                }
                implementation("io.kotest:kotest-runner-junit5:$kotestVersion")
                implementation("io.kotest:kotest-assertions-core:$kotestVersion")
                implementation("io.kotest:kotest-extensions-spring:$kotestVersion")
                implementation("com.ninja-squad:springmockk:5.0.1")
                implementation("org.testcontainers:testcontainers-postgresql")
                implementation("io.kotest:kotest-extensions-testcontainers:$kotestVersion")
            }
        }

        register<JvmTestSuite>("testComponent") {
            useJUnitJupiter("5.14.2")
            dependencies {
                implementation(project())
                implementation("org.springframework.boot:spring-boot-starter-test") {
                    exclude(group = "org.mockito")
                }
                implementation(platform("io.cucumber:cucumber-bom:$cucumberVersion"))
                implementation("io.cucumber:cucumber-java")
                implementation("io.cucumber:cucumber-spring")
                implementation("io.cucumber:cucumber-junit-platform-engine")
                implementation("org.junit.platform:junit-platform-suite")
                implementation("io.rest-assured:rest-assured:$restAssuredVersion")
                implementation("org.testcontainers:testcontainers-postgresql")
                implementation("io.kotest:kotest-assertions-core:$kotestVersion")
            }
        }

        register<JvmTestSuite>("testArchitecture") {
            useJUnitJupiter("5.14.2")
            dependencies {
                implementation(project())
                implementation("com.lemonappdev:konsist:$konsistVersion")
                implementation("io.kotest:kotest-runner-junit5:$kotestVersion")
                implementation("io.kotest:kotest-assertions-core:$kotestVersion")
            }
        }
    }
}

tasks.check {
    dependsOn(
        testing.suites.named("testIntegration"),
        testing.suites.named("testComponent"),
        testing.suites.named("testArchitecture"),
    )
}

configurations.named("testIntegrationImplementation") {
    extendsFrom(configurations.implementation.get())
}

configurations.named("testComponentImplementation") {
    extendsFrom(configurations.implementation.get())
}

configurations.named("testArchitectureImplementation") {
    extendsFrom(configurations.implementation.get())
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll("-Xjsr305=strict", "-Xannotation-default-target=param-property")
        jvmTarget.set(JvmTarget.JVM_25)
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}

tasks.test {
    finalizedBy(tasks.jacocoTestReport)
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports {
        xml.required = true
        html.required = true
    }
}

tasks.register<JacocoReport>("jacocoFullReport") {
    group = "verification"
    description = "Rapport JaCoCo agregeant les tests unitaires, d'integration et de composants"
    dependsOn(tasks.test, tasks.named("testIntegration"), tasks.named("testComponent"))
    executionData(
        fileTree(layout.buildDirectory.dir("jacoco")) {
            include("test.exec", "testIntegration.exec", "testComponent.exec")
        }
    )
    sourceSets(sourceSets.main.get())
    reports {
        xml.required = true
        html.required = true
    }
}

pitest {
    targetClasses.add("com.jicay.rover.*")
    junit5PluginVersion = "1.2.1"
    avoidCallsTo.set(setOf("kotlin.jvm.internal"))
    mutators.set(setOf("STRONGER"))
    threads.set(Runtime.getRuntime().availableProcessors())
    testSourceSets.addAll(sourceSets["test"])
    mainSourceSets.addAll(sourceSets["main"])
    outputFormats.addAll("XML", "HTML")
    excludedClasses.addAll(
        "**RoverApplication",
        "com.jicay.rover.application.*",
        "com.jicay.rover.infrastructure.*",
    )
}
