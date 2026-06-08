# ADR-030: Coupon Application 계층 — XxxFacade 대신 CouponApplicationService 사용

- 날짜: 2026-06-08
- 상태: 승인됨

---

## Introduction & Goals

- **Context / Background**:
  기존 프로젝트의 Application 계층은 `XxxFacade` 명칭을 사용하며, DomainService를 직접 조율하는 구조다 (ADR-027 참고). Coupon 도메인에서는 Domain Service를 두지 않고, Application 계층이 Repository와 Entity를 직접 사용하여 유즈케이스를 조율한다. 이 구조에서 `Facade`라는 명칭은 맞지 않는다.

- **Goals**:
  Coupon Application 계층의 실제 역할(Repository + Entity 직접 조율)을 이름에 정확히 반영한다.

---

## Detailed Design

### System Architecture

```
Interfaces   (Controller)
    ↓
Application  (CouponApplicationService)  ← Repository + Entity 직접 사용
    ↓
Infrastructure (Repository)
```

DomainService가 없으므로 ADR-027에서 정의한 `Facade` 구조와 다르다.

```
// 기존 Facade 패턴 (DomainService 조율)
OrderFacade → OrderService → OrderRepository

// Coupon (DomainService 없음)
CouponApplicationService → CouponRepository, CouponTemplateRepository
```

### Constraints

- `CouponApplicationService`는 다른 `XxxFacade`를 호출하지 않는다.
- `CouponApplicationService`에 비즈니스 규칙을 작성하지 않는다. 규칙은 Entity에 캡슐화한다.
- 향후 Coupon 도메인에 DomainService가 필요해지면 `XxxFacade`로 전환을 검토한다.

---

## Alternatives Considered

| 옵션 | Pros | Cons |
|------|------|------|
| `CouponFacade` 유지 (기존 컨벤션) | 프로젝트 전체 일관성 유지. | DomainService 없이 Repository를 직접 사용하는 구조에서 Facade 명칭은 부정확. |
| **선택: `CouponApplicationService`** | 실제 역할(유즈케이스 조율)을 이름이 정확히 표현. DomainService 없는 구조에 적합. | 기존 `XxxFacade` 컨벤션과 불일치. |

**선택 근거:**

ADR-027에서 이 프로젝트가 `Facade`를 선택한 근거는 "DomainService를 조율하면서 Controller에 단순 진입점을 제공"하는 구조였다. Coupon 도메인은 DomainService가 없어 Repository를 직접 사용하므로, 동일한 근거가 성립하지 않는다. 정확한 역할 표현을 위해 `CouponApplicationService`를 사용한다.
