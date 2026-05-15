# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

이 문서는 두 영역으로 나뉜다:

1. **사용자 정의 규칙 (User-Defined Rules)** — 사용자가 직접 작성한 프로젝트 컨벤션·작업 지침. **최우선 적용**한다.
2. **자동 생성된 프로젝트 개요 (Auto-Generated Project Overview)** — Claude가 `/init`으로 코드베이스를 분석해 작성한 참고 자료. 사실 정보(빌드 명령, 모듈 구조 등)이며, 1번과 충돌 시 1번을 따른다.

---

# 1. 사용자 정의 규칙 (User-Defined Rules)

> 이 영역은 사용자가 직접 관리한다. 아래 섹션에 규칙을 추가하라.

<!-- USER_RULES_START -->

## 0. 대원칙 (Prime Directive)

**프로젝트 목적은 프로덕션 산출이 아니라 "학습과 고민"이다.** 현업에서 실제로 마주하는 문제를 간접 경험하며 사고력을 기르는 것이 본 저장소의 존재 이유다. Claude는 이 전제를 모든 응답에 반영해야 한다.

### 협업 모드

1. **선택지 + 트레이드오프 우선** — 어떤 기능을 구현하든 코드부터 쏟아내지 말고, 가능한 접근법(최소 2개) · 각 접근의 장단점 · 학습 관점의 의미를 먼저 제시한다.
2. **방향성은 제안, 결정은 사용자** — Claude는 추천(권장안) 의견을 명시할 수 있다. 그러나 **최종 결정은 항상 사용자가 내린다.** 사용자의 명시적 선택 없이 방향성 결정 사항을 코드로 옮기지 않는다.
3. **"왜"를 함께 설명한다** — 결과물보다 그 결정에 이르는 사고 과정·근거가 더 중요하다. 단순 정답 제공이 아니라 사용자가 스스로 판단할 수 있는 재료를 제공한다.

### Claude 행동 체크리스트

- [ ] 구현 요청을 받으면 → **먼저 옵션 비교**, 그다음 사용자 결정 확인 → 그제서야 구현.
- [ ] 자명한 trivial 작업(오타 수정, 단순 리네이밍 등)은 옵션 없이 바로 진행해도 무방.
- [ ] 권장안을 낼 때는 "**권장: A** — 이유: …" 형태로 분명히 표기, 단 사용자에게 강요하지 않는다.
- [ ] 사용자가 내린 결정은 따르되, 학습 관점에서 짚을 가치가 있으면 한 줄 코멘트를 붙인다.

## 1. 핸드코딩 영역 보존 (Hand-Coding Zone)

**기능별로 가장 핵심이 되는 구현 1개는 사용자가 직접 손으로 작성할 수 있도록 비워둔다.** 학습 목적상 직접 짜보는 경험이 가장 큰 이해를 만들기 때문이다.

### 적용 규칙

- 새 기능 구현 시 Claude는 **스캐폴드(주변부)만 작성**한다: 패키지/클래스 골격, 시그니처, DTO 매핑, 어노테이션, 의존성 주입.
- **핵심 1개**는 비워두고 표식을 남긴다:
  ```java
  // TODO(hand-coded): 직접 구현할 영역
  //   - 의도: <무엇을 해야 하는지 한 줄>
  //   - 힌트: <필요시 1~2줄, 정답은 적지 말 것>
  throw new UnsupportedOperationException("hand-coded zone");
  ```
- 어디를 비울지 **사용자에게 먼저 제안**하고 합의 후 진행. 보통 후보:
  - 도메인 `Service`의 핵심 비즈니스 메서드 (계산/검증/상태 전이)
  - 도메인 `Model`의 핵심 메서드 (불변식·invariant 처리)
  - 알고리즘성 코드 (정렬·매칭·할인 계산 등)
- 사용자가 "이번엔 다 채워줘"라고 명시하면 예외적으로 전체 구현 가능.

### Claude 행동 체크리스트 (구현 시)

- [ ] 구현 시작 전, "**핸드코딩 후보: X.Y() 메서드** — 이유: …" 한 줄 제안.
- [ ] 사용자 합의 후, 해당 지점만 `TODO(hand-coded)` 표식으로 남기고 컴파일은 통과시킨다.
- [ ] 테스트는 작성하되, 핸드코딩 영역의 테스트는 **빨간 상태(failing)**로 두어 사용자가 구현 후 통과시키도록 한다.
- [ ] 사용자가 직접 채운 코드를 리뷰 요청하면, 단점 지적 전 **선택의 근거를 먼저 묻는다**.

## 2. 개발 워크플로 — TDD (Red → Green → Refactor)

**현 시점 워크플로는 TDD.** 추후 TLD(Test-Last Development)로 전환할 수 있으며, 그 결정은 사용자가 명시적으로 내린다. Claude는 사용자의 별도 지시가 없는 한 **항상 TDD 사이클로 진행**한다.

### 사이클

| Phase | 핵심 | 절대 규칙 |
|---|---|---|
| **🔴 Red** | 요구사항을 만족하는 **실패하는 테스트** 먼저 작성 | 테스트가 처음부터 통과하면 그 테스트가 잘못된 것 → 실패하도록 수정 |
| **🟢 Green** | Red 테스트를 통과시키는 **최소 코드** 작성 | **오버엔지니어링 금지.** 처음부터 최적화하지 않는다 (모니터링 후 근거를 가지고 개선) |
| **🔵 Refactor** | 가독성/구조 정리, unused import 제거, 객체지향성 개선 | 모든 테스트 그린 유지. 불필요한 private 함수 지양 |

- **모든 테스트는 3A 패턴**: `// Arrange` → `// Act` → `// Assert` 구조 명시 (주석 또는 빈 줄로 구분).
- **한 사이클 = 한 기능 단위.** 여러 기능을 한 사이클에 밀어 넣지 않는다.
- 사이클마다 사용자에게 phase 진입을 보고하고 다음 phase로 넘어갈지 확인한다 (학습 의도상 흐름이 보여야 함).

### 성능 최적화 원칙 — "왜를 겪고 나서 최적화"

- Green phase에서는 **단순/직관적 구현**을 선호한다 (N+1, 단일 트랜잭션, 단일 인덱스 등 그대로).
- 최적화는 **모니터링/측정으로 문제를 직접 관찰한 뒤** 진행한다 (Prometheus/Grafana, 로그, 부하 테스트 등).
- Claude는 최적화 옵션과 트레이드오프를 제시할 수 있으나, **선제적으로 적용하지 않는다.**

### Never Do (금지)

- **동작하지 않는 코드**, 의미 없는 Mock 데이터로 채우는 더미 구현 금지. 항상 실제로 돌아가는 코드를 만든다.
- **null-safety 누락 금지.** Java에서는 `Optional` 활용. 외부 경계값은 명시적 검증.
- **`System.out.println`/디버그 print 잔존 금지.** 로깅이 필요하면 `log.*` 사용.

### Recommendation (권장)

- **E2E 테스트**: 실제 HTTP 호출로 API 검증 (`MockMvc`/`TestRestTemplate`/Testcontainers).
- **재사용 가능한 객체 설계**: 도메인 객체는 다른 유스케이스에서도 재활용 가능한 형태로.
- **성능 최적화 제안 명시**: 측정 기반 개선 시 "현 상태 → 가설 → 검증 → 적용" 순으로 기록.
- **HTTP 요청 샘플 정리**: 신규 API는 `http/<app>/**.http`에 분류해 추가 (JetBrains HTTP Client 포맷).

### Priority (우선순위)

1. **실제로 동작하는 해결책**만 고려한다.
2. **null-safety, thread-safety**를 고려한다.
3. **테스트 가능한 구조**로 설계한다 (의존성 주입, 순수 함수 분리 등).
4. **기존 코드 패턴**을 먼저 분석하고 일관성을 유지한다 (`product` 패키지가 레퍼런스).

### Claude 행동 체크리스트 (TDD 진행 시)

- [ ] 새 기능 시작 시 "🔴 Red 작성 시작합니다 — 대상 테스트: …" 한 줄 보고.
- [ ] 테스트 작성 → 실행해서 **실제로 실패**하는지 확인하고 결과 공유.
- [ ] 사용자 합의 후 🟢 Green 진입, 최소 구현. 다시 실행해서 그린 확인.
- [ ] 핸드코딩 영역(섹션 1)이 그 사이클의 핵심이라면, Green을 사용자가 채우도록 비우고 테스트만 빨갛게 두기.
- [ ] 🔵 Refactor 단계는 "리팩토링할 후보들" 목록 먼저 제시 → 사용자 선택 후 적용.
- [ ] phase 전환 시 항상 테스트 결과(빨강/초록)를 evidence로 보여준다.

<!-- USER_RULES_END -->

---

# 2. 자동 생성된 프로젝트 개요 (Auto-Generated Project Overview)

> 아래 내용은 Claude가 `/init` 명령으로 자동 생성했다 (2026-05-10).
> 코드 변경 시 사실 관계가 어긋날 수 있으므로 주기적 갱신이 필요하다.
> 사용자 정의 규칙(섹션 1)과 충돌하면 섹션 1을 우선한다.

## Project

Loopers commerce backend template — Spring Boot 3.4.4 / Java 21 / Gradle multi-module monorepo. Korean-language project; PR template, error messages, and inline comments are Korean.

## Commands

Use the wrapper (`./gradlew`). Tests run on JUnit Platform with `spring.profiles.active=test`, `user.timezone=Asia/Seoul`, and `maxParallelForks=1` (forced sequential — do not parallelize tests).

```bash
# Build everything (skip tests)
./gradlew build -x test

# Build & test all modules
./gradlew build

# Test a single module
./gradlew :apps:commerce-api:test

# Run a single test class / method
./gradlew :apps:commerce-api:test --tests "com.loopers.interfaces.api.product.ProductV1ControllerTest"
./gradlew :apps:commerce-api:test --tests "*ProductV1ControllerTest.createProduct_*"

# Run an app locally (default profile is `local`)
./gradlew :apps:commerce-api:bootRun
./gradlew :apps:commerce-batch:bootRun
./gradlew :apps:commerce-streamer:bootRun

# Jacoco coverage report (per module, after test)
./gradlew :apps:commerce-api:jacocoTestReport
```

Local infra is required before `local` profile boots — MySQL, Redis (master + read replica), Kafka, kafka-ui:

```bash
docker-compose -f ./docker/infra-compose.yml up -d
docker-compose -f ./docker/monitoring-compose.yml up -d   # Prometheus + Grafana on :3000 (admin/admin)
```

MySQL is `loopers` / `application` / `application` on `:3306`. Redis master `:6379`, replica `:6380`. Kafka broker `localhost:19092` (host) / `kafka:9092` (in-network).

## Architecture

### Module hierarchy (enforced by `build.gradle.kts:75-78,109-111`)

- `apps/*` — executable Spring Boot apps. **Only** apps produce `BootJar`; libraries produce plain `Jar`. The `apps`, `modules`, `supports` aggregator projects themselves are disabled (no tasks run on them).
- `modules/*` — `java-library` + `java-test-fixtures`. Reusable Spring configuration; **must not** depend on a specific domain. Each module ships a `*.yml` (e.g. `jpa.yml`, `redis.yml`, `kafka.yml`) loaded via `spring.config.import` from the app's `application.yml`.
- `supports/*` — add-ons (logging, monitoring, jackson). Same `java-library` shape, also imported via `spring.config.import`.

App → modules/supports dependency directions:
- `commerce-api`: jpa, redis, jackson, logging, monitoring (web + actuator + springdoc).
- `commerce-batch`: jpa, redis, jackson, logging, monitoring (`spring-boot-starter-batch`).
- `commerce-streamer`: jpa, redis, **kafka**, jackson, logging, monitoring (web + actuator).

QueryDSL is wired only in apps (see `apps/*/build.gradle.kts` `annotationProcessor("com.querydsl:querydsl-apt::jakarta")`); modules expose `querydsl-jpa` as `api`. When adding QueryDSL `Q*` types, the annotation processor must be present in the app build file, not the module.

### Layered package convention inside `commerce-api`

Package root: `com.loopers`. Layers (do not cross-import upwards):

```
interfaces/api/<feature>     ← REST controllers, V1 DTOs, ApiSpec interfaces
application/<feature>        ← Facades + *Info DTOs (use-case orchestration)
domain/<feature>             ← *Service, *Model (entity), *Repository (interface)
infrastructure/<feature>     ← *RepositoryImpl, *JpaRepository (Spring Data)
support/error                ← CoreException, ErrorType
```

Conventions to follow when adding a feature (`product` is the canonical reference):
- Controllers return `ApiResponse<T>` (`interfaces/api/ApiResponse.java`) — `ApiResponse.success(data)` / `ApiResponse.fail(code, msg)`. Do not return raw entities or `ResponseEntity` from controllers; the global advice handles failures.
- DTOs live as nested records inside `<Feature>V1Dto` (request + response in one file). Map domain ↔ DTO via static `from(...)` factories.
- Facades (`@Component`) are thin orchestrators — they call domain services and convert `*Model` → `*Info`. Don't put business rules here.
- Domain services are `@Component` + `@Transactional(readOnly = true)` by default; use `@Transactional` on writes. Throw `new CoreException(ErrorType.X, "korean message")` for business failures — never throw raw `RuntimeException` from domain.
- Repositories: define an interface in `domain/<feature>/<X>Repository`; implement in `infrastructure/<feature>/<X>RepositoryImpl` that delegates to a Spring Data `<X>JpaRepository`. The domain layer never imports `org.springframework.data.*`.

### Error handling

`ApiControllerAdvice` (`interfaces/api/ApiControllerAdvice.java`) is the **only** place that builds error responses. It maps:
- `CoreException` → `errorType.status` + Korean `customMessage`.
- `MethodArgumentTypeMismatchException`, `MissingServletRequestParameterException`, `HttpMessageNotReadableException` (incl. `InvalidFormatException` / `MismatchedInputException`), `ServerWebInputException` → `BAD_REQUEST` with field-aware Korean messages.
- `NoResourceFoundException` → `NOT_FOUND`. Anything else → `INTERNAL_ERROR` with `log.error`.

To add a new error class, add an enum constant to `ErrorType` (status + reason-phrase code + Korean default message). Do not invent ad-hoc HTTP responses elsewhere.

### Cross-cutting

- Default JVM/app timezone is forced to `Asia/Seoul` in `CommerceApiApplication.@PostConstruct` and as a test JVM arg.
- `@ConfigurationPropertiesScan` is on the main app class — `@ConfigurationProperties` records are picked up automatically.
- Profiles: `local` (default), `test`, `dev`, `qa`, `prd`. `springdoc.api-docs.enabled=false` in `prd`. Swagger UI: `/swagger-ui.html`.
- Logging module ships profile-specific Slack appender configs (`slack-log-{dev,qa,prd}.xml`) and JSON/plain console appenders selected by logback profile.
- Version: when running outside CI, `version` is set to the short git hash via `getGitHash()` in the root `build.gradle.kts`; do not set `version` manually unless intentionally pinning.

### Tests

- Test fixtures published from `modules/jpa` and `modules/redis` (and `modules/kafka` when relevant): `DatabaseCleanUp`, `RedisCleanUp`, `MySqlTestContainersConfig`, `RedisTestContainersConfig`. Apps consume via `testImplementation(testFixtures(project(":modules:jpa")))`.
- Testcontainers (`mysql`, `redis`, `kafka`) is the integration-test path — `org.testcontainers:junit-jupiter` is on the classpath. Tests assume Docker is running.
- Stack: JUnit 5, Mockito 5, springmockk 4, instancio-junit 5. Spring Batch tests use `spring-batch-test`.
- `maxParallelForks = 1` is intentional (shared MySQL/Redis containers). Don't change without coordinating cleanup strategy.

## Repo specifics worth knowing

- PR template (`.github/pull_request_template.md`) is in Korean and expects: 배경 / 목표 / 결과, decision log (alternatives + tradeoffs), and reflection. Fill those sections, not English equivalents.
- HTTP samples in `http/commerce-api/` are JetBrains HTTP Client format; env vars in `http/http-client.env.json`.
- `.omc/`, `.codeguide/`, `.claude/` are agent tooling state — generally do not commit changes to them as part of feature work.
