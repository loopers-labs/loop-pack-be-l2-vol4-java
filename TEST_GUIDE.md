# 테스트 가이드

이 프로젝트는 3종류의 테스트를 계층적으로 운용한다.
각 테스트는 검증 범위와 속도가 다르며, 목적에 맞게 작성한다.

---

## 테스트 피라미드

```
         /‾‾‾‾‾‾‾\
        /   E2E    \       ← 적게, 핵심 시나리오만
       /‾‾‾‾‾‾‾‾‾‾‾\
      /    통합 테스트  \    ← 레이어 연결 검증
     /‾‾‾‾‾‾‾‾‾‾‾‾‾‾‾\
    /    단위 테스트    \   ← 많이, 빠르게
   /‾‾‾‾‾‾‾‾‾‾‾‾‾‾‾‾‾‾‾\
```

---

## 단위 테스트 (Unit Test)

**파일 패턴:** `domain/*ModelTest`  
**예시:** `UserModelTest`, `ExampleModelTest`

### 목적
클래스 하나(주로 도메인 Model)의 비즈니스 로직만 독립적으로 검증한다.

### 특징
- Spring 컨텍스트 없음 — 순수 Java 객체만 사용
- DB, 네트워크, 외부 의존성 없음
- 빠름 (밀리초 단위)

### 예시 코드
```java
class UserModelTest {

    @DisplayName("유저 모델을 생성할 때,")
    @Nested
    class Create {

        @DisplayName("이메일 형식이 올바르지 않으면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenEmailFormatIsInvalid() {
            // arrange
            String email = "invalid-email";

            // act
            CoreException result = assertThrows(CoreException.class, () ->
                new UserModel("testuser", "Password@1", "홍길동", "19900101", email)
            );

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }
}
```

### 무엇을 테스트하나
- Model 생성자의 유효성 검증 (null, blank, 포맷 체크)
- Model의 상태 변경 메서드 (`update()`, `delete()` 등)
- 도메인 규칙 위반 시 올바른 예외(`CoreException`)가 발생하는지

---

## 통합 테스트 (Integration Test)

**파일 패턴:** `domain/*ServiceIntegrationTest`  
**예시:** `UserServiceIntegrationTest`, `ExampleServiceIntegrationTest`

### 목적
Service ↔ Repository ↔ 실제 DB가 올바르게 연결되어 동작하는지 검증한다.

### 특징
- `@SpringBootTest` — Spring 컨텍스트 전체 로드
- Testcontainers로 실제 MySQL 컨테이너를 띄워서 테스트
- `@AfterEach`에서 `DatabaseCleanUp.truncateAllTables()`로 테스트 간 데이터 격리
- 단위 테스트보다 느림 (초 단위, 컨테이너 초기화 시간 포함)

### 예시 코드
```java
@SpringBootTest
class UserServiceIntegrationTest {

    @Autowired private UserService userService;
    @Autowired private UserJpaRepository userJpaRepository;
    @Autowired private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("이미 존재하는 로그인 ID로 가입하면, CONFLICT 예외가 발생한다.")
    @Test
    void throwsConflict_whenLoginIdAlreadyExists() {
        // arrange — 실제 DB에 데이터 저장
        userJpaRepository.save(new UserModel("testuser", ...));

        // act — 실제 Service 호출
        CoreException result = assertThrows(CoreException.class, () ->
            userService.register(new UserModel("testuser", ...))
        );

        // assert
        assertThat(result.getErrorType()).isEqualTo(ErrorType.CONFLICT);
    }
}
```

### 무엇을 테스트하나
- Service의 비즈니스 로직이 실제 DB와 함께 올바르게 동작하는지
- 중복 데이터 처리, 조회 실패 시 예외 발생 등 DB 의존적인 시나리오
- 트랜잭션이 올바르게 동작하는지 (`@Transactional` 롤백 등)

---

## E2E 테스트 (End-to-End Test)

**파일 패턴:** `interfaces/api/*E2ETest`  
**예시:** `UserV1ApiE2ETest`, `ExampleV1ApiE2ETest`

### 목적
클라이언트 입장에서 HTTP 요청 → Controller → Facade → Service → DB → 응답 전체 흐름을 검증한다.

### 특징
- `@SpringBootTest(webEnvironment = RANDOM_PORT)` — 실제 서버 포트로 실행
- `TestRestTemplate`으로 실제 HTTP 요청을 전송
- Testcontainers로 실제 MySQL 컨테이너 사용
- 가장 느리고, 실패 시 원인 파악이 가장 어려움

### 예시 코드
```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class UserV1ApiE2ETest {

    private static final String ENDPOINT = "/api/v1/users";

    @Autowired private TestRestTemplate testRestTemplate;
    @Autowired private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("유효한 정보를 주면, 200 OK 와 유저 정보를 반환한다.")
    @Test
    void returnsUserInfo_whenValidInfoIsProvided() {
        // arrange
        UserV1Dto.RegisterRequest request = new UserV1Dto.RegisterRequest(
            "testuser", "Password@1", "홍길동", "19900101", "test@loopers.com"
        );

        // act — 실제 HTTP POST 요청
        ResponseEntity<ApiResponse<UserV1Dto.UserResponse>> response =
            testRestTemplate.exchange(ENDPOINT, HttpMethod.POST, new HttpEntity<>(request), ...);

        // assert — HTTP 상태 코드 + 응답 바디 검증
        assertAll(
            () -> assertTrue(response.getStatusCode().is2xxSuccessful()),
            () -> assertThat(response.getBody().data().loginId()).isEqualTo("testuser")
        );
    }
}
```

### 무엇을 테스트하나
- API 경로, HTTP 메서드, 요청/응답 포맷이 올바른지
- 잘못된 입력에 대해 올바른 HTTP 상태 코드(400, 404, 409 등)가 반환되는지
- 전체 레이어가 실제 환경과 동일하게 동작하는지

---

## 비교 요약

| | 단위 테스트 | 통합 테스트 | E2E 테스트 |
|---|---|---|---|
| **파일 패턴** | `*ModelTest` | `*ServiceIntegrationTest` | `*E2ETest` |
| **검증 범위** | Model 클래스 1개 | Service + Repository + DB | HTTP 요청 → DB 전체 |
| **Spring 컨텍스트** | ❌ | ✅ | ✅ |
| **실제 DB** | ❌ | ✅ Testcontainers | ✅ Testcontainers |
| **실제 HTTP** | ❌ | ❌ | ✅ TestRestTemplate |
| **속도** | 빠름 (ms) | 보통 (수 초) | 느림 (수십 초) |
| **테스트 수** | 많이 | 보통 | 적게 |
| **실패 원인 파악** | 쉬움 | 보통 | 어려움 |

---

## 실행 명령어

```shell
# 특정 단위 테스트
./gradlew :apps:commerce-api:test --tests "com.loopers.domain.user.UserModelTest"

# 특정 통합 테스트
./gradlew :apps:commerce-api:test --tests "com.loopers.domain.user.UserServiceIntegrationTest"

# 특정 E2E 테스트
./gradlew :apps:commerce-api:test --tests "com.loopers.interfaces.api.UserV1ApiE2ETest"

# 전체 테스트
./gradlew :apps:commerce-api:test
```

> **참고:** 통합 테스트와 E2E 테스트는 Docker가 실행 중이어야 한다. (Testcontainers가 MySQL 컨테이너를 자동으로 띄운다)
