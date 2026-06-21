dependencies {
    // spring
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    // monitoring
    implementation("io.micrometer:micrometer-registry-prometheus")
    implementation("io.micrometer:micrometer-tracing-bridge-otel")
    implementation("io.opentelemetry:opentelemetry-exporter-otlp")
    // Slack Appender
    implementation("com.github.maricn:logback-slack-appender:${project.properties["slackAppenderVersion"]}")
}
