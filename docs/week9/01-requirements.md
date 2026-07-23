# 01. 요구사항 명세 — Redis ZSET 실시간 랭킹 시스템

## 0. 이 문서의 역할

week7에서 구축한 이벤트/Kafka 파이프라인(`product_metrics` CQRS 집계) 위에, **Redis Sorted Set(ZSET)** 을 이용한 **실시간 상품 랭킹**을 추가한다. 이전까지 카프카 컨슘으로 `product_metrics` 테이블에 적재하던 집계 이벤트를, 같은 스트림에서 **또 하나의 소비 경로**로 분기해 일간 랭킹 ZSET에 반영하고, 이를 바탕으로 **오늘의 인기상품 API**를 제공한다.

이 문서는 요구사항·정책만 명세한다. 후속 설계는 분리한다.
- 시스템 행동 흐름 → [`02-sequence-diagrams.md`](./02-sequence-diagrams.md)
- 컴포넌트/클래스 모델 → [`03-class-diagram.md`](./03-class-diagram.md)
- Redis ZSET 키·스코어 모델 → [`04-redis-model.md`](./04-redis-model.md)
- 구현 노트(트레이드오프) → [`05-implementation-notes.md`](./05-implementation-notes.md)

기존 도메인은 [`../week2`](../week2/01-requirements.md)~[`../week7`](../week7/01-requirements.md)를 따른다. 이 문서는 그 위에 추가되는 부분(랭킹 적재/조회)만 명세한다.

> **기존 자산과의 관계**: 랭킹은 **읽기 전용 파생 뷰**다. 이벤트 발행(commerce-api outbox)·전송(Kafka)·`product_metrics` 집계(commerce-streamer `metrics-aggregator`)는 week7 구조를 **그대로 재사용**하고, 이번 주차는 같은 토픽을 소비하는 **별도 컨슈머 그룹**과 그 결과를 노출하는 API만 추가한다.

---

## 1. 개요

`product_metrics`는 상품별 좋아요/조회/판매 **누적 측정값**을 담지만, "지금 뜨는 상품"을 답하기엔 부적합하다. 누적값은 오래된 인기 상품이 상위를 고착하고, 정렬·상위 N 조회를 매 요청 DB에 부담시킨다.

week9의 목표는 **시간 감쇠(일간 버킷) + 가중 신호(조회·좋아요·주문) 합산**을 Redis ZSET으로 실시간 유지하고, `ZREVRANGE`로 상위 N을 O(log N + M)에 조회하는 것이다.

1. **Kafka Consumer → ZSET 적재** — 조회/좋아요/주문 이벤트를 컨슘해 일간 키(`ranking:all:{yyyyMMdd}`) ZSET에 이벤트별 가중 점수를 누적한다.
2. **Ranking API** — 일간 랭킹을 페이지 단위로 조회(`GET /api/v1/rankings`)하고, 상품 상세 조회 시 해당 상품의 **오늘 순위**를 함께 노출한다.

---

## 2. 범위 (Scope)

### In scope (Must-Have)

- **Redis ZSET 랭킹 적재**
  - `PRODUCT_VIEWED` / `LIKE_CHANGED` / `ORDER_PAID` 이벤트를 컨슘해 일간 ZSET에 가중 점수 누적.
  - 이벤트별 Weight/Score 설계 및 매출 정규화(log).
  - ZSET 스펙: `KEY = ranking:all:{yyyyMMdd}`, `TTL = 2 Day`.
- **Ranking API**
  - 랭킹 페이지 조회: `GET /api/v1/rankings?date=yyyyMMdd&size=20&page=1`.
  - 상품 상세 조회 시 해당 상품의 랭킹(순위) 정보 추가.
- **과거 랭킹 스냅샷** (2026-07-17 추가)
  - 확정된 하루치 상위 N을 배치로 `ranking_daily_snapshot`에 보존해 TTL(2일) 이후에도 조회 가능하게 한다.
  - 조회는 날짜에 따라 ZSET(오늘·어제) / 스냅샷(그 이전)을 자동 선택한다.

### Out of scope (Nice-to-Have — 이번 주차 미포함)

- **초 실시간(시간 단위) 랭킹** — 시간 버킷 키(`ranking:all:{yyyyMMddHH}`) 및 슬라이딩 윈도우.
- **콜드 스타트 문제 해결** — 신규/무이력 상품 노출 보정, 부트 시 `product_metrics`로 ZSET 워밍업.
- **카프카 배치 리스너 정제/스루풋 튜닝** — 배치 리스너 자체는 재사용하되, 랭킹 전용 스루풋 최적화는 후속.

---

## 3. 스코어링 정책 (핵심 설계)

이벤트별 **최종 가산치 = Weight × Score** (과제 예시 기준). 상세 계산식은 [`04-redis-model.md`](./04-redis-model.md) §3.

| 이벤트 | Weight | Score | 최종 가산치 |
| --- | --- | --- | --- |
| 조회(`PRODUCT_VIEWED`) | 0.1 | 1 | **+0.1** |
| 좋아요(`LIKE_CHANGED`) | 0.2 | delta(±1) | **±0.2** (취소는 감점) |
| 주문(`ORDER_PAID`) | 0.6 | log10(1 + 단가×수량) | **+0.6 × log10(1+매출)** |

**설계 결정**

- **D1. 매출 log 정규화** — `price×amount`는 값 폭이 매우 커(수백~수백만) 그대로 쓰면 고가 상품 1건이 조회/좋아요 수천 건을 압도한다. `log10(1+매출)`로 상한을 눌러 세 신호의 스케일을 맞춘다.
- **D2. 좋아요 취소 = 감점** — `LIKE_CHANGED`의 delta(±1)를 그대로 곱해, 좋아요 취소 시 스코어가 내려가 랭킹이 자연스럽게 하락한다.
- **D3. 일자 버킷 = 이벤트 발생시각 기준** — 컨슘 시각이 아니라 이벤트 `occurredAt`(KST 환산)으로 날짜 키를 정해, 자정 근처 이벤트도 올바른 날짜 버킷에 들어간다.
- **D4. 가중치는 튜닝 대상** — 현재 코드 상수. 신호 비중은 실험으로 조정할 수 있으며, 추후 `application.yml` 외부화 가능.

---

## 4. 기능 요구사항 (FR)

- **FR-1** 조회/좋아요/주문 이벤트를 컨슘해 해당 일자 ZSET에 가중 점수를 누적한다.
- **FR-2** 좋아요 취소는 스코어를 감점한다(음수 delta 반영).
- **FR-3** 일간 ZSET은 생성 후 최소 2일 뒤 만료된다(오늘/어제 조회 가능).
- **FR-4** `GET /api/v1/rankings`로 지정 일자 랭킹의 한 페이지(내림차순)를 조회한다. `date` 생략 시 오늘(KST), `page`는 1-based.
- **FR-5** 랭킹 항목은 순위·상품 요약(이름/가격/브랜드/좋아요수)·스코어를 포함한다.
- **FR-6** 상품 상세(`GET /api/v1/products/{id}/detail`)는 해당 상품의 **오늘 순위**(`rank`)와 **어제 순위**(`rankYesterday`)를 포함한다. 각각 해당 일자 랭킹에 없으면 null.
- **FR-7** 상품 상세의 두 순위로 **추세**(상승·하락·신규진입·이탈)를 판별할 수 있어야 한다. 서버는 델타로 압축하지 않고 원본 순위 둘을 준다 — 한쪽이 null인 경우(신규진입/이탈)가 의미를 갖고 델타로는 표현되지 않기 때문. 해석은 클라이언트 몫.
- **FR-8** 확정된 하루치 랭킹의 상위 N을 배치가 영속 스냅샷에 보존한다. 재실행해도 결과가 같아야 한다(멱등).
- **FR-9** `GET /api/v1/rankings`는 TTL이 지난 과거 일자도 스냅샷에서 조회할 수 있다. **한 날짜의 결과는 반드시 한 소스에서만 온다**(ZSET/스냅샷 혼합 금지 — [`05-implementation-notes.md`](./05-implementation-notes.md) §2.6).

## 5. 비기능 요구사항 (NFR)

- **NFR-1 실시간성** — 이벤트 소비~ZSET 반영은 카프카 파이프라인 지연 내(초 단위). 랭킹은 **근사값**을 허용한다.
- **NFR-2 파이프라인 분리** — 랭킹 적재는 `product_metrics` 집계와 **독립 컨슈머 그룹**으로, 서로의 오프셋/장애에 영향을 주지 않는다.
- **NFR-3 조회 성능** — 상위 N 조회는 `ZREVRANGE`로 O(log N + M). 랭킹 페이지 조립 시 상품/브랜드/좋아요수는 **배치 조회로 N+1 회피**. 스냅샷 조회에 ZSET 대비 왕복이 늘어서는 안 된다(정상 경로 비용 불변).
- **NFR-4 저장 비용 한계** — 실시간 랭킹(ZSET)은 일간 키 TTL 2일로 과거 데이터를 자동 회수한다.
  > **스냅샷은 이 원칙에서 벗어난다.** 과거 조회(FR-9)를 위해 보존하는 이상 자동 회수와 양립할 수 없어, **계층을 나눠** 푼다 — 실시간 계층(ZSET)은 2일로 회수하고, 보존 계층(DB)은 상위 N만 남긴다. 다만 **보존 계층엔 TTL이 없어 자동으로 줄지 않으므로 보관 정책이 필요하다**(미정 — `migration_ranking_snapshot.sql`에 삭제 쿼리만 적어둠).

---

## 6. 정책 결정 & Open Questions

- **P-1 멱등성** — `ZINCRBY`는 그 자체로 멱등이 아니므로, `event_handled`를 `ranking-aggregator` 그룹으로 재사용해 그룹별 1회 처리를 보장한다(`metrics-aggregator`와 동일 패턴). 중복은 예외적 상황이 아니다 — 하이브리드 Outbox(즉시 발행 + 릴레이 폴링)의 경합으로 같은 이벤트가 두 번 발행되며, E2E에서 13건 중 1건으로 관측됐다. Redis와 DB를 한 트랜잭션으로 묶을 수는 없어 "Redis 반영 → 같은 트랜잭션에서 마킹" 순서를 택했고, 마킹 커밋 전 장애 시 그 배치만 이중 가산되는 잔여 창이 남는다(유실 없음). 이 잔여 오차는 근사 허용 + 2일 TTL 리셋으로 수용한다 — 상세는 [`05-implementation-notes.md`](./05-implementation-notes.md) §2.1.
- **P-2 삭제/비활성 상품** — ZSET엔 남을 수 있으나, API 조립 시 활성 상품만 조회되어 랭킹에서 제외된다(노출 필터).
- **OQ-1 (Nice-to-Have)** 시간 단위 랭킹의 윈도우 병합(최근 N시간 합산) 전략.
- **OQ-2 (Nice-to-Have)** 콜드 스타트 — 부팅 시 `product_metrics` 스냅샷으로 ZSET 워밍업 여부.
