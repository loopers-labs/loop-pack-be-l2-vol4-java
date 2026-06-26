# 03 · Fallback — "외부 장애를 결제 실패로 단정하지 않는다"

> **목적**: Stage 3(Resilience4j fallback + 실패 종류 분기)가 Stage 2의 마지막 결함 — *PG 실패가 502로 사용자에게 직격되고, 결과 불명과 확정 실패가 구분되지 않던 것* — 을 실제로 걷어내는지 **런타임으로 검증**한다. baseline·timeout과 동일한 측정 하니스(`seed.sh` + `stage1-baseline.js`)를 그대로 쓴다.

---

## 1. 한 줄 결론

**Stage 2에서 결제 요청의 39.4%까지 치솟던 5xx 직격(`payment_500`)이 Stage 3에서 0.00%로 사라졌다.** PG가 느리거나(타임아웃) 죽어도(5xx) 사용자에겐 502가 아니라 **201**이 나가고, 결제 *상태*만 `PENDING`(결과 불명) / `FAILED`(확정 거절)로 갈린다. 어댑터의 단일 502 변환을 `PaymentRequestResult`(ACCEPTED/UNKNOWN/REJECTED) 3분기로 교체한 결과이며, DB 그라운드 트루스가 세 분기 + 콜백 종결까지 정확히 보여준다.

---

## 2. 검증 구성

### 2.1 적용한 변경 (Stage 2 → Stage 3)

```
POST /api/v1/payments → PaymentFacade.createPayment
   1) acceptPayment(...)              ─ PENDING 저장(즉시 커밋)
   2) paymentGateway.requestPayment   ─ @CircuitBreaker(name="pg-simulator", fallbackMethod)
        ├─ 정상 ack ............ PaymentRequestResult.accepted(txKey)  → PENDING + txKey 기록
        └─ fallback 진입(어댑터에서 예외 분류)
             ├─ RetryableException(타임아웃/네트워크) → unknown()   → PENDING 유지(txKey 없음)
             └─ FeignException status≥500 ............ rejected()  → FAILED 확정
   3) applyRequestResult(result) → save               (Facade는 결과를 엔티티에 위임만)
```

- **이전(Stage 2)**: 모든 `FeignException` → `CoreException(PAYMENT_GATEWAY_ERROR, 502)` 단일 변환 → 사용자 502.
- **이후(Stage 3)**: 어댑터가 *결과 불명*과 *확정 실패*를 갈라 흡수 → 사용자는 항상 201, 상태값만 분기.

### 2.2 환경 (baseline/timeout과 동일 하니스)

| 구성 | 값 |
|---|---|
| commerce-api 톰캣 max-threads | **200**(기본 — 이번 검증은 스레드 고갈 재현이 아니라 **응답 정합성** 검증이 목적) |
| Feign read-timeout | **300ms**(Stage 2 확정값 유지) |
| pg-simulator 접수 | `Thread.sleep(100~500ms)` + **40% 확률 500**(변경 없음) |
| pg-simulator 처리(콜백) | 1~5s 후 70% 승인 / 20% 한도초과 / 10% 잘못된카드 |
| 시드 | `seed.sh` — 유저 `looptester` + CREATED 주문 3000건 |

> Stage 4(서킷)의 부하 그림과 달리, 여기서는 톰캣 스레드를 줄이지 않는다. Stage 3의 claim은 "스레드가 산다"가 아니라 **"응답이 의도대로 분기한다"** 이기 때문이다.

---

## 3. 결과

### 3.1 응답 본문 분기 — 결제 40건 순차 관찰

전부 `201`. **502는 0건.** `status`는 PENDING/FAILED 혼재(접수 성공분은 곧 콜백으로 종결되어 즉시 응답은 PENDING).

### 3.2 DB 그라운드 트루스 — 상태 × 거래키 분포 (35건)

| status | transaction_key | 건수 | 분기 해석 |
|---|---|---|---|
| `PENDING` | NULL | **23** | **UNKNOWN** — 접수 지연 >300ms 타임아웃 → 결과 불명 흡수 |
| `FAILED` | NULL | **9** | **REJECTED** — PG 5xx 접수 거절 → 찌꺼기 없는 확정 실패 |
| `SUCCESS` | present | 5 | ACCEPTED(접수 성공) → 콜백 승인(70%) |
| `FAILED` | present | 3 | ACCEPTED → 콜백 비즈니스 거절(한도/카드, 30%) |

→ **즉시 응답 분포**로 환산하면 ACCEPTED 8 / UNKNOWN 23 / REJECTED 9.
PG 사양 역산과 일치한다: 접수 지연 100~500ms 균등 → `P(≤300ms)≈50%`. 그중 60% 성공(ACCEPTED ~30%) · 40% 500(REJECTED ~20%), 나머지 50%는 타임아웃(UNKNOWN). 관찰값(20% / 22% / 58%)이 소표본 오차 내에서 부합.

### 3.3 집계 교차검증 — k6 90s 부하 (`stage1-baseline.js`)

| 지표 | **baseline(Stage 1)** | **Stage 3** |
|---|---|---|
| `payment_500`(5xx 직격) | **39.4%** | **0.00%** (0 / 2286) |
| `payment_fail`(비2xx) | 39.4% | **1.74%** (40건) |
| `prober_fail` | — | 0.00% |

- **`payment_500` 39.4% → 0%**: PG의 40% 접수 실패가 더는 사용자 5xx로 새지 않고 전부 201로 흡수됨.
- **`payment_fail` 1.74%(정확히 40건)의 정체**: 게이트웨이 실패가 아니라, §3.1에서 이미 결제한 주문 1~40번을 k6가 다시 건드려 받은 **409 Conflict**(k6가 orderId 1부터 소비). 게이트웨이 기인 실패는 0.

---

## 4. 해석 — 검증이 말해주는 것

1. **"내부는 정상 응답"의 실증.** 외부가 40% 확률로 죽고 절반이 타임아웃인 상황에서도 사용자는 100% 201을 받는다. 502 직격(Stage 2의 마지막 결함)이 코드뿐 아니라 런타임에서도 제거됐다. (체크리스트: *외부 장애 시 내부 정상 응답*)
2. **결과 불명과 확정 실패의 분리가 데이터로 보인다.** `txkey=NULL`이면서 `PENDING`(23건)은 "PG가 처리했을 수도 있다"는 정직한 미결 상태이고, `txkey=NULL`이면서 `FAILED`(9건)는 "거래키 발급 전 5xx, 찌꺼기 없음"의 확정 실패다. 전자는 **Stage 6 폴링**이, 후자는 그 자리에서 종결된다.
3. **콜백 경로까지 동반 회귀.** ACCEPTED 8건이 콜백으로 SUCCESS 5 / FAILED 3으로 종결된 것은 Stage 0의 콜백·주문 전이가 Stage 3 변경 후에도 살아있음을 보여준다.

---

## 5. 한계 / 정직한 메모

- **request-time 4xx는 0건.** pg-simulator 접수는 500만 던지므로, 어댑터의 `status≥500` 분류에서 4xx가 UNKNOWN으로 흡수되는 **빈틈은 현재 데드 패스**다(실측으로도 4xx 미발생 확인). 4xx를 REJECTED로 가르는 정리는 **Stage 4**의 record/ignore와 한 묶음으로 이월했다.
- **스레드 고갈/서킷 OPEN은 이 리포트의 범위 밖.** 톰캣 200으로 돌렸으므로 자원 고갈 곡선은 그리지 않는다 — 그건 Stage 4(`reports/04-circuit.md`)에서 다룬다.
- **느슨한 서킷.** 이번 CB는 `failure-rate 90%`의 placeholder라 부하 중 열리지 않았다(fallback은 호출 자체의 예외로 동작). 본격 임계치 역산·민감도는 Stage 4.

---

## 6. 재현 방법

```bash
# 0) 인프라(MySQL/Redis) + pg-simulator(8082) + commerce-api(8080)
docker-compose -f ./docker/infra-compose.yml up -d
SPRING_PROFILES_ACTIVE=local ./gradlew :apps:pg-simulator:bootRun &
SPRING_PROFILES_ACTIVE=local ./gradlew :apps:commerce-api:bootRun &

# 1) 시드 (유저 looptester + 주문 3000)
bash docs/volume-6/measurement/k6/seed.sh

# 2) 응답 본문 분기 관찰 — 결제 N건 순차 요청 (status/txKey 확인)
#    이후 DB 집계로 그라운드 트루스 확인:
docker exec docker-mysql-1 mysql -uroot -proot -e "
  SELECT status,
         SUM(transaction_key IS NOT NULL) AS txkey_present,
         SUM(transaction_key IS NULL)     AS txkey_null,
         COUNT(*) AS total
  FROM loopers.payments GROUP BY status ORDER BY status;"

# 3) 집계 교차검증 — payment_500 이 0% 인지
BASE_URL=http://localhost:8080 k6 run docs/volume-6/measurement/k6/stage1-baseline.js
```
