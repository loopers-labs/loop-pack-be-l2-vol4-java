# 좋아요 수(like_count) 비정규화 설계

## 1. 배경

상품 목록 조회 시 `likes_desc` (좋아요 많은 순) 정렬이 요구된다.  
정규화된 구조에서 이를 구현하면 매 조회마다 `likes` 테이블을 집계해야 한다.

```sql
-- 정규화 방식 (집계 쿼리)
SELECT p.*, COUNT(l.id) AS like_count
FROM products p
LEFT JOIN likes l ON l.product_id = p.id
WHERE p.deleted_at IS NULL
GROUP BY p.id
ORDER BY like_count DESC
LIMIT 20;
```

상품·좋아요 수가 늘어날수록 `GROUP BY` 비용이 선형 이상으로 증가하고, 인덱스도 활용하기 어렵다.  
이 문제를 해결하기 위해 `products.like_count` 비정규화 컬럼을 도입했다.

---

## 2. 현재 구조

### 데이터 이중 관리

```
likes 테이블                     products 테이블
┌──────────────────┐             ┌──────────────────────┐
│ id               │             │ id                   │
│ user_id    (FK)  │             │ name                 │
│ product_id (FK)  │─────────▶  │ price                │
│ liked_at         │             │ like_count  ◀── 캐시 │
└──────────────────┘             │ deleted_at           │
UNIQUE(user_id, product_id)      └──────────────────────┘
```

| 데이터 | 역할 |
|--------|------|
| `likes` 테이블 | 관계의 원천 (누가 어떤 상품을 좋아요했는가) |
| `products.like_count` | 집계 캐시 (정렬·응답 전용) |

두 데이터는 **다른 목적**을 가지므로 이중 관리는 의도된 설계다.

### 동기화 방식

좋아요 등록·취소와 `like_count` 갱신을 **동일 트랜잭션** 안에서 처리한다.

```java
// LikeService.like()
@Transactional
public void like(Long userId, Long productId) {
    // ...유효성 검증 (삭제 상품, 중복 체크)...
    likeRepository.save(new LikeModel(userId, productId));
    productRepository.incrementLikeCount(productId);   // atomic UPDATE
}
```

```sql
-- atomic UPDATE (JPA @Modifying)
UPDATE products SET like_count = like_count + 1 WHERE id = ?
UPDATE products SET like_count = like_count - 1 WHERE id = ? AND like_count > 0
```

`AND like_count > 0` 가드로 음수 방지. `clearAutomatically = true`로 영속성 컨텍스트 캐시 무효화.

---

## 3. 장단점

### 장점

| 항목 | 내용 |
|------|------|
| **정렬 성능** | `ORDER BY like_count DESC` + 인덱스로 집계 없이 정렬 가능 |
| **응답 단순화** | 상품 조회 시 별도 COUNT 쿼리 불필요, `products` 한 번만 조회 |
| **확장성** | 좋아요 수백만 건이어도 정렬 쿼리 비용 불변 |
| **구현 단순성** | `likes_desc` 외에 추가 정렬 기준이 생겨도 동일 패턴 적용 가능 |

### 단점

| 항목 | 내용 |
|------|------|
| **데이터 정합성 리스크** | `likes` 행 수 ≠ `like_count` 가능성 (drift) |
| **쓰기 부하** | 좋아요마다 `products` 테이블도 UPDATE 발생 |
| **동시성 복잡도** | 동시 좋아요 시 products 행 잠금 경합 가능성 |
| **운영 복잡도** | 두 데이터를 항상 함께 관리해야 함 |

---

## 4. Trade-off 분석

### 정합성 vs 성능

```
정규화                          비정규화
━━━━━━━━━━━━━━━━━━━━          ━━━━━━━━━━━━━━━━━━━━━━━━
항상 정확한 카운트               약간의 drift 가능성
집계 쿼리 (읽기 비용 높음)       캐시 갱신 (쓰기 비용 추가)
단순한 데이터 관리               이중 관리 필요
```

**선택 기준**: 이 서비스에서 `likes_desc` 정렬은 상품 목록 조회마다 발생하는 **읽기 핫 경로(hot path)**다.  
반면 좋아요 등록·취소는 **사용자 액션**으로 상대적으로 덜 빈번하다.  
읽기가 압도적으로 많은 상황에서 쓰기 비용을 약간 늘려 읽기를 최적화하는 것이 합리적이다.

### 동시성 처리

| 방식 | 장점 | 단점 |
|------|------|------|
| Optimistic Lock (`@Version`) | 락 오버헤드 없음 | 충돌 시 재시도 로직 필요, 좋아요처럼 빈번한 업데이트에 부적합 |
| Pessimistic Lock (`SELECT FOR UPDATE`) | 순서 보장 | 동시 요청 시 대기 큐 형성, throughput 감소 |
| **Native atomic UPDATE** ✅ | DB가 row 단위로 원자적 처리 | 중간 값 읽기 불가 |

DB의 row-level 잠금을 활용한 `UPDATE SET col = col + 1`이 이 유즈케이스에서 가장 적합하다.  
애플리케이션 레벨 재시도나 명시적 잠금 없이도 동시성이 보장된다.

### drift 발생 시나리오

| 시나리오 | 영향 | 현재 대응 |
|----------|------|-----------|
| 트랜잭션 커밋 직전 crash | like 저장 성공 + count 미반영 | 트랜잭션 롤백으로 자동 복구 |
| 배치/마이그레이션으로 likes 직접 조작 | count 불일치 | 재집계 배치 필요 |
| 버그로 인한 누락 | 누적 drift | 모니터링 + 재집계 배치 필요 |

---

## 5. 추후 개선 / 고도화 방안

### 5-1. 재집계 배치 (단기)

drift 방지를 위한 주기적 재집계 배치를 도입한다.

```sql
-- products.like_count를 likes 테이블 실제 count로 보정
UPDATE products p
SET p.like_count = (
    SELECT COUNT(*) FROM likes l WHERE l.product_id = p.id
)
WHERE p.deleted_at IS NULL;
```

- 주기: 일 1회 새벽 저트래픽 시간대
- 실행 단위: 전체 또는 최근 변경된 product_id 배치

### 5-2. Redis 카운터 (중기)

동시 좋아요 수가 매우 많아지면 products 테이블 자체가 write bottleneck이 된다.  
이 경우 Redis INCR/DECR로 카운터를 관리하고, DB는 주기적으로 sync한다.

```
좋아요 등록
  → likes 테이블 INSERT
  → Redis INCR like:count:{productId}   ← 인메모리, 초고속

상품 조회
  → Redis GET like:count:{productId}    ← DB 조회 없음

주기적 sync (배치)
  → Redis 값 → products.like_count UPDATE
```

장점: products 테이블 write lock 완전 제거  
단점: Redis 장애 시 카운터 손실 위험, 아키텍처 복잡도 증가

### 5-3. 이벤트 기반 분리 (장기)

좋아요 등록을 이벤트로 발행하고, 카운터 갱신을 별도 컨슈머가 처리한다.

```
LikeService.like()
  → likes INSERT
  → LikedEvent 발행 (Kafka / Spring ApplicationEvent)

LikeCountConsumer
  → LikedEvent 수신
  → products.like_count atomic UPDATE
```

장점: 좋아요 등록과 카운트 갱신의 결합도 완전 분리, 각 책임이 독립적으로 확장 가능  
단점: 최종 일관성(eventual consistency)만 보장 — 등록 직후 count가 아직 반영되지 않을 수 있음

### 5-4. 모니터링 (운영)

운영 중 drift를 감지하기 위한 지표를 추가한다.

```sql
-- drift 탐지 쿼리 (정기 실행)
SELECT p.id, p.like_count AS cached, COUNT(l.id) AS actual,
       (p.like_count - COUNT(l.id)) AS drift
FROM products p
LEFT JOIN likes l ON l.product_id = p.id
GROUP BY p.id
HAVING drift != 0;
```

drift가 임계값 초과 시 알람 발송 + 재집계 배치 트리거.

---

## 6. 결론

| 항목 | 현재 선택 | 이유 |
|------|-----------|------|
| 방식 | 비정규화 + atomic UPDATE | 읽기 최적화, 구현 단순성 |
| 동시성 | DB row-level 원자 UPDATE | 재시도 없이 안전 |
| 정합성 | 트랜잭션 보장 + 0 미만 가드 | 일반적 drift 방지 |
| 추후 | 재집계 배치 → Redis 카운터 → 이벤트 분리 | 트래픽에 따라 단계적 고도화 |

현재 구조는 **"서비스 초기 규모에서 충분히 안전하고 단순한 선택"** 이다.  
트래픽이 실제로 늘어나는 시점에 5-2, 5-3 단계를 선택적으로 적용하면 된다.
