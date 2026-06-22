dependencies {
    // add-ons
    implementation(project(":modules:jpa"))
    implementation(project(":modules:redis"))
    implementation(project(":modules:kafka"))
    implementation(project(":supports:jackson"))
    implementation(project(":supports:logging"))
    implementation(project(":supports:monitoring"))

    // web
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-actuator")

    // 외부 PG(pg-simulator) 연동 — 선언적 HTTP(OpenFeign) + 재시도/서킷(Resilience4j).
    // 버전은 spring-cloud BOM(2024.0.1)이 관리 → 핀 불필요. @Retry/@CircuitBreaker AOP를 위해 aop 스타터 포함.
    implementation("org.springframework.cloud:spring-cloud-starter-openfeign")
    implementation("io.github.resilience4j:resilience4j-spring-boot3")
    implementation("org.springframework.boot:spring-boot-starter-aop")

    // security (BCrypt 비밀번호 해싱 — 필터체인/자동설정 없이 crypto 유틸만)
    implementation("org.springframework.security:spring-security-crypto")
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:${project.properties["springDocOpenApiVersion"]}")

    // querydsl
    annotationProcessor("com.querydsl:querydsl-apt::jakarta")
    annotationProcessor("jakarta.persistence:jakarta.persistence-api")
    annotationProcessor("jakarta.annotation:jakarta.annotation-api")

    // test-fixtures
    testImplementation(testFixtures(project(":modules:jpa")))
    testImplementation(testFixtures(project(":modules:redis")))

    // 좋아요 비동기 집계 — 테스트에서 Kafka 브로커를 Testcontainers로 띄운다(KafkaTemplate/Admin 실제 동작)
    testImplementation("org.springframework.kafka:spring-kafka-test")
    testImplementation("org.testcontainers:kafka")
}
