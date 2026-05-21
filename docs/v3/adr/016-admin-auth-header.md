# ADR-016: Admin 인증 — 테이블 미생성, 헤더 고정값 검증

- 날짜: 2026-05-22
- 상태: 승인됨

## 결정

Admin 인증을 위한 별도 테이블을 생성하지 않는다. `X-Loopers-Ldap: loopers.admin` 헤더의 고정값 일치 여부로 검증한다.

```java
// AdminAuthInterceptor
String ldap = request.getHeader("X-Loopers-Ldap");
if (!"loopers.admin".equals(ldap)) {
    throw new CoreException(ErrorType.FORBIDDEN, "관리자 권한이 없습니다.");
}
```

## 근거

요구사항에서 Admin 인증은 `X-Loopers-Ldap: loopers.admin` 헤더로 명시되어 있다. 이는 내부 운영 도구 수준의 단순 접근 제어이며, 실제 LDAP 연동이나 Admin 계정 관리는 현재 범위에 포함되지 않는다.

### 고려한 대안

#### Option 1. Admin 테이블 생성 + DB 검증 (기각)

`users` 테이블과 별개의 `admin` 테이블을 두고, 요청 헤더의 식별 정보로 `admin` 테이블을 조회하여 인증하는 방식이다. 판매자·관리자 등 역할 구분이 필요한 경우 `admin` 테이블에 역할 컬럼을 추가하거나, `users` 테이블과 JOIN하여 권한을 검증할 수 있다.

- **장점**: 계정 관리(생성·삭제·권한 변경)가 가능하다. 역할 기반 접근 제어(RBAC)로 확장 가능하다.
- **단점**: Admin CRUD API, 인증 토큰 관리 등 추가 구현 범위가 크다.

---

#### Option 2. 헤더 고정값 검증 (채택)

`X-Loopers-Ldap` 헤더의 값이 `loopers.admin`과 일치하는지 확인하는 방식이다.

- **장점**: 구현이 단순하다. 요구사항을 정확히 만족한다. Admin 테이블 없이 `HandlerInterceptor` 한 곳에서 처리된다.
- **단점**: 고정값이 노출되면 누구나 Admin 권한을 획득할 수 있다. 실제 운영 환경에서는 적합하지 않다.

## 향후 고려사항

추후 판매자 / 관리자 구분 혹은 관리자 헤더의 값이 변한다면, Option 1 채택 진행.
