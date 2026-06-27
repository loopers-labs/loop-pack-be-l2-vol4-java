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
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:${project.properties["springDocOpenApiVersion"]}")

    // external client
    implementation("org.springframework.cloud:spring-cloud-starter-openfeign")

    // resilience
    implementation("io.github.resilience4j:resilience4j-spring-boot3")
    implementation("org.springframework.boot:spring-boot-starter-aop")

    // security
    implementation("org.springframework.security:spring-security-crypto")

    // querydsl
    annotationProcessor("io.github.openfeign.querydsl:querydsl-apt:${project.properties["queryDslVersion"]}:jpa")
    annotationProcessor("jakarta.persistence:jakarta.persistence-api")
    annotationProcessor("jakarta.annotation:jakarta.annotation-api")

    // test-fixtures
    testImplementation(testFixtures(project(":modules:jpa")))
    testImplementation(testFixtures(project(":modules:redis")))
}
