dependencies {
    // add-ons
    implementation(project(":modules:jpa"))
    implementation(project(":modules:redis"))
    implementation(project(":modules:ranking"))
    implementation(project(":supports:jackson"))
    implementation(project(":supports:logging"))
    implementation(project(":supports:monitoring"))

    // batch
    implementation("org.springframework.boot:spring-boot-starter-batch")
    testImplementation("org.springframework.batch:spring-batch-test")

    // querydsl
    annotationProcessor("com.querydsl:querydsl-apt::jakarta")
    annotationProcessor("jakarta.persistence:jakarta.persistence-api")
    annotationProcessor("jakarta.annotation:jakarta.annotation-api")

    // test-fixtures
    testImplementation(testFixtures(project(":modules:jpa")))
    testImplementation(testFixtures(project(":modules:redis")))
}

val rankingBatchBenchmarkCardinalities = providers.gradleProperty("rankingBatchBenchmarkCardinalities").orElse("100,10000,100000")
val rankingBatchBenchmarkActiveHours = providers.gradleProperty("rankingBatchBenchmarkActiveHours").orElse("1")
val rankingBatchBenchmarkChunkSizes = providers.gradleProperty("rankingBatchBenchmarkChunkSizes").orElse("100,1000,3000")
val rankingBatchBenchmarkRuns = providers.gradleProperty("rankingBatchBenchmarkRuns").orElse("5")
val rankingBatchBenchmarkWarmup = providers.gradleProperty("rankingBatchBenchmarkWarmup").orElse("1")
val rankingBatchBenchmarkFreshnessIntervals = providers.gradleProperty("rankingBatchBenchmarkFreshnessIntervals").orElse("1,10,60")
val rankingBatchBenchmarkOutputDir = providers.gradleProperty("rankingBatchBenchmarkOutputDir").orElse(
    layout.buildDirectory.dir("reports/ranking-batch").map { it.asFile.absolutePath },
)
val rankingBatchBenchmarkLabel = providers.gradleProperty("rankingBatchBenchmarkLabel").orElse("local")

tasks.named<Test>("test") {
    useJUnitPlatform {
        excludeTags("benchmark")
    }
}

tasks.register<Test>("rankingBatchBenchmark") {
    group = "verification"
    description = "Runs the daily ranking snapshot throughput, recovery, and freshness benchmarks."

    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath
    maxParallelForks = 1
    useJUnitPlatform {
        includeTags("benchmark")
    }

    systemProperty("rankingBatchBenchmarkCardinalities", rankingBatchBenchmarkCardinalities.get())
    systemProperty("rankingBatchBenchmarkActiveHours", rankingBatchBenchmarkActiveHours.get())
    systemProperty("rankingBatchBenchmarkChunkSizes", rankingBatchBenchmarkChunkSizes.get())
    systemProperty("rankingBatchBenchmarkRuns", rankingBatchBenchmarkRuns.get())
    systemProperty("rankingBatchBenchmarkWarmup", rankingBatchBenchmarkWarmup.get())
    systemProperty("rankingBatchBenchmarkFreshnessIntervals", rankingBatchBenchmarkFreshnessIntervals.get())
    systemProperty("rankingBatchBenchmarkOutputDir", rankingBatchBenchmarkOutputDir.get())
    systemProperty("rankingBatchBenchmarkLabel", rankingBatchBenchmarkLabel.get())
    systemProperty("spring.profiles.active", "test")
    systemProperty("api.version", System.getProperty("api.version") ?: "1.40")
    systemProperty("spring.jpa.show-sql", "false")
    systemProperty("spring.batch.job.enabled", "false")
    systemProperty("user.timezone", "Asia/Seoul")
    jvmArgs("-Xshare:off")

    shouldRunAfter(tasks.named("test"))
    outputs.upToDateWhen { false }
}
