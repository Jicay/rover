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

// Kotest 6 and Cucumber 7.34.3 require JUnit Platform 1.14.x / Jupiter 5.14.x.
// Spring Boot BOM may pull junit-platform-launcher at a lower version; align all platform jars.
// Le BOM Spring Boot 4 pousse JUnit Jupiter 6 : on le ramene sur 5.14.2 partout, sinon
// le moteur Kotest (JUnit Platform 1.x) et Jupiter 6 se retrouvent dans le meme classpath.
extra["junit-jupiter.version"] = "5.14.2"
configurations.all {
    resolutionStrategy.eachDependency {
        if (requested.group == "org.junit.platform") {
            useVersion("1.14.2")
        }
    }
}

val kotestVersion = "6.1.11"

// Cucumber 7.34.x est compile contre JUnit 5.14.2 / JUnit Platform 1.14.2, exactement les
// versions epinglees ci-dessus : c'est lui qui s'aligne sur Kotest, pas l'inverse.
val cucumberVersion = "7.34.8"

// RestAssured n'est PAS gere par le BOM Spring Boot 4 (il l'etait en Boot 3) : version explicite.
val restAssuredVersion = "6.0.1"

dependencies {
    implementation("org.springframework.boot:spring-boot-starter")
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    // Jackson 3 (Spring Boot 4) : module Kotlin necessaire pour (de)serialiser les data classes.
    implementation("tools.jackson.module:jackson-module-kotlin")

    // Persistance : JdbcTemplate + HikariCP, migrations Liquibase, driver Postgres.
    implementation("org.springframework.boot:spring-boot-starter-jdbc")
    // Spring Boot 4 a eclate les auto-configurations en modules : liquibase-core seul
    // n'apporte plus LiquibaseAutoConfiguration, il faut le starter (qui l'embarque).
    implementation("org.springframework.boot:spring-boot-starter-liquibase")
    implementation("org.liquibase:liquibase-core")
    runtimeOnly("org.postgresql:postgresql")

    testImplementation("io.mockk:mockk:1.14.9")
    testImplementation("io.kotest:kotest-assertions-core:$kotestVersion")
    testImplementation("io.kotest:kotest-property:$kotestVersion")
    testImplementation("io.kotest:kotest-runner-junit5:$kotestVersion")
}

// Les tests d'integration vivent dans leur propre source set (src/testIntegration/kotlin).
testing {
    suites {
        register<JvmTestSuite>("testIntegration") {
            useJUnitJupiter("5.14.2")
            dependencies {
                // Donne acces aux classes de src/main (le extendsFrom ci-dessous donne ses dependances).
                implementation(project())
                // Spring Boot 4 : la tranche @WebMvcTest vit dans son propre starter,
                // qui embarque spring-boot-starter-test. Mockito est exclu, on mocke avec MockK.
                implementation("org.springframework.boot:spring-boot-starter-webmvc-test") {
                    exclude(group = "org.mockito", module = "mockito-core")
                }
                implementation("io.kotest:kotest-runner-junit5:$kotestVersion")
                implementation("io.kotest:kotest-assertions-core:$kotestVersion")
                // Publiee sous io.kotest et versionnee comme Kotest depuis la 6
                // (l'ancienne io.kotest.extensions:kotest-extensions-spring s'arrete a 1.3.0).
                implementation("io.kotest:kotest-extensions-spring:$kotestVersion")
                implementation("com.ninja-squad:springmockk:5.0.1")
                // Testcontainers 2.x : les modules ont ete renommes (org.testcontainers:postgresql
                // -> org.testcontainers:testcontainers-postgresql). La version vient du BOM
                // Spring Boot 4 (2.0.5), inutile de la figer ici.
                implementation("org.testcontainers:testcontainers-postgresql")
                // Meme deplacement que kotest-extensions-spring : publiee sous io.kotest et
                // versionnee comme Kotest depuis la 6. L'ancienne coordonnee
                // io.kotest.extensions:kotest-extensions-testcontainers s'arrete a 2.0.2.
                implementation("io.kotest:kotest-extensions-testcontainers:$kotestVersion")
            }
        }

        // Les tests de composants vivent eux aussi dans leur propre source set
        // (src/testComponent/kotlin) : ils demarrent l'application entiere et ne mockent rien.
        register<JvmTestSuite>("testComponent") {
            useJUnitJupiter("5.14.2")
            dependencies {
                implementation(project())
                // Apporte spring-boot-test (@SpringBootTest, @LocalServerPort) et spring-test
                // (@DynamicPropertySource). Mockito est exclu : un test de composant ne mocke rien.
                implementation("org.springframework.boot:spring-boot-starter-test") {
                    exclude(group = "org.mockito")
                }
                // Le BOM Cucumber aligne cucumber-java, cucumber-spring et le moteur JUnit Platform.
                implementation(platform("io.cucumber:cucumber-bom:$cucumberVersion"))
                implementation("io.cucumber:cucumber-java")
                implementation("io.cucumber:cucumber-spring")
                // Le moteur JUnit Platform remplace l'ancien io.cucumber:cucumber-junit (JUnit 4),
                // que le support de cours cite encore : inutile ici, la suite tourne sur JUnit 5.
                implementation("io.cucumber:cucumber-junit-platform-engine")
                // @Suite / @IncludeEngines. Version imposee a 1.14.2 par le resolutionStrategy.
                implementation("org.junit.platform:junit-platform-suite")
                implementation("io.rest-assured:rest-assured:$restAssuredVersion")
                // Version heritee du BOM Spring Boot 4 (2.0.5), comme pour testIntegration.
                implementation("org.testcontainers:testcontainers-postgresql")
                implementation("io.kotest:kotest-assertions-core:$kotestVersion")
            }
        }
    }
}

// Sans ca, `./gradlew build` ne compilerait meme pas src/testIntegration et src/testComponent.
tasks.check {
    dependsOn(testing.suites.named("testIntegration"), testing.suites.named("testComponent"))
}

configurations.named("testIntegrationImplementation") {
    extendsFrom(configurations.implementation.get())
}

configurations.named("testComponentImplementation") {
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

// Couverture agregee : tests unitaires + tests d'integration + tests de composants.
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
    // Les mutations ne visent que le domaine : la couche driving est couverte par les
    // tests d'integration, qui ne tournent pas sous PITest.
    excludedClasses.addAll(
        "**RoverApplication",
        "com.jicay.rover.application.*",
        "com.jicay.rover.infrastructure.*",
    )
}

/*val pitestClasspath: Configuration by configurations.creating

dependencies {
	pitestClasspath("org.pitest:pitest:1.19.0")
	pitestClasspath("org.pitest:pitest-entry:1.19.0")
	pitestClasspath("org.pitest:pitest-command-line:1.19.0")
	pitestClasspath("org.pitest:pitest-junit5-plugin:1.2.1")
	pitestClasspath("io.kotest:kotest-runner-junit5:5.9.1")
}

tasks.register<JavaExec>("pitest") {
	group = "verification"
	description = "Run mutation tests with PITest"
	dependsOn(tasks.testClasses)
	mainClass = "org.pitest.mutationtest.commandline.MutationCoverageReport"
	classpath = pitestClasspath

	val outputDir = layout.buildDirectory.dir("reports/pitest").get().asFile

	doFirst {
		outputDir.mkdirs()
		val cpArgs = mutableListOf<String>()
		(sourceSets["main"].output.classesDirs + sourceSets["test"].output.classesDirs +
			sourceSets["main"].runtimeClasspath + sourceSets["test"].runtimeClasspath).forEach { f ->
			cpArgs += listOf("--classPath", f.absolutePath)
		}
		args = listOf(
			"--reportDir", outputDir.absolutePath,
			"--targetClasses", "com.jicay.rover.domain.*",
			"--excludedClasses", "*Test,*Test$*",
			"--targetTests", "com.jicay.rover.*",
			"--sourceDirs", "src/main/kotlin",
			"--outputFormats", "HTML",
			"--mutationThreshold", "80"
		) + cpArgs
	}
}
*/