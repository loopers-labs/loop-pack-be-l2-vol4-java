# [Design Doc] 적절한 DB 동시성 제어 방식 설정

## Introduction & Goals

- **Context / Background**: 재고 차감, 쿠폰 사용, 좋아요 카운트는 여러 요청이 동시에 건드릴 수 있는 공유 자원이다. 단순히 엔티티 조회 → 메모리 수정 → dirty checking으로 커밋하면, 두 트랜잭션이 같은 값을 읽고 각자 수정을 커밋하는 Lost Update가 생긴다. 세 도메인은 접근 패턴과 충돌 빈도가 달라서 하나의 전략을 일괄 적용하기보다 각각의 특성에 맞는 방법을 골랐다.

- **Goals**:
  - 재고/쿠폰/좋아요 세 영역에서 Lost Update 없이 정합성 보장
  - 불필요한 lock 경합을 줄여 처리량 유지
  - 실패 시 트랜잭션 전체 롤백으로 부분 반영 방지

---

## Detailed Design

### System Architecture

동시성 제어는 Infrastructure 레이어(JPA 어노테이션, JPQL)에서 담당한다. Application 레이어는 Repository 메서드를 호출할 뿐이고, 락 전략이 어디에 있는지 알 필요가 없다.

```
[Request] → OrderFacade (@Transactional)
               ├── StockRepository.decreaseStock()          ← atomic UPDATE
               ├── UserCouponRepository.findByIdWithCoupon()
               │     └── userCoupon.use()                   ← @Version 낙관적 락
               └── OrderRepository.save()

[Request] → LikeService (@Transactional)
               └── ProductRepository.incrementLikeCount()   ← atomic UPDATE
```

`OrderFacade.createOrder()`는 단일 `@Transactional` 안에서 재고 차감 → 쿠폰 사용 → 주문 저장을 순서대로 처리하므로, 중간에 실패하면 앞 단계도 함께 롤백된다.

---

### Data Models

**재고 (`stocks`)**

```sql
quantity INT NOT NULL
-- UPDATE SET quantity = quantity - n WHERE quantity >= n 으로 차감
-- version 컬럼 없음, 쿼리 자체가 원자적
```

**발급 쿠폰 (`user_coupons`)**

```sql
status  VARCHAR(10) NOT NULL  -- AVAILABLE | USED (EXPIRED는 저장 안 함)
version BIGINT      NOT NULL  -- @Version: JPA가 커밋 시 WHERE version = ? 조건 추가
```

**좋아요 (`products`)**

```sql
like_count BIGINT NOT NULL
-- UPDATE SET like_count = like_count + 1
-- @Modifying(clearAutomatically = true) 로 영속성 컨텍스트 자동 clear
```

---

### API Design

락 전략은 API 스펙에 영향을 주지 않는다. 실패 시 아래 응답을 반환한다.

| 상황 | HTTP Status |
|------|-------------|
| 재고 부족 (affected rows = 0) | `400 Bad Request` |
| 쿠폰 이중 사용 (`OptimisticLockingFailureException`) | `400 Bad Request` |

서버는 실패를 그대로 반환하고, 재시도는 클라이언트 책임이다.

---

### Constraints

- **재고 롤백**: `decreaseStock()`은 원자적 UPDATE지만 `@Transactional` 경계 안에 있어서, 이후 단계(쿠폰, 주문 저장)가 실패하면 DB 레벨에서 함께 롤백된다.
- **쿠폰 재시도 없음**: 낙관적 락 충돌은 "다른 트랜잭션이 이미 사용 완료"를 의미한다. 재시도해도 `use()`의 status 체크 또는 다음 version 비교에서 또 실패하기 때문에 재시도 로직을 두지 않았다.
- **재고 `clearAutomatically` 미설정**: `StockJpaRepository.decreaseQuantity()`에는 `clearAutomatically`가 없다. 현재 `OrderFacade` 흐름에서 차감 후 재고를 같은 트랜잭션 내 재조회하는 코드가 없어서 stale 문제는 없지만, 흐름이 바뀌면 주의해야 한다.
- **EXPIRED 동적 계산**: `user_coupons.status`는 AVAILABLE/USED만 저장하고 만료 여부는 조회 시점에 `expiredAt` 비교로 계산한다. 별도 배치 없이 즉시 반영되지만, 대량 발급 환경에서 만료 필터 쿼리에 `expiredAt` 인덱스가 없으면 풀스캔이 생길 수 있다.

---

## Alternatives Considered

### 재고 차감

| 옵션 | Pros | Cons |
|------|------|------|
| A. `SELECT ... FOR UPDATE` (비관적 락) | 순차 처리 보장, 코드 직관적 | 조회 시점부터 row lock → 트랜잭션이 길수록 경합 증가, 재고 0일 때도 lock 보유 |
| **선택: B. `UPDATE WHERE quantity >= n`** | lock 없이 확인+차감을 단일 쿼리로 원자 처리, affected=0으로 부족 판단, 재고 0이어도 lock 없음 | 실패 시 "현재 잔여 재고 N개" 메시지 포함이 어려움 |

**선택 근거:** "재고 확인 → 차감"을 두 단계로 나누면 그 사이에 다른 트랜잭션이 끼어들 수 있다. `WHERE quantity >= n` 조건이 검증과 차감을 한 쿼리 안에서 원자적으로 처리하므로 lock 없이도 정합성이 보장된다.

---

### 쿠폰 사용

| 옵션 | Pros | Cons |
|------|------|------|
| A. `SELECT ... FOR UPDATE` (비관적 락) | 충돌 시 대기 후 순차 처리, 명시적 | 조회 시점부터 트랜잭션 끝까지 row lock 유지 |
| **선택: B. `@Version` (낙관적 락)** | 조회 시 lock 없음, 충돌이 드문 상황에서 overhead 없음 | 충돌 시 `OptimisticLockingFailureException` 핸들링 필요 |

**선택 근거:** 같은 쿠폰에 동시 요청이 몰리는 경우가 드물고(1인 1쿠폰 구조), 충돌이 났다는 건 이미 다른 트랜잭션이 사용 완료했다는 의미라 재시도가 필요 없다. 매 조회마다 lock을 잡는 비관적 락보다 낙관적 락이 일반 케이스에서 불필요한 비용이 없다.

---

### 좋아요 카운트

| 옵션 | Pros | Cons |
|------|------|------|
| A. 엔티티 조회 후 dirty checking | 코드 단순 | 조회~커밋 사이에 다른 트랜잭션 개입 시 Lost Update |
| B. `SELECT ... FOR UPDATE` (비관적 락) | Lost Update 방지 | 좋아요처럼 빈번한 write에선 lock 경합이 병목이 됨 |
| **선택: C. `UPDATE SET like_count = like_count ± 1`** | lock 없이 DB 레벨 원자성 보장, 고빈도 write에서도 경합 없음, `clearAutomatically = true`로 stale 문제도 해결 | 도메인 메서드 대신 쿼리 직접 작성 필요 |

**선택 근거:** 좋아요는 많은 사용자가 독립적으로 동시에 누르는 패턴이다. 원자적 UPDATE는 lock 없이 카운트 증감을 DB 레벨에서 처리해 Lost Update를 막는다. 충돌 빈도가 높을수록 비관적 락 대비 처리량 이점이 커진다.
