dependencies {
    // add-ons
    implementation(project(":modules:jpa"))
    implementation(project(":modules:redis"))
    implementation(project(":supports:jackson"))
    implementation(project(":supports:logging"))
    implementation(project(":supports:monitoring"))

    // web
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-actuator")

    // openfeign (PG 연동 전송용 — resilience4j 는 게이트웨이에서 수동 조합, feign.circuitbreaker 미사용)
    implementation("org.springframework.cloud:spring-cloud-starter-openfeign")

    // resilience4j (PG 호출 회복탄력성 — CircuitBreaker(Retry(RateLimiter)) 수동 데코레이터 조합)
    implementation("io.github.resilience4j:resilience4j-spring-boot3")
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:${project.properties["springDocOpenApiVersion"]}")

    // security
    implementation("org.springframework.boot:spring-boot-starter-security")

    // SQL 로깅 (실제 바인딩 값 인라인, local 프로파일에서만 활성화)
    implementation("com.github.gavlyukovskiy:p6spy-spring-boot-starter:${project.properties["p6spyVersion"]}")

    // querydsl
    annotationProcessor("com.querydsl:querydsl-apt::jakarta")
    annotationProcessor("jakarta.persistence:jakarta.persistence-api")
    annotationProcessor("jakarta.annotation:jakarta.annotation-api")

    // test-fixtures
    testImplementation(testFixtures(project(":modules:jpa")))
    testImplementation(testFixtures(project(":modules:redis")))
}
