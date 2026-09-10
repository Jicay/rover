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
extra["junit-jupiter.version"] = "5.14.2"
configurations.all {
    resolutionStrategy.eachDependency {
        if (requested.group == "org.junit.platform") {
            useVersion("1.14.2")
        }
    }
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter")
    implementation("org.jetbrains.kotlin:kotlin-reflect")

    testImplementation("io.mockk:mockk:1.14.9")
    testImplementation("io.kotest:kotest-assertions-core:6.1.11")
    testImplementation("io.kotest:kotest-property:6.1.11")
    testImplementation("io.kotest:kotest-runner-junit5:6.1.11")
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

pitest {
    targetClasses.add("com.jicay.rover.*")
    junit5PluginVersion = "1.2.1"
    avoidCallsTo.set(setOf("kotlin.jvm.internal"))
    mutators.set(setOf("STRONGER"))
    threads.set(Runtime.getRuntime().availableProcessors())
    testSourceSets.addAll(sourceSets["test"])
    mainSourceSets.addAll(sourceSets["main"])
    outputFormats.addAll("XML", "HTML")
    excludedClasses.add("**RoverApplication")
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