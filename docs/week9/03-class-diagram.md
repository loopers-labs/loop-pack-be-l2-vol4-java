# 03. 클래스 다이어그램 — 실시간 랭킹

랭킹 관련 컴포넌트를 앱별로 정리한다. 두 앱은 코드를 공유하지 않으므로(앱 경계), ZSET 키 포맷은 **문자열 계약**으로만 맞춘다([`04-redis-model.md`](./04-redis-model.md) §1).

- **commerce-streamer** — 적재(쓰기) 측. Kafka Consumer → ZSET.
- **commerce-api** — 조회(읽기) 측. ZSET → Ranking API / 상품 상세.

---

## 1. commerce-streamer (적재/쓰기)

```mermaid
classDiagram
    class ProductRankingConsumer {
        <<@Component>>
        -RankingAggregator rankingAggregator
        +consume(List~EventEnvelope~, Acknowledgment)
    }
    class RankingAggregator {
        <<@Component>>
        +String CONSUMER_GROUP = "ranking-aggregator"
        -RankingRedisRepository rankingRedisRepository
        -EventHandledRepository eventHandledRepository
        +apply(List~EventEnvelope~) @Transactional
        -add(deltas, key, productId, score)
    }
    class EventHandledRepository {
        <<@Component>>
        +findHandled(group, eventIds) Set~Long~
        +markHandled(group, eventIds)
    }
    class RankingScorePolicy {
        <<final>>
        +double VIEW_WEIGHT = 0.1
        +double LIKE_WEIGHT = 0.2
        +double ORDER_WEIGHT = 0.6
        +viewScore() double
        +likeScore(delta) double
        +orderScore(unitPrice, qty) double
    }
    class RankingRedisRepository {
        <<@Component>>
        -RedisTemplate~String,String~ redisTemplate
        +incrementAll(Map~String,Map~String,Double~~)
    }
    class RankingKey {
        <<final>>
        +ZoneId ZONE = Asia/Seoul
        +Duration TTL = 2d
        +daily(LocalDate) String
        +dateOf(String occurredAt) LocalDate
    }
    class EventEnvelope {
        <<record>>
        Long eventId
        String eventType
        String occurredAt
        JsonNode payload
    }

    ProductRankingConsumer --> RankingAggregator
    RankingAggregator --> RankingScorePolicy : 점수 계산
    RankingAggregator --> RankingKey : 일자/키
    RankingAggregator --> RankingRedisRepository : incrementAll
    RankingAggregator --> EventHandledRepository : 멱등(find/mark)
    RankingRedisRepository --> RankingKey : TTL
    ProductRankingConsumer ..> EventEnvelope
```

> `EventEnvelope`와 `EventHandledRepository` 모두 week7 `metrics` 패키지의 것을 재사용한다. `EventHandledRepository`는 `consumer_group`을 파라미터로 받도록 설계돼 있어 `ranking-aggregator` 그룹으로 그대로 쓸 수 있다(§2.1). `ProductRankingConsumer`는 `metrics-aggregator`와 **다른 그룹**으로 같은 토픽을 독립 소비하며, `event_handled`도 그룹별 행이라 서로 경합하지 않는다.

---

## 2. commerce-api (조회/읽기) — 레이어드

```mermaid
classDiagram
    class RankingV1Controller {
        <<@RestController>>
        -RankingFacade rankingFacade
        +getRankings(date, page, size) ApiResponse
    }
    class RankingV1Dto {
        <<static>>
        RankingPageResponse
        RankedItem
    }
    class RankingFacade {
        <<@Component>>
        -RankingRepository rankingRepository
        -ProductService productService
        -ProductMetricsService productMetricsService
        -BrandService brandService
        +getRanking(date, page, size) RankingPageInfo
    }
    class RankingPageInfo {
        <<record>>
        List~RankedProductInfo~ items
        long totalCount
        int page
        int size
    }
    class RankedProductInfo {
        <<record>>
        long rank
        Long productId
        String name
        Long price
        Long brandId
        String brandName
        Long likesCount
        double score
    }
    class RankingRepository {
        <<interface / domain port>>
        +findPage(date, page, size) List~RankedProduct~
        +size(date) long
        +findRank(date, productId) Optional~Long~
    }
    class RankedProduct {
        <<record>>
        long rank
        Long productId
        double score
    }
    class RankingRedisRepository {
        <<@Repository>>
        -RedisTemplate~String,String~ redisTemplate
        +findPage(...) : ZREVRANGE
        +size(...) : ZCARD
        +findRank(...) : ZREVRANK
    }
    class RankingKey {
        <<final>>
        +ZoneId ZONE = Asia/Seoul
        +daily(LocalDate) String
        +today() LocalDate
    }

    RankingV1Controller --> RankingFacade
    RankingV1Controller --> RankingV1Dto
    RankingFacade --> RankingRepository
    RankingFacade --> RankingPageInfo
    RankingPageInfo --> RankedProductInfo
    class RankingCompositeRepository {
        <<@Primary @Repository>>
        -RankingRedisRepository redis
        -RankingSnapshotRepository snapshot
        +findPage(date, page, size) List~RankedProduct~
        +size(date) long
        +findRank(date, productId) Optional~Long~
    }
    class RankingSnapshotRepository {
        <<@Repository>>
        -RankingSnapshotJpaRepository jpa
        +findPage(date, page, size) List~RankedProduct~
        +size(date) long
    }
    RankingRepository <|.. RankingCompositeRepository : implements (유일)
    RankingCompositeRepository --> RankingRedisRepository : 오늘·어제(ZSET)
    RankingCompositeRepository --> RankingSnapshotRepository : 그 이전(DB)
    RankingRedisRepository --> RankingKey
    RankingRedisRepository ..> RankedProduct
    RankingSnapshotRepository ..> RankedProduct
```

**레이어드 규칙 준수**

- `domain.ranking.RankingRepository`는 **포트(인터페이스)**, `infrastructure.ranking.RankingCompositeRepository`가 유일한 구현이다 — 도메인은 저장 기술(Redis/DB)도, 데이터가 **어느 소스에서 왔는지도** 모른다. Redis/스냅샷 선택은 인프라 관심사라 포트 뒤에 숨는다.
- `RankingRedisRepository`·`RankingSnapshotRepository`는 포트를 직접 구현하지 않는다 — 컴포지트 뒤의 한쪽 소스일 뿐이다. 소스 선택 규칙(날짜 단위, 혼합 금지)은 [`05-implementation-notes.md`](./05-implementation-notes.md) §2.6.
- `Controller → Facade → Repository(port)` 단방향. Facade가 랭킹(순위·스코어)과 상품 요약을 조립·변환한다.
- API DTO(`RankingV1Dto`)와 응용 DTO(`RankingPageInfo`/`RankedProductInfo`)는 분리.

---

## 3. 상품 상세 순위·추세 통합 (변경분)

```mermaid
classDiagram
    class ProductFacade {
        <<@Component>>
        -RankingRepository rankingRepository
        +getProductDetail(id, userId) ProductDetailInfo
    }
    class CachedProductDetail {
        <<record>>
        +toInfo(boolean liked, Long rank, Long rankYesterday) ProductDetailInfo
    }
    class ProductDetailInfo {
        <<record>>
        ...기존 필드...
        boolean liked
        Long rank
        Long rankYesterday
    }
    ProductFacade --> RankingRepository : findRank(today, id)
    ProductFacade --> RankingRepository : findRank(today-1, id)
    ProductFacade --> CachedProductDetail : toInfo(liked, rank, rankYesterday)
    CachedProductDetail --> ProductDetailInfo
```

> `ProductDetailInfo`/`CachedProductDetail.toInfo`/`ProductV1Dto.ProductDetailResponse`에 `rank`·`rankYesterday` 필드를 추가했다. 둘 다 캐시에 담지 않고 Facade가 매 조회 조합한다(§2.4). 두 순위의 조합으로 상승·하락·신규진입·이탈이 판별된다(§2.5).
