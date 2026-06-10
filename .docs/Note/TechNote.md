# [Retrospective] 쿠폰·재고 동시성 전략 분기 — 비관적 락 vs 원자적 UPDATE

## TL;DR

쿠폰 동시성 제어를 비관적 락에서 원자적 UPDATE로 전환해, 조회 시점부터 락을 선점하는 방식을 없앴다.
재고(비관적 락)와 쿠폰(원자적 UPDATE)을 도메인 특성에 따라 전략을 분기했고, 두 전략 모두 `createOrder` 단일 트랜잭션 안에서 원자적으로 처리된다.

---

## 본문

### 개요

- **목표**: 쿠폰 소비 + 재고 선점 + 주문 생성을 단일 트랜잭션으로 묶어 부분 성공을 제거하고, 동시 요청 시 정합성을 보장한다.
- **중점 사항**: 도메인 특성이 다른 재고·쿠폰에 동일한 락 전략을 일괄 적용하지 않고, 각 도메인에 맞는 방식을 선택하는 것.

---

### 핵심 기술 및 결정

| 기술/설계 항목 | 선택한 대안 | 선택 이유 |
|---|---|---|
| 트랜잭션 범위 | `createOrder` 단일 트랜잭션으로 통합 | 쿠폰·재고·주문 중 하나라도 실패하면 모두 롤백, 부분 성공 제거 |
| 재고 동시성 | 비관적 락 (`SELECT FOR UPDATE`) | 모든 유저가 같은 row 경쟁 → 충돌 빈도 높음. 도메인 검증 로직(`StockModel.reserve()`)을 Java에서 유지해야 함 |
| 쿠폰 동시성 | 원자적 UPDATE (`WHERE status = 'AVAILABLE'`) | 유저별 row 분리 → 낮은 충돌. 단순 상태 전이라 SQL 조건으로 충분. 조회 시점 선제 락 없음 |
| 쿠폰 실패 원인 구분 | 소유 확인(락 없는 SELECT) + 원자적 UPDATE 2단계 분리 | 원자적 UPDATE 단독으로는 "미보유"(404)와 "이미 사용"(400)을 구분 불가 |

---

### 트러블슈팅

#### 문제 1 — `@Lock`을 기존 조회 메서드에 직접 추가

**문제 현상**: `StockRepository`의 `findAllByProductIds`에 `@Lock(PESSIMISTIC_WRITE)`를 추가하자 테스트 9개가 `GenericJDBCException`으로 실패했다.

**원인 분석**: Spring Data JPA는 Repository 인터페이스 기본 트랜잭션을 `readOnly=true`로 설정한다. 읽기 전용 트랜잭션 컨텍스트에서는 `SELECT FOR UPDATE`를 발급할 수 없다.

**해결 방안**: 락이 필요한 경우와 일반 조회를 전용 메서드로 분리했다.

```java
findAllByProductIds(ids)          // 일반 조회 — readOnly 포함 모든 컨텍스트
findAllByProductIdsWithLock(ids)  // 쓰기 전용 — PESSIMISTIC_WRITE
```

이후 `@Lock`은 반드시 `WithLock` 접미사를 가진 전용 메서드에만 적용하는 규칙을 지키고 있다.

---

#### 문제 2 — `@Modifying` 쿼리에서 `TransactionRequiredException`

**문제 현상**: 쿠폰 원자적 UPDATE를 구현하고 테스트를 돌리자 `TransactionRequiredException`이 발생했다.

**원인 분석**: Spring Data JPA의 `@Modifying`은 쓰기 쿼리임을 선언하지만, 트랜잭션을 직접 시작하지 않는다. 인터페이스 기본 트랜잭션이 `readOnly=true`이므로 쓰기 쿼리를 발급하면 예외가 터진다.

**해결 방안**: `@Modifying`과 함께 `@Transactional`을 명시적으로 선언했다.

```java
@Transactional
@Modifying
@Query("UPDATE CouponIssueModel c SET c.status = :newStatus WHERE c.id = :id AND c.status = :curStatus")
int updateStatusIfAvailable(@Param("id") Long id, ...);
```

문제 1과 동일한 맥락이다 — readOnly 컨텍스트에서 쓰기를 시도하면 실패한다.

---

#### 발견 — `int` 반환 패턴이 rollbackOnly 함정을 피하고 있었다

`useIfAvailable()`을 구현할 때 `@Modifying` 쿼리의 표준 반환 타입인 `int`를 썼고, Facade에서 `== 0`을 체크해 직접 예외를 던졌다. 며칠 전 랜덤 아티클 리뷰에서 봤던 우아한형제들 기술 블로그 글이 문득 생각나 다시 읽었는데, 내 구현에 대입해보니 의도치 않게 중요한 함정을 피하고 있었다는 걸 알았다.

Spring에서 `@Transactional` 메서드(REQUIRED)가 RuntimeException을 던지면, 바깥 트랜잭션이 이미 진행 중이더라도 해당 트랜잭션에 `rollbackOnly` 플래그를 세팅한다. 이후 바깥 메서드가 그 예외를 try-catch로 잡아도 커밋 시점에 `AbstractPlatformTransactionManager`가 플래그를 확인하고 `UnexpectedRollbackException`을 던진다.

만약 `useIfAvailable()`이 `int` 대신 예외를 직접 던졌다면 이 함정에 빠졌을 것이다.

```java
// 이렇게 구현했다면 → rollbackOnly 함정
@Transactional // REQUIRED — createOrder 트랜잭션에 참여
public void useIfAvailable(Long id) {
    int rows = jpaRepository.updateStatusIfAvailable(...);
    if (rows == 0) throw new CoreException(...); // 중첩 @Transactional 안에서 예외 → rollbackOnly 세팅
}

// 실제 구현 → 함정 없음
@Transactional
public OrderInfo createOrder(...) {
    if (couponIssueRepository.useIfAvailable(id) == 0) { // int 반환, 예외 없음
        throw new CoreException(...); // createOrder 본인이 직접 던짐 → 정상 롤백
    }
}
```

`@Modifying` 쿼리가 `int`를 반환하도록 설계된 것은 Spring Data JPA의 관례이지만, 이 패턴이 "예외는 트랜잭션 경계의 가장 바깥에서 던진다"는 원칙과도 맞닿아 있다는 걸 뒤늦게 알았다.

---

### 회고

- **Keep**: 도메인 특성(충돌 빈도, 조건 복잡도, 로직 위치)을 분석하고 전략을 분기한 것. 일괄 비관적 락보다 각 도메인에 맞는 방식을 선택하는 게 더 정확하다.
- **Problem**: 처음엔 쿠폰도 재고와 같은 비관적 락으로 구현했다. 도메인 특성을 충분히 따져보지 않고 익숙한 방식을 먼저 적용했다.
- **Try**: 트래픽이 늘어 재고가 병목이 되면 재고도 원자적 UPDATE로 전환 가능한지 검토. 이 경우 `affected rows = 0` 모호성을 해결하는 방안(사전 조회 분리 또는 예외 메시지 통합)을 함께 설계해야 한다.

---

## Deep Dive

### 비관적 락 — 어떻게 동작하는가

```sql
SELECT * FROM coupon_issues WHERE user_id = ? AND coupon_id = ? FOR UPDATE
```

`FOR UPDATE`는 해당 row에 배타 락(exclusive lock)을 건다. 이후 같은 row에 접근하는 다른 트랜잭션은 이 트랜잭션이 커밋 또는 롤백될 때까지 **블로킹**된다.

```
Thread 1: SELECT FOR UPDATE → 락 획득 → 상태 확인 → UPDATE → COMMIT → 락 해제
Thread 2: SELECT FOR UPDATE →        [BLOCKED — Thread 1 커밋까지 대기]        → 재조회 → USED 확인 → 예외
```

**락 보유 구간이 핵심이다.** `createOrder` 트랜잭션 안에서 `SELECT FOR UPDATE`를 발급하면 락은 재고 선점, 주문 저장이 끝나고 트랜잭션이 커밋될 때까지 유지된다. Thread 2는 그 전체 구간 동안 기다린다. 충돌이 없는 경우에도 락 획득·해제 비용이 항상 발생한다.

---

### 원자적 UPDATE — 어떻게 동작하는가

```sql
UPDATE coupon_issues SET status = 'USED' WHERE id = ? AND status = 'AVAILABLE'
```

DB 엔진(InnoDB)은 이 구문을 원자적으로 실행한다. WHERE 조건 확인과 쓰기가 분리되지 않는다.

락 동작은 결과에 따라 달라진다.

- **1 row affected** (AVAILABLE → USED 성공): 해당 row에 X 락을 획득하고 **커밋까지 유지**한다. SELECT FOR UPDATE와 동일하다. 차이는 락 획득 시점 — SELECT FOR UPDATE는 조회 시점부터 잡지만, 원자적 UPDATE는 실제 쓰기 시점에만 잡는다.
- **0 rows affected** (이미 USED): WHERE 조건이 맞지 않아 X 락을 유지하지 않고 즉시 반환한다. SELECT FOR UPDATE라면 이미 USED인 row에도 X 락을 걸고 롤백까지 유지했겠지만, 여기서는 락 없이 끝난다.

```
Thread 1: SELECT (소유 확인) → UPDATE WHERE status='AVAILABLE' → 1 row → 성공
Thread 2: SELECT (소유 확인) → UPDATE WHERE status='AVAILABLE' → 0 rows → 예외
```

두 스레드가 소유 확인을 동시에 통과해도 UPDATE 단계에서 DB가 직렬화한다. 하나가 먼저 USED로 바꾸면 나머지의 WHERE 조건이 맞지 않아 0 rows가 된다. SELECT 시점부터 미리 락을 잡지 않아도 "오직 하나만 성공"이 보장된다.

**두 단계 사이의 race condition**: 소유 확인(step 1)과 원자적 UPDATE(step 2) 사이에 간격이 존재한다. 그러나 step 2의 WHERE 조건이 이 간격을 안전하게 처리한다. 두 스레드가 step 1을 동시에 통과하더라도 step 2를 성공시키는 건 하나뿐이다.

---

### 낙관적 락은 왜 선택하지 않았나

낙관적 락(`@Version`)은 커밋 시점에 충돌을 감지하고 실패한 트랜잭션을 재시도한다.

- **재고**: 모든 유저가 같은 row를 경쟁 → 충돌 빈도 높음 → 재시도가 연쇄적으로 발생(재시도 폭풍). 비관적 락이 적합하다.
- **쿠폰**: 유저별 row 분리 → 충돌 빈도 낮음 → 낙관적 락도 가능하지만, 원자적 UPDATE가 "실패"로 즉시 끝내므로 재시도 없이 더 간단하다.

---

### 재고에 원자적 UPDATE를 적용하지 않은 이유

```sql
-- 재고에 원자적 UPDATE를 적용하면?
UPDATE stocks SET reserved_stock = reserved_stock + ? 
WHERE product_id = ? AND (total_stock - reserved_stock) >= ?
```

`affected rows = 0`이면 두 가지 중 하나다.
1. 재고 부족 (`total_stock - reserved_stock < qty`)
2. 해당 상품의 재고 row가 존재하지 않음

Java에서 이 둘을 구분할 수 없어 정확한 예외를 던질 수 없다.

또한 `StockModel.reserve()`의 도메인 검증 로직(수량 계산, 예외 처리)이 Java에 있어, SQL WHERE 절로 이전하면 그 로직을 잃게 된다. 비관적 락으로 row를 잠근 뒤 Java에서 도메인 로직을 실행하는 방식이 적합하다.

---

### 전략 비교

| 항목 | 비관적 락 | 원자적 UPDATE |
|---|---|---|
| 동시성 보장 방식 | 락으로 직렬화 (대기) | WHERE 조건으로 직렬화 (실패) |
| 락 보유 구간 | SELECT ~ COMMIT (트랜잭션 전체) | 1 row: UPDATE 시점 ~ COMMIT / 0 rows: 락 없음 |
| 실패한 스레드 | 대기 후 재확인 | 즉시 0 rows 반환 |
| 도메인 로직 위치 | Java | SQL WHERE 절 |
| 적합한 도메인 | 충돌 빈도 높음, 조건 복잡, Java 로직 필요 | 충돌 빈도 낮음, 단순 상태 전이 |

---

## Reference

- [Spring Data JPA — Locking](https://docs.spring.io/spring-data/jpa/reference/jpa/locking.html)
- [MySQL InnoDB Locking](https://dev.mysql.com/doc/refman/8.0/en/innodb-locking.html)
- [우아한형제들 기술 블로그 — 응? 이게 왜 롤백되는 거지?](https://techblog.woowahan.com/2606/)
