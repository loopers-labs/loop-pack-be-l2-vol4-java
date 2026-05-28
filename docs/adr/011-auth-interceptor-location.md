# ADR-011: 인증 인터셉터 위치 — support/auth

- 날짜: 2026-05-21
- 상태: 대체됨 → [ADR-020](./020-auth-interceptor-location-revised.md)

## 결정

`UserAuthInterceptor`와 `AdminAuthInterceptor`는 `support/auth/` 패키지에 위치한다.

```
support/
├── error/
│   ├── CoreException
│   └── ErrorType
└── auth/
    ├── UserAuthInterceptor    ← X-Loopers-LoginId / X-Loopers-LoginPw 검증
    ├── AdminAuthInterceptor   ← X-Loopers-Ldap 고정값 검증
    └── WebMvcConfig           ← Interceptor 등록 (WebMvcConfigurer)
```

## 근거

인터셉터는 특정 도메인에 속하지 않고 전체 요청에 횡단 적용되는 인프라성 코드다. `interfaces/api/`는 도메인별 패키지(`brand/`, `product/`, `like/`, `order/`)로 구성되어 있어, 도메인과 무관한 인터셉터를 그 안에 두면 구조적 일관성이 깨진다.

`support/error/`가 레이어 횡단 에러 처리를 담당하는 것과 같이, `support/auth/`는 레이어 횡단 인증·인가 처리를 담당한다.

### 고려한 대안

#### Option 1. interfaces/api/interceptor/ (기각)

`interfaces/api/` 하위에 `interceptor/` 패키지를 두는 방식이다.

```
interfaces/api/
├── interceptor/
│   ├── UserAuthInterceptor
│   └── AdminAuthInterceptor
├── brand/
├── product/
...
```

- **장점**: 웹 요청 처리 체인의 일부이므로 `interfaces` 레이어에 두는 것이 의미상 자연스럽다.
- **단점**: `interfaces/api/`는 도메인별로 구성된 패키지 구조인데, 도메인과 무관한 횡단 관심사를 함께 두면 일관성이 깨진다.

---

#### Option 2. support/auth/ (채택)

`support/` 하위에 `auth/` 패키지를 두는 방식이다. 이미 `support/error/`가 레이어 횡단 에러 처리를 담당하는 구조와 일관성을 유지한다.

- **장점**: 도메인 패키지 구조를 오염시키지 않는다. 횡단 관심사끼리 한 곳에 모여 응집도가 높다.
- **단점**: 인터셉터가 Spring MVC 웹 레이어의 개념임에도 `support/`에 위치하므로, 레이어 경계가 다소 느슨하게 보일 수 있다.

## 향후 고려사항

인증 방식이 헤더 기반에서 JWT나 Session 기반으로 변경될 경우, `support/auth/` 내부만 수정하면 된다. 도메인 패키지에는 영향이 없다.
