# 04 · Circuit Breaker — "계속 실패하는 PG는 이제 그만 두드린다"

> **목적**: Stage 4(Resilience4j CircuitBreaker)가 Stage 2의 마지막 결함 — *동기 호출이라 타임아웃을 걸어도 스레드 점유 자체는 사라지지 않아 자원 고갈이 "해소"가 아니라 "완화"에 그쳤던 것* — 을 **원천 차단**하는지 런타임으로 검증한다. 부하 중 `CLOSED→OPEN` 전이가 일어나고, OPEN 동안 PG를 호출하지 않아(=스레드를 점유하지 않아) 결제와 무관한 요청까지 보호되는지를, 서킷 메트릭(`resilience4j_circuitbreaker_*`)과 함께 본다.

---

## 1. 한 줄 결론

**유효 실패율 ≈80%인 PG에 부하를 걸자 서킷이 `t+22s`에 `CLOSED→OPEN`으로 전이했고(failure_rate 80.95% > 임계 50%), 이후 결제 2001건 중 1932건(96.5%)을 PG에 보내지 않고 즉시 단락(PENDING 흡수)했다.** 톰캣 10스레드 환경(baseline·timeout과 동일 하니스)에서도 결제와 무관한 prober는 **0% 실패 · load p50 5.92ms**로 살아남았다 — Stage 2가 "완화"에 그쳤던 스레드 고갈을 서킷이 **원천 차단**했다. 내부 응답은 100% 201, `payment_500`은 baseline 39.4%에서 **0%**를 유지했다.

---

## 2. 검증 구성

### 2.1 적용한 변경 (Stage 3 → Stage 4)

```
POST /api/v1/payments → PaymentFacade.createPayment
   1) acceptPayment(...)            ─ PENDING 저장(즉시 커밋)
   2) paymentGateway.requestPayment ─ @CircuitBreaker(name="pg-simulator", fallbackMethod)
        ├─ CLOSED: PG 호출 → ack / 타임아웃 / 5xx (Stage 3 분기 그대로)
        └─ OPEN:   PG 미호출 → CallNotPermittedException → fallback → unknown() → PENDING
   3) applyRequestResult(result) → save
```

- **서킷 설정(역산값)**: `COUNT_BASED`(window 50, min-calls 20) · **failure-rate 50%** · slow-call 250ms/50% · wait-in-open 10s · half-open 5.
- **record / ignore**: record = `RetryableException`(타임아웃/네트워크) + `FeignServerException`(5xx) / ignore = `FeignClientException`(4xx, 의도된 거절 → 집계 제외).
- **fallback 최외곽**: `@CircuitBreaker`가 유일한 resilience 어노테이션이라 fallback이 최외곽. aspect-order `circuit-breaker:2 > retry:1`은 Stage 5 Retry 대비 prep.

### 2.2 임계치 역산 (PG 사양 코드에서)

`pg-simulator`의 접수(`PaymentApi`)는 **`sleep(100~500ms)` 균등 + 40% 확률 500**. Feign read-timeout 300ms 기준으로:

| 즉시 결과 | 확률(역산) | 서킷 집계 |
|---|---|---|
| 타임아웃(`P(delay>300ms)=200/400`) | **50%** | record(`RetryableException`) |
| 완료분 중 5xx(`50%×40%`) | **20%** | record(`FeignServerException`) |
| 완료분 중 성공(`50%×60%`) | 30% | success |

→ CB가 보는 **유효 실패율 ≈70%**. 임계 50%를 여유 있게 상회 → 부하 중 확실히 OPEN. (정상 PG라면 95%+ 성공이라 같은 50% 임계로도 안 열린다 — 이 시뮬레이터는 *정상 상태가 곧 70~80% 실패*인 만성 불량 PG라 서킷이 열리는 게 정상 동작이다.)

### 2.3 환경

| 구성 | 값 |
|---|---|
| commerce-api 톰캣 max-threads | **10**(`SERVER_TOMCAT_THREADS_MAX=10` — 01/02 baseline과 동일, 스레드 보호 비교용) |
| Feign read-timeout | 300ms |
| k6 부하 | `stage2-circuit.js` — prober 5/s(75s) + payment 40/s(10~60s 구간) |
| 시드 | `seed.sh` — 유저 `looptester` + CREATED 주문 3000건 |

---

## 3. 결과

### 3.1 서킷 상태 타임라인 (`/actuator/prometheus` 2초 폴링)

```
t+10s        payment 부하 시작 (이전엔 prober 만 → 서킷 CLOSED 유지)
t+10~30s     CLOSED  — 10스레드가 PG(100~500ms)에 묶여 window(min 20건)가 더디게 참
t+32s        ┌ CLOSED→OPEN  failure_rate 80.95% > 50%   ← 전이!
t+32~80s     │ OPEN 유지, wait-in-open 10s 마다 half_open 프로브 5건
t+42/52/62s  │   half_open → 프로브 전부 재실패 → 즉시 재OPEN (≈4 회 OPEN 에피소드)
t+80s        └ 부하 종료
```

- **`not_permitted_calls` 누적 0 → 1932**: OPEN 전이 직후(t+34s)부터 단조 증가. 각 1 = **PG를 부르지 않고 단락한 결제 요청**.
- 관찰 `failure_rate` 80.95%는 역산(≈70%)보다 약간 높다 — 10스레드 큐잉으로 PG 호출이 더 자주 300ms를 넘겨 타임아웃 비중이 커졌기 때문. **역산이 임계 설정의 올바른 출발점이었음**을 실측이 확인.

### 3.2 PG 도달 vs 단락 — 서킷이 실제로 막았는가

| 구분 | 건수 | 메트릭 |
|---|---|---|
| **PG 미호출(단락)** | **1932** | `not_permitted_calls_total` |
| PG 도달 — 실패(record) | 52 | `calls_seconds_count{kind="failed"}` (타임아웃 41 + 5xx 11) |
| PG 도달 — 성공 | 17 | `calls_seconds_count{kind="successful"}` |
| PG 도달 — ignored(4xx) | 0 | `calls_seconds_count{kind="ignored"}` (pg-simulator는 4xx 미발생) |
| **합계** | **2001** | = 전체 결제 요청 수 |

→ **결제의 96.5%(1932/2001)가 PG를 건드리지도 않고 즉시 PENDING으로 흡수**됐다. PG에 닿은 건 OPEN 전(닫힘 구간 ~22s)과 half_open 프로브뿐인 **69건**.

### 3.3 DB 그라운드 트루스 (2001건) — 산술 정합

| status | transaction_key | 건수 | 분기 해석 |
|---|---|---|---|
| `PENDING` | NULL | **1973** | **UNKNOWN** = 단락 1932 + 타임아웃 41 → 결과 불명 흡수 |
| `FAILED` | NULL | **11** | **REJECTED** = PG 5xx 접수 거절 |
| `SUCCESS` | present | 13 | ACCEPTED(17) → 콜백 승인 |
| `FAILED` | present | 4 | ACCEPTED(17) → 콜백 비즈니스 거절(한도/카드) |

UNKNOWN 1973 = (단락 1932 + 타임아웃 41) ✓ · ACCEPTED 17 = (13 + 4) ✓ · REJECTED 11 = (PG 도달 실패 52 − 타임아웃 41) ✓. **메트릭과 DB가 완전히 맞물린다.**

### 3.4 자원 회수 — 무관 요청(prober) 보호

| 지표 | **baseline(Stage 1)** | **Stage 2(timeout)** | **Stage 4(circuit)** |
|---|---|---|---|
| prober load p50 | 18ms→**1.62s**(50VU) | 1.62s→1.44s (완화) | **5.92ms** |
| prober load p95 / p99 | — | — | 176ms / 373ms |
| prober 실패율 | (붕괴) | — | **0.00%** (0/375) |
| `payment_500` | **39.4%** | 73%대 비2xx | **0.00%** (0/2001) |
| `payment_fail`(비2xx) | 39.4% | 73% | **0.00%** |

- prober load **p50 5.92ms** — 부하 중에도 중앙값은 사실상 무부하. p95/p99(176/373ms)의 꼬리는 **OPEN 전 닫힘 구간(t+10~32s)**에 10스레드가 PG에 묶였던 순간의 잔재이고, 서킷이 열린 뒤로는 스레드가 회수돼 prober가 즉시 통과했다.
- *부하 형태 주의*: baseline/timeout은 `ramping-vus`(닫힌 루프), 본 측정은 `constant-arrival-rate 40/s`라 prober 숫자의 1:1 비교는 아니다. 그러나 **"같은 10스레드에서 PG가 느려도 prober가 안 무너진다"**는 질적 전환은 분명하다.

---

## 4. 해석 — 검증이 말해주는 것

1. **고갈의 "원천 차단".** Stage 2의 타임아웃은 *물을 300ms 만에 포기*했지만 그동안 스레드는 점유됐다("완화"). 서킷은 OPEN 동안 **호출 자체를 안 해** 스레드를 즉시 돌려준다 — prober p50가 6ms로 산 것이 그 증거다. (체크리스트: *서킷으로 장애 확산 방지*)
2. **OPEN = "PG를 부르지 않는다"가 메트릭으로 증명된다.** `not_permitted_calls=1932`는 추정이 아니라 라이브러리가 센 **단락 횟수**다. PG 도달 69건 + 단락 1932건 = 2001건으로 닫힌다.
3. **역산이 임계 설정의 출발점으로 유효했다.** 사양에서 뽑은 ≈70%가 실측 80.95%로 근사했고, 임계 50%는 이 분포를 여유 있게 갈라 결정적으로 열렸다. 절대값을 맹신하지 않되, 역산이 "어디서 시작할지"를 정확히 줬다.
4. **결과 불명은 끝까지 PENDING으로 정직하다.** 단락된 1932건은 "PG가 받았는지 우리가 모른다"는 미결 상태로 남는다(자동 실패 단정 없음) → **Stage 6 폴링**이 정합성을 복구할 대상.

---

## 5. 한계 / 정직한 메모

- **`CLOSED→OPEN`에 22s가 걸렸다.** COUNT_BASED(min 20건)인데 10스레드가 PG에 묶여 *완료* 호출이 초당 ~3건뿐이라 window가 더디게 찼다. 스레드가 넉넉하면(고스루풋) 1~2s면 열린다. → **저스루풋·롱 의존지연 환경에선 TIME_BASED나 더 작은 min-calls가 OPEN을 앞당긴다**(민감도 후속 과제).
- **결제 단건의 HTTP 지연으로는 단락(~1ms)이 안 보인다.** `payment_open_ms` p50가 108ms인 건, 단락은 즉시여도 10스레드가 포화된 동안 요청이 **톰캣 큐**에서 대기하기 때문. 단락의 진짜 증거는 HTTP 지연이 아니라 `not_permitted_calls`(1932)와 prober 보호다. (스레드가 넉넉했다면 결제 지연으로도 드러났을 것 — 트레이드오프.)
- **slow-call(250ms)은 대체로 중복.** read-timeout 300ms 하드컷이 있어 느린 호출 대부분은 이미 타임아웃 예외로 record되고, slow-call은 `[250,300ms]` 얇은 밴드(`slow_call_rate` 40%)만 추가로 잡는다. 타임아웃이 느슨할 때 진가를 발휘하는 장치라, 본 구성에선 보조적이다.
- **request-time 4xx는 여전히 0건(ignored=0).** pg-simulator가 접수에서 4xx를 안 내므로 `FeignClientException` ignore 경로는 데드 패스다. 분류(`status>=400`→REJECTED)와 ignore 설정은 정합하게 넣었으나 실측 발화는 없다.
- **"오픈 횟수" 전용 카운터는 없다.** prometheus엔 상태 게이지(`_state`)와 단락 수(`_not_permitted_calls`)만 노출된다. OPEN 에피소드 수(≈4회)는 타임라인의 half_open→open 재전이로 센다.

---

## 6. 재현 방법

```bash
# 0) 인프라(MySQL/Redis) + pg-simulator(8082) + commerce-api(8080, 톰캣 10스레드)
docker-compose -f ./docker/infra-compose.yml up -d
SPRING_PROFILES_ACTIVE=local ./gradlew :apps:pg-simulator:bootRun &
SERVER_TOMCAT_THREADS_MAX=10 SPRING_PROFILES_ACTIVE=local ./gradlew :apps:commerce-api:bootRun &

# 1) 시드 (유저 looptester + 주문 3000)
bash docs/volume-6/measurement/k6/seed.sh

# 2) 서킷 상태 폴링(2초 간격, 백그라운드) + k6 부하
M=http://localhost:8081/actuator/prometheus
( for i in $(seq 0 43); do
    curl -s "$M" | grep -E 'circuitbreaker_state\{.*\} 1\.0|not_permitted_calls_total\{|failure_rate\{'
    echo "---"; sleep 2
  done ) > /tmp/cb-timeline.txt &
BASE_URL=http://localhost:8080 k6 run docs/volume-6/measurement/k6/stage2-circuit.js

# 3) 단락 vs 도달 + DB 그라운드 트루스
curl -s "$M" | grep -E 'not_permitted_calls_total|calls_seconds_count'
docker exec docker-mysql-1 mysql -uroot -proot -e "
  SELECT status, SUM(transaction_key IS NOT NULL) txkey_present,
         SUM(transaction_key IS NULL) txkey_null, COUNT(*) total
  FROM loopers.payments GROUP BY status ORDER BY status;"
```
