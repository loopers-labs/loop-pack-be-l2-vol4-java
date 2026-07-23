# 📝 Round 10 Quests

---

## 💻 Implementation Quest

> 이번에는 Spring Batch 를 활용해 주간, 월간 랭킹을 제공해 볼 거예요.
이전에 적재했던 `product_metrics` 와 같은 일간 집계정보를 기반으로 **주간, 월간 랭킹 시스템을 구축**해봅니다.
> 

<aside>
🎯

**Must-Have (이번 주에 무조건 가져가야 좋을 것-**무조건 ****하세요**)**

- Spring Batch
- Batch Processing
- Materialized View (Statistics)
</aside>

### 📋 과제 정보

이번 주는 대규모 데이터 집계 및 조회 전용 구조에 대한 설계를 진행해 봅니다.

### (1) Spring Batch Job 구현

- 하루치 메트릭 테이블을 읽어 데이터를 집계하고 처리해봅니다.
    - 대상 테이블 : `product_metrics`
    - Chunk-Oriented 방식을 통해 대량의 데이터를 읽고 처리할 수 있도록 구성해 보세요.

### (2) Materialized View 설계

- 집계 결과를 조회 전용 테이블 (MV) 로 저장합니다.
    - `mv_product_rank_weekly` : 주간 TOP 100 랭킹
    - `mv_product_rank_monthly` : 월간 TOP 100 랭킹

### (3) Ranking API 확장

- 기존 Ranking 을 제공하는 GET `/api/v1/rankings?date=yyyyMMdd&size=20&page=1` 에서 기간 정보를 전달받아 API 로 일간, 주간, 월간 랭킹을 제공할 수 있도록 개선합니다.

---

## ✅ Checklist

### 🧱 Spring Batch

- [ ]  Spring Batch Job 을 작성하고, 파라미터 기반으로 동작시킬 수 있다.
- [ ]  Chunk Oriented Processing (Reader/Processor/Writer or Tasklet) 기반의 배치 처리를 구현했다.
- [ ]  집계 결과를 저장할 Materialized View 의 구조를 설계하고 올바르게 적재했다.

### 🧩 Ranking API

- [ ]  API 가 일간, 주간, 월간 랭킹을 제공하며 조회해야 하는 형태에 따라 적절한 데이터를 기반으로 랭킹을 제공한다.

---


**주제 추천**

- 단순히 “무엇을 했다”가 아니라, **10주 동안 어떻게 성장했는지**를 돌아본다.
- “기능 구현” 중심이 아니라, **사고방식/문제 해결/설계 선택 과정** 중심으로 기록한다.
- 이 글은 **개인 포트폴리오**이자, 앞으로 학습 방향을 스스로 점검하는 기준점이 된다.

### 담으면 좋은 내용

1. **전체 여정 요약**
    - 1~10주차 동안 다뤘던 주요 테마 및 문제점들을 간단히 돌아보기
    - 단순 나열이 아니라, **흐름이 어떻게 연결되었는지** 를 강조
2. **가장 큰 전환점**
    - **내 기존의 사고방식이 바뀌었다** 싶은 순간
    - *예: 4주차 트랜잭션/락을 통해 단순 @Transactional 이상의 고민을 알게 된 점, 7주차 이벤트 분리를 통해 ‘확장성’에 눈을 뜬 경험*
3. **나의 Trade-off 판단**
    - 실습 중 내가 내린 중요한 선택 1~2개
    - 왜 그 선택을 했고, 대안은 뭐였는지, 지금 다시 한다면 어떻게 할 건지
4. **실전과의 연결**
    - “이건 실제 회사/서비스에서 써먹을 수 있겠다” 싶은 포인트
    - *예: 캐시 무효화 전략, Kafka 기반 집계, Resilience4j 설정 등*