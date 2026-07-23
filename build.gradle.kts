import org.gradle.api.Project.DEFAULT_VERSION
import org.springframework.boot.gradle.tasks.bundling.BootJar

/** --- configuration functions --- */
fun getGitHash(): String {
    return runCatching {
        providers.exec {
            commandLine("git", "rev-parse", "--short", "HEAD")
        }.standardOutput.asText.get().trim()
    }.getOrElse { "init" }
}

/** --- project configurations --- */
plugins {
    java
    id("org.springframework.boot") apply false
    id("io.spring.dependency-management")
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

allprojects {
    val projectGroup: String by project
    group = projectGroup
    version = if (version == DEFAULT_VERSION) getGitHash() else version

    repositories {
        mavenCentral()
    }
}

subprojects {
    apply(plugin = "java")
    apply(plugin = "org.springframework.boot")
    apply(plugin = "io.spring.dependency-management")
    apply(plugin = "jacoco")

    dependencyManagement {
        imports {
            mavenBom("org.springframework.cloud:spring-cloud-dependencies:${project.properties["springCloudDependenciesVersion"]}")
        }
    }

    dependencies {
        // Web
        runtimeOnly("org.springframework.boot:spring-boot-starter-validation")
        // Spring
        implementation("org.springframework.boot:spring-boot-starter")
        // Serialize
        implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")
        // Lombok
        implementation("org.projectlombok:lombok")
        annotationProcessor("org.projectlombok:lombok")
        // Test
        testRuntimeOnly("org.junit.platform:junit-platform-launcher")
        // testcontainers:mysql 이 jdbc 사용함
        testRuntimeOnly("com.mysql:mysql-connector-j")
        testImplementation("org.springframework.boot:spring-boot-starter-test")
        testImplementation("com.ninja-squad:springmockk:${project.properties["springMockkVersion"]}")
        testImplementation("org.mockito:mockito-core:${project.properties["mockitoVersion"]}")
        testImplementation("org.instancio:instancio-junit:${project.properties["instancioJUnitVersion"]}")
        // Testcontainers
        testImplementation("org.springframework.boot:spring-boot-testcontainers")
        testImplementation("org.testcontainers:testcontainers")
        testImplementation("org.testcontainers:junit-jupiter")
    }

    tasks.withType(Jar::class) { enabled = true }
    tasks.withType(BootJar::class) { enabled = false }

    configure(allprojects.filter { it.parent?.name.equals("apps") }) {
        tasks.withType(Jar::class) { enabled = false }
        tasks.withType(BootJar::class) { enabled = true }
    }

    tasks.test {
        maxParallelForks = 1
        useJUnitPlatform()
        systemProperty("user.timezone", "Asia/Seoul")
        systemProperty("spring.profiles.active", "test")
        // Testcontainers 1.20.x defaults to Docker API 1.32, but Docker 29 requires 1.40+.
        systemProperty("api.version", System.getProperty("api.version") ?: "1.40")
        jvmArgs("-Xshare:off")
    }

    tasks.withType<JacocoReport> {
        mustRunAfter("test")
        executionData(fileTree(layout.buildDirectory.asFile).include("jacoco/*.exec"))
        reports {
            xml.required = true
            csv.required = false
            html.required = false
        }
        afterEvaluate {
            classDirectories.setFrom(
                files(
                    classDirectories.files.map {
                        fileTree(it) {
                            exclude("**/Q*.class")
                        }
                    },
                ),
            )
        }
    }
}

// module-container 는 task 를 실행하지 않도록 한다.
project("apps") { tasks.configureEach { enabled = false } }
project("modules") { tasks.configureEach { enabled = false } }
project("supports") { tasks.configureEach { enabled = false } }

val rankingE2eJavaExecutable = providers.environmentVariable("E2E_JAVA").orElse(
    providers.provider {
        val candidates = listOf(
            "/opt/homebrew/opt/openjdk@21/bin/java",
            "/usr/local/opt/openjdk@21/bin/java",
            "${System.getProperty("java.home")}/bin/java",
        )
        candidates.firstOrNull { file(it).canExecute() }
            ?: error("Java executable not found. Set E2E_JAVA to a Java 21 executable.")
    },
)
val commerceApiBootJar = project(":apps:commerce-api").tasks.named<BootJar>("bootJar")
val commerceStreamerBootJar = project(":apps:commerce-streamer").tasks.named<BootJar>("bootJar")

tasks.register<Exec>("rankingKafkaE2E") {
    group = "verification"
    description = "Runs the separate-process Outbox -> Kafka -> Redis -> Ranking API system E2E."

    dependsOn(commerceApiBootJar, commerceStreamerBootJar)
    commandLine("bash", layout.projectDirectory.file("scripts/e2e/ranking-kafka-e2e.sh").asFile.absolutePath)

    doFirst {
        environment("E2E_JAVA", rankingE2eJavaExecutable.get())
        environment("E2E_API_JAR", commerceApiBootJar.get().archiveFile.get().asFile.absolutePath)
        environment("E2E_STREAMER_JAR", commerceStreamerBootJar.get().archiveFile.get().asFile.absolutePath)
    }

    outputs.upToDateWhen { false }
}
