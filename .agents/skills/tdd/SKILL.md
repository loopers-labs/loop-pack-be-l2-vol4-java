---
name: tdd
description: Use when implementing or changing behavior in this Java/Spring repository where domain rules, state changes, branching, persistence boundaries, or regressions need test-first confidence; also when the user asks for "TDD", "테스트 먼저", or "Red-Green-Refactor".
user-invocable: true
---

# /tdd — 실전형 Red-Green-Refactor

테스트가 설계를 이끌어야 하는 변경에서 사용한다. 핵심은 실패 테스트로 기대 행위를 고정하고, 최소 구현으로 통과시킨 뒤, 동작을 유지하며 정리하는 것이다.

## 적용 기준

사용자가 명시적으로 TDD 를 요청하면 아래 사이클을 따른다. 그 외에는 변경 위험도에 따라 적용 강도를 고른다.

| 강도 | 대상 | 방식 |
|---|---|---|
| 반드시 적용 | 도메인 규칙, 상태 전이, 금액/날짜 계산, 권한/중복/예외 조건, 버그 수정, 핵심 로직 리팩토링 | 실패 테스트부터 시작 |
| 권장 | application orchestration, Repository/트랜잭션 경계, API 에러 계약 | 가장 위험한 행위부터 테스트 |
| 판단 가능 | 단순 DTO 필드, 단순 매핑, 설정/문서, 이미 검증된 얇은 위임 코드 | 기존 테스트 확인 또는 필요한 단정만 보강 |

## 사이클

한 사이클은 하나의 행위를 다룬다. 작은 변경은 빠르게 돌고, 복잡한 변경은 행위 목록을 먼저 쪼갠다.

### RED — 실패하는 테스트 작성

1. 이번 사이클에서 검증할 행위를 한 문장으로 정리한다.
2. 가능한 가장 안쪽 레이어에서 테스트한다. 도메인 규칙은 도메인 단위 테스트, 트랜잭션/쿼리는 통합 테스트, HTTP 계약은 E2E 테스트로 둔다.
3. `arrange` 에 도메인 의미가 있는 예시 값이 들어가면 테스트 작성 전에 사용자에게 어떤 값을 넣을지 확인한다. 사용자가 위임하면 자연스러운 도메인 예시를 선택한다.
4. 그 행위만 검증하는 테스트를 작성하고 실행해 실제 실패를 확인한다.
5. 실패 사유가 의도한 사유인지 확인한다. 컴파일 에러, 잘못된 셋업, NPE 같은 실패면 테스트를 먼저 고친다.

### GREEN — 통과하는 최소 코드 작성

1. 현재 실패 테스트를 통과시키는 데 필요한 최소 구현만 작성한다.
2. 미래 요구사항을 위한 분기, 추상화, 방어 코드를 미리 넣지 않는다.
3. 대상 테스트를 먼저 통과시킨 뒤, 영향 범위에 맞는 클래스/모듈 테스트를 실행한다.

### REFACTOR — 행위 유지, 구조 정리

1. 동작은 그대로 두고 이름, 중복, 책임 위치, 테스트 가독성만 정리한다.
2. 새 행위나 새 예외 조건은 추가하지 않는다. 필요하면 다음 RED 로 넘긴다.
3. 정리 후 테스트를 다시 실행해 GREEN 을 유지한다.

## 기존 코드 / 버그 수정

- 기존 구현을 무조건 지우고 다시 시작하지 않는다. 먼저 현재 동작을 고정하는 characterization test 또는 regression test 를 작성한다.
- 버그 수정은 버그를 재현하는 실패 테스트를 먼저 만든 뒤 수정한다.
- 기존 테스트가 이미 깨져 있으면 새 RED 와 기존 실패를 구분한다. 구분이 안 되면 멈추고 사용자에게 공유한다.
- 리팩토링은 행위 보존 테스트가 있거나, 먼저 테스트를 보강한 뒤 진행한다.

## 테스트가 어려울 때 보는 설계 신호

- Mock 이 많아지면 책임이 섞였는지, 도메인 규칙이 application/service 에 흩어졌는지 확인한다.
- Spring 컨텍스트 없이는 테스트가 어려운 도메인 규칙은 도메인 객체나 도메인 서비스로 옮길 수 있는지 본다.
- 외부 의존 때문에 테스트가 복잡하면 port 를 두고 Fake/Stub 으로 대체할 수 있는지 본다.
- 테스트 셋업이 검증 행위보다 커지면 테스트 레이어를 잘못 골랐을 수 있다.

## 안티패턴 — 발견하면 사이클 복원

- 테스트 없이 핵심 로직을 먼저 작성.
- 한 번에 여러 행위의 테스트를 작성.
- 실패 확인 없이 GREEN 부터 진행.
- GREEN 단계에서 새 기능이나 미래 분기를 추가.
- REFACTOR 단계에서 새 행위, 새 예외 조건, 새 정책을 추가.

## 멈추고 질문해야 하는 상황

- 무슨 행위를 테스트해야 할지 한 문장으로 정리되지 않음.
- 테스트 작성에 필요한 입력 형식·외부 의존이 불분명.
- 기존 테스트가 이미 깨져 있어 RED 가 의도한 실패인지 구분 안 됨.
- 요구사항이 너무 커서 한 사이클로 쪼개기 모호함 → 하위 행위 목록을 제안하고 우선순위 받기.

## Java / Spring 프로젝트 (이 저장소 전용 가이드)

이 저장소(loopers Spring multi-module) 에서 작업할 때 따른다.

**도구 고정**

- 프레임워크: JUnit 5 (`org.junit.jupiter.api.*`)
- 단정: AssertJ (`org.assertj.core.api.Assertions.assertThat`) 만 사용. `org.junit.jupiter.api.Assertions.assertEquals` 등 사용 금지.
- 모킹: Mockito (`@Mock`, `Mockito.when(...)`, `verify(...)`). Spring 컨텍스트 안에서는 `springmockk` 보조 사용.
- 데이터 픽스처: `Instancio` (가능하면 사용, 강제 아님).
- 통합/E2E: Testcontainers (이미 `modules/jpa`, `modules/redis` testFixtures 에 구성됨) → Docker 필수.

**외부 의존 분리**

- 도메인 단위 테스트는 Spring 컨텍스트, DB, Redis, Kafka, HTTP 에 의존하지 않는다.
- 외부 의존이 필요하면 Fake/Stub 을 우선 사용한다.
- Mockito 는 동작을 만들기 위한 가짜 구현이 과도하게 커지거나, 외부 호출 자체가 검증 대상일 때만 사용한다.

**테스트 레이어 선택 (한 사이클 = 한 레이어)**

| 검증 대상 | 테스트 종류 | 명명 | 컨텍스트 |
|---|---|---|---|
| 도메인 객체 invariant / 순수 로직 | 단위 | 도메인 클래스명 + `Test` (예: `ProductTest`) | Spring 미사용, 새 객체 직접 생성 |
| 서비스 + 레포지토리 경계 (트랜잭션·쿼리) | 통합 | `*ServiceIntegrationTest` | `@SpringBootTest` + Testcontainers |
| 컨트롤러까지 포함한 HTTP 행위 | E2E | `*ApiE2ETest` | `@SpringBootTest(webEnvironment = RANDOM_PORT)` + `TestRestTemplate` |

가능하면 **가장 안쪽 레이어** 에서 검증한다. 도메인 invariant 는 도메인 클래스명 + `Test` 에서, 컨트롤러 응답 포맷은 `*ApiE2ETest` 에서. 어디서 잡을지 모호하면 사용자에게 묻는다.

**본문 주석은 AAA 고정**

- 한 테스트 메서드를 `// arrange` → `// act` → `// assert` 3 블록으로 나눈다.
- 셋업이 없거나 act 와 assert 가 한 줄이면 `// act & assert` 로 합쳐도 OK.
- `// given` / `// when` / `// then` 주석은 사용하지 않는다 (이 저장소 컨벤션은 AAA, 기존 테스트가 모두 AAA 로 통일돼 있음).
- `arrange` 의 이름, 브랜드명, 상품명, 설명처럼 도메인 의미가 있는 값은 테스트 작성 전에 사용자에게 확인한다. 단순 경계값(`null`, blank, `0`, `-1`) 은 필요할 때 바로 사용한다.
- DisplayName 은 한국어 서술형 ("...할 경우, ...을 반환한다") — 이쪽은 GWT-like 문장이 자연스러움.

**E2E / 통합 테스트 골격**

```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class FeatureV1ApiE2ETest {
    @Autowired TestRestTemplate restTemplate;
    @Autowired DatabaseCleanUp databaseCleanUp;

    @AfterEach void tearDown() { databaseCleanUp.truncateAllTables(); }

    @DisplayName("POST /api/v1/...")
    @Nested
    class Post {
        @DisplayName("...할 경우, ...을 반환한다.")
        @Test
        void name() { /* arrange / act / assert */ }
    }
}
```

- DisplayName 은 한국어로, HTTP 엔드포인트별 `@Nested`.
- `@AfterEach` 의 `truncateAllTables()` 누락 금지 → 다음 테스트로 데이터가 샌다.

**도메인 invariant 사이클 예시**

```text
RED:   ProductTest.생성_시_가격이_음수면_BAD_REQUEST_예외 — assertThatThrownBy(...).isInstanceOf(CoreException.class)
GREEN: Product 생성자에 price < 0 가드 1 줄 추가
REFACTOR: 중복된 검증 메시지를 상수화, 또는 생략
```

**테스트 실행 명령** (PowerShell)

```powershell
# 한 테스트 메서드만
.\gradlew :apps:commerce-api:test --tests "com.loopers.domain.product.ProductTest.<methodName>"

# 한 클래스
.\gradlew :apps:commerce-api:test --tests com.loopers.domain.product.ProductTest

# 모듈 전체 (회귀 확인)
.\gradlew :apps:commerce-api:test
```

**예외 검증**

```java
assertThatThrownBy(() -> Product.builder().name(name).description(desc).price(-1L).stock(0).build())
    .isInstanceOf(CoreException.class)
    .extracting("errorType").isEqualTo(ErrorType.BAD_REQUEST);
```

`CoreException` + `ErrorType` 이외의 새 예외 타입을 만들지 않는다. `ApiResponse` / `ApiControllerAdvice` 가 이미 매핑을 처리하므로 컨트롤러에서 catch 하지 않는다.

## 보고 형식

매 사이클마다 의식적으로 보고하지 않는다. 긴 작업에서는 의미 있는 체크포인트만 짧게 공유한다.

- RED 확인: 어떤 행위가 어떤 이유로 실패했는지.
- 설계 판단: 테스트 레이어, Fake/Stub, 트랜잭션 경계 등 선택이 필요한 지점.
- GREEN/REFACTOR 결과: 변경 요약과 통과한 테스트.
- 최종 보고: 수정 범위, 실행한 검증 명령, 남은 리스크.

---

## 일반 테스트 작성 룰 (TDD 사이클 외 신규 테스트 작성 시에도 적용)

### A. 테스트는 **동작과 규칙** 을 검증한다

내부 구현 디테일이 아니라 결과/규칙을 검증.

```java
@Test
void appliesFreeShipping_whenOrderTotalIsAtLeast10000() {
    // arrange
    DeliveryFeeCalculator calculator = new DeliveryFeeCalculator();

    // act
    int fee = calculator.calculate(10_000);

    // assert
    assertThat(fee).isEqualTo(0);
}
```

### B. 테스트 명명 — 영어 메서드 + 한국어 DisplayName (이 저장소 컨벤션)

- Java 메서드명: 영어 camelCase (`returnsExampleInfo_whenValidIdIsProvided`).
- `@DisplayName`: 한국어 서술형 (`"존재하는 예시 ID를 주면, 해당 예시 정보를 반환한다."`).
- HTTP 엔드포인트별 `@Nested` (`class Get { ... }`, `class Post { ... }`).

### C. 하나의 테스트는 하나의 상황만

```java
// Bad — 여러 조건을 하나에 몰기
@Test void 주문_테스트() { /* 무료배송 + 쿠폰 + 포인트 + 취소 */ }

// Good
@Test void appliesFreeShipping_whenOrderTotalIsAtLeast10000() {}
@Test void rejectsExpiredCoupon() {}
```

### D. 핵심 비즈니스 규칙부터

모든 코드를 테스트하지 않는다. **변경 가능성이 높고 실수하면 위험한 규칙** 부터:

- 금액/날짜 계산
- 상태 전이
- 권한 검증
- 중복 검증
- 예외 조건
- 타입별 정책 분기
- 엔티티 매핑 규칙

### E. Mock 검증 남용 금지

`verify()` 는 리팩토링에 약한 테스트를 만든다. Fake/Stub 과 결과 상태 검증을 우선한다.

```java
// 가능하면 이쪽
assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);

// 외부 호출 자체가 중요할 때만 (메일 발송, 외부 API)
verify(mailSender).send(any(ApprovalMail.class));
```

Repository, PasswordHasher, Clock 처럼 테스트에서 고정 동작이 필요할 뿐인 의존성은 간단한 Fake/Stub 으로 대체할 수 있는지 먼저 본다.

### F. 단위 / 통합 / E2E 구분

| 종류 | 대상 | 명명 | 컨텍스트 |
|---|---|---|---|
| 단위 | 도메인 규칙·계산·검증 | 도메인 클래스명 + `Test` (예: `ProductTest`) | Spring 미사용 |
| 통합 | Repository 쿼리·JPA 매핑·트랜잭션 | `*ServiceIntegrationTest` | `@SpringBootTest` + Testcontainers |
| E2E | API 요청/응답·컨트롤러 행위 | `*ApiE2ETest` | `RANDOM_PORT` + `TestRestTemplate` |

### G. DB 의존 로직은 Testcontainers 전용

이 저장소는 H2 를 쓰지 않는다. JPA/QueryDSL/Native Query/날짜 함수/JSON 컬럼/Lock/제약조건 검증은 모두 `modules/jpa` testFixtures 의 Testcontainers MySQL 위에서 수행.

### H. AAA 본문 + GWT 금지 (재강조)

본문 주석은 `// arrange` → `// act` → `// assert` 만. `// given/when/then` 사용 금지 (TDD 사이클 외 일반 테스트도 동일).

### I. TDD 적용 기준 재강조

핵심 규칙, 상태 전이, 버그 수정, 복잡한 분기는 실패 테스트부터 시작한다.

단순 DTO, 매핑, 설정, 문서, 이미 검증된 얇은 위임 코드는 변경 위험도에 따라 테스트 보강 여부를 판단한다. 사용자가 명시적으로 `/tdd` 또는 "테스트 먼저" 를 요청하면 단순 작업이라도 사이클을 따른다.
