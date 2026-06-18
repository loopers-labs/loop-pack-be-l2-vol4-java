# ADR-001 — 재고 차감 동시성 제어: 비관적 락 채택

- **Status**: Accepted
- **Date**: 2026-06-09

---

## Context

주문 시 재고를 차감한다(`OrderFacade.placeOrder` → `StockService.decrease`). 현재 차감 로직은 다음과 같다.

```
StockModel.decrease(amount):
  - amount <= 0        → BAD_REQUEST   (입력 검증, 동시성과 무관)
  - quantity < amount  → OUT_OF_STOCK  (동시성 불변식)
  - quantity -= amount                 (read-modify-write)
```

조회 후 메모리에서 검증·차감하는 **read-modify-write 패턴**이며, 현재 **락이 없다 → Lost Update(과판매) 위험**이 있다. 두 주문이 같은 재고(예: 5)를 동시에 읽고 각자 차감하면 한쪽 갱신이 덮어써져 과판매가 발생한다. (MySQL InnoDB 기본 격리수준 REPEATABLE READ 로도 read-modify-write 의 Lost Update 는 막히지 않는다.)

**도메인 특성**
- 단일 행(`stock.product_id` unique).
- 인기/한정 수량 상품은 **경합이 높다**(동일 행에 동시 주문 집중).
- 재고는 줄기만 하며, 충돌 시 "뒤 요청이 잠깐 대기 후 갱신된 재고로 진행"이 자연스럽다(재시도가 아니라 직렬화).
- 과판매는 **절대 허용 불가**.

---

## Decision

**재고 차감의 메인 동시성 제어로 비관적 락(`SELECT … FOR UPDATE`, `@Lock(PESSIMISTIC_WRITE)`)을 채택한다.** `StockModel.decrease()` 의 불변식("재고 0 미만 금지")은 **도메인 엔티티에 그대로 유지**한다.

### 결정의 핵심 근거 — 두 개의 축

이 결정은 두 질문을 순서대로 통과한 결과다.

**축 1 (기술적 강제): 검증+변경이 단일 조건부 SQL 한 문장으로 접히는가? → YES**
현재 로직은 `quantity >= amount` 검증과 차감뿐이라 `UPDATE stock SET quantity = quantity - :n WHERE product_id = :id AND quantity >= :n` (영향 0행 → 품절) 한 문장으로 접힌다. 외부값·타 애그리거트·읽은 값에 따른 앱 분기가 없다. → 원자적 조건부 UPDATE가 **기술적으로 가능한** 상황이다. (만약 접히지 않았다면 원자 UPDATE는 후보에서 자동 탈락하고, 락 전략 중에서만 골랐을 것이다.)

**축 2 (설계 가치 판단): 불변식을 도메인에 둘 것인가? → YES (DDD)**
원자 UPDATE로 가면 "재고 0 미만 금지" 불변식이 엔티티에서 빠져 SQL 의 `WHERE` 로 새어나가 모델이 빈약(anemic)해진다. 본 프로젝트는 비즈니스 규칙을 도메인 객체에 캡슐화하는 것을 1순위 가치로 둔다(Tell, Don't Ask). 불변식을 엔티티에 유지하기로 한 선택이 **앱 측 read-modify-write 윈도우**를 만들고, **비관적 락은 그 윈도우의 자연스러운 보호 장치**다.

> 즉 "DDD 로 `decrease()` 불변식을 엔티티에 둔다 → read-modify-write 윈도우가 생긴다 → 그 윈도우를 비관락으로 보호한다" 가 일관된 귀결이다. (anemic 아키텍처였다면 윈도우 자체가 없어 원자 UPDATE 가 메인이었을 것이다.)

---

## Alternatives considered

### 낙관적 락 (`@Version`) — 탈락
- 충돌이 **드물 때만** 유리하다(비차단의 이득은 챙기고 후불 비용은 거의 안 냄). 충돌 시 이미 한 작업(읽기+로직+UPDATE 시도)을 버리고 재시도하므로, **고경합에서는 버려지는 작업이 폭증**한다(최악 O(N²) 재시도, thundering herd).
- 재고는 인기상품 기준(worst-case)으로 잡아야 하며 고경합이 본질 → **부적합**. 락 전략은 worst-case 로 결정한다.

### 원자적 조건부 UPDATE (`UPDATE … WHERE quantity >= :n`) — 대안(미채택, 비교 대상)
- 가장 단순하고, read-modify-write 윈도우 자체가 없다. 우리 프로젝트에 이미 동일 패턴 레퍼런스가 있다(`ProductJpaRepository.incrementLikeCount`, `@Modifying(clearAutomatically, flushAutomatically)`).
- **락 거동은 비관락과 사실상 동일**: `UPDATE` 도 대상 행에 **배타락(X)** 을 걸고 **커밋까지** 유지한다(공유락이 아니다 — 그래야 Lost Update 가 막힌다). `product_id` 가 unique 인덱스라 정확히 그 한 행만 잠근다. 따라서 같은 상품 동시 주문이 한 건씩 직렬화되는 것은 비관락과 같다.
- **미채택 이유**: 불변식이 SQL 로 새어 모델이 빈약해진다(축 2). 성능 우위는 "직렬화는 동일하고, 비관락의 SELECT 왕복·앱 계산이라는 부가 오버헤드를 던" 정도로 한정되며, 그 차이의 유의미성은 아직 측정하지 않았다.

---

## Consequences

**얻는 것**
- 과판매 0 보장(행을 직렬화).
- `StockModel.decrease()` 불변식이 도메인에 유지되어 모델이 풍부함(향후 재고 규칙 확장 시에도 엔티티가 규칙의 집).
- 재시도 로직 불필요.

**감수하는 것 / 주의**
- 배타락은 **커밋까지 보유**된다 → 보유 시간을 줄이기 위해 `OrderFacade` 의 호출 순서를 **쿠폰 적용 → 주문 생성 → 재고 차감**(재고를 마지막)으로 유지한다.
- 다항목 주문에서 여러 재고 행을 잠글 때 트랜잭션마다 획득 순서가 다르면 **데드락** → **`StockService.decreaseAll(Map<productId, qty>)` 가 productId 오름차순으로 잠가** 일관된 락 순서를 보장한다. `OrderFacade` 는 차감 수량 맵만 넘기는 평평한 호출 1개로 유지(락 순서 책임은 stock 도메인이 소유).
- 락 대기 동안 DB 커넥션을 점유 → 커넥션 풀 압박은 감수한다. 락 대기는 InnoDB 기본(`innodb_lock_wait_timeout`)에 맡겨 직렬화하며, 별도 짧은 락 타임아웃 힌트는 두지 않는다(대기→진행 결정과 모순).
- **구현 형태**: 잠금 전용 조회 `StockRepository.findByProductIdForUpdate` (`@Lock(PESSIMISTIC_WRITE)` + `@Query`) 를 신설해 **차감 경로(`StockService.decrease`)에만** 적용한다. 기존 `findByProductId` 는 읽기 경로(상품 조회·구매가능 표시) 유지 — 읽기에 락이 새지 않도록 분리. `@Lock` 은 Spring Data 프록시가 적용하므로 Repository 메서드에 둔다.
- **락 충돌 → 409**: 락 대기 타임아웃 등 `CannotAcquireLockException`(`ConcurrencyFailureException` 하위)은 `ApiControllerAdvice` 가 **409 `CONCURRENCY_CONFLICT`** 로 매핑한다. 이 핸들러는 ADR-002(쿠폰 낙관락)에서 신설한 것을 **공유**한다(낙관·비관 공통 상위 타입을 한 핸들러로 커버).

**검증**
- 재고 N, N+k 개 동시 주문 → 성공 주문 == N, 재고 정확히 0, 과판매 0 을 `ExecutorService` + `CountDownLatch` 로 단언(Testcontainers 실 MySQL). → `StockConcurrencyTest`.
- 다항목 주문을 반대 순서로 동시 실행해도 데드락/500 없이 전부 성공·두 재고 정확히 차감(정렬 락 순서 회귀 검증). → 동 테스트.
