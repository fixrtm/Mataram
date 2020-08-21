plugins {
    kotlin("jvm") version "1.4.0"
    idea
}

sourceSets {
    main {
        java {
            srcDirs("src/main/generated")

        }
    }
}

val generator by sourceSets.creating
val generatorImplementation by configurations.getting(Configuration::class)

group = "org.example"
version = "1.0-SNAPSHOT"

repositories {
    jcenter()
}

idea {
    module {
        inheritOutputDirs = true
        generatedSourceDirs.add(projectDir.resolve("src/main/generated"))
    }
}

val kotestVersion: String = "4.1.3"

dependencies {
    implementation(kotlin("stdlib"))
    implementation("org.jetbrains.kotlinx:kotlinx-collections-immutable-jvm:0.3.2")

    implementation("org.ow2.asm:asm:8.0.1")
    implementation("org.ow2.asm:asm-commons:8.0.1")
    // TODO: use latest when kotest is upgraded
    implementation("com.github.ajalt:clikt:2.6.0")

    testImplementation("io.kotest:kotest-runner-junit5-jvm:$kotestVersion")
    testRuntimeOnly("io.kotest:kotest-runner-console-jvm:$kotestVersion")
    testImplementation("io.kotest:kotest-assertions-core-jvm:$kotestVersion")
    testImplementation("io.kotest:kotest-property-jvm:$kotestVersion")

    testImplementation("io.mockk:mockk:1.10.0")

    generatorImplementation(kotlin("stdlib"))
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    kotlinOptions {
        jvmTarget = "1.8"
        languageVersion = "1.4"
        freeCompilerArgs += "-Xopt-in=kotlin.RequiresOptIn"
        freeCompilerArgs += "-Xmulti-platform"
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
    }
}

val generateClasses by tasks.creating(JavaExec::class) {
    outputs.dir("src/main/generated")
    inputs.dir("templates")
    classpath = generator.runtimeClasspath
    main = "com.anatawa12.decompiler.generator.MainKt"
    args = listOf(
        "processesFile",
        "templates/generateProcesses.txt"
    )
}

tasks["compileKotlin"].dependsOn(generateClasses)
