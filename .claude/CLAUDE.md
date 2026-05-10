# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 프로젝트 개요

Loopers 가 제공하는 Spring Boot 기반 Java 템플릿 프로젝트입니다. 커머스 도메인을 다루는 멀티 모듈 Gradle 프로젝트로 구성되어 있습니다.

- **언어/런타임**: Java 21 (Gradle toolchain 으로 강제)
- **빌드 도구**: Gradle (Kotlin DSL, `build.gradle.kts`)
- **프레임워크**: Spring Boot 3.4.4, Spring Cloud 2024.0.1
- **데이터**: MySQL 8.0 + JPA(Hibernate) + QueryDSL, Redis 7.0 (master/replica), Kafka 3.5.1
- **테스트**: JUnit Platform + Testcontainers (MySQL/Redis/Kafka), Mockito, Instancio, Jacoco
- **모니터링**: Spring Actuator + Prometheus + Grafana, Micrometer Tracing(brave)

## 자주 쓰는 명령어

### 로컬 인프라 기동 (개발 시작 전 필수)
```shell
# MySQL, Redis(master/replica), Kafka, Kafka-UI
docker-compose -f ./docker/infra-compose.yml up -d
# Prometheus(9090), Grafana(3000, admin/admin)
docker-compose -f ./docker/monitoring-compose.yml up -d
```

### 빌드 / 테스트
```shell
# 전체 빌드 (테스트 포함)
./gradlew build

# 전체 테스트
./gradlew test

# 특정 모듈만 테스트
./gradlew :apps:commerce-api:test
./gradlew :modules:jpa:test

# 특정 테스트 클래스 / 메서드 실행
./gradlew :apps:commerce-api:test --tests "com.loopers.domain.example.ExampleServiceIntegrationTest"
./gradlew :apps:commerce-api:test --tests "*.ExampleV1ApiE2ETest.Get.returnsExampleInfo_whenValidIdIsProvided"

# Jacoco 커버리지 리포트 (test 이후 자동 실행 가능, xml 리포트만 생성됨)
./gradlew :apps:commerce-api:jacocoTestReport

# 실행 가능한 부트 jar 빌드 (apps/* 만 BootJar 활성화)
./gradlew :apps:commerce-api:bootJar
```

### 애플리케이션 실행
```shell
# commerce-api (Web, 8080 / actuator 8081 / Swagger /swagger-ui.html)
./gradlew :apps:commerce-api:bootRun

# commerce-batch (특정 잡 실행: -Djob.name=...)
./gradlew :apps:commerce-batch:bootRun --args='--job.name=demoJob'

# commerce-streamer (Kafka consumer)
./gradlew :apps:commerce-streamer:bootRun
```

기본 프로필은 `local` 이며 `test/dev/qa/prd` 가 정의되어 있습니다. 테스트 실행 시 `tasks.test` 가 `spring.profiles.active=test`, `user.timezone=Asia/Seoul` 을 강제로 주입합니다.

## 멀티 모듈 구조

모듈 위계와 역할은 엄격히 구분됩니다 (`README.md`, `settings.gradle.kts` 참고).

```
apps/        - 실행 가능한 SpringBootApplication. BootJar 만 enable, 일반 Jar 는 disable.
  commerce-api       - REST API (web). jpa+redis+supports 를 사용
  commerce-streamer  - Kafka consumer. kafka 모듈 추가 사용
  commerce-batch     - Spring Batch 워커. spring-boot-starter-batch
modules/     - 재사용 가능한 configuration (구현/도메인 비의존). java-library + java-test-fixtures
  jpa    - DataSource(Hikari), JPA, QueryDSL, BaseEntity, MySQL Testcontainer + DatabaseCleanUp
  redis  - master/replica Lettuce, RedisTemplate, Redis Testcontainer + RedisCleanUp
  kafka  - producer/consumer factory, BATCH_LISTENER 컨테이너 팩토리
supports/    - 부가 기능 add-on
  jackson    - ObjectMapper 커스터마이저 (NON_NULL, snake/enum 처리 등)
  logging    - logback config, slack appender, micrometer-tracing-brave
  monitoring - actuator + prometheus registry
```

루트 `build.gradle.kts` 의 `subprojects` 블록이 모든 하위 모듈에 다음을 일괄 적용합니다:
- `java`, `org.springframework.boot`, `io.spring.dependency-management`, `jacoco` 플러그인
- 공통 의존성: starter, validation runtime, jackson-jsr310, lombok, junit, springmockk, mockito, instancio, testcontainers
- `apps/*` 만 `BootJar` 활성화 / 그 외는 일반 `Jar`
- 컨테이너 프로젝트(`apps`, `modules`, `supports`)는 모든 task disable

새 모듈을 추가할 때는 `settings.gradle.kts` 에 등록하고, 모듈별 `build.gradle.kts` 에는 그 모듈만의 추가 의존성만 명시하면 됩니다.

## commerce-api 패키지 레이어링

`com.loopers` 하위는 의도적으로 분리된 4개 레이어 + support 입니다. 모든 새 도메인은 이 구조를 그대로 따릅니다.

```
interfaces/api/<domain>/  - @RestController, *V1ApiSpec(Swagger 인터페이스), *V1Dto(record)
application/<domain>/     - *Facade (@Component) - 유스케이스 조합. domain 호출 후 *Info 로 변환
domain/<domain>/          - *Service(@Component, @Transactional), *Model(@Entity, BaseEntity 상속), *Repository(인터페이스)
infrastructure/<domain>/  - *JpaRepository(JpaRepository 확장), *RepositoryImpl(@Component, domain 인터페이스 구현)
support/error/            - CoreException, ErrorType(enum)
interfaces/api/           - ApiResponse<T>(공통 래퍼), ApiControllerAdvice(전역 예외 처리)
```

핵심 규약:
- **DI 는 Lombok `@RequiredArgsConstructor` + `final` 필드**. `@Autowired` 필드 주입은 사용하지 않음.
- **`@Service` 대신 `@Component`** 가 도메인/인프라 양쪽에서 일관되게 쓰임.
- **`@Transactional` 은 domain 의 Service 에 위치**. Facade 는 트랜잭션 경계가 아님 (조합만 담당).
- **도메인 모델은 자기 검증을 한다.** 생성자/`update()` 안에서 인자가 잘못되면 `CoreException(ErrorType.BAD_REQUEST, ...)` 을 던진다 (`ProductModel`, `ExampleModel` 참고).
- **Repository 인터페이스는 domain 에**, 구현체는 infrastructure 에 두는 헥사고날 스타일. 컨트롤러/Facade 는 domain 의 인터페이스만 참조.
- **응답 포맷**: 모든 컨트롤러 메서드는 `ApiResponse<T>` 를 반환. 에러는 `ApiControllerAdvice` 가 `CoreException`/Spring 예외를 잡아 `ErrorType` 기반으로 통일된 포맷으로 변환.

### BaseEntity 사용 규약 (`modules/jpa`)

모든 `@Entity` 는 `com.loopers.domain.BaseEntity` 를 상속합니다.
- `id` (IDENTITY), `createdAt`, `updatedAt`, `deletedAt` (Soft Delete) 가 자동 관리됨 (`@PrePersist`/`@PreUpdate`).
- 시간 타입은 `ZonedDateTime`. 애플리케이션 기동 시 `Asia/Seoul` 로 강제 설정 (`CommerceApiApplication#started`), Hibernate 는 UTC 정규화 (`jpa.yml` 의 `timezone.default_storage: NORMALIZE_UTC`).
- `delete()`/`restore()` 는 멱등하게 동작.
- 추가 검증이 필요하면 `protected void guard()` 를 오버라이드. PrePersist/PreUpdate 시점에 호출됨.

`JpaConfig` 는 `@EntityScan({"com.loopers"})` + `@EnableJpaRepositories({"com.loopers.infrastructure"})` 로 스캔 범위를 고정합니다. **infrastructure 패키지 외부에 JpaRepository 를 두면 빈으로 잡히지 않습니다.**

## 설정/프로필 임포트 구조

각 app 의 `application.yml` 은 `spring.config.import` 로 모듈의 yml 을 합쳐 사용합니다.

```yaml
spring.config.import:
  - jpa.yml         # modules/jpa
  - redis.yml       # modules/redis
  - kafka.yml       # modules/kafka (streamer 만)
  - logging.yml     # supports/logging
  - monitoring.yml  # supports/monitoring
```

각 yml 안에 `local/test/dev/qa/prd` 프로필 블록이 함께 들어 있습니다. **프로필별 분기를 추가할 때는 새 파일을 만들지 말고 해당 모듈 yml 안에 `---` 로 구분하여 추가합니다.**

DB 연결은 `datasource.mysql-jpa.main` prefix 의 HikariConfig 로 외부화됩니다 (`DataSourceConfig`). `local`/`test` 프로필은 `ddl-auto: create` 입니다 — 마이그레이션 도구는 없으며, 스키마는 엔티티 기준으로 매번 생성됩니다.

## 테스트 작성 규약

- 테스트 작명: `<도메인행위>_<조건>` 패턴 (`returnsExampleInfo_whenValidIdIsProvided`).
- `@DisplayName` + `@Nested` 로 시나리오를 분류하고, AAA(`// arrange / act / assert`) 주석을 본문에 명시.
- 검증은 `assertAll(...)` + AssertJ `assertThat`, 예외는 `assertThrows` 후 `errorType` 검증.
- **통합/E2E 테스트는 Testcontainers 를 사용**합니다.
  - `:modules:jpa` 의 testFixtures 가 `MySqlTestContainersConfig` (static 블록에서 컨테이너 시작 후 `System.setProperty` 로 jdbc-url 주입) 와 `DatabaseCleanUp` 을 제공.
  - `:modules:redis` 의 testFixtures 가 `RedisTestContainersConfig` 와 `RedisCleanUp` 제공.
  - 새 통합 테스트에서는 `@AfterEach` 에서 `databaseCleanUp.truncateAllTables()` (또는 `redisCleanUp.truncateAll()`) 를 호출해 격리시킵니다 (`ExampleServiceIntegrationTest`, `ExampleV1ApiE2ETest` 참고).
- **테스트는 직렬 실행**: `tasks.test { maxParallelForks = 1 }`. 컨테이너 자원 공유 + System property 주입 방식 때문에 병렬화하면 안 됩니다.
- E2E 는 `@SpringBootTest(webEnvironment = RANDOM_PORT)` + `TestRestTemplate` 로 작성.
- 새 app 모듈에서 testFixtures 를 쓰려면 `testImplementation(testFixtures(project(":modules:jpa")))` 처럼 명시적으로 의존성에 추가해야 함 (이미 `commerce-api`/`commerce-batch`/`commerce-streamer` build.gradle.kts 에 적용됨).

## 에러 처리 컨벤션

- 비즈니스 예외는 모두 `CoreException(ErrorType, customMessage?)` 로 통일.
- HTTP 상태/에러 코드/기본 메시지는 `ErrorType` enum 에서 관리 (`INTERNAL_ERROR`, `BAD_REQUEST`, `NOT_FOUND`, `CONFLICT`).
- 새 에러 카테고리가 필요하면 **`ErrorType` 에 추가**. 컨트롤러에서 직접 `ResponseEntity.status(...)` 를 만들지 말 것.
- Spring 의 표준 예외 (`MethodArgumentTypeMismatchException`, `MissingServletRequestParameterException`, `HttpMessageNotReadableException`, `ServerWebInputException`, `NoResourceFoundException`) 는 `ApiControllerAdvice` 가 이미 한국어 메시지로 변환하고 있으므로 중복 처리 추가 금지.

## 커밋 규칙

[Conventional Commits](https://www.conventionalcommits.org/) 스타일을 따릅니다.

```
<type>(<scope>): <subject>

<body>
```

- **type** (필수, 소문자): `feat`, `fix`, `refactor`, `perf`, `test`, `docs`, `style`, `chore`, `build`, `ci`, `revert`
- **scope** (선택): 변경 범위. 모듈 또는 도메인 단위로 표기 — 예: `commerce-api`, `jpa`, `product`, `example`
- **subject**: 한국어 명령형/요약체. 끝에 마침표 없음. 50자 이내 권장.
- **body** (선택): "왜" 중심으로 한국어로 기술. 한 줄 비우고 작성.
- **breaking change** 는 `feat!:` 또는 footer 에 `BREAKING CHANGE:` 로 표시.

예시:
```
feat(product): 상품 재고 차감 API 추가
fix(jpa): BaseEntity preUpdate 시 updatedAt 미반영 수정
refactor(commerce-api): ExampleFacade 의존성 주입을 생성자 방식으로 통일
chore: PR 템플릿 통일 및 개선
```

훅으로 강제되지는 않으니 메시지 작성 시 위 형식을 직접 지켜야 합니다.

## 그 외 메모

- `version` 은 `git rev-parse --short HEAD` 결과로 자동 세팅됨 (루트 build.gradle.kts).
- `.editorconfig`: `max_line_length=130` (테스트 코드는 제한 없음). 한 줄이 길어지더라도 테스트 가독성을 우선.
- `BootJar` 의 메인 클래스는 패키지가 `com.loopers` (각 app 의 `*Application.java` 한 개씩).
- IntelliJ HTTP Client 요청 샘플은 `http/commerce-api/*.http`, 환경 변수는 `http/http-client.env.json`.
- PR 본문은 `.github/pull_request_template.md` 의 "배경/목표/결과" + "선택지와 결정" 섹션을 채우는 형식. 학습 과정의 의사결정 기록을 중요시.
- `.codeguide/loopers-1-week.md` 가 1주차 학습 퀘스트(회원/내정보/포인트) 테스트 케이스 체크리스트. 새 도메인을 만들 때 참고.
- GitHub Actions 워크플로 `main.yml` 은 PR 시 `qodo-ai/pr-agent` 만 돌림 — CI 단의 빌드/테스트는 별도로 없음. 로컬에서 `./gradlew build` 통과를 보장해야 함.
