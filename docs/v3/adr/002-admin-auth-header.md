# ADR-002: 어드민 인증 헤더 검증

- 날짜: 2026-05-20
- 상태: 승인됨

## 결정

어드민 인증은 `X-Loopers-Ldap` 헤더 값이 `"loopers.admin"`과 일치하는지만 확인한다. DB 조회나 별도 어드민 엔티티 없이 애플리케이션 레벨에서 하드코딩 검증으로 처리한다.

## 근거

요구사항 문서에 **"인증/인가는 주요 스코프가 아니므로 구현하지 않습니다"** 라고 명시되어 있다. 이커머스 핵심 비즈니스 로직(Brand, Product, Like, Order) 구현에 집중하기 위해 인증 복잡도를 최소화한다.

### 고려한 대안

#### Option 1. 하드코딩 고정값 검증 (채택)

`X-Loopers-Ldap` 헤더 값을 `"loopers.admin"` 문자열과 단순 비교하는 방식이다. DB 조회 없이 Interceptor에서 즉시 처리한다.

```java
if (!"loopers.admin".equals(request.getHeader("X-Loopers-Ldap"))) {
    throw new CoreException(ErrorType.FORBIDDEN, "어드민 권한이 필요합니다.");
}
```

- **장점**: 구현이 극히 단순하고, DB 조회가 없어 인증 처리 비용이 없다. 요구사항에서 명시적으로 인증을 스코프 외로 정의했으므로 현재 단계에 적합하다.
- **단점**: 어드민이 단 한 명(고정값)이므로 확장성이 없다. 헤더 값이 노출되면 누구나 어드민으로 행세할 수 있다. 실제 서비스에는 절대 사용할 수 없는 방식이다.

---

#### Option 2. users 테이블에 role 컬럼 추가

`users` 테이블에 `role` 컬럼(`USER` / `ADMIN`)을 추가하고, 헤더로 유저를 조회한 후 role을 검증하는 방식이다.

```java
User user = userRepository.findByLoginId(loginId);
if (user.getRole() != Role.ADMIN) {
    throw new CoreException(ErrorType.FORBIDDEN, "어드민 권한이 필요합니다.");
}
```

- **장점**: 유저와 어드민을 동일 테이블에서 관리하므로 스키마가 단순하다. 여러 어드민 계정을 지원할 수 있다.
- **단점**: 유저/어드민 인증 흐름이 동일해지므로, 현재의 `X-Loopers-LoginId` / `X-Loopers-Ldap` 헤더 분리 방식과 맞지 않는다. 인증 복잡도가 높아져 현재 스코프를 벗어난다.

---

#### Option 3. 별도 admins 테이블 운영

`admins` 테이블을 별도로 두고 LDAP 값으로 어드민을 조회하는 방식이다.

```java
Admin admin = adminRepository.findByLdap(ldapValue)
    .orElseThrow(() -> new CoreException(ErrorType.FORBIDDEN));
```

- **장점**: 어드민과 유저의 관심사가 완전히 분리된다. 어드민별 권한 세분화(브랜드 관리자, 주문 관리자 등)로 확장하기 용이하다.
- **단점**: 어드민 테이블, Repository, Service 추가로 구현 비용이 높다. 현재 요구사항에서 어드민은 단일 고정값이므로 과도한 설계다.

## 향후 고려사항

서비스 확장 시 Option 2 또는 Option 3으로 전환을 고려한다. 특히 멀티 어드민, 권한 세분화가 필요해질 경우 Option 3이 적합하다.
