dependencies {
    // add-ons
    implementation(project(":modules:jpa"))
    implementation(project(":modules:redis"))
    implementation(project(":modules:kafka"))
    implementation(project(":modules:ranking"))
    implementation(project(":supports:jackson"))
    implementation(project(":supports:logging"))
    implementation(project(":supports:monitoring"))

    // web
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-aop")
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:${project.properties["springDocOpenApiVersion"]}")
    implementation("org.springframework.security:spring-security-crypto")
    implementation("io.github.resilience4j:resilience4j-spring-boot3:${project.properties["resilience4jVersion"]}")

    // querydsl
    annotationProcessor("com.querydsl:querydsl-apt::jakarta")
    annotationProcessor("jakarta.persistence:jakarta.persistence-api")
    annotationProcessor("jakarta.annotation:jakarta.annotation-api")

    // test-fixtures
    testImplementation(testFixtures(project(":modules:jpa")))
    testImplementation(testFixtures(project(":modules:redis")))
}

val queueBenchmarkUsers = providers.gradleProperty("queueBenchmarkUsers").orElse("60")
val queueBenchmarkConcurrency = providers.gradleProperty("queueBenchmarkConcurrency").orElse("20")
val queueBenchmarkBatchSizes = providers.gradleProperty("queueBenchmarkBatchSizes").orElse("5,10,18")
val queueBenchmarkAdmitDelaysMs = providers.gradleProperty("queueBenchmarkAdmitDelaysMs").orElse("100")
val queueBenchmarkRuns = providers.gradleProperty("queueBenchmarkRuns").orElse("2")
val queueBenchmarkWarmupUsers = providers.gradleProperty("queueBenchmarkWarmupUsers").orElse("10")
val queueBenchmarkDbPoolSize = providers.gradleProperty("queueBenchmarkDbPoolSize").orElse("10")
val queueBenchmarkOutputDir = providers.gradleProperty("queueBenchmarkOutputDir").orElse(
    layout.buildDirectory.dir("reports/waiting-queue").map { it.asFile.absolutePath },
)
val queueBenchmarkLabel = providers.gradleProperty("queueBenchmarkLabel").orElse("local")

tasks.named<Test>("test") {
    useJUnitPlatform {
        excludeTags("benchmark")
    }
}

tasks.register<Test>("waitingQueueBenchmark") {
    group = "verification"
    description = "Runs the configurable waiting-queue capacity benchmark."

    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath
    maxParallelForks = 1
    useJUnitPlatform {
        includeTags("benchmark")
    }

    systemProperty("queueBenchmarkUsers", queueBenchmarkUsers.get())
    systemProperty("queueBenchmarkConcurrency", queueBenchmarkConcurrency.get())
    systemProperty("queueBenchmarkBatchSizes", queueBenchmarkBatchSizes.get())
    systemProperty("queueBenchmarkAdmitDelaysMs", queueBenchmarkAdmitDelaysMs.get())
    systemProperty("queueBenchmarkRuns", queueBenchmarkRuns.get())
    systemProperty("queueBenchmarkWarmupUsers", queueBenchmarkWarmupUsers.get())
    systemProperty("queueBenchmarkDbPoolSize", queueBenchmarkDbPoolSize.get())
    systemProperty("queueBenchmarkOutputDir", queueBenchmarkOutputDir.get())
    systemProperty("queueBenchmarkLabel", queueBenchmarkLabel.get())

    systemProperty("spring.profiles.active", "test")
    systemProperty("user.timezone", "Asia/Seoul")
    systemProperty("api.version", System.getProperty("api.version") ?: "1.40")
    systemProperty("spring.jpa.show-sql", "false")
    systemProperty("loopers.waiting-queue.scheduler-enabled", "false")
    systemProperty("loopers.outbox.relay-enabled", "false")
    systemProperty("loopers.payment.reconciliation-enabled", "false")
    systemProperty("management.server.port", "0")
    systemProperty("datasource.mysql-jpa.main.maximum-pool-size", queueBenchmarkDbPoolSize.get())
    jvmArgs("-Xshare:off")

    shouldRunAfter(tasks.named("test"))
    outputs.upToDateWhen { false }
}
