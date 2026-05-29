# TDD 학습 노트 — 회원가입 예제로 따라가는 8단계

> 사이클을 돌면서 *"이걸 왜 이렇게 하는가?"* 가 헷갈릴 때 펼쳐 보는 노트.
> 각 Phase 는 [`TDD_PROGRESS.md`](./TDD_PROGRESS.md) 의 단계 진입 직전 학습 포인트 박스에서 링크된다.

---

## Phase 1 — 테스트가 왜 필요한가

### 가장 원시적인 검증

테스트라는 개념을 모르면 검증은 보통 `main()` 에서 직접 호출 + `println` 으로 한다.

```java
public static void main(String[] args) {
    UserModel u = new UserModel("한글ID", "Password@1", "홍길동", "1990-01-01", "test@x.com");
    System.out.println(u.getLoginId());
}
```

### 무엇이 문제인가

1. **사람이 매번 눈으로 확인** — 케이스 5개면 5번 실행
2. **자동 검증 없음** — "예외가 떠야 하는데 안 떴음"을 사람이 깨닫지 못하면 통과
3. **회귀 불가능** — 한 달 뒤 비밀번호 정규식을 고치면 이메일 검증이 깨졌어도 모름
4. **의도와 동작의 분리 없음** — "한글 ID 는 BAD_REQUEST 가 나와야 한다"는 의도가 코드에 없음

### 자동화된 테스트의 정체

테스트란 **"이 입력에서 이 결과가 나와야 한다"는 의도를 코드로 박제한 것**.
실행하면 OK/FAIL 이 자동, 의도가 깨지면 즉시 알려준다.

> **핵심 통찰**: 테스트는 "확인 행위"가 아니라 **"의도의 박제"** 다.

---

## Phase 2 — TDD: Red → Green → Refactor

### "코드 먼저"의 함정

코드를 먼저 다 짜고 테스트를 쓰면 **자기 코드에 유리한 테스트만 쓰게 된다**.
이미 동작하는 걸 보면서 쓰니 예외 케이스가 누락된다.

### TDD 의 순서

1. **🔴 Red** — 실패하는 테스트부터.
   `UserModel` 생성자에 검증 로직이 없으니 한글 ID 를 넘겨도 예외가 안 난다 → 테스트가 실패.

2. **🟢 Green** — 가장 단순한 방법으로 통과.
   정규식 한 줄 추가. 이때 "이메일도 검증해야 하니까..."라며 미리 만들지 않는다. **오버엔지니어링 금지**.

3. **🔵 Refactor** — 통과 상태에서 중복 제거 + 이름 개선.
   `Pattern.compile(...)` 을 매번 호출하던 걸 `static final` 로 빼낸다. 테스트가 안전망이라 **자신 있게 손댄다**.

### 왜 Red 가 먼저인가

Red 없이 바로 Green 으로 가면 **테스트 자체가 틀렸을 때 알아차릴 수 없다**.
잘못 짠 테스트는 어떤 코드든 통과시킨다. Red 를 한 번 보고 가야 *"이 테스트가 정말 실패할 수 있다"* 가 검증된다.

### RED 직전 스켈레톤 — "테스트가 컴파일될 정도의 최소한"

RED 를 시작하려면 *어떤 코드가 이미 있어야 하나?* 정답은 **"테스트 코드가 컴파일되는 최소한"**. 더 만들면 RED 가 GREEN 으로 둔갑한다 (다음 항목).

계층별로 미리 만드는 것:

| 계층 | RED 직전 필요한 것 | 만들지 말아야 할 것 |
|---|---|---|
| **단위** (`UserModelTest`) | 필드 + 생성자 + (테스트에서 호출하는) getter | 검증 로직, `@Entity`, Lombok, 안 쓰는 getter |
| **통합** (`UserServiceIntegrationTest`) | Service 메서드 시그니처, Repository 인터페이스, JPA 엔티티 어노테이션 | 메서드 본문, save 호출, 트랜잭션 처리 |
| **E2E** (`UserV1ApiE2ETest`) | Controller `@PostMapping` + 빈 메서드, Request/Response DTO, Facade 시그니처 | Controller → Facade 연결, 응답 변환 |

회원가입 예제의 사이클 1 직전 `UserModel`:

```java
public class UserModel {
    private String loginId;
    // ...

    public UserModel(String loginId, ...) {
        this.loginId = loginId;   // ← 그냥 대입. throw 없음
    }

    public String getLoginId() { return loginId; }
    // 다른 getter 없음 — 이 사이클 테스트에서 안 쓰므로 (YAGNI)
}
```

### "통과해버리는 RED" 의 함정

RED 를 작성했는데 **테스트가 우연히 통과해버리는 상황**이 있다. 그러면 사이클이 무너진다.

```java
// 사이클 1 RED — 한글 ID 면 예외 기대
assertThrows(CoreException.class, () -> new UserModel("한글ID", ...));
```

이 테스트가 *통과해버리는* 두 가지 경우:
1. `UserModel` 에 이미 정규식 검증이 들어 있음 → **이미 GREEN 인 걸 RED 라고 착각**
2. 다른 필드 (`password`, `birth`) 가 null/잘못된 값이라 그쪽에서 먼저 예외 → **이유가 다른 예외**가 통과시킴

→ 그래서 *"유효한 기본값 + 한 필드만 깨뜨리기"* 패턴이 중요하다 (Phase 3). 그리고 RED 단계에서 *반드시* 한 번 테스트를 돌려서 **실패 메시지가 의도한 메시지** 인지 눈으로 확인한다.

> 실패 패턴 비교:
> - ✅ **AssertionFailedError** ("예외가 던져질 것을 기대했는데 안 던져짐") — 진짜 RED
> - ❌ **NullPointerException / 다른 예외** — 시나리오가 잘못된 것. 다른 필드 때문에 먼저 깨짐

### REFACTOR 단계에서 구체적으로 뭘 하나

GREEN 직후 손대는 곳들 — *기능을 추가하지 않고* 품질만 올린다:

| 종류 | 예시 |
|---|---|
| **매직 넘버/문자열 상수화** | `Pattern.compile("^[A-Za-z0-9]{1,10}$")` → `private static final Pattern LOGIN_ID_PATTERN = ...` |
| **중복 제거** | 검증 로직 여러 개 → `validateLoginId(String)` 같은 private 메서드로 추출 |
| **이름 개선** | `check()` → `validateLoginIdFormat()` |
| **unused import 제거** | 패키지 정리 |
| **객체 생성 위치 최적화** | `Pattern.compile()` 을 생성자 내부 → static final 로 이동 (한 번만 컴파일) |

**금지 사항:**
- ❌ 새 기능 추가 (다음 RED 에서 한다)
- ❌ 테스트 수정 (테스트가 안전망이 됐는데 이걸 건드리면 안전망이 사라짐)

> **핵심 통찰**: Red 는 **"테스트 자체의 신뢰도"** 를 검증하는 단계다.

---

## Phase 3 — 단위 테스트를 왜 여러 개로 쪼개는가

### 안티패턴 — 한 테스트에 다 몰아넣기

```java
@Test
void registerValidation() {
    assertThrows(..., () -> new UserModel("한글ID", ...));
    assertThrows(..., () -> new UserModel(VALID, "짧음", ...));
    assertThrows(..., () -> new UserModel(VALID, VALID, VALID, "잘못된 날짜", ...));
}
```

문제:
1. **첫 번째에서 실패하면 나머지가 실행되지 않는다** — 이메일/비밀번호 검증도 같이 깨졌어도 모른다
2. **실패 메시지가 "어디"인지 모른다** — 디버깅 비용 폭증
3. **테스트 이름이 의도를 못 담는다**

### 쪼개기의 원칙 — "한 테스트는 한 가지 이유로만 실패한다"

```java
@Test void throwsBadRequest_whenLoginIdFormatIsInvalid()    { ... }
@Test void throwsBadRequest_whenEmailFormatIsInvalid()      { ... }
@Test void throwsBadRequest_whenPasswordFormatIsInvalid()   { ... }
```

IDE 에 5개의 ✅/❌ 가 따로 뜬다. 어느 규칙이 깨졌는지 즉시 보인다.

### 추가 패턴 — "유효한 기본값 + 한 필드만 깨뜨리기"

```java
private static final String VALID_LOGIN_ID = "testuser";
private static final String VALID_PASSWORD = "Password@1";
// ...

@Test void throwsBadRequest_whenEmailFormatIsInvalid() {
    String invalidEmail = "invalid-email";
    // 다른 필드는 전부 VALID, 이메일 한 개만 깨뜨림
    assertThrows(..., () ->
        new UserModel(VALID_LOGIN_ID, VALID_PASSWORD, VALID_NAME, VALID_BIRTH, invalidEmail));
}
```

→ 실패 시 **"이메일 때문이지, 다른 필드 때문이 아니다"** 가 확정된다.

> **핵심 통찰**: 테스트의 가치는 *"왜 실패했는지"* 즉시 답하는 능력에서 나온다.

---

## Phase 4 — 의존성을 만나면? 테스트 더블 5형제

### 새 문제

`UserModel` 단위 테스트는 순수 객체라 쉬웠다. 그런데 `UserService.register()` 를 테스트하려면 의존성이 생긴다.

```java
public UserModel register(UserModel user) {
    if (userRepository.existsByLoginId(user.getLoginId())) {  // ← DB
        throw new CoreException(ErrorType.CONFLICT);
    }
    user.encodePassword(passwordEncoder);                      // ← 외부 인코더
    return userRepository.save(user);                          // ← DB
}
```

이걸 테스트하려면 DB 가 필요한데, 단위 테스트에 DB 를 띄우면 느리고 격리 안 되고 환경 의존적이다.

### 해결책 — 의존성을 "가짜"로 바꿔치기

Gerard Meszaros 의 5분류:

| 종류 | 한 줄 설명 | 회원가입 예시 |
| --- | --- | --- |
| **Dummy** | 그냥 인자 자리만 채움 | `register(new DummyUser())` |
| **Stub** | 정해진 답을 돌려준다 | `existsByLoginId()` → 항상 `false` |
| **Spy** | 진짜처럼 동작 + 호출 기록 | 진짜 save 실행 + "save 1번 호출됐는지" 확인 |
| **Mock** | 호출 기대를 미리 선언, 검증 실패 시 실패 | "save 가 정확히 한 번, 이 인자로 호출돼야 함" |
| **Fake** | 진짜처럼 동작하는 단순 구현체 | 평문 그대로 반환하는 `PasswordEncoder` |

### 회원가입 예제로 본 차이

```java
// Fake — PasswordEncoder
PasswordEncoder fakeEncoder = new PasswordEncoder() {
    public String encode(String raw) { return raw; }
    public boolean matches(String raw, String enc) { return raw.equals(enc); }
};

// Stub — Repository 가 답을 박아둠
when(userRepository.existsByLoginId("testuser")).thenReturn(true);

// Spy — 진짜 빈을 감싸서 호출 기록
@MockitoSpyBean UserRepository userRepository;
verify(userRepository).save(any());

// Mock — 기대 호출 검증
verify(userRepository, times(1)).save(argThat(u -> u.getLoginId().equals("testuser")));
```

### 어떤 걸 언제 쓰나

- **Stub** — 협력자의 *반환값*이 시나리오 전제일 때
- **Mock/Spy** — *호출 자체*가 검증 포인트일 때
- **Fake** — 협력자를 *완전히 대체*하되 동작은 살릴 때
- **Dummy** — 그냥 인자 자리만 채울 때

> **핵심 통찰**: 테스트 더블은 "DB 회피용 트릭"이 아니라 **검증의 초점을 좁히는 도구** 다.

---

## Phase 5 — Mockito 는 무엇인가? 왜 손으로 안 짜는가?

### 손으로 다 짤 수 있다

Phase 4 의 Fake/Stub/Spy 는 전부 손으로 짤 수 있다. 익명 클래스 또는 별도 클래스로.

```java
class StubUserRepository implements UserRepository {
    public boolean existsByLoginId(String id) { return true; }
    public UserModel save(UserModel u) { return u; }
    public Optional<UserModel> findByLoginId(String id) { return Optional.empty(); }
}
```

그런데 `UserRepository` 에 메서드가 20개 있다면? 매번 20개를 다 구현하는 건 비효율적이다.

### Mockito 는 자동화 도구

Mockito 는 **인터페이스를 바이트코드 수준에서 분석해서, 자동으로 더블을 만들어주는 라이브러리**.

```java
UserRepository repo = mock(UserRepository.class);     // 20개 메서드 자동 생성
when(repo.existsByLoginId(any())).thenReturn(true);   // 필요한 것만 답을 박는다
```

### 자주 하는 오해

> "Mockito = Mock"

**아니다.** Mockito 는 *라이브러리 이름*, Mock 은 *테스트 더블 5형제 중 하나*.
Mockito 로 Stub 도 만들고 Spy 도 만들고 Mock 도 만든다.

| | Mockito 문법 |
|---|---|
| Stub 만들기 | `when(...).thenReturn(...)` |
| Mock 만들기 | `mock(...)` + `verify(...)` |
| Spy 만들기 | `spy(...)` or `@MockitoSpyBean` |

### 손 vs Mockito 선택 기준

- 의존성 메서드가 1~3개 → 손으로 Fake 가 더 명확
- 메서드가 많거나 일회성 답만 필요 → Mockito 가 빠름

> **핵심 통찰**: Mockito 는 *도구*, Mock 은 *개념*. 둘을 구분해야 헷갈리지 않는다.

---

## Phase 6 — 단위 테스트만으로는 왜 안 되는가? 통합 테스트

### 단위 테스트는 "가정" 위에 서 있다

`UserService` 단위 테스트는 이렇게 짠다:

```java
when(userRepository.existsByLoginId("testuser")).thenReturn(false);
when(userRepository.save(any())).thenReturn(savedUser);
// → 가정: "Repository 가 이렇게 동작할 것이다"
```

이 가정이 틀리면?
- 실제 JPA save 가 트랜잭션 없이 호출되어 저장이 안 됐을 수 있다
- DB unique 제약이 안 걸려서 중복 가입이 실제론 통과될 수 있다
- `@Transactional` 경계가 잘못 잡혀서 dirty checking 이 안 일어날 수 있다

### 통합 테스트의 정체

```java
@SpringBootTest
class UserServiceIntegrationTest {
    @Autowired UserService userService;          // 진짜 빈
    @Autowired UserRepository userRepository;    // 진짜 빈 (JPA)
    @Autowired DatabaseCleanUp databaseCleanUp;  // 진짜 DB (Testcontainers)

    @Test void throwsConflict_whenLoginIdAlreadyExists() {
        userService.register(userA);
        assertThrows(CoreException.class, () -> userService.register(userB));
        // 진짜 DB 에 한 번 저장 → 두 번째 시도가 진짜로 막히는지
    }
}
```

단위 테스트에서 검증할 수 없는 것들을 잡는다:
- JPA 가 실제 INSERT 를 날리는지
- 트랜잭션 경계가 의도대로 잡히는지
- DB 제약(unique, not null)이 작동하는지
- dirty checking 이 변경을 UPDATE 로 반영하는지

### 단위 vs 통합 — 진짜 차이

| | 단위 테스트 | 통합 테스트 |
|---|---|---|
| 속도 | 수십 ms | 수 초 |
| 검증 대상 | "내 코드의 로직" | "내 코드 + 프레임워크 가정" |
| 격리 | 완전 (Mock) | 진짜 협력자와 함께 |
| 답하는 질문 | "이 분기가 맞나?" | "내 가정이 진짜 환경에서 맞나?" |

> **핵심 통찰**: 단위는 *"내 로직"* 을 검증하고, 통합은 *"내 가정"* 을 검증한다.

---

## Phase 7 — 통합이 있는데도 왜 E2E 가 필요한가

### 통합 테스트의 사각지대

통합 테스트가 `UserService.register(user)` 를 검증해도 **사용자가 실제로 보내는 HTTP 요청**까지는 검증하지 않는다.

회원가입 예제에서, 통합이 통과해도 다음은 못 잡는다:

1. **JSON 직렬화/역직렬화** — 클라이언트가 `{ "loginId": "..." }` 로 보내는데 내부에서 `login_id` 로 받으면 E2E 만 깨진다
2. **HTTP 상태 코드 매핑** — 서비스는 `CoreException(CONFLICT)` 을 던지지만 Advice 가 이걸 `409` 로 변환하는지는 E2E 만 검증
3. **헤더 처리** — `X-Loopers-LoginId` 누락 시 400 보장은 E2E 만
4. **URL 경로/메서드 매핑** — `POST /api/v1/users` 가 정말 register 를 호출하는가

### E2E 에서 다루면 안 되는 것 — 도메인 검증 중복

> **주의**: 도메인이 이미 처리하는 케이스를 E2E 에서 재검증하면 "통과해버리는 RED" 함정에 빠진다.

```
❌ E2E 에서 다루면 RED 가 성립 안 되는 것:
   "null loginId → 400"
   → 도메인 생성자가 CoreException(BAD_REQUEST) 를 이미 던짐
   → 컨트롤러 연결 전에도 400 이 나오면? (사실은 404 가 남)
   → 컨트롤러 연결 후에는 이미 통과 → green-by-accident

✅ E2E 고유 검증 대상:
   "중복 loginId → 409"
   → 통합은 CoreException(CONFLICT) 발생을 검증
   → E2E 는 그것이 HTTP 409 로 변환되는지 검증 (다른 질문)
   → 컨트롤러 없으면 404 → 진짜 RED 가능
```

**E2E RED 를 올바르게 쓰려면**: 컨트롤러를 추가하기 전에 모든 시나리오의 RED 를 먼저 작성한다.

```
[컨트롤러 없는 상태]
  사이클 9 RED:  성공 → 200 기대  → 404 뜸 → FAIL ✅
  사이클 10 RED: 충돌 → 409 기대  → 404 뜸 → FAIL ✅

[컨트롤러 추가 후 — 한 번에 GREEN]
  사이클 9 GREEN + 사이클 10 GREEN 동시에 통과 ✅
```

### E2E 의 정체

```java
@SpringBootTest(webEnvironment = RANDOM_PORT)
class UserV1ApiE2ETest {
    @Autowired TestRestTemplate restTemplate;

    @Test void returnsRegisteredUser_whenValidRequest() {
        ResponseEntity<...> response = restTemplate.postForEntity(
            "/api/v1/users",
            new RegisterRequest("testuser", "Password@1", ...),
            ApiResponse.class
        );
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        // 진짜 HTTP → 진짜 컨트롤러 → 진짜 서비스 → 진짜 DB → 진짜 응답
    }
}
```

E2E 는 **사용자가 본 그대로의 동작** = API 계약의 마지막 방어선.

### 단위 / 통합 / E2E 한 줄 정리

| | 답하는 질문 |
|---|---|
| 단위 | "이 도메인 규칙/분기가 의도대로 동작하는가?" |
| 통합 | "내 코드와 프레임워크가 진짜 환경에서 맞물려 돌아가는가?" |
| E2E | "외부에서 본 API 계약이 지켜지는가?" |

각 단계는 **서로 다른 질문에 답한다**. 어느 하나로 다른 둘을 대체할 수 없다.

> **핵심 통찰**: 통합은 *"내부"*, E2E 는 *"외부 계약"*. 같은 질문의 다른 강도가 아니라 **다른 질문**이다.

---

## Phase 8 — 테스트 피라미드와 회고

### 왜 비율이 필요한가

세 종류 다 쓰는 건 알겠다. 그런데 **무한정 쓸 순 없다**.

| | 1개 실행 비용 | 신뢰도 | 변경 영향 |
|---|---|---|---|
| 단위 | ~10 ms | "내 로직" 한정 | UI 바뀌어도 안 깨짐 |
| 통합 | ~1~5 s | "내 코드 + DB" | 스키마 바뀌면 깨짐 |
| E2E | ~5~30 s | "전체 계약" | API 경로 바뀌면 깨짐 |

E2E 를 100개 쓰면 빌드가 30분 걸리고, 작은 변경 하나에 수십 개가 깨진다.

### 테스트 피라미드

```
        ┌─────┐
        │ E2E │   10%   ← 핵심 시나리오만
        ├─────┤
        │ 통합 │   20%   ← 외부 협력자 검증
        ├─────┤
        │ 단위 │   70%   ← 빠르고 많이
        └─────┘
```

### 회원가입 예제의 적절한 분배

- **단위 8~10개** — loginId/email/birth/password 형식 위반 각각, 마스킹 경계 등
- **통합 5~7개** — 정상 가입, 중복 CONFLICT, 비밀번호 암호화 저장 등
- **E2E 3~5개** — 정상 가입, 필수 필드 누락, 중복 가입, 헤더 누락 등

### 새 기능 추가 시 결정 트리

```
새 기능 → 도메인 규칙인가?       → YES → 단위
       └ 도메인이 아닌가?         → DB/외부 연동인가?  → YES → 통합
                                  └ HTTP 계약인가?     → YES → E2E
```

> **핵심 통찰**: 피라미드 비율은 **"속도 × 신뢰도"의 최적해**. 위로 갈수록 비싸지만 진짜에 가깝고, 아래로 갈수록 싸지만 가정에 의존한다.

---

## Phase 9 — 사이클을 굴리며 챙기는 실용 가이드

> Phase 1~8 이 *이론* 이라면 9 는 *현장 노하우*. 사이클을 굴리다 보면 반복적으로 부딪히는 질문들의 답.

### AAA 패턴 — 한 테스트의 내부 구조

```java
@Test
void throwsBadRequest_whenLoginIdFormatIsInvalid() {
    // arrange — 테스트에 필요한 입력/상태를 준비
    String invalidLoginId = "한글아이디";

    // act — 검증하려는 메서드 호출 (보통 한 줄)
    CoreException ex = assertThrows(CoreException.class, () ->
        new UserModel(invalidLoginId, VALID_PASSWORD, VALID_NAME, VALID_BIRTH, VALID_EMAIL)
    );

    // assert — 결과 검증
    assertThat(ex.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
}
```

| 단계 | 한 줄 |
|---|---|
| **Arrange** (준비) | 시나리오 전제를 만든다 — 데이터, 협력자 stub 세팅 |
| **Act** (실행) | 검증 대상 호출. **한 줄이 이상적** — 한 동작에 대해서만 결과를 따짐 |
| **Assert** (검증) | 결과/상태 변화 확인. `assertAll` 로 묶거나 한 줄로 |

→ 주석으로 단계를 명시하면 *읽는 사람이 "여기가 검증 대상이구나"* 를 즉시 안다.

### 테스트 명명 규칙 — 의도가 이름에 담겨야 한다

| 패턴 | 예시 |
|---|---|
| `행동_when_상황` | `throwsBadRequest_whenLoginIdFormatIsInvalid` |
| `상태_then_결과` (Given-When-Then) | `givenInvalidLoginId_whenCreate_thenThrowsBadRequest` |
| `@DisplayName` 한글 사용 | `"loginId 가 영문/숫자 10자 이내 형식에 맞지 않으면, BAD_REQUEST 예외가 발생한다."` |

**나쁜 이름:** `test1`, `userTest`, `validation` — 깨졌을 때 *무엇이* 깨진 건지 모름.

### 사이클 시간 ≈ 5~10 분

한 사이클(🔴→🟢→🔵)이 *한 시간*을 넘기면 **TDD 흉내**다. 케이스를 더 잘게 쪼개야 한다.

```
사이클이 길어지는 신호:
- RED 단계에서 새 클래스 3개를 만들고 있음    → 케이스가 너무 큼
- GREEN 단계에서 추가 검증 로직 4개를 짜고 있음 → 한 사이클에 여러 규칙
- REFACTOR 단계에서 30분째 구조를 바꿈        → 별도 사이클로 분리
```

회원가입의 5개 단위 케이스(로그인ID/이메일/생년월일/비밀번호 RULE/비밀번호 contains 생년월일)를 각각 1 사이클로 쪼개는 이유 = **각 사이클을 작게 유지하기 위해**.

### YAGNI 와 TDD 의 관계

> "You Aren't Gonna Need It" — 지금 필요하지 않은 코드는 짜지 마라.

TDD 의 GREEN 원칙 *"가장 단순한 방법으로 통과"* = YAGNI 의 실천 메커니즘.

```
사이클 1 GREEN — loginId 검증만 추가하면 됨

❌ 안티패턴:
"이메일 검증도 곧 필요할 테니까 미리..."
"비밀번호 검증 메서드도 같이..."

✅ TDD 의 답:
"테스트가 강제하는 것만. 이메일은 다음 RED 가 강제한다."
```

→ **테스트가 강제하지 않은 코드는 안 쓴다.** 이게 오버엔지니어링을 막는 유일한 객관 기준이다.

### 테스트 더블 결정 트리 (Phase 4 의 보강)

협력자가 있을 때, 어떤 더블을 쓸지:

```
협력자가 있다
├ 호출 자체가 검증 포인트인가? (save 가 불렸나, 인코더가 불렸나)
│  ├ YES → Mock / Spy
│  │       ├ 진짜 동작도 필요? → Spy (@MockitoSpyBean)
│  │       └ 호출만 검증?      → Mock (mock + verify)
│  │
│  └ NO (반환값이 시나리오 전제) → Stub / Fake
│       ├ 일회성 답이면         → Stub (when(...).thenReturn(...))
│       ├ 협력자 전체를 대체    → Fake (간단한 구현체, 예: 평문 PasswordEncoder)
│       └ 그냥 인자 자리만 채움 → Dummy
```

회원가입 통합 테스트의 PasswordEncoder 가 **Fake** 인 이유: BCrypt 의 비결정성(같은 입력에 매번 다른 해시)을 빼고 *검증 흐름*만 격리하기 위해.

### TDD 사이클 끝날 때 자문

```
🔴 RED   ── 정말 빨갛게 실패했나? (예외 미발생 / AssertionError)
            의도한 이유로 실패했나? (다른 필드 NPE 가 아닌)
🟢 GREEN  ── 방금 테스트뿐 아니라 이전 모든 테스트도 통과하나?
            오버엔지니어링 없이 최소한만 추가했나?
🔵 REFACTOR ── 새 기능을 끼워넣지 않았나?
              테스트는 안 건드렸나?
              모든 테스트 여전히 통과하나?
```

> **핵심 통찰**: 이론(Phase 1~8) 은 한 번 외우면 끝이지만, 실용 가이드(Phase 9) 는 **매 사이클에서 다시 적용** 한다. 사이클을 굴리는 동안 곁에 두고 본다.

---

## 한 줄로 외울 9개

| Phase | 한 줄 |
|---|---|
| 1 | 테스트는 "확인 행위"가 아니라 "의도의 박제"다 |
| 2 | Red 는 "테스트 자체의 신뢰도"를 검증하는 단계다 |
| 3 | 테스트의 가치는 "왜 실패했는지" 즉시 답하는 능력이다 |
| 4 | 테스트 더블은 "검증의 초점을 좁히는 도구"다 |
| 5 | Mockito 는 도구, Mock 은 개념 — 둘을 구분하자 |
| 6 | 단위는 "내 로직", 통합은 "내 가정"을 검증한다 |
| 7 | 통합은 "내부", E2E 는 "외부 계약" — 다른 질문이다 |
| 8 | 피라미드 비율은 "속도 × 신뢰도"의 최적해다 |
| 9 | 이론은 한 번 외우면 끝, 실용 가이드는 매 사이클 다시 적용한다 |
