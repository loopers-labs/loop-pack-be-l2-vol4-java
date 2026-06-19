# 쿠폰 도메인 모델링 설계

## 개요

쿠폰 도메인은 두 개의 독립적인 애그리거트로 구성된다.

- **CouponTemplate**: 어드민이 생성·관리하는 쿠폰 규칙 정의
- **IssuedCoupon**: 유저에게 발급된 쿠폰 인스턴스

---

## 애그리거트 분리 근거

두 개념은 라이프사이클과 쓰기 주체가 다르다.

| | CouponTemplate | IssuedCoupon |
|---|---|---|
| 생성 주체 | 어드민 | 유저 (발급 요청 시) |
| 변경 시점 | 이름·할인 정책·만료일 수정 (저빈도) | 발급 시 생성, 주문 시 `AVAILABLE → USED` (고빈도) |
| 삭제 | Soft delete (발급 내역과 무관하게 독립) | 삭제 없음 |
| 트랜잭션 결합 | 독립 | 주문 트랜잭션에 종속 |

---

## 애그리거트 간 참조 방식

`IssuedCoupon`은 `CouponTemplate`의 `id`만 보유한다. JPA `@ManyToOne` 연관관계를 맺지 않는다.

```java
// IssuedCouponModel
private Long couponTemplateId;  // @ManyToOne 없이 ID만 참조
```

같은 애그리거트 내 관계(예: `Order ↔ OrderItem`)만 JPA 연관관계를 허용하고, 애그리거트 간 참조는 ID로만 표현한다는 프로젝트 전반 원칙을 따른다.

두 애그리거트를 조합해야 하는 유스케이스는 `CouponFacade`(또는 `OrderFacade`)에서 각 서비스를 순서대로 호출하여 처리한다.

---

## 도메인 모델

### CouponTemplateModel

쿠폰 규칙을 정의하는 애그리거트 루트.

| 필드 | 타입 | 설명 |
|---|---|---|
| `name` | String | 쿠폰 이름 (blank 불가) |
| `discountPolicy` | DiscountPolicy (Embedded) | 할인 타입(FIXED/RATE)과 값 |
| `minOrderAmount` | BigDecimal | 최소 주문 금액 (null 허용 → 제한 없음) |
| `expiredAt` | ZonedDateTime | 만료 일시 (null 불가) |

**주요 행위**

- `isExpired()` : 현재 시각이 `expiredAt`을 지났는지 여부 반환
- `validateApplicability(originalPrice)` : 만료 여부 + 최소 주문 금액 충족 여부를 함께 검증. 실패 시 `CoreException(BAD_REQUEST)` 던짐
- `calculateDiscountAmount(originalPrice)` : `DiscountPolicy`에 계산 위임
- Soft delete 적용 (`@SQLRestriction("deleted_at IS NULL")`)

### DiscountPolicy (Embeddable)

할인 계산 책임을 캡슐화한 값 객체.

| CouponType | 계산 방식 |
|---|---|
| `FIXED` | `min(value, originalPrice)` — 정액 할인, 주문 금액 초과 불가 |
| `RATE` | `floor(originalPrice × value / 100)` — 비율 할인, 소수점 버림 |

- `value` 는 0 초과 필수. RATE 타입은 100 이하로 제한.

### IssuedCouponModel

유저에게 발급된 쿠폰 인스턴스.

| 필드 | 타입 | 설명 |
|---|---|---|
| `couponTemplateId` | Long | 발급 근거가 된 템플릿 ID |
| `userId` | Long | 발급 대상 유저 ID |
| `status` | CouponStatus | `AVAILABLE` / `USED` |
| `version` | long | 낙관적 락용 버전 컬럼 (`@Version`) |

**주요 행위**

- `validateOwner(userId)` : 소유자 불일치 시 `CoreException(FORBIDDEN)` 던짐
- `use()` : 이미 `USED` 상태이면 `CoreException(CONFLICT)` 던지고, `AVAILABLE` 상태이면 `USED`로 전이

#### 상태 전이

```
발급 시  → AVAILABLE
주문 사용 시 → USED  (use() 내부 상태 검증 + 낙관적 락, 재사용 불가)
```

### CouponStatus와 EXPIRED 처리

도메인 내 `CouponStatus`는 `AVAILABLE` / `USED` 두 값만 가진다.

만료 여부는 DB 칼럼이 아닌 `CouponTemplateModel.expiredAt`을 기준으로 런타임에 판단한다. 따라서 `EXPIRED`는 도메인 상태가 아닌 **응답 레이어의 표현 개념**이다.

```java
// CouponV1Dto.MyIssuedCouponResponse
info.status() == CouponStatus.USED
    ? CouponStatusDto.USED
    : ZonedDateTime.now().isAfter(info.expiredAt()) ? CouponStatusDto.EXPIRED : CouponStatusDto.AVAILABLE
```

이렇게 책임을 분리한 이유: 만료는 시간의 흐름에 따라 자동으로 변하는 파생 상태이므로, DB에 `EXPIRED`를 기록하면 배치 업데이트 등 별도 유지 비용이 발생한다. `expiredAt` 값 자체가 진실의 원천(source of truth)이며 응답 시점에 계산하는 것이 가장 단순하다.

---

## 동시성 처리

쿠폰 사용은 **낙관적 락(`@Version`)**으로 처리한다.

`IssuedCouponModel`에 `@Version` 컬럼을 추가한다. 충돌 시 JPA가 던지는 `ObjectOptimisticLockingFailureException`은 `ApiControllerAdvice`에서 `CONFLICT` HTTP 응답으로 변환된다.

- 쿠폰 사용은 첫 요청만 성공하고 나머지는 영구 실패해야 하는 단방향 상태 전이다. 충돌 시 즉시 실패를 반환하는 낙관적 락이 이 특성에 맞다.
- 도메인 규칙(`AVAILABLE → USED`)이 `IssuedCouponModel.use()` 안에 Java 코드로 캡슐화된 채 유지된다.
- 성능이 중요해지면 단일 쿼리로 처리하는 원자적 UPDATE로 전환을 고려할 수 있다. 단, 그 경우 도메인 규칙이 SQL 레벨로 내려가므로 계약 테스트로 보완해야 한다. (ADR-005 참고)

---

## 레이어별 책임

```
CouponAdminV1Controller
├── CouponTemplateService   (CRUD: 단일 애그리거트 작업)
└── CouponFacade            (발급 내역 조회: 템플릿 존재 확인 후 IssuedCoupon 조회)

CouponV1Controller
└── CouponFacade            (발급: 사용자 인증 → 만료 확인 → IssuedCoupon 생성)

UserV1Controller
└── CouponFacade            (내 쿠폰 목록 조회: 사용자 인증 → 발급 쿠폰 목록 반환)

OrderFacade
├── IssuedCouponService     (소유자 검증 조회(`getMyIssuedCoupon`) + 낙관적 락 사용 처리(`use`))
└── CouponTemplateService   (적용 가능 여부 검증 + 할인 금액 계산)
```

**CouponTemplateService**: 템플릿 CRUD. 단일 애그리거트 내 작업만 담당.

**IssuedCouponService**: 발급, 내 쿠폰 목록 조회, 소유자 검증 조회, 쿠폰 사용.

**CouponFacade**: 유저 인증과 쿠폰 도메인을 조합하는 유스케이스 담당. 주문 시 쿠폰 적용은 `OrderFacade`가 `IssuedCouponService`와 `CouponTemplateService`를 직접 조합하여 처리한다.
