plugins {
    `java-library`
    `java-test-fixtures`
}

dependencies {
    // jpa
    api("org.springframework.boot:spring-boot-starter-data-jpa")
    // querydsl
    api("com.querydsl:querydsl-jpa::jakarta")
    annotationProcessor("com.querydsl:querydsl-apt::jakarta")
    annotationProcessor("jakarta.persistence:jakarta.persistence-api")
    annotationProcessor("jakarta.annotation:jakarta.annotation-api")
    // uuid-creator (UUID v7 generation)
    implementation("com.github.f4b6a3:uuid-creator:5.3.3")
    // jdbc-mysql
    runtimeOnly("com.mysql:mysql-connector-j")

    testImplementation("org.testcontainers:mysql")

    testFixturesImplementation("org.springframework.boot:spring-boot-starter-data-jpa")
    testFixturesImplementation("org.testcontainers:mysql")
}
