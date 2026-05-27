# Week 1 세션 구현 로그

> 2026-05-14 ~ 2026-05-15 진행된 개발 세션의 고민, 결정, 구현 내용을 정리한 문서입니다.

---

## 목차

1. [세션 목표](#1-세션-목표)
2. [개발 환경 설정](#2-개발-환경-설정)
3. [회원가입 API](#3-회원가입-api)
4. [내 정보 조회 API](#4-내-정보-조회-api)
5. [커밋 이력](#5-커밋-이력)
6. [기술 탐구 노트](#6-기술-탐구-노트)
7. [남은 작업](#7-남은-작업)

---

## 1. 세션 목표

`week1-guide.md` 기능 명세 기반으로 아래 두 기능을 구현하는 것이 목표였다.

| 기능 | 엔드포인트 | 상태 |
|------|-----------|------|
| 회원가입 | `POST /api/v1/users` | ✅ 완료 |
| 내 정보 조회 | `GET /api/v1/users/me` | ✅ 완료 |
| 비밀번호 수정 | `PATCH /api/v1/users/me/password` | ⏳ 미착수 |

---

## 2. 개발 환경 설정

### 2.1 Claude Code 커스텀 슬래시 커맨드

`.claude/commands/` 에 프로젝트 워크플로우에 맞는 커맨드 5종을 추가했다.

| 커맨드 | 파일 | 역할 |
|--------|------|------|
| `/test` | `test.md` | 단위·통합·E2E 테스트 코드 작성 |
| `/refactor` | `refactor.md` | 성능·설계 관점 리팩터링 |
| `/vcs` | `vcs.md` | git commit, diff 관리 |
| `/doc` | `doc.md` | 요구사항 분석, 구현 내용, 결과 문서화 |
| `/plan` | `plan.md` | 구현 전 설계 분석 및 방향 제안 |

> 커맨드를 명시적으로 입력하지 않아도 자연어 요청으로 자동 매핑된다.
> 커스텀 커맨드는 CLAUDE.md와 함께 커밋 `37b3953`에 포함됐다.

---

## 3. 회원가입 API

### 3.1 요구사항 분석

```
POST /api/v1/users
Body: { loginId, password, name, birthDate, email }

비즈니스 규칙:
- loginId: 영문 + 숫자만 허용 (중복 불가)
- email: xx@yy.zz 형식
- birthDate: 현재 시점 이전 날짜 (yyyy-MM-dd)
- password: 8~16자, 허용 문자(영문 대소문자·숫자·특수문자), 생년월일 포함 불가
- 저장 전 BCrypt 암호화 필수
```

### 3.2 설계 결정

#### 레이어 간 객체 구분

| 레이어 | 타입 | 설명 |
|--------|------|------|
| `interfaces` | `UserRegisterRequest` | HTTP 요청 바디 역직렬화 |
| `application` | `UserRegisterCommand` | Facade 입력 파라미터 |
| `domain` | `UserModel` | 도메인 불변식을 보장하는 핵심 모델 (JPA Entity) |
| `application` | `UserInfo` | Facade 출력, 레이어 간 반환 DTO |
| `interfaces` | `UserRegisterResponse` | HTTP 응답 바디 직렬화 |

레이어 간 매핑은 각 객체에 변환 메서드를 두는 패턴을 사용한다.
```
UserRegisterRequest.toCommand() → UserRegisterCommand
UserRegisterCommand.toDomain(encodedPassword) → UserModel
UserInfo.from(UserModel) → UserInfo
```

#### 비밀번호 인코더 위치

처음에는 `UserModel` 또는 `UserService`에 `PasswordEncoder`를 넣는 방안을 고민했다.

| 위치 | 기각 이유 |
|------|-----------|
| `UserModel` | 도메인 모델이 인프라 빈(PasswordEncoder)에 의존하게 됨 |
| `UserService` (register) | 서비스가 암호화 책임까지 갖는 것은 적절하나, 테스트 시 PasswordEncoder mock이 필요해짐 |
| `UserFacade` | **선택.** Facade가 오케스트레이션 레이어로서 인코딩 → 도메인 객체 생성 → 저장을 조율 |

```java
// UserFacade
public UserInfo register(UserRegisterCommand command) {
    passwordPolicy.validate(command.password(), command.birthDate()); // 형식 검증
    String encodedPassword = passwordEncoder.encode(command.password()); // 인코딩
    UserModel user = command.toDomain(encodedPassword); // 도메인 객체 생성
    UserModel saved = userService.register(user); // 저장 (중복 검증)
    return UserInfo.from(saved);
}
```

#### 비밀번호 형식 검증 위치: `PasswordPolicy`

비밀번호 형식 검증(8~16자, 생년월일 포함 여부)을 어디에 둘지 고민했다.

- **Facade에 직접 넣기**: 회원가입 외에 비밀번호 변경 시에도 동일 검증이 필요 → 중복 발생 우려
- **`UserModel` 생성자**: 모델이 raw password를 받아야 함 → 도메인 모델에 평문 비밀번호가 노출됨
- **`PasswordPolicy` (domain 컴포넌트)**: **선택.** 재사용 가능한 도메인 정책 객체. Spring 없이 순수 Java로 테스트 가능.

```java
@Component
public class PasswordPolicy {
    // 회원가입, 비밀번호 변경 양쪽에서 재사용 가능
    public void validate(String rawPassword, LocalDate birthDate) {
        validateFormat(rawPassword);
        validateNotContainsBirthDate(rawPassword, birthDate);
    }
}
```

#### LocalDate 직렬화

프론트엔드와의 연동을 위해 `birthDate`를 JSON에서 어떻게 표현할지 논의했다.

- `timestamp` (long): 프론트에서 파싱 편리하나 직관성 낮음
- **`yyyy-MM-dd` (ISO 8601 문자열)**: **선택.** 사람이 읽기 쉽고 국제 표준

```java
// JacksonConfig.java
featuresToDisable = {SerializationFeature.WRITE_DATES_AS_TIMESTAMPS}
```

Jackson의 `JavaTimeModule`을 등록하면 기본적으로 timestamp로 직렬화한다. 이 설정으로 ISO 8601 문자열 출력을 강제한다.

### 3.3 구현 파일 목록

```
[domain]
domain/user/UserModel.java          — 도메인 불변식, maskedName()
domain/user/UserRepository.java     — 인터페이스
domain/user/UserService.java        — register() (중복 검증), authenticate()
domain/user/PasswordPolicy.java     — 비밀번호 형식 + 생년월일 포함 검증

[application]
application/user/UserRegisterCommand.java — Facade 입력 + toDomain()
application/user/UserFacade.java          — 오케스트레이션
application/user/UserInfo.java            — Facade 출력 DTO

[infrastructure]
infrastructure/user/UserJpaRepository.java  — Spring Data JPA
infrastructure/user/UserRepositoryImpl.java — Repository 구현

[interfaces]
interfaces/api/user/UserV1Controller.java — POST /api/v1/users
interfaces/api/user/UserV1Dto.java        — Request / Response

[config]
support/security/PasswordEncoderConfig.java — BCryptPasswordEncoder 빈
```

### 3.4 테스트 전략

#### 단위 테스트

| 파일 | 검증 대상 | 케이스 수 |
|------|-----------|-----------|
| `UserModelTest` | loginId·email·name·birthDate·password 도메인 불변식, maskedName() | 18 |
| `PasswordPolicyTest` | 비밀번호 형식(길이·허용문자), 생년월일 4가지 포맷 포함 여부 | 9 |
| `UserServiceTest` | register() 중복 CONFLICT, 정상 저장 / authenticate() 성공·없는 loginId·틀린 PW | 5 |

> `UserServiceTest`에서 검토한 것: `then(userRepository).should().save(user)`는 `assertThat(result).isEqualTo(user)`가 이미 save() 호출을 간접 증명하므로 제거. 반면 `should(never()).save()`는 "CONFLICT 시 저장이 일어나선 안 된다"는 독립적 의미가 있어 유지.

#### 통합 테스트

| 파일 | 검증 대상 |
|------|-----------|
| `UserServiceIntegrationTest` | Testcontainers MySQL로 실제 DB 저장·중복·BCrypt 인증 검증 |

---

## 4. 내 정보 조회 API

### 4.1 요구사항 분석

```
GET /api/v1/users/me
Headers: X-Loopers-LoginId, X-Loopers-LoginPw (필수)

응답: { loginId, name(마스킹), birthDate, email }
- 이름 마스킹: 마지막 글자를 '*'로 치환 (예: 홍길동 → 홍길*)
- 헤더 누락 또는 인증 실패 시 401
```

### 4.2 설계 결정

#### 인증 헤더 처리 방식

초기 구현 방향: 컨트롤러에서 `@RequestHeader`로 직접 받아 Facade에 전달.

```java
// 문제 있는 방식: 인증 코드가 컨트롤러마다 반복됨
@GetMapping("/me")
public ApiResponse<UserMeResponse> getMe(
    @RequestHeader("X-Loopers-LoginId") String loginId,
    @RequestHeader("X-Loopers-LoginPw") String rawPw) { ... }
```

**채택한 방식: `AuthInterceptor` + `CurrentUserArgumentResolver`**

설계 근거는 `auth-design-decisions.md` 참고. 핵심 판단:

1. **인증 강제(경로 기반)** → `AuthInterceptor.preHandle()` 담당
2. **인증 객체 주입(파라미터 기반)** → `CurrentUserArgumentResolver.resolveArgument()` 담당

두 역할을 분리한 이유: 리졸버만 쓰면 `@CurrentUser` 파라미터가 없는 메서드는 인증이 일어나지 않는다. 인터셉터는 경로 패턴 기반으로 `@CurrentUser` 여부와 무관하게 작동한다.

```
Request → AuthInterceptor.preHandle()
              헤더 추출 → authenticate() → request.setAttribute("currentUser", user)
          → CurrentUserArgumentResolver.resolveArgument()
              request.getAttribute("currentUser") → @CurrentUser UserModel 파라미터
          → Controller(@CurrentUser UserModel user)
```

#### Interceptor vs Filter 선택 이유

| 기준 | Filter | Interceptor (선택) |
|------|--------|-------------------|
| Spring 빈 주입 | 가능하지만 과거엔 번거로움 | 자연스럽게 주입됨 |
| 경로 패턴 관리 | `FilterRegistrationBean` 설정 필요 | `addPathPatterns()` / `excludePathPatterns()` |
| `@ControllerAdvice` 연계 | 연계 안 됨 | 연계됨 |
| HandlerMethod 접근 | 불가 | `handler instanceof HandlerMethod` 로 접근 가능 |

#### 이름 마스킹 위치

`maskedName()`을 `UserModel`에 두는 것이 적절한가를 검토했다.

- **Facade에서 마스킹**: 조회 방식에 따라 마스킹 정책이 달라질 수 있으나 도메인 규칙이 아닌 표현 정책처럼 보임
- **UserModel에 메서드**: **선택.** "이름 마지막 글자 마스킹"은 도메인 정책. 여러 레이어에서 재사용 가능.

```java
public String maskedName() {
    return name.substring(0, name.length() - 1) + "*";
}
```

### 4.3 구현 파일 목록

```
[domain] — 추가/변경
domain/user/UserModel.java    — maskedName() 추가
domain/user/UserService.java  — authenticate() 추가

[interfaces] — 신규
interfaces/api/auth/CurrentUser.java                — @Target(PARAMETER) 커스텀 애노테이션
interfaces/api/auth/AuthInterceptor.java            — 헤더 검증 + 인증 + attribute 저장
interfaces/api/auth/CurrentUserArgumentResolver.java — @CurrentUser 파라미터 바인딩
interfaces/api/WebMvcConfig.java                    — Interceptor·Resolver 등록

[interfaces] — 변경
interfaces/api/user/UserV1Controller.java — GET /me 추가
interfaces/api/user/UserV1Dto.java        — UserMeResponse 추가

[support] — 변경
support/error/ErrorType.java — UNAUTHORIZED(401) 추가
```

### 4.4 테스트 전략

#### 단위 테스트

| 파일 | 케이스 |
|------|--------|
| `UserModelTest` | maskedName() — 다자 이름, 1자 이름 |
| `UserServiceTest` | authenticate() 성공·없는 loginId·틀린 비밀번호 |
| `AuthInterceptorTest` | LoginId 헤더 누락 401·LoginPw 헤더 누락 401·인증 성공(attribute 저장+true 반환)·잘못된 인증 예외 전파 |
| `CurrentUserArgumentResolverTest` | supportsParameter true(@CurrentUser 있음)·false(없음)·resolveArgument attribute 반환 |

#### 통합 테스트

| 파일 | 검증 대상 |
|------|-----------|
| `UserServiceIntegrationTest` | authenticate() BCrypt 실제 인코딩 기반 성공·없는 loginId·틀린 PW |
| `AuthInterceptorIntegrationTest` | `@WebMvcTest` + `@Import(WebMvcConfig, AuthInterceptor, Resolver)` — 인터셉터가 MVC 파이프라인에 실제로 연결됐는지 확인 (단위 테스트로 커버 불가한 영역) |

> `AuthInterceptorIntegrationTest`가 검증하는 핵심: `WebMvcConfig`에 인터셉터를 등록하지 않으면 단위 테스트는 통과해도 실제로는 작동하지 않는다. 이 간극을 `@WebMvcTest`로 잡는다.

---

## 5. 커밋 이력

| 해시 | 날짜 | 메시지 |
|------|------|--------|
| `37b3953` | 2026-05-14 | chore: Claude Code 커스텀 슬래시 커맨드 5종 추가 및 CLAUDE.md 초기화 |
| `9d9b425` | 2026-05-15 | feat(commerce-api): 회원가입 API 구현 |
| `6176275` | 2026-05-15 | test(commerce-api): 회원가입 단위 테스트 및 통합 테스트 추가 |
| `3fea3a0` | 2026-05-15 | feat(commerce-api): 내 정보 조회 API 구현 및 인증 인터셉터 도입 |
| `36a22d2` | 2026-05-15 | test(commerce-api): 내 정보 조회 단위 테스트 및 통합 테스트 추가 |

---

## 6. 기술 탐구 노트

세션 중 구현 결정과 맞물려 심화 탐구한 두 가지 주제다. 기술 블로그 포스팅 소재로 정리했다.

### 6.1 Spring에서 인증 정보를 가로채는 방법 비교

`AuthInterceptor`를 구현하면서 "다른 방법은 무엇이 있고, 왜 이것을 선택했는가"를 정리했다.

#### 요청 처리 레이어별 위치

```
HTTP 요청
  → [Servlet Filter]              — DispatcherServlet 진입 전
  → [DispatcherServlet]
  → [HandlerInterceptor.preHandle] — 핸들러 결정 후
  → [ArgumentResolver]             — 파라미터 바인딩 시
  → [Controller Method]
```

#### 방법별 비교

| 방법 | 장점 | 단점 | 주요 유스케이스 |
|------|------|------|----------------|
| **Servlet Filter** | 범위 가장 넓음, 응답 직접 제어 | @ExceptionHandler 미연계, HandlerMethod 접근 불가 | CORS, 요청 로깅, MDC |
| **HandlerInterceptor** | HandlerMethod 접근, 경로 패턴 선언적 관리 | Filter 이후 실행, 서블릿 미포함 요청 처리 불가 | 애노테이션 기반 권한 분기 |
| **ArgumentResolver** | 특정 파라미터에만 선택적 적용 | 단독으로는 인증 강제 불가 | Interceptor와 조합해 파라미터 주입 |
| **Spring Security** | 표준화, 인가 내장, OAuth2 확장 | 학습 곡선, 설정 장황 | OAuth2, RBAC, 외부 사용자 서비스 |
| **AOP** | 코드 침투 없음, 서비스 레이어 적용 | 흐름 불투명, HTTP 컨텍스트 접근 번거로움 | 감사 로그, 속도 제한 |

#### 우리가 Interceptor를 선택한 이유 요약

- Spring Security 없이 가벼운 구조 유지
- `excludePathPatterns`으로 회원가입 경로를 선언적으로 제외
- `HandlerMethod` 접근으로 미래에 `@PublicApi` 같은 애노테이션 기반 스킵 확장 가능
- `@ControllerAdvice`와 자연스럽게 연계돼 예외 처리 일관성 유지

#### 설계 진화 경로

```
1단계: 컨트롤러에서 @RequestHeader 직접 처리 (반복 코드)
     ↓
2단계: Interceptor + ArgumentResolver (현재 상태)
     ↓
3단계: Spring Security Resource Server + JWT (서비스 확장 시)
```

### 6.2 MSA / Cloud-Native 환경에서의 인증 아키텍처

MSA 전환 시 단일 서버의 `AuthInterceptor`가 어떤 인프라 레이어로 이동하는지 정리했다.

#### 책임 이동 구조

```
[인증 서버 (Keycloak / Spring Authorization Server)]
  loginId + password → JWT (Access Token) 발급

[API Gateway (Spring Cloud Gateway / Kong)]
  JWT 서명 검증 → X-User-Id, X-User-Roles 헤더로 변환 → 내부 전달

[Service Mesh (Istio / Envoy Sidecar)]
  서비스 간 mTLS → 워크로드 신원(SPIFFE) 기반 East-West 보안

[마이크로서비스]
  X-User-Id 헤더만 읽음. 인증 코드 없음.
```

#### 핵심 개념 정리

| 개념 | 설명 |
|------|------|
| **OIDC** | OAuth2 위의 신원 계층. JWT `id_token`으로 "이 토큰이 누구인지" 표준화 |
| **JWKS URI** | 인증 서버의 공개키를 런타임에 동적으로 받아오는 엔드포인트. JWT 검증에 사용 |
| **mTLS** | 양방향 TLS. 클라이언트·서버 모두 인증서 검증. Istio가 클러스터 내 자동 적용 |
| **SPIFFE** | 워크로드 신원 표준. Istio mTLS 내부 구현 기반 |
| **Zero Trust** | "네트워크 안에 있다고 신뢰하지 않는다". 모든 통신을 워크로드 신원으로 검증 |

#### 단계적 도입 경로

```
1단계 → Interceptor (현재)
          서비스 1개, 팀 소규모

2단계 → 인증 서버 분리 + JWT
          서비스 2~3개. 각 서비스에 Spring Security Resource Server 적용.

3단계 → API Gateway
          JWT 검증을 게이트웨이로 올림. 서비스에서 인증 코드 제거.

4단계 → Service Mesh (Istio)
          서비스 다수. Zero Trust. East-West 보안 자동화.
```

#### 더 공부할 내용

- `spring-security-oauth2-resource-server` — JWT 검증을 Spring Security 표준으로 연결하는 방법
- OAuth2 Token Exchange (RFC 8693) — 서비스 A→B 호출 시 사용자 토큰 위임 방식
- OPA (Open Policy Agent) — 인가 정책을 코드와 분리해 Istio와 통합
- BFF (Backend for Frontend) — 프론트엔드 전용 백엔드에서 토큰 관리를 서버 사이드로 이관하는 이유

---

## 7. 남은 작업

### 기능 구현

- [ ] **비밀번호 수정** (`PATCH /api/v1/users/me/password`)
  - 필요 정보: 기존 비밀번호, 새 비밀번호
  - `PasswordPolicy.validate()` 재사용
  - 현재 비밀번호와 동일한 새 비밀번호 불가 검증 추가 필요

### 테스트

- [ ] E2E 테스트 — `@SpringBootTest(webEnvironment = RANDOM_PORT)` + Testcontainers
  - 회원가입 → 내 정보 조회 전체 흐름
  - 헤더 누락·잘못된 인증 정보 시나리오
  - `excludePathPatterns` 검증 (회원가입은 헤더 없이 통과)

### 문서

- [ ] `http/user.http` — 완성된 API 호출 예시 파일 작성
