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

```shell
# 인프라 기동 (개발 시작 전 필수)
docker-compose -f ./docker/infra-compose.yml up -d       # MySQL, Redis, Kafka
docker-compose -f ./docker/monitoring-compose.yml up -d  # Prometheus(9090), Grafana(3000)

# 빌드 / 테스트
./gradlew build
./gradlew :apps:commerce-api:test
./gradlew :apps:commerce-api:test --tests "com.loopers.domain.example.ExampleServiceIntegrationTest"
./gradlew :apps:commerce-api:jacocoTestReport
./gradlew :apps:commerce-api:bootJar

# 애플리케이션 실행
./gradlew :apps:commerce-api:bootRun                                    # Web, 8080 / actuator 8081
./gradlew :apps:commerce-batch:bootRun --args='--job.name=demoJob'
./gradlew :apps:commerce-streamer:bootRun
```

기본 프로필은 `local` 이며 `test/dev/qa/prd` 가 정의되어 있습니다. 테스트 실행 시 `tasks.test` 가 `spring.profiles.active=test`, `user.timezone=Asia/Seoul` 을 강제로 주입합니다.

## 멀티 모듈 구조

```
apps/        - 실행 가능한 SpringBootApplication. BootJar 만 enable, 일반 Jar 는 disable.
  commerce-api       - REST API (web). jpa+redis+supports 를 사용
  commerce-streamer  - Kafka consumer. kafka 모듈 추가 사용
  commerce-batch     - Spring Batch 워커. spring-boot-starter-batch
modules/     - 재사용 가능한 configuration. java-library + java-test-fixtures
  jpa    - DataSource(Hikari), JPA, QueryDSL, BaseEntity, MySQL Testcontainer + DatabaseCleanUp
  redis  - master/replica Lettuce, RedisTemplate, Redis Testcontainer + RedisCleanUp
  kafka  - producer/consumer factory, BATCH_LISTENER 컨테이너 팩토리
supports/    - jackson / logging / monitoring add-on
```

루트 `build.gradle.kts` 의 `subprojects` 블록이 공통 플러그인/의존성을 일괄 적용합니다. 새 모듈은 `settings.gradle.kts` 에 등록하고 모듈별 `build.gradle.kts` 에는 추가 의존성만 명시합니다.

## commerce-api 패키지 레이어링

```
interfaces/api/<domain>/  - @RestController, *V1ApiSpec(Swagger 인터페이스), *V1Dto(record)
application/<domain>/     - *Facade (@Component) - 여러 도메인 조합 유스케이스. domain 호출 후 *Info 로 변환
                          - *Service (@Component) - 단일 도메인 유스케이스 (Repository 조합, 단순 조회/저장 흐름)
domain/<domain>/          - *Model(@Entity, BaseEntity 상속), *Repository(인터페이스), 도메인 간 협력 로직이 있는 경우 *Service
infrastructure/<domain>/  - *JpaRepository(JpaRepository 확장), *RepositoryImpl(@Component, domain 인터페이스 구현)
support/error/            - CoreException, ErrorType(enum)
interfaces/api/           - ApiResponse<T>(공통 래퍼), ApiControllerAdvice(전역 예외 처리)
```

핵심 규약:
- **DI 는 Lombok `@RequiredArgsConstructor` + `final` 필드**. `@Autowired` 필드 주입은 사용하지 않음.
- **`@Service` 대신 `@Component`** 가 도메인/인프라 양쪽에서 일관되게 쓰임.
- **`@Transactional` 은 application Service/Facade 에 유연하게 적용**. 단일 도메인 조작은 application Service 에, 여러 도메인에 걸친 원자성이 필요한 경우(예: 주문 플로우)는 Facade 에 붙인다.
- **도메인 모델은 자기 검증을 한다.** 생성자/`update()` 안에서 인자가 잘못되면 `CoreException(ErrorType.BAD_REQUEST, ...)` 을 던진다.
- **Repository 인터페이스는 domain 에**, 구현체는 infrastructure 에 두는 헥사고날 스타일.
- **응답 포맷**: 모든 컨트롤러 메서드는 `ApiResponse<T>` 를 반환. 에러는 `ApiControllerAdvice` 가 통일된 포맷으로 변환.

### BaseEntity 사용 규약 (`modules/jpa`)

모든 `@Entity` 는 `com.loopers.domain.BaseEntity` 를 상속합니다.
- `id` (IDENTITY), `createdAt`, `updatedAt`, `deletedAt` (Soft Delete) 가 자동 관리됨 (`@PrePersist`/`@PreUpdate`).
- 시간 타입은 `ZonedDateTime`. 애플리케이션 기동 시 `Asia/Seoul` 로 강제 설정, Hibernate 는 UTC 정규화.
- `delete()`/`restore()` 는 멱등하게 동작. 추가 검증은 `protected void guard()` 오버라이드.

`JpaConfig` 는 `@EntityScan({"com.loopers"})` + `@EnableJpaRepositories({"com.loopers.infrastructure"})` 로 스캔 범위를 고정합니다. **infrastructure 패키지 외부에 JpaRepository 를 두면 빈으로 잡히지 않습니다.**

## 설정/프로필 임포트 구조

각 app 의 `application.yml` 은 `spring.config.import` 로 모듈 yml(`jpa.yml`, `redis.yml`, `logging.yml`, `monitoring.yml`)을 합쳐 사용합니다. 각 yml 안에 `local/test/dev/qa/prd` 프로필 블록이 함께 들어 있습니다. **프로필별 분기는 새 파일을 만들지 말고 해당 모듈 yml 안에 `---` 로 구분하여 추가합니다.**

DB 연결은 `datasource.mysql-jpa.main` prefix 의 HikariConfig 로 외부화됩니다. `local`/`test` 프로필은 `ddl-auto: create` — 스키마는 엔티티 기준으로 매번 생성됩니다.

## 테스트 작성 규약

- 테스트 작명: `<도메인행위>_<조건>` 패턴 (`returnsExampleInfo_whenValidIdIsProvided`).
- `@DisplayName` + `@Nested` 로 시나리오를 분류하고, GWT(`// given / when / then`) 주석을 본문에 명시.
- 검증은 `assertAll(...)` + AssertJ `assertThat`, 예외는 `assertThrows` 후 `errorType` 검증.
- **통합/E2E 테스트는 Testcontainers 를 사용**합니다. `:modules:jpa` testFixtures 의 `MySqlTestContainersConfig` + `DatabaseCleanUp`, `:modules:redis` testFixtures 의 `RedisTestContainersConfig` + `RedisCleanUp` 을 활용합니다. `@AfterEach` 에서 `truncateAllTables()` / `truncateAll()` 로 격리합니다.
- **테스트는 직렬 실행**: `tasks.test { maxParallelForks = 1 }`. 컨테이너 자원 공유 + System property 주입 방식 때문에 병렬화하면 안 됩니다.
- E2E 는 `@SpringBootTest(webEnvironment = RANDOM_PORT)` + `TestRestTemplate` 로 작성.

## 에러 처리 컨벤션

- 비즈니스 예외는 모두 `CoreException(ErrorType, customMessage?)` 로 통일.
- HTTP 상태/에러 코드/기본 메시지는 `ErrorType` enum 에서 관리 (`INTERNAL_ERROR`, `BAD_REQUEST`, `NOT_FOUND`, `CONFLICT`).
- 새 에러 카테고리가 필요하면 **`ErrorType` 에 추가**. 컨트롤러에서 직접 `ResponseEntity.status(...)` 를 만들지 말 것.
- Spring 표준 예외는 `ApiControllerAdvice` 가 이미 처리하므로 중복 처리 추가 금지.

## 도메인 & 객체 설계 전략

- 도메인 객체는 비즈니스 규칙을 캡슐화해야 합니다.
- 애플리케이션 서비스는 서로 다른 도메인을 조립해, 도메인 로직을 조정하여 기능을 제공해야 합니다.
- 규칙이 여러 서비스에 나타나면 도메인 객체에 속할 가능성이 높습니다.
- 각 기능에 대한 책임과 결합도에 대해 개발자의 의도를 확인하고 개발을 진행합니다.

## 아키텍처, 패키지 구성 전략

- 본 프로젝트는 레이어드 아키텍처를 따르며, DIP (의존성 역전 원칙) 을 준수합니다.
- API request/response DTO와 응용 레이어의 DTO는 분리해 작성합니다.
- 패키징 전략은 4개 레이어 패키지를 두고, 하위에 도메인 별로 패키징합니다.
  - `/interfaces/api` (presentation), `/application/..` (application), `/domain/..` (domain), `/infrastructure/..` (infrastructure)

## 커밋 규칙

[Conventional Commits](https://www.conventionalcommits.org/) 스타일: `<type>(<scope>): <subject>`

- **type**: `feat`, `fix`, `refactor`, `perf`, `test`, `docs`, `style`, `chore`, `build`, `ci`, `revert`
- **scope**: 모듈 또는 도메인 단위 — 예: `commerce-api`, `jpa`, `product`
- **subject**: 한국어 명령형/요약체. 끝에 마침표 없음. 50자 이내 권장.
- **body** (선택): "왜" 중심으로 한국어로 기술.

예시:
```
feat(product): 상품 재고 차감 API 추가
refactor(commerce-api): ExampleFacade 의존성 주입을 생성자 방식으로 통일
```

## 그 외 메모

- `version` 은 `git rev-parse --short HEAD` 결과로 자동 세팅됨 (루트 build.gradle.kts).
- `.editorconfig`: `max_line_length=130` (테스트 코드는 제한 없음).
- IntelliJ HTTP Client 요청 샘플은 `http/commerce-api/*.http`, 환경 변수는 `http/http-client.env.json`.
- PR 본문은 `.github/pull_request_template.md` 의 "배경/목표/결과" + "선택지와 결정" 섹션을 채우는 형식.
- `.codeguide/loopers-1-week.md` 가 1주차 학습 퀘스트 테스트 케이스 체크리스트.
- GitHub Actions `main.yml` 은 PR 시 `qodo-ai/pr-agent` 만 돌림 — 로컬에서 `./gradlew build` 통과를 보장해야 함.
