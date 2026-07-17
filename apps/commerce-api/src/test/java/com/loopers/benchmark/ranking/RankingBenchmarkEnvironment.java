package com.loopers.benchmark.ranking;

import java.util.TimeZone;

record RankingBenchmarkEnvironment(
    String javaVersion,
    String osName,
    String osArchitecture,
    int availableProcessors,
    String timezone,
    String redisVersion
) {
    static RankingBenchmarkEnvironment capture(String redisVersion) {
        return new RankingBenchmarkEnvironment(
            System.getProperty("java.version", "unknown"),
            System.getProperty("os.name", "unknown"),
            System.getProperty("os.arch", "unknown"),
            Runtime.getRuntime().availableProcessors(),
            TimeZone.getDefault().getID(),
            redisVersion == null ? "unknown" : redisVersion
        );
    }
}
