# 인증 처리 설계 결정 문서

> 회원가입 및 인증 헤더 공통화 작업 중 논의된 설계 결정 사항을 정리한 문서입니다.

---

## 1. 기능 요구사항 요약

### 회원가입
- 필요 정보: 로그인 ID, 비밀번호, 이름, 생년월일, 이메일
- 이미 가입된 로그인 ID로는 가입 불가
- 각 필드 포맷 검증 필요
  - ID: 영문 및 숫자 10자 이내
  - 이메일: `xx@yy.zz` 형식
  - 생년월일: `yyyy-MM-dd` 형식
- 비밀번호 규칙
  - 8~16자의 영문 대소문자, 숫자, 특수문자만 허용
  - 생년월일이 비밀번호 내에 포함될 수 없음
  - 저장 시 암호화 필수

### 인증 헤더 (회원가입 이후 유저 정보가 필요한 모든 요청)
```
X-Loopers-LoginId : 로그인 ID
X-Loopers-LoginPw : 비밀번호
```

---

## 2. 인증 헤더 공통화 방식

### 결정: `HandlerInterceptor` + `HandlerMethodArgumentResolver` 조합

매 컨트롤러 메서드마다 헤더를 직접 꺼내 검증하는 것은 비효율적이므로, Spring MVC의 두 확장 포인트를 조합해 공통화한다.

**처리 흐름:**
```
Request
  → AuthInterceptor.preHandle()
      : 헤더 유무 검증 → UserService.authenticate() → request attribute에 UserModel 저장
  → CurrentUserArgumentResolver.resolveArgument()
      : request attribute에서 UserModel 꺼내 → 컨트롤러 파라미터로 주입
  → Controller(@CurrentUser UserModel user)
```

**`Filter` 대신 `HandlerInterceptor`를 선택한 이유:**
- Filter는 Servlet 컨테이너 수준에서 동작하여 경로 제어(`excludePathPatterns` 등)를 Spring MVC 밖에서 별도로 처리해야 함
- Interceptor는 Spring 빈을 자연스럽게 주입받을 수 있고, `WebMvcConfigurer`를 통해 경로 매핑이 명확하게 관리됨

---

### 현재 방식의 한계 (Trade-off)

결정에는 이점만큼 감수한 제약이 있다.

#### 1. 타입 안전성 없음

`request.setAttribute`는 문자열 키에 `Object`를 저장하는 구조다.

```java
request.setAttribute("currentUser", user);                        // 저장: 문자열 키
UserModel user = (UserModel) request.getAttribute("currentUser"); // 조회: 캐스팅 필요
```

키 오타나 잘못된 캐스팅은 컴파일 타임이 아닌 런타임에서만 발견된다. `SecurityContextHolder.getContext().getAuthentication()` 같은 typed API가 없다는 점이 명확한 약점이다.

#### 2. Filter보다 늦게 실행 → MDC 연동 불가

```
ServletFilter → DispatcherServlet → Interceptor → Controller
```

구조화 로깅(Logback MDC)에 인증된 사용자 ID를 심어 모든 로그 라인에 남기려면 Filter 단계가 필요하다. Interceptor는 그 이후에 실행되므로, Filter 레벨에서 인증 컨텍스트가 필요한 요구사항(접근 로그, 분산 추적 correlation ID 등)엔 적합하지 않다.

#### 3. DispatcherServlet 밖 적용 불가

Interceptor는 Spring MVC `DispatcherServlet` 안에서만 동작한다. 아래 상황에서는 인증 컨텍스트가 전달되지 않는다.

| 상황 | 이유 |
|---|---|
| Actuator 엔드포인트 | 별도 서블릿 컨텍스트로 동작 |
| `@Scheduled`, `@Async` | HTTP 요청 스레드와 무관 |
| Kafka 컨슈머 (`commerce-streamer`) | DispatcherServlet 없음 |
| 배치 잡 (`commerce-batch`) | DispatcherServlet 없음 |

현재 프로젝트에서 Batch/Streamer가 별도 모듈로 분리된 것은 이 한계와 무관하게 올바른 구조지만, 동일 앱 내에서 비 HTTP 흐름이 생기면 인증 컨텍스트를 별도로 다뤄야 한다.

#### 4. 비동기 컨트롤러에서 attribute 유실

`request.setAttribute`는 요청 스레드에 바인딩된다. `ArgumentResolver`가 파라미터로 주입해주는 시점은 안전하지만, 비동기 흐름 내부에서 attribute를 다시 참조하면 null이 된다.

```java
@GetMapping("/me")
public CompletableFuture<ApiResponse<?>> getMeAsync(@CurrentUser UserModel user) {
    return CompletableFuture.supplyAsync(() -> {
        // user 파라미터는 주입됐지만,
        // 여기서 request.getAttribute("currentUser")를 직접 꺼내면 null
    });
}
```

#### 5. WebFlux 전환 시 전면 재작성

`HandlerInterceptor`는 Spring MVC 전용이다. WebFlux로 마이그레이션하면 `WebFilter` + `ServerWebExchange` 기반으로 완전히 교체해야 한다. Spring Security는 `ReactiveSecurityContextHolder`를 통해 이 문제를 미리 추상화해두고 있다.

---

### 방식별 Trade-off 비교

| 방식 | 강점 | 약점 |
|---|---|---|
| **Interceptor + Resolver** (채택) | Spring MVC 통합 자연스러움, 빈 주입 용이, 경로 제어 명확 | Filter 이후 실행, 타입 안전성 없음, MVC 밖 적용 불가 |
| **`OncePerRequestFilter`** | Filter 단계 실행 → MDC 연동 가능, 비 MVC 경로 포함 | 경로 제어 번거로움, URL 매칭 직접 구현 필요 |
| **Spring Security** | 표준화, 역할 기반 접근 제어 내장, typed context, WebFlux 지원 | 단순 케이스에서 설정 과잉, 의존성 범위 확대 |
| **AOP (`@Around`)** | 서비스 레이어까지 강제 적용 가능 | request 컨텍스트 접근 어려움, 실행 순서 제어 복잡 |

**`OncePerRequestFilter`로 전환을 고려할 시점:**
- MDC 기반 구조화 로깅에 인증된 사용자 정보가 필요해질 때
- Actuator 등 비 MVC 경로에도 인증을 적용해야 할 때

**Spring Security 도입을 고려할 시점:** → [아래 섹션 참고](#대안-검토-spring-security-도입)

---

### 대안 검토: Spring Security 도입

Spring Security를 사용하면 아래와 같이 표준 보안 프레임워크 위에서 인증을 구현할 수 있다.

**Spring Security 기반 구현 방식 (검토안):**
```
Request
  → OncePerRequestFilter (커스텀)
      : X-Loopers-LoginId / X-Loopers-LoginPw 헤더 파싱
      → AuthenticationManager → CustomAuthenticationProvider
      → SecurityContextHolder에 Authentication 저장
  → Controller(@AuthenticationPrincipal UserModel user)
```

**기각 이유:**

| 항목 | 내용 |
|---|---|
| 과도한 설정 오버헤드 | Spring Security 자동 구성은 CSRF, 세션 관리, HTTP Basic, Form Login 등 불필요한 필터 체인을 활성화한다. 이를 비활성화하는 설정 코드가 본래 기능보다 많아짐 |
| 의존 범위 확대 | `spring-security-crypto`만 의존하는 현재 설계와 달리, `spring-boot-starter-security` 추가 시 전체 Security 자동 구성이 활성화되어 기존 동작에 예기치 않은 영향이 생김 |
| 추상화 비용 | `Authentication`, `GrantedAuthority`, `SecurityContext` 등 프레임워크 추상화 계층이 삽입되어, 단순한 헤더 검증 로직을 파악하기 어려워짐 |
| 현재 요구사항과 불일치 | 이 프로젝트의 인증 요구사항은 "요청마다 헤더로 자격증명을 전달"하는 Stateless 방식이다. Spring Security가 제공하는 세션·토큰 기반 인증 관리는 불필요함 |

**Spring Security 도입을 고려할 시점:**
- JWT/OAuth2 등 토큰 기반 인증 체계로 전환할 때
- 역할(Role) 기반 접근 제어(RBAC)가 필요해질 때
- 멀티 인증 방식(소셜 로그인 등)을 지원해야 할 때

---

## 3. 모듈 위치 결정

### 결정: `apps/commerce-api` 내 `interfaces/api/auth/` 패키지

**검토한 위치와 기각 이유:**

| 위치 | 기각 이유 |
|---|---|
| `modules/` | reusable 원칙인데, `AuthInterceptor → UserService(도메인)`에 의존하므로 원칙 위반 |
| `supports/` 모듈 | 동일하게 도메인 결합 발생, reusable 불가 |
| `apps/commerce-api` 직하 | 적절한 위치이나 패키지 구조 정리 필요 |

**`interfaces/api/auth/`로 결정한 근거:**
- 인증은 "HTTP 요청의 헤더를 파싱해 도메인 서비스를 호출하는 것" → `interfaces` 레이어의 책임
- `commerce-batch`, `commerce-streamer`는 HTTP 헤더 인증이 불필요 → 공유 불필요
- 관련 파일(`AuthInterceptor`, `CurrentUserArgumentResolver`, `@CurrentUser`)이 응집된 묶음이므로 하위 패키지로 분리

**`WebMvcConfig` 위치: `interfaces/api/` 직하**
- 설정하는 대상(Interceptor, Resolver)이 모두 `interfaces/api/` 레이어 소속이므로 같은 레이어에 배치
- `auth/` 안에 두지 않는 이유: 나중에 CORS, MessageConverter 등 auth 외 설정이 추가될 수 있음
- 별도 `config/` 패키지는 설정 클래스가 여러 개로 늘어나는 시점에 도입 고려

### 최종 패키지 구조

```
apps/commerce-api/src/main/java/com/loopers/
├── interfaces/
│   └── api/
│       ├── auth/
│       │   ├── AuthInterceptor.java            ← 헤더 검증 + User 조회 + attribute 저장
│       │   ├── CurrentUserArgumentResolver.java ← attribute → 파라미터 주입
│       │   └── CurrentUser.java                ← 커스텀 애노테이션 (@Target PARAMETER)
│       ├── WebMvcConfig.java                   ← Interceptor/Resolver 등록
│       ├── ApiControllerAdvice.java
│       ├── ApiResponse.java
│       └── {feature}/
├── application/
├── domain/
│   └── user/
│       ├── UserModel.java
│       ├── UserService.java                    ← AuthInterceptor가 의존
│       └── UserRepository.java
└── infrastructure/
    └── user/
```

---

## 4. 현재 구현 상태

아래 파일이 생성됐으나 **껍데기(빈 클래스)만 존재하며 실제 구현 미완성:**

```
apps/commerce-api/src/main/java/com/loopers/interfaces/api/auth/
├── AuthInterceptor.java              ← 미구현
├── CurrentUserArguementResolver.java ← 미구현 (오타 주의: Argument)
└── CurrentUser.java                  ← 미구현 (@interface 선언 필요)
```

> **주의:** `CurrentUserArguementResolver.java` 파일명에 오타가 있음 (`Arguement` → `Argument`). 다른 PC에서 작업 시 파일명 수정 필요.

---

## 5. 구현해야 할 코드 스펙

### `@CurrentUser` 애노테이션
```java
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface CurrentUser {}
```

### `AuthInterceptor`
```java
public class AuthInterceptor implements HandlerInterceptor {
    private final UserService userService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String loginId = request.getHeader("X-Loopers-LoginId");
        String loginPw = request.getHeader("X-Loopers-LoginPw");

        if (loginId == null || loginPw == null) {
            throw new CoreException(ErrorType.UNAUTHORIZED, "인증 헤더가 누락되었습니다.");
        }

        UserModel user = userService.authenticate(loginId, loginPw);
        request.setAttribute("currentUser", user);
        return true;
    }
}
```

### `CurrentUserArgumentResolver`
```java
public class CurrentUserArgumentResolver implements HandlerMethodArgumentResolver {

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(CurrentUser.class);
    }

    @Override
    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                                  NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
        HttpServletRequest request = (HttpServletRequest) webRequest.getNativeRequest();
        return request.getAttribute("currentUser");
    }
}
```

### `WebMvcConfig`
```java
@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {
    private final AuthInterceptor authInterceptor;
    private final CurrentUserArgumentResolver currentUserArgumentResolver;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns("/api/v1/users/signup");
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(currentUserArgumentResolver);
    }
}
```

### 컨트롤러 사용 예시
```java
@GetMapping("/me")
public ApiResponse<UserV1Dto.Response> getMe(@CurrentUser UserModel user) {
    // user가 자동으로 주입됨
}
```

---

## 6. 테스트 전략

### 단위 테스트 (`AuthInterceptorTest`, `CurrentUserArgumentResolverTest`)
- Spring 컨텍스트 없이 순수 Java 테스트
- `UserService`는 Mock 처리
- `MockHttpServletRequest` 활용

| 테스트 케이스 | 검증 대상 |
|---|---|
| 헤더 없을 때 예외 발생 | `AuthInterceptor` |
| 헤더 있을 때 `UserService.authenticate()` 호출 | `AuthInterceptor` |
| 인증 성공 시 request attribute에 User 저장 | `AuthInterceptor` |
| `@CurrentUser` 있는 파라미터에만 동작 | `CurrentUserArgumentResolver` |
| attribute에서 올바르게 User 반환 | `CurrentUserArgumentResolver` |

### 통합 테스트 (`AuthInterceptorIntegrationTest`)
- `@WebMvcTest` + `MockMvc` 사용 (DB 불필요, UserService Mock)
- **핵심 검증: `WebMvcConfig`에 등록이 실제로 됐는지** (단위 테스트로 커버 불가한 영역)

| 테스트 케이스 |
|---|
| 헤더 없이 보호된 API 요청 → 401 |
| 유효한 헤더로 보호된 API 요청 → 200 |
| Interceptor가 MVC 파이프라인에 연결됐는지 확인 |

### E2E 테스트 (`AuthE2ETest`)
- `@SpringBootTest(webEnvironment = RANDOM_PORT)` + 실제 DB (Testcontainers)
- 실제 HTTP 흐름에서 인증 시나리오 전체 검증

| 테스트 케이스 |
|---|
| 헤더 없이 보호된 API 요청 → 401 |
| 잘못된 비밀번호로 요청 → 401 |
| 올바른 credentials로 요청 → 200 |
| 회원가입 엔드포인트는 헤더 없이도 통과 (`excludePathPatterns` 검증) |

---

## 7. 남은 작업

- [ ] `CurrentUser.java` → `@interface`로 구현
- [ ] `CurrentUserArguementResolver.java` → 파일명 오타 수정 후 구현
- [ ] `AuthInterceptor.java` 구현
- [ ] `WebMvcConfig.java` 생성 및 등록
- [ ] `UserModel`, `UserService`, `UserRepository` 구현 (회원가입 도메인)
- [ ] `ErrorType`에 `UNAUTHORIZED` 추가 여부 검토
- [ ] 단위/통합/E2E 테스트 작성
