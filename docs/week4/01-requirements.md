# Requirements — Coupon

이 문서는 쿠폰 도메인과 주문 쿠폰 적용의 요구사항·정책 결정을 기록한다. HTTP 상태 코드나 구현 세부보다, 사용자가 수행하는 행동·도메인이 지켜야 하는 규칙·이번 범위에서 내린 정책 결정을 중심으로 남긴다. 공통 정책(soft delete, `BaseEntity`, 금액 `BIGINT` KRW, `/api/v1/admin/...` 분리, 헤더 인증)은 `docs/week2/01-requirements.md`를 따른다.

## 문제 정의

쿠폰의 어려움은 CRUD가 아니라 "발급된 권리를 주문에 한 번만, 정확한 금액으로 소비시키는 일관성 문제"에 있다. 핵심은 트랜잭션·동시성이다.

| 관점 | 재해석된 문제 |
| --- | --- |
| 사용자 | 내가 받은 쿠폰이 "지금 쓸 수 있는지(AVAILABLE)"를 신뢰할 수 있어야 하고, 주문에 적용하면 정확히 할인되어야 한다. |
| 비즈니스 | 쿠폰 1장은 정확히 1회만 사용되어야 한다(이중 사용 = 손실). 만료·타인 소유 쿠폰은 사용 불가. 관리자는 정액/정률 정책을 운영할 수 있어야 한다. |
| 시스템 | 동시에 같은 쿠폰으로 두 주문이 들어와도 하나만 성공해야 한다. 주문 실패 시 쿠폰은 다시 AVAILABLE로 복원되어야 한다. 주문에는 할인 내역이 스냅샷으로 남아야 한다. |

## 핵심 개념 — 두 개의 분리된 Aggregate

- **쿠폰 템플릿 (`Coupon`)** — 관리자가 만드는 정책. 타입(FIXED/RATE), 값, 최소주문금액, 만료시각. ADMIN CRUD와 "발급 내역 조회"의 기준.
- **발급된 쿠폰 (`UserCoupon`)** — 특정 유저가 발급받은 한 장. 템플릿 참조(`couponId`) + 소유자(`userId`) + 상태 + 정책 스냅샷.

두 Aggregate는 서로 ID로만 참조한다(JPA 연관관계 매핑 금지, 프로젝트 규약).

## 제공 기능

### 사용자

| 기능 | 인증 | 엔드포인트 | 설명 |
| --- | --- | --- | --- |
| 쿠폰 발급 요청 | USER 필요 | `POST /api/v1/coupons/{couponId}/issue` | 템플릿으로부터 내 쿠폰 한 장을 발급한다. |
| 내 쿠폰 목록 조회 | USER 필요 | `GET /api/v1/users/me/coupons` | 보유 쿠폰을 상태(AVAILABLE/USED/EXPIRED)와 함께 조회한다. |

### 관리자

| 기능 | 인증 | 엔드포인트 |
| --- | --- | --- |
| 템플릿 목록 조회 | ADMIN 필요 | `GET /api/v1/admin/coupons?page=0&size=20` |
| 템플릿 상세 조회 | ADMIN 필요 | `GET /api/v1/admin/coupons/{couponId}` |
| 템플릿 등록 | ADMIN 필요 | `POST /api/v1/admin/coupons` |
| 템플릿 수정 | ADMIN 필요 | `PUT /api/v1/admin/coupons/{couponId}` |
| 템플릿 삭제 | ADMIN 필요 | `DELETE /api/v1/admin/coupons/{couponId}` |
| 발급 내역 조회 | ADMIN 필요 | `GET /api/v1/admin/coupons/{couponId}/issues?page=0&size=20` |

> 요구사항 원문의 `/api-admin/v1/coupons` 의도는 기존 컨벤션 `/api/v1/admin/coupons`(`hasRole("ADMIN")` 보호)로 흡수한다.

## 도메인 규칙

### 쿠폰 템플릿 (`Coupon`)

- 타입은 `FIXED`(정액, 원) 또는 `RATE`(정률, %)다.
- `value`는 정액이면 할인 금액(원), 정률이면 퍼센트(%)다.
- `minOrderAmount`(최소 주문 금액)는 선택이며, 없으면 조건 없이 적용 가능하다.
- `expiredAt`은 필수다.
- 삭제는 soft delete다(공통 정책). 발급분은 정책을 스냅샷으로 들고 있어 템플릿 삭제와 무관하게 동작한다.

### 발급된 쿠폰 (`UserCoupon`)

- 한 유저는 같은 템플릿을 1회만 발급받을 수 있다. 중복 발급 요청은 실패한다(CONFLICT).
  - `unique(coupon_id, user_id)` 제약으로 DB 레벨에서 강제한다(동시 발급 경합 차단).
- 발급 시점에 템플릿의 `type`/`value`/`minOrderAmount`/`expiredAt`를 모두 스냅샷으로 복사한다. 이후 템플릿이 수정돼도 발급분의 할인 금액·만료는 발급 시점 값으로 고정된다.
- 따라서 `UserCoupon`은 만료 판정·할인 계산에 템플릿 조회가 필요 없는 자기완결적 객체다.
- 저장 상태는 `AVAILABLE`, `USED` 두 가지다.
- `EXPIRED`는 저장하지 않는 파생 상태다. 조회·사용 시점에 `expiredAt < now`로 계산한다(만료 전이 배치 없음).

### 할인 계산

- 정률(RATE): `floor(orderAmount * value / 100)` — 소수점 버림.
- 정액(FIXED): `value` 원.
- 할인액이 주문 금액보다 크면 최종 결제 금액은 0원으로 클램핑한다(음수 불가).
- `minOrderAmount`가 있고 주문 금액이 그에 미달이면 주문은 실패한다(BAD_REQUEST). "할인 미적용 후 성공"이 아니다.

### 주문 쿠폰 적용

- 쿠폰은 주문 1건당 1장만 적용 가능하다. `couponId`는 선택이며 생략 시 미적용이다.
- 다음의 쿠폰으로 요청하면 주문은 실패한다:
  - 존재하지 않는 쿠폰
  - 이미 사용된(USED) 쿠폰
  - 만료된(`expiredAt < now`) 쿠폰
  - 타 유저 소유 쿠폰
  - `minOrderAmount` 미충족
- 주문 성공 시 해당 쿠폰은 즉시 USED로 전이되며 재사용 불가하다. USED 전이는 주문 생성(PENDING) 트랜잭션 안에서 원자적으로 일어난다.
- 동시에 같은 쿠폰으로 두 주문이 들어오면 하나만 성공한다 — `UserCoupon`을 비관적 락(`findByIdForUpdate`)으로 조회해 직렬화한다.
  - 낙관적 락 대신 비관적 락을 택했다. 재고(`ProductStock`)도 같은 트랜잭션에서 비관적 락으로 잡으므로 동시성 모델을 하나로 맞추는 편이 유지보수에 낫고, 쿠폰 락은 경합이 같은 유저로 좁은 데다 외부 결제가 별도 트랜잭션이라 보유 시간이 짧아 비용도 크지 않기 때문이다.
- 결제 실패 시 보상 트랜잭션(`REQUIRES_NEW`)에서 재고 복구 + 쿠폰 `USED → AVAILABLE` 복원 + 주문 `FAILED` 전이를 함께 수행한다.

### 주문 금액 스냅샷

- 주문은 적용 전 금액 / 할인 금액 / 최종 결제 금액을 모두 보관한다.
  - `totalAmount` — 적용 전 금액(주문 항목 subtotal 합)
  - `discountAmount` — 할인 금액
  - `finalAmount` — 최종 결제 금액(`totalAmount - discountAmount`, 0 클램핑)
- 결제는 `finalAmount`로 진행한다.
- 적용된 쿠폰 식별자(`userCouponId`, nullable)도 함께 보관한다.

## 설계 메모

- `UserCoupon`은 `couponId`(참조)와 정책 스냅샷을 둘 다 가진다. 순수 스냅샷-only가 아니다.
  - 스냅샷(type/value/minOrderAmount/expiredAt) → 주문 할인 계산·만료 판정을 결정적으로 만들어 템플릿 조회(조인) 없이 처리. `UserCoupon`은 자기완결적이다.
  - 참조(`couponId`) → 중복 발급 제약, 발급 내역 조회, 향후 일괄 만료 연장의 대상 추적. 절대 버리지 않는다.
- 일관성·단순성을 위해 `expiredAt`도 스냅샷한다. 만료를 운영에서 자주 조정하지 않는다는 가정. 일괄 연장이 필요해지면 `WHERE coupon_id = ?`로 발급분을 찾아 UPDATE하는 관리자 기능을 그때 명시적으로 추가한다(범위 제외 참조).
- 도메인 순수성: `UserCoupon`은 Repository를 모른다. 스냅샷 덕에 만료 판정도 자체 필드로 처리한다(`use(userId, orderAmount, now)`, `displayStatus(now)`). 템플릿 주입 불필요.
- 할인 분기(FIXED/RATE)는 `CouponType` enum에 다형 메서드로 캡슐화한다(`if (type == ...)` 제거).
- 쿠폰 사용 규칙(소유자·만료·상태·minOrder 검증 + USED 전이)은 `UserCoupon.use(...)` 한 메서드의 불변식으로 묶는다. 비대해지면 `CouponUsagePolicy` 정적 규칙으로 분리할 수 있다.

## 에러 코드 (`CouponErrorCode`)

| 코드 | ErrorType | 상황 |
| --- | --- | --- |
| `COUPON_NOT_FOUND` | NOT_FOUND | 템플릿/발급 쿠폰이 존재하지 않음 |
| `COUPON_ALREADY_ISSUED` | CONFLICT | 동일 템플릿 중복 발급 |
| `COUPON_ALREADY_USED` | CONFLICT | 이미 사용된 쿠폰 적용 |
| `COUPON_EXPIRED` | BAD_REQUEST | 만료된 쿠폰 적용 |
| `COUPON_NOT_OWNED` | BAD_REQUEST | 타 유저 소유 쿠폰 적용 |
| `COUPON_MIN_ORDER_NOT_MET` | BAD_REQUEST | 최소 주문 금액 미충족 |
| `INVALID_COUPON_VALUE` | BAD_REQUEST | 템플릿 등록 시 값 검증 실패(예: 정률 > 100) |

## 범위에서 제외

- 발급 수량 한도(선착순) — 이번 범위 아님. 유저당 1회 제한만 둔다.
- 만료 상태 전이 배치 — 파생 계산으로 대체.
- 발급분 일괄 만료 연장 — 이번 범위 아님. `expiredAt`을 스냅샷한 대가로, 필요해지면 `coupon_id` 기준 일괄 UPDATE 관리자 기능으로 추가한다.
- 결제 도메인 본구현 — `StubPaymentGateway` 유지. 실패 경로 검증을 위한 실패 주입형 Stub은 테스트 한정으로 둘 수 있다.
