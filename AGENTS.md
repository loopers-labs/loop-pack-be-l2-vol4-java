# AGENTS.md

이 파일은 이 저장소에서 작업하는 Codex (Codex.ai/code) 에게 가이드를 제공합니다.

## Working Principles

1. **코딩 전 생각하기** — 추측 금지. 가정은 명시하고, 해석이 갈리면 선택지를 제시. 더 단순한 방법이 있으면 말하고, 헷갈리면 멈추고 질문.
2. **단순함 우선** — 요청된 범위만. 1 회용 추상화·미사용 유연성·발생 불가 시나리오의 에러 처리 추가 금지. "시니어가 보면 과하다고 할까?" 가 판단 기준.
3. **필요한 부분만 수정** — 무관한 코드/주석/포맷 "개선" 금지. 본인 변경으로 죽은 코드만 정리, 기존 dead code 는 언급만. 변경된 모든 라인은 사용자 요청과 직접 연결돼야 함.
4. **목표 중심 실행** — 작업을 검증 가능한 형태로 변환 (예: "버그 수정" → "버그 재현 테스트 작성 → 통과"). 다단계 작업은 `단계 → 검증` 형식의 짧은 계획부터.
5. **TDD 우선** — 핵심 비즈니스 규칙, 상태 변경, 버그 수정, 복잡한 분기는 실패하는 테스트부터 시작한다. 단순 DTO/설정/문서/얇은 위임 코드는 변경 위험도에 따라 테스트 보강 여부를 판단한다.

### 영역별 상세 룰 (skill 로 분리)

| 상황 | Skill | 다루는 내용 |
|---|---|---|
| 새 코드 / 네이밍 / 메서드 분리 / Java 21·Lombok 사용 | `/coding-style` | 가독성, 이름, 단일책임, early return, null 처리, record/switch/var/text block, Lombok 범위 |
| 기존 코드 정리 / 추출 / 중복 제거 | `/refactor` | 동작 보존, 변경 범위 제한, 추출 기준, 레거시 점진 개선, 테스트 안전망 |
| 새 기능 구조 / 책임 분배 / 인터페이스·패턴 도입 | `/design` | 단순 설계 우선, SOLID 적용 기준, Strategy/Factory/Template Method 사용 시점, **Repository port-adapter 예외** |
| 테스트 작성 / TDD 사이클 | `/tdd` | 실전형 Red-Green-Refactor 기준 + 일반 테스트 룰 (AAA, 명명, 한 상황만, mock 남용 금지, 단위/통합/E2E 구분) |
| 트랜잭션 / JPA / QueryDSL 흐름 분석 | `/analyze-query` | 사용자 관점의 일관성, 트랜잭션 범위, readOnly, 영속성 컨텍스트, flush/지연 로딩, 조회/쓰기 혼합 리스크 |

## Stack & Tooling

- Java 21 (Gradle toolchain), Spring Boot 3.4.4, Spring Cloud 2024.0.1
- Gradle (Kotlin DSL) 멀티 모듈, 그룹 `com.loopers`, 버전은 short git hash (`build.gradle.kts:5`)
- JPA + QueryDSL (jakarta), MySQL 8 — `ddl-auto=create` 는 `local`/`test` 한정
- Redis 7 master/replica (lettuce). 기본 템플릿 `READ_FROM=REPLICA_PREFERRED`, master qualifier 는 `MASTER`
- Kafka 3.5 (KRaft) — 공용 `BATCH_LISTENER` 팩토리는 `modules/kafka`
- 테스트: JUnit 5, Mockito, springmockk, Instancio, Testcontainers (MySQL/Redis)

## Common Commands

```powershell
# 인프라 (MySQL/Redis master+replica/Kafka/Kafka UI)
docker-compose -f .\docker\infra-compose.yml up
# 모니터링 (Grafana http://localhost:3000, admin/admin)
docker-compose -f .\docker\monitoring-compose.yml up

# 빌드 / 테스트
.\gradlew build -x test
.\gradlew test
.\gradlew :apps:commerce-api:test
.\gradlew :apps:commerce-api:test --tests com.loopers.domain.example.ExampleTest
.\gradlew :apps:commerce-api:jacocoTestReport

# 앱 실행 (기본 프로필 local)
.\gradlew :apps:commerce-api:bootRun
.\gradlew :apps:commerce-streamer:bootRun
.\gradlew :apps:commerce-batch:bootRun --args="--job.name=demoJob"
```

테스트 JVM 은 `Asia/Seoul`, `spring.profiles.active=test`, `maxParallelForks=1` 로 고정 (`build.gradle.kts:80`). Swagger UI: `http://localhost:8080/swagger-ui.html` (`prd`/`qa` 비활성).

## Git Workflow

### Branch Naming

새 브랜치는 아래 prefix + kebab-case 설명으로 생성. 작업 종류에 맞는 prefix 선택.

| Prefix | 용도 | 예시 |
|---|---|---|
| `feature/` | 신규 기능 | `feature/manual-product-management` |
| `fix/` | 버그 수정 | `fix/delete-request-400-error` |
| `refactor/` | 기능 변경 없는 구조 개선 | `refactor/manual-product-dto` |
| `test/` | 테스트 코드 추가/수정 | `test/manual-product-service` |
| `docs/` | 문서 수정 | `docs/api-spec` |
| `chore/` | 설정, 빌드, 의존성 등 | `chore/update-dependencies` |
| `hotfix/` | 운영 긴급 수정 | `hotfix/login-token-error` |

### Commit Message

Conventional Commits 형식 `<type>: <한글 설명>`. **type 은 영어, 본문은 한글**. "무엇을 했는지" 가 아니라 "왜/무슨 변경인지" 가 드러나게 (`수정`, `작업중` 같은 의미 없는 메시지 금지).

| Type | 용도 |
|---|---|
| `feat` | 기능 추가 |
| `fix` | 버그 수정 |
| `refactor` | 리팩토링 (동작 변경 없음) |
| `test` | 테스트 추가/수정 |
| `docs` | 문서 수정 |
| `style` | 포맷팅, 세미콜론 등 |
| `chore` | 빌드, 설정, 의존성 |
| `perf` | 성능 개선 |
| `revert` | 커밋 되돌림 |

예시: `feat: 회원 가입 API 추가`, `fix: 상품 저장 시 boolean 변환 오류 수정`, `refactor: 상품 ID 변환 로직 분리`.

## Multi-Module Layout

루트 빌드는 모든 서브 프로젝트에 `java`/`spring-boot`/`dependency-management`/`jacoco` 적용, 컨테이너 프로젝트(`apps`/`modules`/`supports`) task 는 비활성. **부모가 `apps` 인 모듈만 `bootJar`** 생성, 나머지는 일반 `jar` (`build.gradle.kts:75`).

```
apps/        실행 가능 Spring Boot 앱 (bootJar)
  commerce-api        REST API (servlet, springdoc)
  commerce-batch      Spring Batch (web=none), `spring.batch.job.name` 으로 잡 1개씩
  commerce-streamer   Kafka 컨슈머
modules/     재사용 설정 (java-library)
  jpa                 Datasource/JPA/QueryDSL + BaseEntity + jpa.yml + testFixtures(Testcontainers, DatabaseCleanUp)
  redis               Master/replica Lettuce + testFixtures(Testcontainers, RedisCleanUp)
  kafka               공용 producer/consumer/listener-factory
supports/    add-on (java-library)
  jackson, logging, monitoring   (logging/monitoring 은 리소스 전용)
```

각 앱은 `spring.config.import` 로 모듈 yml(`jpa.yml`, `redis.yml`, `kafka.yml`, `logging.yml`, `monitoring.yml`) 을 가져옴. 새 모듈 설정은 `src/main/resources/<name>.yml` + 앱의 `import` 항목 추가.

## App Architecture (commerce-api)

본 프로젝트는 레이어드 아키텍처를 따르며, 도메인이 구현 기술에 직접 의존하지 않도록 Repository port-adapter 방식으로 DIP 를 지킨다. 패키지 기본 구조는 `<feature>/<layer>` 이다.

- `<feature>/interfaces/api` — feature별 HTTP request/response DTO, Controller.
- `<feature>/application` — `*Facade`, `*Command`, `*Info`, 유스케이스 조합과 도메인 결과 변환.
- `<feature>/domain` — 도메인 객체/엔티티, 도메인 서비스, `*Repository` 인터페이스.
- `<feature>/infrastructure` — JPA/Redis/Kafka/외부 API 구현체와 `*RepositoryImpl`.
- `shared/presentation` — 여러 feature 에서 공유하는 HTTP 응답 envelope(`ApiResponse`, `PageResponse`)과 controller advice.
- `shared/<concern>` — 여러 feature 에 걸친 공통 관심사. 예: `shared/error`, `shared/pagination`.

`Request`/`Response` 명명은 HTTP 입출력을 표현하는 `<feature>/interfaces/api` 에만 사용한다. 여러 feature 에서 공유하는 HTTP 응답 envelope/advice 는 `shared/presentation` 에 둔다. `application` 입력은 `SignUpCommand`, `CreateOrderCommand` 처럼 `Request` 를 빼고 유스케이스 의미로 이름 짓는다.

입력 검증은 가장 가까운 진입 경계에서 처리한다. HTTP request shape 검증은 `<feature>/interfaces/api` 의 DTO/Controller 에서 `@Valid` 와 Bean Validation 으로 처리하고, 인증 사용자 식별은 인증 필터/시큐리티 경계가 책임진다. `application` 의 `Facade` 는 adapter 가 만든 유효한 `Command` 를 받아 도메인 서비스와 도메인 객체를 조합하는 흐름에 집중하며, 이미 위 레이어에서 보장한 `command == null`, `productId == null`, `quantity <= 0`, 인증 사용자 null 같은 검증을 중복 작성하지 않는다. 단, Entity/VO/Domain Service 의 핵심 invariant 는 항상 도메인 레이어에 남긴다.

HTTP 외에 Batch/Kafka/internal job 이 같은 유스케이스를 직접 호출한다면, 그 adapter 경계에서 별도로 검증하거나 Command 생성 경로를 안전하게 만든다. "언젠가 다른 호출자가 생길 수 있다"는 이유만으로 Facade 에 광범위한 방어 검증을 추가하지 않는다.

응답은 모두 `ApiResponse<T>` (`Metadata{result, errorCode, message}` + `data`). `CoreException(ErrorType, msg)` 는 `ApiControllerAdvice` 가 `ApiResponse.fail(...)` 로 매핑하므로 컨트롤러에서 catch 하지 말고 advice 를 확장한다.

엔티티는 `modules/jpa` 의 `BaseEntity` 를 상속한다: IDENTITY id, `createdAt`/`updatedAt`/`deletedAt` (soft delete), `@PrePersist`/`@PreUpdate` 에서 호출되는 `protected guard()` 훅. `delete()`/`restore()` 는 멱등.

Aggregate 내부의 생명주기가 함께 움직이는 구성요소는 객체 연관관계로 표현한다. 예: `Order -> OrderItem`. 다른 Aggregate 를 참조할 때는 JPA 연관관계 대신 ID 또는 주문 스냅샷 같은 값으로 연결해 결합을 낮춘다. 예: `Product.brandId`, `ProductStock.productId`, `Like.productId`, `OrderItemSnapshot.productId`. 단순 조회 조인은 Repository/Query 단계에서 처리하고, 도메인 모델에 다른 Aggregate 객체 참조를 편의상 추가하지 않는다.

도메인 책임, 패키지 경계, Facade/Service 트랜잭션 위치 판단은 `/design` 을 따른다.

## Test Conventions

- **단정문은 AssertJ `assertThat`, 모킹은 Mockito (Spring 컨텍스트 보조는 `springmockk`), 프레임워크는 JUnit 5.** 다른 단정/모킹 라이브러리 도입 금지.
- Testcontainers (MySQL/Redis) 사용 → Docker 필수. `test` 프로필도 `ddl-auto=create`.
- E2E: `*ApiE2ETest` + `@SpringBootTest(webEnvironment = RANDOM_PORT)` + `TestRestTemplate`, `@AfterEach` 에서 `databaseCleanUp.truncateAllTables()` (JPA 메타모델 기반, FK 체크 끄고 truncate).
- 앱은 `testImplementation(testFixtures(project(":modules:jpa")))` 로 fixtures 사용.
- 테스트 `arrange` 에 들어가는 이름, 브랜드명, 상품명, 설명처럼 도메인 의미가 있는 예시 값은 작성 전에 사용자에게 확인한다. 단순 경계값(`null`, blank, `0`, `-1`) 은 바로 사용 가능.
- 네이밍: 도메인 단위 테스트는 도메인 클래스명 + `Test` (예: `ProductTest`), 통합 테스트는 `*ServiceIntegrationTest`, E2E 는 `*ApiE2ETest`. DisplayName 한국어, HTTP 엔드포인트별 `@Nested`.

## Local Profiles

`spring.profiles.active`: `local` (bootRun 기본), `test` (Gradle 강제), `dev`, `qa`, `prd`. 로컬 DB: `application/application @ jdbc:mysql://localhost:3306/loopers`. `prd` 는 springdoc 비활성.
