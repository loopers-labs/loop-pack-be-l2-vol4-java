# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 프로젝트 개요

루퍼스 4기 백엔드 학습용 Spring Boot 멀티모듈 템플릿 (`loopers-java-spring-template`, group `com.loopers`).

### 기술 스택 / 버전

| 영역 | 버전 |
| --- | --- |
| Java toolchain | **21** |
| Spring Boot | **3.4.4** |
| Spring Dependency Management | 1.1.7 |
| Spring Cloud BOM | 2024.0.1 |
| Spring Kafka | 부모 BOM 따름 |
| Spring Batch | 부모 BOM 따름 |
| QueryDSL | `com.querydsl:querydsl-jpa::jakarta` (APT) |
| Hibernate / Data JPA | Spring Boot 부모 BOM |
| MySQL Driver | `mysql-connector-j` (runtime), MySQL 서버 8.0 |
| Redis | `spring-boot-starter-data-redis`, 서버 Redis 7.0 (master + read replica) |
| Kafka | `spring-kafka` 3.5.1 (KRaft) |
| SpringDoc OpenAPI | 2.7.0 |
| Lombok | Spring Boot 부모 BOM |
| Micrometer Prometheus | Spring Boot 부모 BOM |
| Logback Slack Appender | 1.6.1 |
| JUnit Platform | Spring Boot 부모 BOM |
| Mockito | 5.14.0 |
| SpringMockK | 4.0.2 |
| Instancio | 5.0.2 |
| Testcontainers | MySQL / Redis / Kafka |

`gradle.properties`가 단일 진실의 원천이다 — 버전을 바꿀 때는 여기에서 바꾼다.

### 모듈 구성 (`settings.gradle.kts`)

```
apps/        ← 실행 가능한 @SpringBootApplication (BootJar = true, plain Jar = false)
  commerce-api       REST API 진입점 (구현 주 대상). Web + Actuator + SpringDoc.
  commerce-batch     Spring Batch 잡 실행기.
  commerce-streamer  Kafka 컨슈머 애플리케이션.
modules/     ← 재사용 가능한 설정 라이브러리 (java-library + java-test-fixtures)
  jpa     Data JPA, QueryDSL APT, Hikari DataSource, BaseEntity, Testcontainers(MySQL) fixture
  redis   Data Redis, master/replica 라우팅, Testcontainers(Redis) fixture
  kafka   Spring Kafka, Testcontainers(Kafka) fixture
supports/    ← 부가 기능 add-on
  jackson      JSR310 + Kotlin 모듈
  logging      logback + Slack appender 설정 (logback.xml)
  monitoring   Actuator + Prometheus + Micrometer Tracing (Brave)
```

루트 `build.gradle.kts`의 `subprojects` 블록이 모든 하위 모듈에 공통 의존성과 테스트 설정을 주입한다. `apps/`만 BootJar를 빌드하고, 나머지는 일반 jar를 빌드해 다른 모듈이 의존할 수 있게 한다.

### 아키텍처 컨벤션 (`commerce-api`)

패키지는 헥사고날 풍의 레이어로 나뉜다:

```
com.loopers
  interfaces.api.<domain>     ← Controller, V1Dto, V1ApiSpec(SpringDoc)
  application.<domain>        ← Facade (유스케이스), Info (DTO)
  domain.<domain>             ← Service, Model(@Entity), Repository 인터페이스
  infrastructure.<domain>     ← RepositoryImpl, JpaRepository (Spring Data)
  support.error               ← CoreException + ErrorType
```

호출 방향은 **항상** `interfaces → application → domain → infrastructure`이다. `domain.Repository`는 추상 인터페이스이고 `infrastructure.RepositoryImpl`이 `JpaRepository`를 위임 구현한다 — 도메인이 JPA에 직접 의존하지 않는다.

핵심 공통 컴포넌트:
- `BaseEntity` (`modules/jpa`): 모든 엔티티의 부모. `id`, `createdAt`, `updatedAt`, `deletedAt` 자동 관리. 검증이 필요하면 `guard()`를 오버라이드한다 (`@PrePersist`/`@PreUpdate`에서 호출됨). `delete()`/`restore()`는 멱등.
- `CoreException` + `ErrorType` (`BAD_REQUEST`, `NOT_FOUND`, `CONFLICT`, `INTERNAL_ERROR`): 도메인/서비스에서 던지는 단일 예외. 메시지는 `customMessage`로 override 가능.
- `ApiControllerAdvice`: `CoreException` 및 Spring 표준 예외(타입 mismatch, JSON 파싱 등)를 `ApiResponse.fail(...)`로 변환.
- `ApiResponse<T>` (record): `meta(result, errorCode, message)` + `data`. 컨트롤러는 항상 이 래퍼로 응답한다.
- `ExampleV1*`이 위 레이어/네이밍 규약의 정식 참조 구현이다 — 새 도메인을 추가할 때 이 패턴을 그대로 따른다.

### 설정 / 프로파일

`apps/commerce-api/src/main/resources/application.yml`이 모듈별 yml을 import한다 (`jpa.yml`, `redis.yml`, `logging.yml`, `monitoring.yml`). 프로파일: `local`(기본), `test`, `dev`, `qa`, `prd`.

- `local`/`test`: MySQL `ddl-auto: create`, Redis는 localhost 6379/6380으로 고정.
- `prd`/`qa`/`dev`: 호스트/계정은 환경변수(`MYSQL_HOST`, `MYSQL_PORT`, `MYSQL_USER`, `MYSQL_PWD`, `REDIS_*_HOST/PORT`) 주입 전제.
- HikariCP prefix는 `datasource.mysql-jpa.main` — 다른 DataSource를 추가하려면 `DataSourceConfig`를 본떠 prefix만 바꾸면 된다.

### 테스트

- 루트 빌드 설정에서 `spring.profiles.active=test`, `user.timezone=Asia/Seoul`이 강제된다. `maxParallelForks=1`로 직렬 실행.
- `@SpringBootTest`는 자동으로 `MySqlTestContainersConfig`가 띄운 MySQL 8.0 컨테이너에 붙는다 (`static` 블록에서 시작하고 system property로 jdbc-url 주입).
- 통합/E2E 테스트는 매 `@AfterEach`에서 `DatabaseCleanUp.truncateAllTables()`를 호출해 격리. 새 통합 테스트도 동일하게 한다.
- E2E는 `@SpringBootTest(webEnvironment = RANDOM_PORT)` + `TestRestTemplate` + `ParameterizedTypeReference<ApiResponse<...>>` 패턴 (`ExampleV1ApiE2ETest` 참조).
- 테스트 클래스명 컨벤션: 단위 `*ModelTest` / `*ServiceTest`, 통합 `*IntegrationTest`, E2E `*ApiE2ETest`.

**테스트 스타일 — JUnit5 + AssertJ.**

- 단언은 AssertJ `assertThat(...)`을 기본으로 쓴다.
- 예외 단언은 AssertJ `assertThatThrownBy(...).isInstanceOf(...).extracting("...").isEqualTo(...)` 체인을 쓴다. JUnit의 `assertThrows`는 기본적으로 쓰지 않는다.
- 한 테스트에 단언이 여러 개면 JUnit `assertAll(() -> ..., () -> ...)`로 묶어 첫 실패에서 멈추지 않게 한다.
- `@DisplayName`은 행동을 한국어 평서문으로 적어 케이스 의도를 명세에 묶는다. 데이터 옆 인라인 주석으로 의도를 반복 설명하지 않는다.
- 기존 `ExampleModelTest`처럼 `assertThrows + assertThat`이 혼재된 코드는 본보기로 삼지 않는다.

---

## 자주 쓰는 명령어

빌드/품질:
```bash
./gradlew build                    # 전체 빌드 + 테스트 + jacoco
./gradlew :apps:commerce-api:build # 단일 모듈 빌드
./gradlew clean
```

테스트:
```bash
./gradlew test                                                   # 모든 모듈 테스트
./gradlew :apps:commerce-api:test                                # 단일 모듈
./gradlew :apps:commerce-api:test --tests "ExampleV1ApiE2ETest"  # 단일 클래스
./gradlew :apps:commerce-api:test --tests "*ApiE2ETest.Get.*"    # 중첩 클래스/메서드 패턴
./gradlew :apps:commerce-api:jacocoTestReport                    # 커버리지 XML 리포트
```

애플리케이션 실행:
```bash
./gradlew :apps:commerce-api:bootRun           # 기본 local 프로파일
SPRING_PROFILES_ACTIVE=dev ./gradlew :apps:commerce-api:bootRun
```

로컬 인프라 (실행 전 반드시 띄울 것):
```bash
docker-compose -f ./docker/infra-compose.yml up        # MySQL(3306), Redis master(6379)/replica(6380), Kafka(9092/19092), Kafka UI(9099)
docker-compose -f ./docker/monitoring-compose.yml up   # Grafana(3000, admin/admin) + Prometheus
```

문서/엔드포인트:
- Swagger UI: `http://localhost:8080/swagger-ui.html` (`local`/`dev`/`qa`만 활성, `prd`는 비활성)
- Actuator: `http://localhost:8081/actuator/health`, `/actuator/prometheus` (관리 포트 8081)

---

## 작업 원칙 (항상 필수로 따를 것)

> **트레이드오프:** 이 원칙들은 속도보다 신중함을 우선한다. 사소한 작업에는 판단력을 발휘하라.

### 1. 코드를 짜기 전에 생각하라

**가정하지 말고, 혼란을 숨기지 말고, 트레이드오프를 드러내라.**

구현 전에:
- 가정을 명시적으로 말하라. 불확실하면 묻는다.
- 해석이 여러 가지 가능하다면 모두 제시한다 — 조용히 하나를 고르지 않는다.
- 더 단순한 접근이 있으면 말한다. 정당한 이유가 있을 땐 반박한다.
- 불분명한 게 있으면 멈춰라. 무엇이 헷갈리는지 짚고, 묻는다.

### 2. 단순함이 우선이다

**문제를 해결하는 최소한의 코드. 추측성 코드 금지.**

- 요청되지 않은 기능을 덧붙이지 않는다.
- 1회용 코드에 추상화를 두지 않는다.
- 요청되지 않은 "유연성"이나 "설정 가능성"을 만들지 않는다.
- 일어날 수 없는 시나리오에 대한 예외 처리를 넣지 않는다.
- 200줄을 썼는데 50줄로 가능하다면, 다시 쓴다.

스스로 물어라: "시니어 엔지니어가 보면 이거 과하다고 할까?" 그렇다면 단순화한다.

### 3. 외과적으로 변경하라

**필요한 곳만 손댄다. 본인이 만든 흔적만 정리한다.**

기존 코드를 수정할 때:
- 주변 코드, 주석, 포매팅을 "개선"하지 않는다.
- 부서지지 않은 것을 리팩터링하지 않는다.
- 본인 취향과 다르더라도 기존 스타일에 맞춘다.
- 무관한 데드 코드를 발견했다면 — 삭제하지 말고 언급만 한다.

본인 변경이 고아를 만든 경우:
- 본인 변경 때문에 사용처가 사라진 import/변수/함수만 정리한다.
- 요청받지 않은 이상, 기존부터 있던 데드 코드는 제거하지 않는다.

테스트: 변경된 모든 줄이 사용자의 요청까지 직접 거슬러 올라갈 수 있어야 한다.

### 4. 목표 주도로 실행하라

**성공 기준을 정의하라. 검증될 때까지 루프를 돌려라.**

작업을 검증 가능한 목표로 바꿔라:
- "검증 추가" → "잘못된 입력에 대한 테스트를 쓰고, 통과시킨다"
- "버그 수정" → "버그를 재현하는 테스트를 쓰고, 통과시킨다"
- "X 리팩터" → "리팩터 전후로 모든 테스트가 통과함을 확인한다"

여러 단계 작업이라면 짧은 계획을 명시한다:
```
1. [단계] → 검증: [확인 방법]
2. [단계] → 검증: [확인 방법]
3. [단계] → 검증: [확인 방법]
```

강한 성공 기준은 독립적인 루프를 가능하게 한다. 약한 기준("작동하게 만들어")은 끊임없는 질의응답을 부른다.

**이 원칙들이 작동하고 있다는 신호:** diff에 불필요한 변경이 줄어든다 / 과설계로 인한 재작성이 줄어든다 / 실수 후가 아니라 구현 전에 명확화 질문이 나온다.

### 5. 표현 스타일

코드의 행위와 별개로, 다음 표현 규칙은 본 프로젝트에서 일관되게 따른다.

- **매직넘버 상수화**: 도메인 규칙의 임계값(길이 상·하한, 카테고리 개수 등)은 `private static final` 상수로 추출한다. 사용자 노출 메시지 안의 숫자도 가능한 한 같은 상수를 결합해 표현한다. `0`, `1` 같은 트리비얼 케이스와 테스트 데이터는 강제하지 않는다.
- **문자열 포매팅**: 변수가 들어가는 문자열은 `+` 결합 대신 `String.format("...%d~%d...", a, b)`로 작성한다. 예외 메시지·사용자 노출 텍스트가 대상. 로그 메시지의 SLF4J `{}` 플레이스홀더는 별개.
- **에러 메시지는 사유별로 분리**: 한 검증에 사유가 둘 이상이면 통합 메시지보다 사유별 메시지가 사용자 친화적이다. 예: "로그인 ID는 4~20자만 허용됩니다." 와 "로그인 ID는 영문 및 숫자만 허용됩니다." 처럼 분리한다.
- **장식적 주석 금지**: 데이터 옆 인라인 주석(`"abcd", // 4자 최소`), 표시용 어노테이션 옵션(`@ParameterizedTest(name = "...")`), 가독성 위한 잉여 빈 줄은 넣지 않는다. 의도는 `@DisplayName`·plan.md·채팅에 담는다.
- **정적 팩토리 메서드 네이밍** (Effective Java 권고):
  - 매개변수 **하나** → `from(X)`. 예: `LoginId.from("kyle123")`, `Email.from("kyle@example.com")`.
  - 매개변수 **여러 개** → `of(X, Y, ...)`. 예: `UserModel.of(loginId, name, email, ...)`.

---

## 스킬 자동 트리거

다음 작업은 `.claude/skills/tdd-helper/SKILL.md`가 자동으로 활성화된다:

- 새 기능 구현 (도메인·유스케이스·API 추가)
- 버그 픽스 / 결함 수정
- 리팩터링 (Tidy First 적용)
- 명시적 TDD 사이클 진행 (`plan.md` + "go")

위 작업의 워크플로우(Red-Green-Refactor, 3A, outside-in 레이어 순서), Tidy First, Never Do, Priority는 모두 그 스킬에 있다. 이 문서는 프로젝트 컨벤션과 메타 작업 원칙만 다룬다.

**커밋 작업은 항상 `smart-commit` 스킬을 거친다.** Claude는 `git commit`을 직접 호출하지 않는다 — 커밋 단위 분리, 메시지 작성, Tidy First 라벨, 커밋 전 게이트(테스트·경고 체크)는 모두 smart-commit이 담당한다.
