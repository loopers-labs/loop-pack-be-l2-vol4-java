# 06. 구현 과제 및 체크리스트

> 대상 설계 문서: `01-requirements.md` ~ `05-decisions.md`
> 개발 방식: **TDD (Red → Green → Refactor) + DDD**
> DDD 패턴 적용 기준: `.docs/ddd-guide.md`

---

## 📋 구현 과제

- `.docs/design/01~04.md` 설계 문서를 구현한다.
- 상품, 브랜드, 좋아요, 주문 기능 등의 **도메인 모델 및 도메인 서비스**를 구현한다.
- 도메인 간 협력 흐름을 설계하고, 필요한 로직을 **도메인 서비스**로 분리한다.
- Application Layer에서 도메인 객체를 조합하는 흐름을 구현한다.
  (예: `ProductFacade.getProductDetail(productId)` → `Product + Brand + Like 조합`)
- Repository Interface와 구현체는 분리하고, 테스트 가능성을 고려한 구조를 설계한다.
- 모든 핵심 도메인 로직에 대해 단위 테스트를 작성하고, 예외/경계 케이스도 포함한다.

---

## 🧭 개발 진행 방식 (TDD + DDD)

1. **방향성·주요 의사결정은 개발자 승인 후 진행** — DDD 패턴 선택지는 장단점과 함께 제시하고 승인받는다 (`.docs/ddd-guide.md` 진행 방식 준수).
2. **TDD 사이클**: 도메인별로 RED(실패 테스트) → GREEN(최소 구현) → REFACTOR 순으로 진행한다. AI가 혼자 사이클을 완주하지 않고, 각 단계는 개발자와 함께 확인한다.
3. **단위 테스트 우선**: 외부 의존성은 Fake/Stub으로 분리해 순수 단위 테스트가 가능하게 한다.
4. **개발 완료 후 자동 커밋**: 모든 테스트 통과 시 작업 단위로 커밋한다.

---

## ✅ Checklist

### 🏷 Product / Brand 도메인

- [x] 상품 정보 객체는 브랜드 정보, 좋아요 수를 포함한다.
- [x] 상품의 정렬 조건(`latest`, `price_asc`, `likes_desc`)을 고려한 조회 기능을 설계했다.
- [x] 상품은 재고를 가지고 있고, 주문 시 차감할 수 있어야 한다.
- [x] 재고의 음수 방지 처리는 도메인 레벨에서 처리된다.

### 👍 Like 도메인

- [x] 좋아요는 유저와 상품 간의 관계로 별도 도메인으로 분리했다.
- [x] 상품의 좋아요 수는 상품 상세/목록 조회에서 함께 제공된다.
- [x] 단위 테스트에서 좋아요 등록/취소 흐름을 검증했다.

### 🛒 Order 도메인

- [x] 주문은 여러 상품을 포함할 수 있으며, 각 상품의 수량을 명시한다.
- [x] 주문 시 상품의 재고 차감을 수행한다 (실제 결제 플로우는 후주차에서 다룬다).
- [x] 유저, 상품 부재, 재고 부족 등 예외 흐름을 고려해 설계되었다.
- [x] 단위 테스트에서 정상 주문 / 예외 주문 흐름을 모두 검증했다.

### 🧩 도메인 서비스

- [x] 도메인 간 협력 로직은 Domain Service에 위치시켰다.
- [x] 상품 상세 조회 시 Product + Brand 정보 조합은 도메인 서비스에서 처리했다.
- [x] 복합 유스케이스는 Application Layer에 존재하고, 도메인 로직은 위임되었다.
- [x] 도메인 서비스는 상태 없이, 도메인 객체의 협력 중심으로 설계되었다.

### 🧱 소프트웨어 아키텍처 & 설계

- [x] 전체 프로젝트의 구성은 아래 아키텍처를 기반으로 구성되었다.
  - Application → **Domain** ← Infrastructure
- [x] Application Layer는 도메인 객체를 조합해 흐름을 orchestration 했다.
- [x] 핵심 비즈니스 로직은 Entity, VO, Domain Service에 위치한다.
- [x] Repository Interface는 Domain Layer에 정의되고, 구현체는 Infra에 위치한다.
- [x] 패키지는 계층 + 도메인 기준으로 구성되었다 (`/domain/order`, `/application/like` 등).
- [x] 테스트는 외부 의존성을 분리하고, Fake/Stub 등을 사용해 단위 테스트가 가능하게 구성되었다.

---

## ✅ Checklist — 이번 주차 (쿠폰 + 주문 트랜잭션 + 동시성)

### 🎟 Coupon 도메인

- [x] 쿠폰은 사용자가 소유하고 있으며, 이미 사용된 쿠폰은 사용할 수 없어야 한다. *(발급=UserCoupon 소유, use는 AVAILABLE일 때만)*
- [x] 쿠폰 종류는 정액(FIXED) / 정률(RATE)로 구분되며, 각 적용 로직을 구현하였다. *(CouponType.rawDiscount + cap/floor)*
- [x] 각 발급된 쿠폰은 최대 한번만 사용될 수 있다. *(조건부 UPDATE — 주문 통합 완료)*

### 🧾 주문

- [x] 주문 전체 흐름에 대해 원자성이 보장되어야 한다. *(OrderFacade 단일 트랜잭션)*
- [x] 사용 불가능하거나 존재하지 않는 쿠폰일 경우 주문은 실패해야 한다. *(소유/만료/최소금액/이미사용 검증 → 예외)*
- [x] 재고가 존재하지 않거나 부족할 경우 주문은 실패해야 한다. *(기존 reserve 조건부 UPDATE)*
- [x] 쿠폰·재고 처리 중 하나라도 실패하면 모두 롤백되어야 한다. *(같은 트랜잭션 경계)*
- [x] 주문 성공 시, 모든 처리(재고 예약 + 쿠폰 USED + 주문 저장)가 정상 반영되어야 한다.

### 🧪 동시성 테스트

- [ ] 동일 상품에 여러명이 좋아요/싫어요를 요청해도, 좋아요 수가 정상 반영된다. *(좋아요 도메인 — 별도 점검 필요)*
- [x] 동일 쿠폰으로 여러 기기에서 동시 주문해도, 쿠폰은 단 한번만 사용된다. *(조건부 UPDATE affected=0 → 실패, E2E 동시성 테스트 통과)*
- [ ] 동일 상품에 여러 주문이 동시 요청되어도, 재고가 정상 차감된다. *(reserve 조건부 UPDATE — 동시성 테스트 추가 필요)*

### 📡 과제 집중점 — 락 전략

> 모든 기능 동작을 먼저 개발한 뒤, 동시성·멱등성·일관성·느린 조회·동시 주문 문제를 해결한다.
> 낙관적/비관적 락 중 도메인 특성에 맞는 전략 선택. Application Layer(OrderFacade)의 트랜잭션 경계 설계가 핵심.

| 도메인 | 동시성 위험 | 채택 전략 | 근거 |
|---|---|---|---|
| 재고(Stock) | 오버셀 | **조건부 UPDATE** (`WHERE total-reserved >= qty`) | 단일 원자 UPDATE로 락 경합 최소화 (05 ①) |
| 쿠폰(UserCoupon) | 이중 사용 | **조건부 UPDATE** (`WHERE status=AVAILABLE`) | 재고와 동일 패턴, `@Version` 미사용 기조 (05 ⑰) |
| 좋아요 카운터 | like_count 정합성 | (점검) 조건부/원자 증감 | 동시 등록/취소 시 카운터 보정 |

---

## 🗺 진행 순서 (쿠폰 주문 통합 = 슬라이스 5)

기능 완성 → 동시성 검증 순. 각 단계 RED→GREEN.

1. **(A) OrderModel 금액 분할** — original/discount/pgAmount + `applyCoupon`, OrderInfo/Response/CreateRequest(couponId) 반영
2. **(B) UserCoupon 조건부 UPDATE** — `useIfAvailable`/`releaseByOrderId(s)` repo + service(`getOwned`/`use`/`release`)
3. **(C) OrderFacade 통합** — 쿠폰 검증→use→할인 반영, 재고예약과 단일 트랜잭션 (원자성/롤백)
4. **(D) 실패·만료 복구** — PaymentFacade.fail / expirePendingOrders 에서 쿠폰 release
5. **(E) 동시성 테스트** — 쿠폰 동시 주문 1회 사용, 재고 동시 차감, 좋아요 동시 (기존 조건부 UPDATE 검증 + 좋아요 점검)
6. **Refactor + 커밋**
