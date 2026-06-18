# ADR-002 — 쿠폰 적용 동시성 제어: 낙관적 락 채택

- **Status**: Accepted
- **Date**: 2026-06-10

---

## Context

주문 시 쿠폰을 적용한다(`OrderFacade.placeOrder` → `CouponService.apply` → `UserCoupon.use`). 현재 `use()` 로직은 다음과 같다.

```
UserCoupon.use(requesterId, orderAmount, now):
  - userId != requesterId         → COUPON_NOT_OWNED            (소유 검증, 동시성과 무관)
  - status == USED                → COUPON_ALREADY_USED         (동시성 불변식)
  - isExpired(now)                → COUPON_EXPIRED              (의미 검증)
  - !meetsMinOrderAmount          → COUPON_MIN_ORDER_AMOUNT...  (의미 검증)
  - status = USED; usedAt = now                                 (read-modify-write)
  - return type.discount(orderAmount, discountValue)            (반환값)
```

조회 후 메모리에서 검증·전이하는 **read-modify-write 패턴**이며, 현재 `UserCoupon` 에 **`@Version` 이 없다 → Lost Update(이중사용·이중할인) 위험**이 있다. 두 트랜잭션이 같은 쿠폰을 동시에 `AVAILABLE` 로 읽고 각자 `USED` 로 커밋하면, 둘 다 통과해 한 쿠폰이 두 번 쓰인다. (MySQL InnoDB 기본 격리수준 REPEATABLE READ 로도 read-modify-write 의 Lost Update 는 막히지 않는다 — 재고와 동일한 구조의 문제다.)

**동시성에 민감한 쓰기는 `status` 전이 하나뿐이다.** `type`/`discountValue` 는 발급 시 스냅샷된 **불변 필드**이고 `orderAmount` 는 입력이므로, 할인 계산은 동시 변경과 무관해 **동시성에 안전**하다. 지켜야 할 불변식은 **`AVAILABLE → USED` 전이가 한 쿠폰당 정확히 1회**다.

**도메인 특성 (재고와의 대비가 결정의 핵심)**

| 축 | 재고(ADR-001) | 쿠폰(본 ADR) |
|------|--------------|-------------|
| 경합 빈도 | **높음** (인기상품에 동시 주문 집중) | **낮음** (1인 1소유, "한 사용자가 여러 기기/더블클릭") |
| 충돌 시 올바른 결과 | **대기 후 진행** (직렬화) | **한쪽만 실패** (재사용 불가가 정답) |
| 재시도 | 무의미 (직렬화로 흡수) | **틀림** (두 번째 시도도 어차피 실패가 정답) |

---

## Decision

**쿠폰 적용의 메인 동시성 제어로 낙관적 락(`@Version`)을 채택한다.** `@Version` 은 공용 `BaseEntity` 가 아니라 **`UserCoupon` 엔티티에 로컬로** 추가한다. `UserCoupon.use()` 의 단일사용 불변식(`status == USED → COUPON_ALREADY_USED`)은 **도메인 엔티티에 그대로 유지**한다.

### 결정의 핵심 근거 — 두 개의 축 + 락 family 선택

**축 1 (기술적 강제): 검증+변경이 단일 조건부 SQL 한 문장으로 접히는가? → 부분만(NO)**
재고는 완전히 접혔다(`UPDATE … WHERE quantity >= :n`, read 자체가 사라짐). 쿠폰은 `status` 전이만 `… WHERE status='AVAILABLE'` 로 접힐 뿐, **소유·만료·최소주문금액 검증과 할인 계산 때문에 read 가 본질적으로 남는다.** 즉 쿠폰은 "read 없는 원자 UPDATE"가 애초에 불가능하고, 어떤 전략을 쓰든 **read-modify-write 윈도우가 사라지지 않는** 도메인이다 → 원자 UPDATE로 윈도우를 제거하는 길이 막혀, **락 전략 중에서** 고른다.

**축 2 (설계 가치 판단): 불변식을 도메인에 둘 것인가? → YES (DDD)**
단일사용 불변식을 `use()` 에 유지하면 모델이 풍부해지고(Tell, Don't Ask), 그게 read-modify-write 윈도우를 만들며, 그 윈도우를 락으로 보호한다. 불변식을 SQL `WHERE` 로 빼면 모델이 빈약(anemic)해진다.

**락 family 선택: 낙관 vs 비관 → 낙관 (도메인 특성이 재고와 정반대)**
경합이 **드물면** "평소엔 비차단으로 통과하고 충돌날 때만 비용을 낸다"는 낙관적 베팅이 이득이다. 게다가 쿠폰은 **충돌 시 올바른 결과가 "실패"** 이므로(재사용 불가), 낙관락의 "충돌 → 예외 → 실패"가 그대로 정답이 된다. 재고가 *고경합·대기직렬화*라 비관을 고른 것과 **정확히 반대 축**에서 낙관을 고른다.

> 즉 "DDD 로 `use()` 불변식을 엔티티에 둔다 → read-modify-write 윈도우가 생긴다 → 경합이 드물고 충돌 시 실패가 정답이므로 그 윈도우를 *낙관락*으로 보호한다" 가 일관된 귀결이다.

### 거동 — 2계층 방어

- **순차(흔한 경우)**: 먼저 커밋된 쿠폰을 나중 트랜잭션이 `USED` 로 읽음 → `use()` 의 `status == USED` 가드가 **깔끔한 `COUPON_ALREADY_USED`(409)** 로 잡는다.
- **동시충돌(드문 경우)**: 두 트랜잭션이 모두 `AVAILABLE`(version 0)로 읽어 인메모리 가드를 둘 다 통과 → 한쪽이 먼저 커밋(version 0→1), 다른쪽은 flush/commit 시 `WHERE … AND version=0` 이 0행 → `OptimisticLockException`(`ObjectOptimisticLockingFailureException`) → **409 매핑**.
- **재시도하지 않는다**: 충돌은 일시적 경합이 아니라 진짜 규칙 위반이다. 두 번째 시도는 이미 `USED` 라 어차피 실패가 정답 → 재시도는 무의미·유해.

---

## Alternatives considered

### 비관적 락 (`SELECT … FOR UPDATE`) — 탈락
- 대기한 쪽이 커밋된 `USED` 를 읽어 `use()` 가 **모든 충돌 경우에 깔끔한 `COUPON_ALREADY_USED`** 를 던진다는 강점이 있다(불변식 검출이 전부 도메인에 머문다). 낮은 경합이라 블로킹 비용도 작다.
- **미채택 이유**: 경합이 드문 도메인에서 매 사용마다 행 배타락을 **커밋까지 보유**하는 것은 과한 비용이다. 비관락은 "충돌이 흔할 때" 이득인데 쿠폰의 worst-case 는 고경합이 아니다 → 비차단 낙관이 적합. (재고는 worst-case 가 고경합이라 비관을 골랐다 — **같은 동시성 문제라도 경합 빈도가 전략을 가른다**.)

### 상태 조건부 UPDATE (`UPDATE … SET status='USED' WHERE … AND status='AVAILABLE'`) — 대안(미채택)
- `status` 전이만 보면 기술적으로 가능하고, 0행 → `COUPON_ALREADY_USED` 로 **순차·동시충돌이 모두 동일한 도메인 에러로 일관**되는 장점이 있다(낙관락처럼 framework 예외가 새지 않는다).
- **미채택 이유**: 단일사용 불변식이 SQL `WHERE` 로 새어 모델이 빈약해진다(축 2 위반). 또한 `@Modifying` 벌크 UPDATE 는 1차 캐시를 우회하므로 같은 트랜잭션에서 그 엔티티를 다시 읽을 때 동기화(`clearAutomatically`/`flushAutomatically`)가 필요하다. 불변식을 도메인에 유지하는 가치를 우선했다.

---

## Consequences

**얻는 것**
- 이중사용·이중할인 0 (동시충돌을 version 으로 차단).
- `UserCoupon.use()` 의 단일사용 불변식이 도메인에 유지되어 모델이 풍부함.
- 경합이 드문 도메인에서 **비차단** — 락 대기·커넥션 점유 없음.
- 재시도 로직 불필요(충돌=실패가 정답).

**감수하는 것 / 주의**
- **동시충돌은 framework 예외로 commit 시점에 터진다**: `ApiControllerAdvice` 에 `org.springframework.dao.ConcurrencyFailureException` 핸들러를 추가해 **409**(`ErrorType.CONCURRENCY_CONFLICT`)로 매핑한다. 이 공통 상위 타입은 낙관(`ObjectOptimisticLockingFailureException`)과 재고 비관(`CannotAcquireLockException` 등)을 한 핸들러로 커버하므로 **ADR-001 재고 락 예외 매핑과 공유**된다.
- **이 경로의 409 메시지는 정밀하지 않다**: 동시충돌 경로는 "이미 사용된 쿠폰"이 아니라 일반 동시성 충돌 메시지로 나간다(검출이 도메인이 아니라 영속성 계층이라서). 순차 경로는 여전히 `COUPON_ALREADY_USED` 로 정밀하게 잡힌다 — 낙관락을 택한 데서 오는 trade-off.
- **검출 시점이 flush/commit** 이라, 동시충돌 시 주문 생성·재고 차감까지 한 뒤 마지막에 롤백한다(작업 낭비). 경합이 드물어 이 낭비는 거의 발생하지 않는다 — 낙관락이 저경합에 적합한 바로 그 이유.
- `@Version` 은 `UserCoupon` 에만 둔다. `BaseEntity` 에 넣으면 비관락을 쓰는 재고 등 다른 애그리거트에까지 낙관락 의미가 강제되어 전략이 혼입된다.
- `OrderFacade` 의 호출 순서(쿠폰 적용 → 주문 생성 → 재고 차감)는 그대로 유지. 낙관락은 행을 잠그지 않으므로 순서가 보유시간에 주는 영향은 없으나, 재고 비관락(ADR-001)을 위해 순서는 유지된다.

**검증**
- 같은 `UserCoupon` 으로 N개 동시 주문 → **정확히 1건만 성공**, 쿠폰 `USED` 1회, 나머지는 실패(주문 0 생성), **이중사용 0** 을 `ExecutorService` + `CountDownLatch` 로 단언(Testcontainers 실 MySQL). → `OrderConcurrencyTest`.
- 락 충돌이 사용자에게 **409 `CONCURRENCY_CONFLICT`** 로 나가는지(500 아님) 확인 — `ApiControllerAdviceTest` 가 낙관(`ObjectOptimisticLockingFailureException`)·비관(`CannotAcquireLockException`) 모두 단언.
