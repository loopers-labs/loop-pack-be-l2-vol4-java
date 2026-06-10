# ADR-034: Coupon 도메인 서비스 미도입 및 레이어 책임 재정의

- 날짜: 2026-06-09
- 상태: 승인됨
- 관련: [ADR-030](./030-coupon-application-service-naming.md)

## 결정

Coupon 도메인에 별도의 Domain Service(`CouponDomainService`)를 도입하지 않는다.
모든 비즈니스 규칙은 엔티티에 캡슐화하고, 유스케이스 조율과 Repository 접근은 `CouponApplicationService`가 담당한다.

### 레이어별 책임

| 레이어 | 클래스 | 역할 | Repository 접근 |
|---|---|---|:---:|
| Domain Entity | `CouponEntity`, `CouponTemplateEntity` | 비즈니스 규칙 캡슐화 | X |
| Domain Service | (미도입) | — | X |
| Application Service | `CouponApplicationService` | 유스케이스 조율, Repository 호출, 트랜잭션 관리 | O |

### Domain Service 원칙

도메인 서비스가 도입되는 경우는 아래 조건을 충족할 때로 한정한다:
- 단일 엔티티에 속하지 않는 복잡한 도메인 로직이 존재하는 경우
- 여러 Aggregate를 조합해야 하는 순수 도메인 규칙인 경우
- Repository에 접근하지 않아도 되는 경우

도메인 서비스는 Repository에 접근하지 않는다. Repository 접근이 필요한 로직은 Application Service 책임이다.

## 근거

Coupon 도메인의 비즈니스 규칙 전체가 이미 엔티티 메서드에 캡슐화되어 있다:

| 규칙 | 위치 |
|---|---|
| `use()` — AVAILABLE → USED 상태 전환, 그 외 예외 | `CouponEntity` |
| `resolveStatus(expiredAt)` — lazy 만료 판단 | `CouponEntity` |
| `isOwnedBy(userId)` — 소유권 검증 | `CouponEntity` |
| `isExpired()` — 템플릿 만료 여부 확인 | `CouponTemplateEntity` |
| `calculateDiscount(orderAmount)` — FIXED/RATE 할인 계산 | `CouponTemplateEntity` |

여러 엔티티를 조합해야 하는 복잡한 순수 도메인 로직이 존재하지 않으므로, Domain Service를 추가하면 Repository 접근 없이 의미 있는 로직을 담기 어렵다. 빈 껍데기 클래스를 만드는 것은 불필요한 추상화다.

## 향후 고려사항

- 쿠폰 관련 도메인 규칙이 복잡해져 단일 엔티티로 표현하기 어려운 로직이 생길 경우 Domain Service 도입을 재검토한다.
