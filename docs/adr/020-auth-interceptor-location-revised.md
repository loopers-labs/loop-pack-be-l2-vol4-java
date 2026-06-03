# ADR-020: 인증 인터셉터 위치 재검토 — interfaces/auth

- 날짜: 2026-05-28
- 상태: 승인됨
- 대체: [ADR-011](./011-auth-interceptor-location.md)

## 결정

`UserAuthInterceptor`와 `AdminAuthInterceptor`는 `interfaces/auth/` 패키지에 위치한다.
인증 유스케이스(`authenticate`)는 `application/user/UserFacade`에 위치한다.

```
interfaces/
└── auth/
    ├── UserAuthInterceptor       ← X-Loopers-LoginId / X-Loopers-LoginPw 검증
    ├── AdminAuthInterceptor      ← X-Loopers-Ldap 고정값 검증
    ├── LoginUser.java            ← @LoginUser 어노테이션
    ├── LoginUserArgumentResolver ← request attribute → userId 바인딩
    └── WebMvcConfig              ← Interceptor + ArgumentResolver 등록

application/
└── user/
    └── UserFacade
        └── authenticate(loginId, password): Long  ← 인증 유스케이스
```

## ADR-011 대비 변경 이유

ADR-011 채택 당시에는 인터셉터 내부에서 `UserService`(domain 레이어)를 직접 호출하는 구조를 상정했다.
이후 검토에서 두 가지 문제가 확인되었다.

### 1. 레이어 의존 방향 위반

`support/auth/`에 인터셉터를 두면, `support` 패키지가 `domain` 레이어(UserService)에 직접 의존하게 된다.
이 프로젝트의 레이어 의존 원칙(`interfaces → application → domain`)과 충돌한다.

```
// ADR-011 구조 (문제)
support/auth/UserAuthInterceptor → domain/user/UserService  ← 레이어 건너뜀

// ADR-020 구조 (개선)
interfaces/auth/UserAuthInterceptor
    → application/user/UserFacade.authenticate()
        → domain/user/UserService
```

### 2. support/ 패키지 성격 훼손

기존 `support/error/`(CoreException, ErrorType)는 도메인에 무관한 순수 인프라 코드다.
`UserAuthInterceptor`는 `UserFacade`(application 레이어)에 의존하므로, `support/`의 도메인 무관 원칙과 맞지 않는다.

## 근거

### 인증 유스케이스는 application 레이어 책임

"아이디와 비밀번호로 사용자를 인증한다"는 행위는 도메인 규칙이 아니라 애플리케이션이 제공하는 유스케이스다.
`UserFacade.authenticate()`가 자격증명 검증 로직을 캡슐화하고, 인터셉터는 헤더 추출과 request attribute 저장만 담당한다.

### 인터셉터는 웹 레이어 관심사

인터셉터는 Spring MVC 요청 처리 체인의 일부로, 본질적으로 웹 레이어(interfaces)의 관심사다.
`interfaces/auth/`에 위치하면 레이어 의미와 실제 위치가 일치한다.
`api/` 하위가 아닌 `interfaces/` 바로 아래에 두어 도메인별 패키지(`api/brand/`, `api/product/` 등)와 분리한다.

### 고려한 대안

#### Option 1. support/auth/ (기각 — ADR-011)

- **장점**: 횡단 관심사끼리 모아 응집도를 높일 수 있다.
- **단점**: `UserFacade`(application 레이어) 의존으로 인해 `support/`의 도메인 무관 원칙이 깨진다.

#### Option 2. interfaces/auth/ (채택)

- **장점**: 레이어 의존 방향을 준수한다. 인터셉터의 웹 레이어 성격과 위치가 일치한다.
- **단점**: 횡단 관심사가 `interfaces/` 아래에 위치하게 된다. 단, `api/` 하위가 아닌 별도 패키지로 분리하여 도메인 패키지와의 혼재를 방지한다.

## 향후 고려사항

인증 방식이 헤더 기반에서 JWT나 Session 기반으로 변경될 경우,
`interfaces/auth/`와 `UserFacade.authenticate()` 내부만 수정하면 된다.
