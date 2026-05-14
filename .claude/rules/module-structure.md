# 모듈 구조

`settings.gradle.kts` 의 `include(...)` 와 루트 `build.gradle.kts` 의 `subprojects` 블록을 기준으로 합니다.

## 멀티 모듈 트리

루트는 모든 task 가 비활성화된 컨테이너이며, 실제 실행 가능한 애플리케이션은 `apps/*` 입니다.
`build.gradle.kts` 의 `subprojects` 블록에서 java / Spring Boot / dependency-management / jacoco 플러그인과
공통 의존성(Lombok, Jackson JSR310, Validation, Testcontainers, springmockk, mockito, instancio 등)을 모든 모듈에 일괄 적용합니다.

```
Root  (rootProject.name = "loopers-java-spring-template", group = "com.loopers")
├── apps/                ( Spring Boot 실행 모듈 — BootJar 활성, Jar 비활성 )
│   ├── commerce-api        REST API. spring-web, actuator, springdoc + modules:jpa/redis + supports:jackson/logging/monitoring
│   ├── commerce-streamer   Kafka consumer. spring-web, actuator + modules:kafka
│   └── commerce-batch      Spring Batch 잡 실행기. spring-boot-starter-batch, web-application-type=none
├── modules/             ( 재사용 가능한 인프라 configuration — java-library + java-test-fixtures )
│   ├── jpa                 spring-data-jpa, QueryDSL(jakarta), mysql-connector, testcontainers:mysql
│   │                       └─ BaseEntity / DataSourceConfig / JpaConfig / QueryDslConfig 제공
│   ├── redis               spring-data-redis, RedisConfig (master/readonly 노드 정보), testcontainers-redis
│   └── kafka               spring-kafka, KafkaConfig, testcontainers:kafka (+ spring-kafka-test)
└── supports/            ( 부가 기능 add-on )
    ├── jackson             Jackson JSR310 / kotlin module, JacksonConfig
    ├── logging             actuator + micrometer prometheus / tracing-bridge-brave + Slack appender
    └── monitoring          actuator + micrometer prometheus
```

## apps 패키지 레이아웃 (commerce-api 기준)
`com.loopers` 하위에 layered 아키텍처를 사용합니다.

```
com.loopers
├── CommerceApiApplication
├── interfaces.api/          Controller, DTO, ApiSpec(Swagger), ApiControllerAdvice, ApiResponse
├── application.<domain>/    Facade, Info (UseCase / 트랜잭션 경계)
├── domain.<domain>/         Model, Service, Repository (도메인 계약)
├── infrastructure.<domain>/ RepositoryImpl, JpaRepository (도메인 계약 구현)
└── support.error/           CoreException, ErrorType
```
샘플 도메인으로 `example`, `product` 가 포함되어 있습니다.

- `commerce-streamer` — `com.loopers.interfaces.consumer` 패키지에서 Kafka consumer 를 정의합니다.
- `commerce-batch` — `com.loopers.batch.job.<jobName>.step` 패키지 컨벤션으로 Job/Step/Listener 를 배치합니다.

## Spring 프로파일
- `application.yml` 은 profile 별 document(`local`/`test`, `dev`, `qa`, `prd`)로 분리되어 있습니다.
- 기본 활성 프로파일은 `local` 이며, 테스트 시 `test` 가 강제(`tasks.test { systemProperty("spring.profiles.active", "test") }`)됩니다.
- 각 app 의 `application.yml` 은 모듈 단위 설정 파일(`jpa.yml`, `redis.yml`, `kafka.yml`, `logging.yml`, `monitoring.yml`)을 `spring.config.import` 로 가져옵니다.

## 모듈 추가 시 주의
- `settings.gradle.kts` 의 `include(...)` 에 등록합니다.
- 루트 `build.gradle.kts` 의 `subprojects` 블록이 공통 플러그인·의존성을 부여하므로, 모듈별 `build.gradle.kts` 는 추가 의존성만 선언합니다.
- `apps/*` 모듈만 `bootJar` 가 활성화되고, `modules/*`·`supports/*` 는 일반 `Jar` 로 패키징됩니다.
  ```kotlin
  configure(allprojects.filter { it.parent?.name.equals("apps") }) {
      tasks.withType(Jar::class) { enabled = false }
      tasks.withType(BootJar::class) { enabled = true }
  }
  ```
  이 구분을 깨지 마세요.
- `modules/*` 는 도메인에 종속되지 않는 configuration 만, `supports/*` 는 로깅·모니터링·직렬화 등 add-on 만 둡니다.
