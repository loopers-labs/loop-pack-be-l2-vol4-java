# 테스트 가이드

## 🎯 학습 목표

- 기능 구현보다 먼저 테스트 코드를 작성해본다.
- 테스트 가능한 구조란 무엇인지 체감해본다.
- 회원 등록 / 조회 / 비밀번호 변경을 **테스트 주도로 구현**해본다.

---

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

각 계층은 서로 **다른 질문**에 답한다.

| 계층 | 답하는 질문 |
| --- | --- |
| 단위 | "이 도메인 규칙/분기가 의도대로 동작하는가?" |
| 통합 | "내 코드 + 프레임워크 + DB가 진짜 환경에서 맞물려 도는가?" |
| E2E | "외부에서 본 API 계약(HTTP/JSON/Status)이 지켜지는가?" |

어느 하나로 다른 둘을 대체할 수 없다. 그래서 비율을 조정해 함께 쓴다.

---

## 📐 공통 작성 규칙

### AAA 패턴 — 모든 테스트의 기본 골격

```
Arrange (준비)  — 테스트에 필요한 입력/상태를 만든다
Act     (실행)  — 검증하려는 메서드를 호출한다 (보통 한 줄)
Assert  (검증)  — 결과 또는 상태 변화를 확인한다
```

본 가이드의 모든 예제 코드는 `// arrange`, `// act`, `// assert` 주석으로 이 세 블록을 명시한다. 한 테스트 안에서 세 블록이 섞이면 실패 시 어디가 문제인지 추적이 어려워진다.

### 테스트 명명 규칙 — `<기대결과>_when<조건>`

```
throwsBadRequest_whenEmailFormatIsInvalid()
returnsMaskedName_whenLoginIdExists()
throwsConflict_whenLoginIdAlreadyExists()
```

- **앞**: 무슨 결과가 나와야 하는가 (행동/반환)
- **뒤**: 어떤 조건일 때인가

→ 실패 메시지만 봐도 "어떤 입력에 어떤 결과가 어긋났는지" 즉시 보인다.

---

## 🧱 1. 단위 테스트 (Unit Test)

**파일 패턴:** `domain/*ModelTest`
**예시:** `UserModelTest`

### 목적
도메인 모델 한 클래스의 **순수 로직과 규칙**을 검증한다.

### 환경
- Spring 컨텍스트 없음 — 순수 JVM
- DB / 네트워크 / 외부 의존성 없음
- **테스트 더블**로 모든 협력자를 대체
- 빠름 (밀리초 단위)

### 예시 — 회원가입 도메인
```java
class UserModelTest {

    @DisplayName("이메일 형식이 올바르지 않으면, BAD_REQUEST 예외가 발생한다.")
    @Test
    void throwsBadRequest_whenEmailFormatIsInvalid() {
        // arrange — 다른 필드는 전부 VALID, 이메일 한 개만 깨뜨림
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

### 무엇을 테스트하나
- 생성자의 유효성 검증 (null, 포맷 체크)
- 도메인 상태 변경 메서드 (`changePassword()`, `update()` 등)
- 도메인 규칙 위반 시 올바른 예외(`CoreException`) 발생

---

## 🔁 2. 통합 테스트 (Integration Test)

**파일 패턴:** `domain/*ServiceIntegrationTest`
**예시:** `UserServiceIntegrationTest`

### 목적
Service ↔ Repository ↔ 실제 DB가 **올바르게 연결되어 동작**하는지 검증한다.
즉, 단위 테스트가 전제로 깔았던 **"내 가정"이 실제 환경에서 맞는지** 확인한다.

### 환경
- `@SpringBootTest` — Spring 컨텍스트 전체 로드
- Testcontainers로 실제 MySQL 컨테이너
- `@AfterEach` 에서 `DatabaseCleanUp.truncateAllTables()` 로 격리
- 보통 (초 단위)

### 예시 — 회원가입 도메인
```java
@SpringBootTest
class UserServiceIntegrationTest {

    @Autowired private UserService userService;
    @Autowired private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() { databaseCleanUp.truncateAllTables(); }

    @DisplayName("이미 존재하는 loginId 로 가입하면, CONFLICT 예외가 발생한다.")
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

### 무엇을 테스트하나
- 비즈니스 로직이 실제 DB와 맞물려 의도대로 동작하는지
- 중복 데이터, 조회 실패 등 **DB 의존적 시나리오**
- 트랜잭션 경계 / `@Transactional` dirty checking

---

## 🌐 3. E2E 테스트 (End-to-End Test)

**파일 패턴:** `interfaces/api/*E2ETest`
**예시:** `UserV1ApiE2ETest`

### 목적
클라이언트 입장에서 **HTTP 요청 → Controller → Facade → Service → DB → 응답** 전체 흐름을 검증한다.

### 환경
- `@SpringBootTest(webEnvironment = RANDOM_PORT)` — 실제 서버 포트
- `TestRestTemplate` 으로 진짜 HTTP 요청 전송
- 가장 느림 (수십 초)

### 예시 — 회원가입 도메인
```java
@SpringBootTest(webEnvironment = RANDOM_PORT)
class UserV1ApiE2ETest {

    private static final String ENDPOINT = "/api/v1/users";

    @Autowired private TestRestTemplate testRestTemplate;

    @DisplayName("유효한 정보로 가입 요청하면, 200 OK 와 가입된 회원 정보를 반환한다.")
    @Test
    void returnsRegisteredUser_whenValidRequest() {
        UserV1Dto.RegisterRequest req = new UserV1Dto.RegisterRequest(
            "testuser", "Password@1", "홍길동", "1990-01-01", "test@loopers.com"
        );

        ResponseEntity<ApiResponse<UserV1Dto.UserResponse>> response =
            testRestTemplate.exchange(ENDPOINT, HttpMethod.POST, new HttpEntity<>(req), ...);

        assertAll(
            () -> assertTrue(response.getStatusCode().is2xxSuccessful()),
            () -> assertThat(response.getBody().data().loginId()).isEqualTo("testuser")
        );
    }
}
```

### 무엇을 테스트하나
- API 경로, HTTP 메서드, 요청/응답 포맷 매핑
- 잘못된 입력에 대한 HTTP 상태 코드 (400, 401, 409 등)
- JSON 직렬화/역직렬화, 헤더 처리 (`X-Loopers-LoginId`)
- ApiControllerAdvice 가 도메인 예외를 올바른 status 로 변환하는지

---

## 🔧 테스트 더블 (Test Doubles)

> 테스트 대상이 의존하는 외부 객체의 동작을 **빠르고 안전하게 흉내 내는 대역 객체**.

### 🧩 역할 vs 도구 — 자주 헷갈리는 구분

- `Dummy`, `Stub`, `Mock`, `Spy`, `Fake` 는 **테스트 목적 (역할)**
- `Mockito.mock()`, `Mockito.spy()` 는 **객체 생성 방식 (도구)**

```java
UserRepository repo = mock(UserRepository.class);                  // 도구: mock()
when(repo.findByLoginId("a")).thenReturn(Optional.of(user));       // 역할: Stub
repo.findByLoginId("a");
verify(repo).findByLoginId("a");                                   // 역할: Mock
```

> ✅ 한 mock 객체에 **Stub 역할과 Mock 역할을 동시에** 부여할 수 있다.
> Mockito 는 라이브러리(도구), Mock 은 테스트 더블 5형제 중 하나(개념).

### 📚 역할별 정리

| 역할 | 목적 | 사용 방식 | 회원가입 예시 |
| --- | --- | --- | --- |
| **Dummy** | 자리만 채움 (사용되지 않음) | 인자에 그냥 넘김 | 검증 대상이 아닐 때 더미 UserModel 전달 |
| **Stub** | 고정된 응답 제공 (상태 기반) | `when().thenReturn()` | `existsByLoginId()` → 항상 false 반환 |
| **Mock** | 호출 여부/횟수 검증 (행위 기반) | `verify(...)` | `save()` 가 정확히 1번 호출됐는지 |
| **Spy** | 진짜 객체 감싸기 + 일부 조작 | `spy()`, `@MockitoSpyBean` | 진짜 Repository 동작 + 호출 기록 |
| **Fake** | 실제처럼 동작하는 가짜 구현체 | 직접 클래스 구현 | 평문 그대로 저장하는 PasswordEncoder |

### 🔁 실전 예제 — 회원가입 도메인

#### 📦 Stub — "이렇게 호출하면 이렇게 답해줘"
```java
UserRepository userRepository = mock(UserRepository.class);
when(userRepository.findByLoginId("testuser"))
    .thenReturn(Optional.of(savedUser));
```
- 흐름만 통제하고 싶을 때
- 반환값이 시나리오의 전제 조건일 때

#### 📬 Mock — "너 이렇게 동작했니?"
```java
UserRepository userRepository = mock(UserRepository.class);

userService.register(user);

verify(userRepository, times(1)).save(any(UserModel.class));
```
- 호출 자체가 검증 대상일 때
- 부수효과(저장, 발송 등)가 발생했는지 확인

#### 🕵️ Spy — "진짜로 돌리되, 호출 기록만 남겨"
```java
@SpringBootTest
class UserServiceIntegrationTest {

    @MockitoSpyBean
    private UserRepository userRepository;   // 진짜 빈을 감싼 spy

    @Test
    void savesUser_whenValidUserIsProvided() {
        userService.register(user);
        verify(userRepository).save(any());  // 실제 저장 + 호출 검증
    }
}
```
- 진짜 로직은 그대로 쓰면서 일부만 검증/덮어쓰고 싶을 때

#### 🧪 Fake — "실제처럼 동작하는 단순 구현"
```java
PasswordEncoder fakeEncoder = new PasswordEncoder() {
    @Override public String encode(String raw) { return raw; }
    @Override public boolean matches(String raw, String enc) { return raw.equals(enc); }
};

user.changePassword("NewPassword@2", fakeEncoder);   // BCrypt 무게 없이 검증 흐름만 격리
```
- 실제 구현(BCrypt 등)이 느리거나 비결정적일 때
- 완전히 독립적인 테스트 환경이 필요할 때

---

## 🧱 테스트 가능한 구조

> 검증하고 싶은 로직을, **외부 의존성과 격리된 상태**에서 단독으로 검증할 수 있는 구조.

### ❌ 테스트하기 어려운 구조

| 문제 | 설명 |
| --- | --- |
| **내부에서 의존 객체 직접 생성 (`new`)** | 테스트 대역으로 대체 불가 → 격리 불가능 |
| **하나의 함수가 너무 많은 책임** | 실패 원인 추적 어려움 |
| **외부 API / DB 접근이 하드코딩** | 실제 환경 없이 테스트 불가능 |
| **private 로직, static 메서드 남용** | 외부에서 로직 분리 불가 |

#### 안 좋은 예
```java
public class UserService {
    public UserModel register(String loginId, String password, ...) {
        // ❌ new 로 직접 생성 → Mock/Fake 불가
        if (new UserJpaRepository().existsByLoginId(loginId)) {
            throw new IllegalStateException();
        }
        String encoded = new BCryptPasswordEncoder().encode(password);
        return new UserJpaRepository().save(new UserModel(loginId, encoded, ...));
    }
}
```

### ✅ 테스트 가능한 구조

| 포인트 | 설명 |
| --- | --- |
| **외부 의존성 분리** | 인터페이스화 + 생성자 주입 (DI) |
| **비즈니스 로직 분리** | 도메인 엔티티 or 전용 Service 로 책임 분산 |
| **책임 단일화** | 한 함수는 한 역할만 |
| **상태 중심 설계** | "입력 → 상태 변화 → 결과" 구조 |

#### 좋은 예
```java
@RequiredArgsConstructor
@Component
public class UserService {

    private final UserRepository userRepository;       // 인터페이스 주입
    private final PasswordEncoder passwordEncoder;     // 인터페이스 주입

    public UserModel register(UserModel user) {
        if (userRepository.existsByLoginId(user.getLoginId())) {
            throw new CoreException(ErrorType.CONFLICT);
        }
        user.encodePassword(passwordEncoder);          // 도메인에 위임
        return userRepository.save(user);
    }
}
```
- 인터페이스로 주입 → 단위 테스트에서 Mock/Fake 로 대체 가능
- 암호화 로직은 도메인(`user.encodePassword`)에 위임 → 책임 분리

---

## 🔁 TDD 진행 가이드 (Red → Green → Refactor)

### 핵심 원칙 — "테스트가 코드를 견인한다"

코드를 먼저 쓰지 않는다. **테스트를 먼저 쓴다.** 그 다음 테스트가 통과할 만큼만 코드를 쓴다.
**테스트가 강제로 요구하지 않은 코드는 절대 미리 만들지 않는다.**

### 단계별 정의

| 단계 | 목표 | 만들어야 하는 것 | 만들면 안 되는 것 |
| --- | --- | --- | --- |
| **🔴 RED** | 실패하는 테스트 1개 작성 | 테스트가 컴파일될 정도의 **최소 스켈레톤** | 검증 로직, 비즈니스 규칙 |
| **🟢 GREEN** | 그 테스트만 통과시키기 | 테스트를 통과시킬 **최소 코드** | 미래의 다른 테스트를 위한 코드 |
| **🔵 REFACTOR** | 중복 제거 / 가독성 개선 | 없음 (구조만 정리) | 새로운 기능, 새로운 검증 |

### "어디까지 만들고 RED 를 써야 하나?" — 계층별 가이드

#### ① 단위 테스트 (Model) — 가장 단순
도메인 Model 클래스만 있으면 된다. **Service 는 필요 없다.**
```
RED 직전:
  - domain/<도메인>Model.java
    · 필드 + 생성자 (검증 로직 없이 필드 대입만)
    · 필요한 getter
RED 실행:
  - <도메인>ModelTest.java 작성
  - 생성자가 예외를 안 던지므로 실패 (= RED 확정)
GREEN:
  - 생성자에 검증 로직 추가
```

> ❗ Model 단위 테스트 단계에서는 절대 Service / Repository / Facade 까지 만들지 않는다.
> 그건 다음 사이클(통합 테스트 RED)이 견인할 책임이다.

#### ② 통합 테스트 (Service) — Service 까지 필요
```
RED 직전:
  - domain/<도메인>Repository.java  ← 인터페이스
  - infrastructure/<도메인>JpaRepository.java
  - infrastructure/<도메인>RepositoryImpl.java
  - domain/<도메인>Service.java  ← 메서드 시그니처만
RED 실행:
  - <도메인>ServiceIntegrationTest.java
  - Service 가 비어 있어서 실패 (= RED 확정)
GREEN:
  - Service 내부에 비즈니스 로직
```

#### ③ E2E 테스트 (API) — Controller·Facade 까지 필요
```
RED 직전:
  - interfaces/api/<도메인>V1Dto.java
  - interfaces/api/<도메인>V1ApiSpec.java
  - interfaces/api/<도메인>V1Controller.java  ← 빈 메서드
  - application/<도메인>Facade.java  ← 시그니처만
  - application/<도메인>Info.java
RED 실행:
  - <도메인>V1ApiE2ETest.java
  - 응답이 비어있거나 500 → 실패 (= RED 확정)
GREEN:
  - Controller → Facade → Service 연결
```

### 한 사이클이 끝나면 다음 케이스로

RED → GREEN → REFACTOR 한 바퀴 = 테스트 1개 통과. 다음 케이스로 넘어가서 또 RED 부터.

예시 — 회원가입 단위 테스트 3개 케이스 → 사이클 3번:
1. (RED → GREEN → REFACTOR) loginId 형식 검증
2. (RED → GREEN → REFACTOR) 이메일 형식 검증
3. (RED → GREEN → REFACTOR) 비밀번호 형식 검증

### 체크리스트

매 단계 전에 자문한다:

- [ ] **RED 시작 전**: 지금 만드는 스켈레톤이 "테스트 컴파일을 위한 최소한"인가? 검증 로직을 미리 넣지 않았는가?
- [ ] **RED 확인**: 정말 빨간색으로 실패하는가? (`AssertionError` / `예외 미발생` 등)
- [ ] **GREEN 시작 전**: 지금 추가하는 코드가 "이 테스트를 통과시키는 데 꼭 필요한" 코드인가?
- [ ] **GREEN 확인**: 방금 추가한 테스트뿐 아니라 **이전 모든 테스트도** 여전히 통과하는가?
- [ ] **REFACTOR 확인**: 리팩토링 후에도 모든 테스트가 통과하는가? 새 기능을 끼워넣지 않았는가?

---

## 비교 요약

| | 단위 테스트 | 통합 테스트 | E2E 테스트 |
|---|---|---|---|
| **파일 패턴** | `*ModelTest` | `*ServiceIntegrationTest` | `*E2ETest` |
| **검증 범위** | Model 클래스 1개 | Service + Repository + DB | HTTP → DB 전체 |
| **답하는 질문** | "도메인 규칙이 맞나?" | "내 가정이 진짜 환경에서 맞나?" | "API 계약이 맞나?" |
| **Spring 컨텍스트** | ❌ | ✅ | ✅ |
| **실제 DB** | ❌ | ✅ Testcontainers | ✅ Testcontainers |
| **실제 HTTP** | ❌ | ❌ | ✅ TestRestTemplate |
| **속도** | 빠름 (ms) | 보통 (수 초) | 느림 (수십 초) |
| **테스트 수** | 많이 | 보통 | 적게 |
| **테스트 더블** | 적극 사용 (Fake / Stub) | 제한적 (Spy 정도) | 거의 안 씀 |
| **실패 원인 파악** | 쉬움 | 보통 | 어려움 |
