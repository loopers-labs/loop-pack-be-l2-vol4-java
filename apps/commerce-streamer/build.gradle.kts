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
    implementation("org.springframework.boot:spring-boot-starter-actuator")

    // querydsl
    annotationProcessor("com.querydsl:querydsl-apt::jakarta")
    annotationProcessor("jakarta.persistence:jakarta.persistence-api")
    annotationProcessor("jakarta.annotation:jakarta.annotation-api")

    // test-fixtures
    testImplementation(testFixtures(project(":modules:jpa")))
    testImplementation(testFixtures(project(":modules:redis")))
    testImplementation(testFixtures(project(":modules:kafka")))
}

val rankingBenchmarkEvents = providers.gradleProperty("rankingBenchmarkEvents").orElse("10000")
val rankingBenchmarkBatchSizes = providers.gradleProperty("rankingBenchmarkBatchSizes").orElse("1,100,1000,3000")
val rankingBenchmarkHotCardinality = providers.gradleProperty("rankingBenchmarkHotCardinality").orElse("100")
val rankingBenchmarkUniformCardinality = providers.gradleProperty("rankingBenchmarkUniformCardinality").orElse(rankingBenchmarkEvents)
val rankingBenchmarkRuns = providers.gradleProperty("rankingBenchmarkRuns").orElse("2")
val rankingBenchmarkWarmupEvents = providers.gradleProperty("rankingBenchmarkWarmupEvents").orElse("1000")
val rankingBenchmarkRetryBatchSizes = providers.gradleProperty("rankingBenchmarkRetryBatchSizes").orElse("50,500,3000")
val rankingBenchmarkFailurePoints = providers.gradleProperty("rankingBenchmarkFailurePoints").orElse("0,25,75")
val rankingBenchmarkOutputDir = providers.gradleProperty("rankingBenchmarkOutputDir").orElse(
    layout.buildDirectory.dir("reports/ranking-streamer").map { it.asFile.absolutePath },
)
val rankingBenchmarkLabel = providers.gradleProperty("rankingBenchmarkLabel").orElse("local")

tasks.named<Test>("test") {
    useJUnitPlatform {
        excludeTags("benchmark")
    }
}

tasks.register<Test>("rankingBenchmark") {
    group = "verification"
    description = "Runs ranking aggregation throughput and retry-drift benchmarks."

    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath
    maxParallelForks = 1
    useJUnitPlatform {
        includeTags("benchmark")
    }

    systemProperty("rankingBenchmarkEvents", rankingBenchmarkEvents.get())
    systemProperty("rankingBenchmarkBatchSizes", rankingBenchmarkBatchSizes.get())
    systemProperty("rankingBenchmarkHotCardinality", rankingBenchmarkHotCardinality.get())
    systemProperty("rankingBenchmarkUniformCardinality", rankingBenchmarkUniformCardinality.get())
    systemProperty("rankingBenchmarkRuns", rankingBenchmarkRuns.get())
    systemProperty("rankingBenchmarkWarmupEvents", rankingBenchmarkWarmupEvents.get())
    systemProperty("rankingBenchmarkRetryBatchSizes", rankingBenchmarkRetryBatchSizes.get())
    systemProperty("rankingBenchmarkFailurePoints", rankingBenchmarkFailurePoints.get())
    systemProperty("rankingBenchmarkOutputDir", rankingBenchmarkOutputDir.get())
    systemProperty("rankingBenchmarkLabel", rankingBenchmarkLabel.get())
    systemProperty("spring.profiles.active", "test")
    systemProperty("api.version", System.getProperty("api.version") ?: "1.40")
    systemProperty("spring.jpa.show-sql", "false")
    systemProperty("spring.kafka.listener.auto-startup", "false")
    systemProperty("user.timezone", "Asia/Seoul")
    systemProperty("management.server.port", "0")
    jvmArgs("-Xshare:off")

    shouldRunAfter(tasks.named("test"))
    outputs.upToDateWhen { false }
}
