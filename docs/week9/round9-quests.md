# 📖 Round 9 Quests

- **Calendar**: 🧭 Round.9
- **Tag**: `round9` `task` `BE_L2`

---

## 📝 Round 9 Quests

### 💻 Implementation Quest

> 이번에는 Redis ZSET 을 이용해 랭킹 시스템을 만들어 볼 거예요. 이전에 **Kafka Consumer** 를 통해 적재하던 집계정보를 기반으로 실시간 랭킹 파이프라인을 구축해봅니다.

### 🎯 Must-Have (이번 주에 무조건 가져가야 좋을 것 - 무조건 하세요)

- Redis ZSET
- Realtime Ranking
- Ranking API

### Nice-To-Have (부가적으로 가져가면 좋을 것 - 시간이 허락하면 꼭 해보세요)

- 초 실시간 (시간 단위) 랭킹 만들기
- 콜드 스타트 문제 해결

---

## 📋 과제 정보

이전 주차에서 만들었던 `product_metrics` 테이블을 응용해 카프카 컨슈머에서 실시간 랭킹 집계를 시작합니다. 또한 이 랭킹 정보를 바탕으로 오늘의 인기상품 API 를 만들어 봅니다.

- **(Nice to Have) 카프카 배치 리스너**
  - 메세지 단건 처리는 너무 많은 ZSET 연산, DB 연산을 동반할 수 있으므로 배치 리스너를 이용해 애플리케이션에서 정제하고, 스루풋을 높이기

```
rank 조회          rank 적재
commerce-api → Redis ← commerce-streamer
     │ produce         ↑ consume
     └────────→ Kafka ─┘
```

### (1) Kafka Consumer → Redis ZSET 적재

- 조회/좋아요/주문 이벤트 등을 컨슘해 일간 키 (e.g. `ranking:all:{yyyyMMdd}`) ZSET 에 점수를 누적합니다.
- 각 이벤트에 따라 적절한 Weight 및 Score 를 고민해보고 이를 기반으로 랭킹을 반영합니다.

```
e.g.
조회 : Weight = 0.1, Score = 1
좋아요 : Weight = 0.2, Score = 1
주문 : Weight = 0.6, Score = price * amount (정규화 시에는 log 적용도 가능)
```

- **ZSET 스펙**
  - `TTL` : 2Day
  - `KEY` : `ranking:all:{yyyyMMdd}`

### (2) Ranking API 구현

- 랭킹 Page 조회
  - `GET /api/v1/rankings?date=yyyyMMdd&size=20&page=1`
- 상품 상세 조회 시 해당 상품의 랭킹 정보 추가

---

## (Additionals) 너무 쉽다구요?

- **실시간 Weight 조절**
  - 점수 계산에 사용되는 Weight 를 어떻게 수정할 수 있을지 고민해보기
- **실시간 랭킹**
  - 일간 랭킹이 아닌, 1시간 단위 랭킹을 만들어보기
- **콜드 스타트 완화를 위한 Scheduler 구현**
  - 23시 50분에 Score Carry-Over 를 통해 미리 랭킹판 생성하기

---

## ✅ Checklist

### 📈 Ranking Consumer

- [ ] 랭킹 ZSET 의 TTL, 키 전략을 적절하게 구성하였다

- [ ] 날짜별로 적재할 키를 계산하는 기능을 만들었다

- [ ] 이벤트가 발생한 후, ZSET 에 점수가 적절하게 반영된다

### ⚾ Ranking API

- [ ] 랭킹 Page 조회 시 정상적으로 랭킹 정보가 반환된다

- [ ] 랭킹 Page 조회 시 단순히 상품 ID 가 아닌 상품정보가 Aggregation 되어 제공된다

- [ ] 상품 상세 조회 시 해당 상품의 순위가 함께 반환된다 (순위에 없다면 null)

### 🧪 검증

- [ ] 이벤트 발행 → ZSET 점수 반영 → API 조회까지 E2E 흐름이 정상 동작하는지 확인

- [ ] 일자가 변경되어도 이전 날짜의 랭킹 조회가 정상적으로 동작하는지 확인

- [ ] 가중치 적용이 의도대로 랭킹 순서에 반영되는지 확인 (e.g. 주문 1건 &gt; 좋아요 3건)

---