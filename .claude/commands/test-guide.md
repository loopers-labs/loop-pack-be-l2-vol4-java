# 테스트 가이드

## 테스트 피라미드

```
         /‾‾‾‾‾‾‾\
        /   E2E   \       ← 적게, 핵심 시나리오만
       /‾‾‾‾‾‾‾‾‾‾‾\
      /   통합 테스트   \    ← 레이어 연결 검증
     /‾‾‾‾‾‾‾‾‾‾‾‾‾‾‾\
    /   단위 테스트    \   ← 많이, 빠르게
   /‾‾‾‾‾‾‾‾‾‾‾‾‾‾‾‾‾‾‾\
```

| 계층 | 답하는 질문 |
| --- | --- |
| 단위 | "이 도메인 규칙/분기가 의도대로 동작하는가?" |
| 통합 | "내 코드 + 프레임워크 + DB가 진짜 환경에서 맞물려 도는가?" |
| E2E | "외부에서 본 API 계약(HTTP/JSON/Status)이 지켜지는가?" |

---

## 공통 작성 규칙

### AAA 패턴

```
Arrange (준비)  — 테스트에 필요한 입력/상태를 만든다
Act     (실행)  — 검증하려는 메서드를 호출한다 (보통 한 줄)
Assert  (검증)  — 결과 또는 상태 변화를 확인한다
```

### 테스트 명명 규칙 — `<기대결과>_when<조건>`

```
throwsBadRequest_whenEmailFormatIsInvalid()
returnsMaskedName_whenLoginIdExists()
throwsConflict_whenLoginIdAlreadyExists()
```

---

## 1. 단위 테스트 (Unit Test)

**파일 패턴:** `domain/*ModelTest`

- Spring 컨텍스트 없음 — 순수 JVM
- DB / 네트워크 / 외부 의존성 없음
- 테스트 더블로 모든 협력자를 대체
- 빠름 (밀리초 단위)

```java
class UserModelTest {
    @Test
    void throwsBadRequest_whenEmailFormatIsInvalid() {
        // arrange
        String invalidEmail = "invalid-email";
        // act
        CoreException ex = assertThrows(CoreException.class, () ->
            new UserModel(VALID_LOGIN_ID, VALID_PASSWORD, VALID_NAME, VALID_BIRTH, invalidEmail)
        );
        // assert
        assertThat(ex.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
    }
}
```

**무엇을 테스트하나**
- 생성자의 유효성 검증 (null, 포맷 체크)
- 도메인 상태 변경 메서드 (`changePassword()`, `update()` 등)
- 도메인 규칙 위반 시 올바른 예외(`CoreException`) 발생

---

## 2. 통합 테스트 (Integration Test)

**파일 패턴:** `domain/*ServiceIntegrationTest`

- `@SpringBootTest` — Spring 컨텍스트 전체 로드
- Testcontainers로 실제 MySQL 컨테이너
- `@AfterEach` 에서 `DatabaseCleanUp.truncateAllTables()` 로 격리
- 보통 (초 단위)

```java
@SpringBootTest
class UserServiceIntegrationTest {
    @Autowired private UserService userService;
    @Autowired private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() { databaseCleanUp.truncateAllTables(); }

    @Test
    void throwsConflict_whenLoginIdAlreadyExists() {
        userService.register(new UserModel("testuser", ...));
        CoreException ex = assertThrows(CoreException.class, () ->
            userService.register(new UserModel("testuser", ...))
        );
        assertThat(ex.getErrorType()).isEqualTo(ErrorType.CONFLICT);
    }
}
```

**무엇을 테스트하나**
- 비즈니스 로직이 실제 DB와 맞물려 의도대로 동작하는지
- 중복 데이터, 조회 실패 등 DB 의존적 시나리오
- 트랜잭션 경계 / `@Transactional` dirty checking

---

## 3. E2E 테스트 (End-to-End Test)

**파일 패턴:** `interfaces/api/*E2ETest`

- `@SpringBootTest(webEnvironment = RANDOM_PORT)` — 실제 서버 포트
- `TestRestTemplate` 으로 진짜 HTTP 요청 전송
- 가장 느림 (수십 초)

```java
@SpringBootTest(webEnvironment = RANDOM_PORT)
class UserV1ApiE2ETest {
    @Autowired private TestRestTemplate testRestTemplate;

    @Test
    void returnsRegisteredUser_whenValidRequest() {
        // arrange
        UserV1Dto.RegisterRequest req = new UserV1Dto.RegisterRequest(...);
        // act
        ResponseEntity<ApiResponse<UserV1Dto.RegisterResponse>> response =
            testRestTemplate.exchange(ENDPOINT, HttpMethod.POST, new HttpEntity<>(req), ...);
        // assert
        assertAll(
            () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
            () -> assertThat(response.getBody().data().loginId()).isEqualTo("testuser")
        );
    }
}
```

**무엇을 테스트하나**
- API 경로, HTTP 메서드, 요청/응답 포맷 매핑
- 잘못된 입력에 대한 HTTP 상태 코드 (400, 401, 409 등)
- JSON 직렬화/역직렬화, 헤더 처리 (`X-Loopers-LoginId`)
- ApiControllerAdvice가 도메인 예외를 올바른 status로 변환하는지

---

## 테스트 더블 (Test Doubles)

| 역할 | 목적 | 사용 방식 |
| --- | --- | --- |
| **Dummy** | 자리만 채움 (사용되지 않음) | 인자에 그냥 넘김 |
| **Stub** | 고정된 응답 제공 (상태 기반) | `when().thenReturn()` |
| **Mock** | 호출 여부/횟수 검증 (행위 기반) | `verify(...)` |
| **Spy** | 진짜 객체 감싸기 + 일부 조작 | `spy()`, `@MockitoSpyBean` |
| **Fake** | 실제처럼 동작하는 가짜 구현체 | 직접 클래스 구현 |

> `Mockito.mock()`은 **도구**, `Mock`은 테스트 더블 5형제 중 **역할(개념)**. 한 mock 객체에 Stub과 Mock 역할을 동시에 부여할 수 있다.

---

## TDD 진행 (Red → Green → Refactor)

| 단계 | 목표 | 만들어야 하는 것 | 만들면 안 되는 것 |
| --- | --- | --- | --- |
| **RED** | 실패하는 테스트 1개 작성 | 컴파일될 정도의 최소 스켈레톤 | 검증 로직, 비즈니스 규칙 |
| **GREEN** | 그 테스트만 통과시키기 | 테스트를 통과시킬 최소 코드 | 미래 테스트를 위한 코드 |
| **REFACTOR** | 중복 제거 / 가독성 개선 | 없음 (구조만 정리) | 새로운 기능, 새로운 검증 |

### 계층별 RED 직전 준비물

**단위 테스트 (Model):**
- `domain/<도메인>Model.java` — 필드 + 생성자 (검증 로직 없이)

**통합 테스트 (Service):**
- `domain/<도메인>Repository.java` (인터페이스)
- `infrastructure/<도메인>JpaRepository.java`
- `infrastructure/<도메인>RepositoryImpl.java`
- `domain/<도메인>Service.java` (메서드 시그니처만)

**E2E 테스트 (API):**
- `interfaces/api/<도메인>V1Dto.java`
- `interfaces/api/<도메인>V1ApiSpec.java`
- `interfaces/api/<도메인>V1Controller.java` (빈 메서드)
- `application/<도메인>Facade.java` (시그니처만)
- `application/<도메인>Info.java`

### 체크리스트

- [ ] **RED 시작 전**: 스켈레톤이 "테스트 컴파일을 위한 최소한"인가? 검증 로직을 미리 넣지 않았는가?
- [ ] **RED 확인**: 정말 실패하는가? (`AssertionError` / 예외 미발생 등)
- [ ] **GREEN 시작 전**: 추가하는 코드가 "이 테스트를 통과시키는 데 꼭 필요한" 코드인가?
- [ ] **GREEN 확인**: 이전 모든 테스트도 여전히 통과하는가?
- [ ] **REFACTOR 확인**: 리팩토링 후에도 모든 테스트가 통과하는가? 새 기능을 끼워넣지 않았는가?

---

## 비교 요약

| | 단위 테스트 | 통합 테스트 | E2E 테스트 |
|---|---|---|---|
| **파일 패턴** | `*ModelTest` | `*ServiceIntegrationTest` | `*E2ETest` |
| **검증 범위** | Model 클래스 1개 | Service + Repository + DB | HTTP → DB 전체 |
| **Spring 컨텍스트** | ❌ | ✅ | ✅ |
| **실제 DB** | ❌ | ✅ Testcontainers | ✅ Testcontainers |
| **실제 HTTP** | ❌ | ❌ | ✅ TestRestTemplate |
| **속도** | 빠름 (ms) | 보통 (수 초) | 느림 (수십 초) |
| **테스트 더블** | 적극 사용 (Fake / Stub) | 제한적 (Spy 정도) | 거의 안 씀 |
