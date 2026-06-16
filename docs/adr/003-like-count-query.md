# ADR-003: 좋아요 수 COUNT 쿼리

- 날짜: 2026-05-21 (수정)
- 상태: 승인됨

## 결정

상품의 좋아요 수(`likeCount`)는 `product` 테이블의 `like_count` 컬럼으로 관리한다.

- **좋아요 등록**: `UPDATE product SET like_count = like_count + 1 WHERE id = ?`
- **좋아요 취소**: `UPDATE product SET like_count = like_count - 1 WHERE id = ?`
- **조회**: `ProductEntity.likeCount` 필드를 그대로 반환 (별도 COUNT 쿼리 없음)

## 근거

### 고려한 대안

#### Option 1. COUNT 쿼리 방식

좋아요 수를 별도 컬럼으로 관리하지 않고, 조회 시마다 `likes` 테이블을 COUNT하는 방식이다.

```sql
-- 단건 조회
SELECT COUNT(*) FROM likes WHERE product_id = ? AND deleted_at IS NULL

-- 목록 조회
SELECT product_id, COUNT(*) FROM likes
WHERE product_id IN (?, ?, ...)
GROUP BY product_id
```

- **장점**: 항상 정확한 좋아요 수를 반환한다. 비정규화 컬럼 관리가 필요 없어 스키마가 단순하다.
- **단점**: 단건 조회 시 매 요청마다 COUNT 쿼리가 추가로 발생한다. 목록 조회 시 상품 조회 + COUNT 조회로 쿼리가 2회 발생한다. 트래픽이 늘수록 DB 부하가 선형으로 증가한다.

---

#### Option 2. DB 비정규화 — like_count 컬럼 (채택)

`product` 테이블에 `like_count` 컬럼을 두고, 좋아요 등록/취소 시 SQL 원자적 증감으로 관리하는 방식이다.

```sql
-- 좋아요 등록 (원자적, Lost Update 없음)
UPDATE product SET like_count = like_count + 1 WHERE id = ?

-- 좋아요 취소
UPDATE product SET like_count = like_count - 1 WHERE id = ?
```

초안에서 "Lost Update 가능성"을 우려했으나, 이는 Java에서 값을 읽어 +1 후 저장하는 Read-Modify-Write 패턴의 문제다. SQL 레벨의 원자적 증감은 DB가 단일 연산으로 처리하므로 Lost Update가 발생하지 않는다.

- **장점**: 조회 시 별도 COUNT 쿼리가 없어 DB 부하가 줄어든다. SQL 원자적 증감으로 정합성도 보장된다.
- **단점**: 좋아요 등록/취소 시 product 테이블에도 UPDATE가 발생한다. 좋아요가 폭발적으로 집중될 경우 같은 행에 UPDATE가 몰리는 Hot Row 이슈가 발생할 수 있다.

---

#### Option 3. Redis 카운터

Redis의 `INCR`/`DECR` 명령어로 좋아요 수를 캐싱하고, DB는 Source of Truth로만 사용하는 방식이다.

```
좋아요 등록: SQL UPDATE + INCR like_count:{productId}
조회:        GET like_count:{productId} → miss 시 DB fallback + SET
```

- **장점**: 조회 성능이 O(1)로 가장 빠르다. DB 부하를 크게 줄일 수 있다.
- **단점**: Redis 장애 시 fallback 처리와 캐시 워밍 전략이 필요하다. DB와의 미세한 불일치 가능성이 있다. Option 2 대비 인프라 복잡도가 높다.

## 향후 고려사항

Hot Row 이슈가 발생하면 Option 2(DB 비정규화)를 Source of Truth로 유지하면서 Option 3(Redis 카운터)을 캐시 레이어로 추가하는 방식으로 전환한다. 대규모 트래픽 환경에서는 Kafka 이벤트 기반으로 쓰기 경로를 비동기화하는 것도 고려한다.
