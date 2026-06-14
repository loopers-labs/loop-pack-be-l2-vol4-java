# Week 4 — 트랜잭션과 동시성 제어 정리

> RDBMS의 특성을 이해하고 **트랜잭션을 활용해 동시성 문제를 해결**하며 유스케이스를 완성한다.

## TL;DR

- 단순한 `@Transactional`만으로는 **Lost Update** 같은 정합성 문제를 막지 못한다.
- 동시성 문제는 로컬에서는 잘 드러나지 않고, **운영/부하 테스트 환경**에서 터진다.
- 정합성이 중요한 공유 자원(재고·포인트·쿠폰)에는 **비관적 락 / 낙관적 락**을 상황에 맞게 적용한다.
- 검증은 눈으로 불가능하다. `CountDownLatch + ExecutorService`로 **동시성 테스트**를 작성해 확인한다.

---

## 1. 실무에서 겪는 동시성 문제

- 2명이 같은 상품을 동시에 주문 → 재고가 음수가 되는 현상 (**Lost Update**)
- 포인트가 부족한 유저도 주문이 완료되는 사례
- 여러 트랜잭션이 동시에 재고를 차감하며 정합성이 깨지는 케이스

---

## 2. DB Transaction

트랜잭션은 **하나의 작업 단위**다. 여러 작업이 하나의 논리적 흐름으로 묶여, **실패하면 전체 취소 / 성공하면 전부 반영**되어야 할 때 사용한다.

> e.g. "유저가 상품을 주문한다" = 재고 차감 + 포인트 차감 + 주문 저장이 묶인 하나의 트랜잭션

### ACID 원칙

| 원칙 | 의미 |
| --- | --- |
| **Atomicity (원자성)** | 작업 전체가 성공하거나, 전부 실패해야 함 |
| **Consistency (일관성)** | 비즈니스 규칙을 위반하지 않아야 함 |
| **Isolation (격리성)** | 동시 트랜잭션들이 서로 간섭하지 않도록 함 |
| **Durability (지속성)** | 성공한 트랜잭션의 결과는 디스크에 영구 반영됨 |

### DB 격리 수준 (Isolation Level)

| 격리 수준 | Dirty Read | Non-repeatable Read | Phantom Read |
| --- | --- | --- | --- |
| Read Uncommitted | ✅ 발생 | ✅ 발생 | ✅ 발생 |
| Read Committed | ❌ 방지 | ✅ 발생 | ✅ 발생 |
| Repeatable Read | ❌ 방지 | ❌ 방지 | ✅ 발생 (MySQL InnoDB는 방지) |
| Serializable | ❌ 방지 | ❌ 방지 | ❌ 방지 |

- **Dirty Read**: 다른 트랜잭션이 아직 커밋하지 않은 데이터를 읽음
- **Non-repeatable Read**: 같은 트랜잭션에서 같은 쿼리를 두 번 실행했을 때 결과가 달라짐
- **Phantom Read**: 동일 조건인데 처음엔 없던 행이 두 번째 조회에 나타남

> 🌟 MySQL InnoDB 기본값은 **Repeatable Read**. 대부분을 처리하지만, **완전한 동시성 제어는 별도의 락 또는 Serializable**이 필요하다.

---

## 3. Spring `@Transactional`

AOP 기반 프록시로 트랜잭션 경계를 설정하고, 예외 발생 시 커밋/롤백을 자동 결정한다.

### 기본 동작

- `@Transactional` 메서드는 트랜잭션 범위 안에서 실행된다.
- 범위 내 `RuntimeException` / `Error` 발생 → 자동 롤백
- 정상 종료 → 커밋

### 동작 원리 (요약)

1. 클라이언트 → **프록시** 호출 (실제 객체 아님)
2. `@Transactional` 메타데이터로 `TransactionDefinition` 생성 (전파·격리·readOnly·timeout)
3. `getTransaction(def)`으로 트랜잭션 시작/합류 결정
4. 같은 스레드·트랜잭션 컨텍스트에 바인딩된 Connection/EntityManager로 로직 실행
5. 정상 종료 → `commit` (AFTER_COMMIT 콜백 실행)
6. 예외 → 롤백 규칙 적용
7. 리소스/동기화 해제, Connection/EM 반환

### 롤백 조건

| 예외 타입 | 기본 동작 | 수동 설정 |
| --- | --- | --- |
| `RuntimeException` | 자동 롤백 | - |
| `Checked Exception` (e.g. `IOException`) | **커밋됨** | `rollbackFor` 지정 필요 |
| `Error` (e.g. `OutOfMemoryError`) | 롤백 보장 아님 | 비권장 |

```java
@Transactional(rollbackFor = IOException.class)
public void doSomething() { ... }
```

### 트랜잭션 전파 (Propagation)

| 전파 방식 | 설명 |
| --- | --- |
| `REQUIRED` (default) | 기존 트랜잭션 있으면 참여, 없으면 새로 생성 |
| `REQUIRES_NEW` | 기존 트랜잭션 중단 후 새 트랜잭션 생성 |
| `NESTED` | 부모 트랜잭션 내 Savepoint 두고 하위 트랜잭션 생성 |

> 💡 PG 연동·외부 API 호출은 `REQUIRES_NEW`로 분리해 전체 롤백 영향에서 격리할 수 있다.

### 주의사항

- `@Transactional(readOnly = true)`라도 명시적 DML / `flush()`로 쓰기가 발생할 수 있다 (절대적 차단 아님).
- **Self-invocation**: 같은 클래스 내부 메서드 호출은 프록시를 타지 않아 트랜잭션이 적용되지 않는다.

```java
public void outer() {
    inner();   // 내부 호출 → 프록시 미통과 → @Transactional 무시됨
}

@Transactional
public void inner() { ... }
```

> 트랜잭션 경계가 잘못 설정되면 실패한 요청이 커밋되는 치명적 문제가 발생한다. **메서드 위치·호출 방식·예외 처리**를 함께 고려해야 한다.

---

## 4. JPA Lock 전략

공유 자원(재고·좌석·포인트)에 여러 트랜잭션이 동시 접근할 때 정합성을 보장하는 수단.
**"충돌이 많다/적다"보다 "누가 성공하고 누가 실패/대기할 것인가"**라는 설계 관점으로 접근한다.

| 전략 | 장점 | 단점 | 적합한 상황 |
| --- | --- | --- | --- |
| 🙂 **낙관적 락** | 높은 성능, 락 없음 | 충돌 시 예외 처리 필요 | 조회/수정 빈도 낮은 대상 |
| 😠 **비관적 락** | 안정적, 정합성 보장 | 데드락 위험, 성능 저하 | 정합성 지키며 연산하는 대상 |

### 🙂 낙관적 락 (Optimistic Lock)

- 동시 접근을 허용하고, 트랜잭션 종료 시점에 `@Version` 필드를 비교해 충돌 감지
- 충돌 시 `OptimisticLockingFailureException` → 롤백 또는 재시도 로직 필요
- 철학: "충돌이 없을 것이다"가 아니라 **"충돌해도 실패시켜도 된다"** → 경쟁자 중 1명만 성공

```java
@Entity
class Seat {
    @Id Long id;
    String number;
    boolean isReserved;
    @Version Long version;
}
```

### 😠 비관적 락 (Pessimistic Lock)

- 데이터를 읽는 순간 DB에 락을 걸어 다른 트랜잭션 접근 차단
- 구현: `@Lock(PESSIMISTIC_WRITE)` → `SELECT ... FOR UPDATE`
- 트랜잭션이 끝날 때까지 수정 불가 → **공유 자원을 선점적으로 보호**할 때 적합

```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("SELECT s FROM Seat s WHERE s.id = :id")
Seat findSeatWithLock(@Param("id") Long id);
```

---

## 5. 동시성 테스트 작성 가이드

동시성 문제는 테스트 없이는 발견하기 어렵다.

**핵심 목표**
- 동시 요청 시 정합성이 깨지지 않는지 확인
- 락 전략이 정상 동작하는지 검증
- 예외 상황에서 트랜잭션이 제대로 롤백되는지 확인

**작성 방법**
- `CountDownLatch` + `ExecutorService` (또는 `CompletableFuture`)로 다수 스레드 동시 실행
- 대상 서비스에 `@Transactional` 및 락 전략 포함
- **성공 수 / 실패 수 / DB 최종 상태**를 모두 검증

```java
@DisplayName("동시에 주문해도 재고가 정상적으로 차감된다.")
@Test
void concurrencyTest_stockShouldBeProperlyDecreased() throws InterruptedException {
    int threadCount = 10;
    ExecutorService executor = Executors.newFixedThreadPool(threadCount);
    CountDownLatch latch = new CountDownLatch(threadCount);

    for (int i = 0; i < threadCount; i++) {
        executor.submit(() -> {
            try {
                orderService.placeOrder(1L, 100L, 1);
            } catch (Exception e) {
                System.out.println("실패: " + e.getMessage());
            } finally {
                latch.countDown();
            }
        });
    }
    latch.await();

    Product product = productRepository.findById(100L).orElseThrow();
    assertThat(product.getStock()).isGreaterThanOrEqualTo(0);
}
```

---

## References

| 구분 | 내용 |
| --- | --- |
| Spring Transactional | 트랜잭션 관리 |
| JPA 더티체킹 | Dirty Checking |
| MySQL 격리 레벨 | Transaction Isolation Level |
| JPA 트랜잭션 전파/격리 | Baeldung |
| JPA 낙관적 락 | Baeldung |
| 멀티 스레드 | Baeldung Java Multithreading |
