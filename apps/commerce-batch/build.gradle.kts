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
        excludeTags("benchmark", "period-ranking-benchmark")
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
    filter {
        includeTestsMatching("com.loopers.benchmark.ranking.RankingBatchBenchmarkTest")
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

val periodRankingBenchmarkCardinalities = providers.gradleProperty("periodRankingBenchmarkCardinalities").orElse("1000,5000")
val periodRankingBenchmarkPeriodDays = providers.gradleProperty("periodRankingBenchmarkPeriodDays").orElse("7")
val periodRankingBenchmarkActiveHours = providers.gradleProperty("periodRankingBenchmarkActiveHours").orElse("1")
val periodRankingBenchmarkPageSize = providers.gradleProperty("periodRankingBenchmarkPageSize").orElse("100")
val periodRankingBenchmarkChunkSizes = providers.gradleProperty("periodRankingBenchmarkChunkSizes").orElse("100,500,1000")
val periodRankingBenchmarkRuns = providers.gradleProperty("periodRankingBenchmarkRuns").orElse("3")
val periodRankingBenchmarkWarmup = providers.gradleProperty("periodRankingBenchmarkWarmup").orElse("1")
val periodRankingBenchmarkContentionRuns = providers.gradleProperty("periodRankingBenchmarkContentionRuns").orElse("3")
val periodRankingBenchmarkHoldMs = providers.gradleProperty("periodRankingBenchmarkHoldMs").orElse("400")
val periodRankingBenchmarkOutputDir = providers.gradleProperty("periodRankingBenchmarkOutputDir").orElse(
    layout.buildDirectory.dir("reports/period-ranking").map { it.asFile.absolutePath },
)
val periodRankingBenchmarkLabel = providers.gradleProperty("periodRankingBenchmarkLabel").orElse("local")

tasks.register<Test>("periodRankingBenchmark") {
    group = "verification"
    description = "Runs period ranking aggregation, chunk-size, and isolation-level benchmarks."

    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath
    maxParallelForks = 1
    useJUnitPlatform {
        includeTags("period-ranking-benchmark")
    }
    filter {
        includeTestsMatching("com.loopers.benchmark.ranking.period.PeriodRankingBenchmarkTest")
    }

    systemProperty("periodRankingBenchmarkCardinalities", periodRankingBenchmarkCardinalities.get())
    systemProperty("periodRankingBenchmarkPeriodDays", periodRankingBenchmarkPeriodDays.get())
    systemProperty("periodRankingBenchmarkActiveHours", periodRankingBenchmarkActiveHours.get())
    systemProperty("periodRankingBenchmarkPageSize", periodRankingBenchmarkPageSize.get())
    systemProperty("periodRankingBenchmarkChunkSizes", periodRankingBenchmarkChunkSizes.get())
    systemProperty("periodRankingBenchmarkRuns", periodRankingBenchmarkRuns.get())
    systemProperty("periodRankingBenchmarkWarmup", periodRankingBenchmarkWarmup.get())
    systemProperty("periodRankingBenchmarkContentionRuns", periodRankingBenchmarkContentionRuns.get())
    systemProperty("periodRankingBenchmarkHoldMs", periodRankingBenchmarkHoldMs.get())
    systemProperty("periodRankingBenchmarkOutputDir", periodRankingBenchmarkOutputDir.get())
    systemProperty("periodRankingBenchmarkLabel", periodRankingBenchmarkLabel.get())
    systemProperty("spring.profiles.active", "test")
    systemProperty("api.version", System.getProperty("api.version") ?: "1.40")
    systemProperty("spring.jpa.show-sql", "false")
    systemProperty("spring.batch.job.enabled", "false")
    systemProperty("spring.batch.job.name", "none")
    systemProperty("user.timezone", "Asia/Seoul")
    jvmArgs("-Xshare:off")

    shouldRunAfter(tasks.named("test"))
    outputs.upToDateWhen { false }
}
