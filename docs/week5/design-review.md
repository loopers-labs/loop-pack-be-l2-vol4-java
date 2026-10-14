# 5주차 설계 리뷰 — 읽기 최적화 (인덱스 + 캐시)

> Round 5 의 "가정한 설계에 대해 점검하기" 요구에 따라, 본 PR 의 읽기 최적화 설계를
> 시니어 아키텍트 관점에서 자기 점검한 결과를 정리한다.
> `analyze-query` Skill 의 트랜잭션·동시성 관점도 함께 적용한다.

---

## 0. 설계 요약

- **조회 API 목적**: 상품 목록(브랜드 필터 + 정렬) / 상품 상세
- **주요 조회 조건**: brand_id, sortType ∈ {LATEST, PRICE_ASC, LIKES_DESC}
- **사용한 테이블/데이터**: `product` (10만 행), `brand` (50 행)
- **인덱스**:
  - `idx_product_brand_likes (brand_id, like_count DESC)` — 브랜드 필터 + 인기순 커버
  - `idx_product_likes (like_count DESC)` — 전체 인기순
  - `idx_product_created (created_at DESC)` — 전체 최신순
  - `idx_product_price (price)` — 가격순
- **캐시 적용 여부 및 위치**: 적용. Application Layer Look-aside (`ProductFacade` ↔ `ProductCachePort` ↔ `ProductRedisCacheAdapter`)
- **캐시 키 전략**:
  - 상세: `product:detail:{id}`
  - 목록: `product:list:{brandId|all}:{sortType}`
- **캐시 TTL 가정**:
  - 상세 5분
  - 목록 1분

---

## 1. 이 설계가 성립하기 위해 반드시 참이어야 하는 전제 조건

1. **like_count 가 product 행에 정상적으로 동기화된다**.
   - `LikeService.like/unlike` → `ProductService.increment/decrementLikeCount` (atomic UPDATE) 의 호출 사슬이 끊기지 않아야 한다. 이 사슬이 깨지면 인덱스 `idx_product_brand_likes` 의 정렬 기준이 stale → 인기순 결과가 부정확해진다.
2. **InnoDB buffer pool 이 인덱스 페이지를 상당 부분 캐싱한다**.
   - 인덱스의 효과는 "정렬 회피" 와 "랜덤 IO 감소" 둘 다인데, 두 번째는 buffer pool 에 의존. pool 이 데이터 워킹셋보다 작으면 hot-spot 외엔 항상 cold read.
3. **MySQL 8.0+ 의 descending index 가 실제로 사용된다**.
   - 8.0 이전이면 `DESC` 가 무시되어 backward index scan 으로 fallback → 약간 느림 but 정확. 현 환경은 8.0+ 가정.
4. **brand_id 분포가 극단적으로 편향되어 있지 않다**.
   - 한 brand 에 90% 가 몰려 있으면 옵티마이저가 `idx_product_brand_likes` 보다 PK 풀스캔을 더 싸게 본다.
5. **캐시는 단일 Redis 인스턴스의 데이터 일관성을 보장한다**.
   - 클러스터/멀티 인스턴스 분산 시 `evictListsByBrand` 의 SCAN+DEL 이 모든 노드에 도달함을 가정. 현 환경 (단일 master + replicas) 에서는 master 단방향 write 라 충족.

---

## 2. 트래픽이 10배 증가했을 때 가장 먼저 병목이 될 지점

**1순위: 캐시 MISS 시 DB 쇄도 (cache stampede)**

- 인기 상품 상세 캐시가 TTL 만료되는 순간, 동시 다발 요청이 일제히 DB 로 fallback. `idx_product_brand_likes` 가 빨라도 같은 시점 같은 쿼리가 N개 동시 실행 → connection pool (maximum-pool-size: 40) 고갈 → 503.

**2순위: 목록 캐시의 키 폭증**

- 브랜드 50 × 정렬 3 = 150 키 만 가정했지만, 후속 필터(가격대, 카테고리) 가 추가되면 키 카디널리티가 곱셈으로 늘어남. SCAN+DEL 의 비용 ↑ + Redis 메모리 ↑.

**3순위: like_count UPDATE 의 lock contention**

- 인기 상품일수록 `UPDATE product SET like_count = like_count + 1 WHERE id = ?` 의 row lock 경합 발생. atomic UPDATE 라 대기 시간은 짧지만, 10배 트래픽에선 throughput 한계 도달 가능.

---

## 3. 캐시 적중률이 30% 이하로 떨어졌을 때 발생할 문제

- **DB 부하 ≈ 캐시 없는 상태의 70%** 까지 회귀. 인덱스가 잘 잡혀 있어 catastrophe 까진 아니지만, 평소 가정한 dynamics 가 깨진다.
- **목록 캐시의 1분 TTL 이 30% 적중률을 만든다면**: 트래픽 패턴이 캐시 가치와 맞지 않는 것. 즉 매분 들어오는 사용자가 모두 다른 (brandId, sort) 조합을 요청. → TTL 늘려도 적중률은 안 오르고 Redis 메모리만 낭비됨.
- **상세 캐시 5분에서 30% 적중률**: 변경 (like/order) 이 너무 자주 일어나 evict 가 빈번하다는 신호. like 폭주하는 핫 상품엔 캐시가 무의미. → "최근 N분간 like 변동 < M 회" 등의 조건부 캐시 정책 또는 캐시 → 카운터 분리 (HyperLogLog/atomic counter 별도) 검토.

---

## 4. 데이터 정합성이 깨질 수 있는 시나리오

### Scenario A — 좋아요 evict 와 다른 사용자의 GET 사이 race

1. 사용자 U1 이 like → `LikeService.like` 트랜잭션 시작 (like_count atomic +1)
2. **트랜잭션 커밋 전**, 사용자 U2 의 GET 도착 → 캐시 미스 → DB 조회 → like_count 는 **트랜잭션 격리 수준 (READ_COMMITTED)** 상 아직 옛 값.
3. U2 의 응답이 캐시에 적재됨 (옛 값).
4. U1 의 트랜잭션 커밋 → `LikeFacade.like` 가 `productCache.evictDetail(productId)` 호출.
5. 그러나 4단계 evict 의 호출 시점은 트랜잭션 커밋 **후** 가 아니라 `likeService.like` 메서드 반환 **직후** — Spring `@Transactional` 은 메서드 종료 시 커밋되므로 LikeFacade 단의 evict 는 항상 commit 후이긴 함. 하지만 U2 의 캐시 적재 (3) 가 U1 commit 보다 늦었다면 U2 의 stale 값이 캐시에 남는다.

**대응 방향**: `@TransactionalEventListener(AFTER_COMMIT)` 으로 evict 를 후처리하거나, 캐시 키에 버전(like_count 자체)을 끼우는 versioned cache. 현 단계는 5분 TTL 의 자연 만료에 의존 — 부트캠프 학습 범위 내에서 acceptable.

### Scenario B — 목록 캐시는 createProduct 외엔 invalidate 안 됨

- 가격 변경, 재고 변경, 상태 변경(SOLD_OUT) 시 목록 캐시는 1분 TTL 자연 만료로만 갱신. 그 사이 사용자는 "재고 0 인데 ACTIVE" 같은 inconsistency 를 볼 수 있다.
- 대응: TTL 짧게 (1분) 유지 + 사용자 행동(주문 시도) 단에서 한 번 더 검증 (현 `OrderCreationService.decreaseStock` 의 비관적 락이 이를 책임).

### Scenario C — Redis 다운 → fallback 직후 복구 시 정합성

- Redis 다운 동안 모든 evict 호출이 silently 실패. 복구 후 캐시엔 **다운 직전의 stale 값** 이 그대로 남아 있고, TTL 이 지나기 전엔 갱신 안 됨.
- 대응: Redis 부팅 시 명시적 FLUSH (운영 정책) 또는 모든 키에 짧은 TTL 보장. 현 설계는 두 번째 조건 만족.

---

## 5. 이 설계를 유지하면서 가장 나중까지 미룰 수 있는 개선

- **Materialized View (랭킹 테이블 분리)**: 좋아요순 정렬용 별도 테이블 또는 Redis ZSET 으로 분리. 지금은 `idx_product_likes` 만으로 LIMIT 20 1~2ms 수준이라 trade-off 가치 낮음. 트래픽 100배 / 좋아요 폭주 시점에서 고려.
- **페이지네이션 keyset 변환**: OFFSET 기반 → keyset (cursor) 기반. 현재 LIMIT 만 사용하므로 미진입. 사용자가 깊은 페이지를 자주 보기 시작하면 그때.
- **Read replica 분리**: 조회 트래픽 라우팅. 현재 단일 인스턴스로 충분. 쓰기/읽기 비율이 1:9 이상으로 벌어질 때.

## 6. 가장 먼저 손대야 할 위험 요소

**Cache stampede 방어** — 단일 mutex (싱글 로더) 또는 `XFETCH (probabilistic early expiration)` 패턴 도입. 5주차 범위에선 다루지 않지만, 운영 도입 직전엔 반드시 필요.

이유: 위 §2 의 1순위 병목이자, §4 Scenario A 의 race 발생 빈도를 줄이는 이중 효과. 또한 캐시 TTL 만료가 *예측 가능한* 시점에 *동시에* 일어난다는 점에서, 트래픽 패턴이 정상이어도 발생하는 구조적 위험. 인덱스/캐시 적용은 평균 응답을 줄이지만 cache stampede 는 *p99 응답* 을 폭주시키므로, 사용자 체감 품질에 직접 타격.

---

## 7. analyze-query 관점 — 트랜잭션·동시성 점검

| 항목 | 현 상태 | 평가 |
|------|--------|------|
| 트랜잭션 범위 | `ProductService.list/get` 은 `readOnly=true`, like/unlike/decreaseStock 은 short tx | ✅ 작게 잡혀 있음 |
| 조회/쓰기 혼합 | 분리됨 (조회 facade 와 mutation facade 따로) | ✅ |
| 영속성 컨텍스트 leak | `open-in-view=false`. JSON 직렬화는 Facade 가 반환한 Info DTO 기준 | ✅ Lazy loading 사고 X |
| Flush 타이밍 | `incrementLikeCount` 는 `@Modifying(flushAutomatically=true)` 로 명시 | ✅ |
| 동시성 제어 | 재고: 비관적 락 (4주차). like_count: atomic UPDATE | ✅ 각 일관성 지점에 적절 |
| 캐시 evict 와 tx 경계 | Facade 가 호출 → 메서드 종료 시 tx 커밋 → 그 후 evict. AFTER_COMMIT 이벤트로 옮기면 더 견고 | △ 위 §4 Scenario A 참고 |

---

## 8. 결론

- 인덱스 4개, 캐시 (상세 5분 + 목록 1분), 동기 evict 로 부트캠프 범위의 읽기 최적화 요구는 충족.
- 운영 직전 단계에서는 (a) AFTER_COMMIT evict, (b) cache stampede 방어, (c) 캐시 적중률 모니터링 의 3가지가 필수.
- Materialized View / Read replica 등은 트래픽 규모가 더 명확해진 뒤 도입 결정.
