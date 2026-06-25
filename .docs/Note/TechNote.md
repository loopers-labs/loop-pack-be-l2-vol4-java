# [Challenge Story] Resilience4j CB, 설정만 믿으면 안 된다 (6주차 · 6팀 · 변승진)

## TL;DR

Resilience4j CB를 PG 연동에 붙이면서 "설정만 하면 될 줄 알았던" 두 가지 함정을 발견했다. ① `slowCallDurationThreshold` 실험에서 PG 응답이 TimeLimiter(600ms)를 초과하는 순간 slow call이 아닌 failure로 집계되어 실험 전제 자체가 무너졌다. ② retry가 활성화된 상태에서 CB 슬라이딩 윈도우는 논리 요청이 아닌 PG 호출 단위로 채워지기 때문에, 예상보다 빠르게 CB가 열렸다. 두 현상 모두 k6로 직접 측정해 수치로 확인했다.

---

## Context (배경 및 목표)

- **어떤 시스템을 만드는가**: PG(Payment Gateway) 외부 연동. pg-simulator는 40% 확률로 즉시 에러를 반환하고, 60%는 100~500ms 후 성공한다. PG가 불안정할 때 그 장애가 commerce-api 전체로 번지지 않도록 Resilience4j CB + TimeLimiter + Retry를 조합해 붙였다.

- **가장 큰 기술적 도전 과제**: CB가 "설정대로" 동작하는지 확인하는 것. 설정값을 바꿔가며 실험하는 과정에서 예상과 다른 동작이 두 차례 발견됐다. 두 경우 모두 원인을 모르면 CB가 올바르게 동작한다고 착각하게 만드는 함정이었다.

---

## Design & Implementation (설계 및 구현)

### Resilience4j 핵심 개념 — CB가 OPEN되는 두 가지 경로

Resilience4j CB는 슬라이딩 윈도우로 최근 N건의 호출 이력을 유지하면서, 두 가지 조건 중 하나라도 임계값을 초과하면 OPEN 상태로 전환한다.

```
경로 1 — failureRate:   최근 N 호출 중 '예외가 발생한 호출'의 비율이 threshold 초과
경로 2 — slowCallRate:  최근 N 호출 중 '응답 시간이 X ms를 넘긴 호출'의 비율이 threshold 초과
```

두 경로는 서로 독립적이다. 에러가 없어도 느리기만 하면 CB가 열릴 수 있고, 느리지 않아도 에러율이 높으면 열린다.

### 최종 설정값

```yaml
resilience4j:
  circuitbreaker:
    configs:
      default:
        sliding-window-size: 20           # 최근 20건 기준으로 평가
        failure-rate-threshold: 60        # 60% 이상 실패 시 OPEN
        slow-call-duration-threshold: 100ms  # 100ms 초과 응답을 'slow call'로 간주
        slow-call-rate-threshold: 50      # slow call이 50% 이상이면 OPEN
        wait-duration-in-open-state: 10s  # OPEN 후 10초 대기 → HALF-OPEN
        permitted-number-of-calls-in-half-open-state: 5
  timelimiter:
    configs:
      default:
        timeout-duration: 600ms           # PG 응답이 600ms를 넘으면 강제 종료
```

> **왜 `instances.pg-simulator`가 아닌 `configs.default`인가**
>
> Spring Cloud OpenFeign이 CB를 자동 생성할 때 이름을 `PgPaymentClient#requestPayment(String,PaymentRequest)` 형식으로 만든다. `instances.pg-simulator` 키는 이 이름과 매칭되지 않아 설정 자체가 적용되지 않는다. `configs.default`는 이름에 관계없이 모든 CB 인스턴스의 기본값으로 적용된다.

### PG 호출 전체 흐름

```
PaymentFacade.requestPaymentWithRetry()
  └─ attempt 1: pgPaymentClient.requestPayment()  ← Resilience4j CB가 감싸고 있음
       ├─ 성공 (60%)
       │    → CB 윈도우에 'success' 1건 기록
       │
       ├─ PG 즉시 에러 (40%)
       │    → CB 윈도우에 'failure' 1건 기록
       │    → PgRetriableException → attempt 2 재시도
       │         └─ attempt 2: pgPaymentClient.requestPayment()  ← 이것도 CB 호출
       │
       └─ PG 응답이 600ms 초과
            → TimeLimiter가 TimeoutException을 던짐
            → CB 윈도우에 'failure' 1건 기록  ← slow call이 아님!
```

---

## Engineering Challenges (트러블슈팅 및 최적화)

### 함정 1 — TimeLimiter가 끊어낸 호출은 slowCallRate가 아닌 failureRate에 쌓인다

#### 예상치 못한 현상

`slowCallDurationThreshold: 100ms` 실험이 목표였다. "에러 없이 느리기만 해도 CB가 열린다"는 것을 직접 확인하고 싶었다.

실험 설계는 간단했다. pg-simulator에 slow mode(150~300ms 지연, 에러 없음)를 켜면, 모든 PG 응답이 100ms를 초과해 slow call로 집계되고, slow-call-rate-threshold(50%)를 금방 넘어 CB가 열릴 것이라고 예상했다.

결과는? CB가 열리긴 했다. 그런데 Actuator 메트릭을 보니 `slow_call`이 아니라 `failure`가 쌓이고 있었다. PG는 에러를 반환하지 않는데, 어떻게 failure가 발생하고 있는 것인가?

#### 원인 분석

문제는 TimeLimiter와 slowCallRate의 집계 채널이 다르다는 것이었다.

```
[slowCallRate로 집계되는 경우]
PG 요청 발송 ──────────────────────── 250ms 후 PG 응답 도착
                                      └─ 응답 시간 250ms > 100ms(threshold)
                                      → CB: slowCall + 1

[failureRate로 집계되는 경우]
PG 요청 발송 ─── 600ms 후 TimeLimiter 발동 ──→ PG 아직 응답 안 옴
                  └─ TimeoutException 발생
                  → CB: failure + 1  ← 'slow'가 아니라 '예외'로 분류됨
```

slow mode PG(150~300ms) + DB 오버헤드를 합산하면 전체 처리 시간이 600ms TimeLimiter를 초과하는 케이스가 생긴다. TimeLimiter가 먼저 끊어버리면 Resilience4j는 그 호출이 "느렸다"는 것을 알 방법이 없다. 응답이 오지 않은 채 예외가 발생했으므로 단순히 failure로 기록한다.

즉, slowCallRate로 열린 게 아니라 failureRate로 열린 것이었다. 실험 전제("에러 없이 느리기만 할 때")가 성립하지 않고 있었다.

#### 해결 — TimeLimiter를 실험 기간만 1000ms로 올리기

PG 응답(최대 300ms) + 오버헤드가 TimeLimiter보다 확실히 아래에 있어야 slow call이 정확히 집계된다.

```yaml
# 실험 중에만 적용, 이후 600ms로 복원
timelimiter:
  configs:
    default:
      timeout-duration: 1000ms
```

이 상태에서 k6로 재실행하자 slow call만으로 CB가 열리는 것을 확인했다.

```
[k6 스크립트: slow-call-cb-test.js — 15 req/s, 40초]

구간          응답 시간    HTTP 상태  설명
0~3초 (~20건) 380~900ms   200        CB 윈도우 채우는 중
3초 이후      300~500ms   503        slowCallRate 100% > 50% → CB OPEN
```

503 응답 시간이 300~500ms인 이유: CB가 열려 PG 호출 자체는 차단됐지만, 내부에서 TX1(주문 상태 업데이트) + TX C(결제 실패 처리) 트랜잭션이 실행되기 때문이다.

> **핵심**: PG가 에러를 전혀 반환하지 않았음에도 CB가 열렸다. TimeLimiter를 올리지 않았다면 "slowCallRate로 CB가 열렸다"고 잘못 해석했을 것이다.

---

### 함정 2 — Retry가 활성화되면 CB 윈도우는 논리 요청 수가 아니라 PG 호출 수 기준으로 채워진다

#### 예상치 못한 현상

`sliding-window-size: 20`이면 "사용자 입장에서 20번 결제를 시도한 후에 CB가 평가를 시작한다"고 생각했다.

그런데 retry=3(현재 설정)에서 k6로 직접 세어보니 14번째 논리 요청에서 CB가 열렸다. 예상(20번)보다 6번 일찍 열린 것이다.

#### 원인 분석

retry는 PG 호출 실패 시 같은 논리 요청을 다시 시도한다. 이때 각 시도(attempt)는 CB 슬라이딩 윈도우에 독립적인 항목으로 기록된다. 즉, "논리 요청 1건 = CB 호출 1건"이 아니다.

```
[논리 요청 1건이 CB 윈도우에 남기는 기록 — PG 실패율 40%]

케이스                              확률    CB 기록
성공(1회만에)                       60%    success 1건
실패 → 성공(2번째에)                24%    failure 1건 + success 1건 = 2건
실패 → 실패 → 성공(3번째에)         9.6%   failure 2건 + success 1건 = 3건
실패 → 실패 → 실패(전부 실패)        6.4%   failure 3건               = 3건

논리 요청 1건당 평균 CB 호출 수:
  1×0.60 + 2×0.24 + 3×0.096 + 3×0.064 = 1.56건
```

따라서 20-call 윈도우를 채우는 데 필요한 논리 요청 수는 `20 ÷ 1.56 ≈ 13건`이다.

```
retry=3: 논리 요청 ~13건 → 윈도우 20건 채워짐 → CB 평가 → OPEN
retry=1: 논리 요청 ~20건 → 윈도우 20건 채워짐 → CB 평가 → OPEN
```

#### k6로 직접 측정

가상 유저 1명이 순차적으로 실행하며 "몇 번째 논리 요청에서 CB가 처음 열리는가"를 추적했다. `pg.retry-max-attempts` 설정으로 retry 횟수를 전환해 두 번 실행했다.

```
[retry=3 로그]
#1~#13:  [200] — 정상 응답
#14:     [CB 최초 OPEN] — CB 열림
#15~#40: [CB OPEN] — 이후 전부 차단

[retry=1 로그]
#1, #3, #4: [503] — PG 에러 즉시 503 (CB는 아직 OPEN 아님*)
#5~#11:     [200]
#12, #15, #18: [503] — PG 에러
#19~#20:    [200]
#21~#40:    [503] — 연속 503 시작 → CB 진짜 OPEN
```

> *retry=1에서 초반 503은 CB OPEN이 아니다. PG 에러 → 재시도 소진 → SERVICE_UNAVAILABLE(503)이다. "연속 503이 시작되는 지점"을 CB OPEN 시점으로 봐야 한다.

**결과 비교**

| 구분 | CB 최초 OPEN 논리 요청 | 이론값 |
|------|----------------------|--------|
| retry=3 | **#14** | 20 ÷ 1.56 ≈ 13 → **#14 ✓** |
| retry=1 | **#21** | 20 ÷ 1.00 = 20 → **#21 ✓** |

이론값과 실측값이 정확히 일치했다.

---

## Verification & Insight (검증)

세 가지 k6 실험을 통해 CB 동작을 수치로 확인했다.

### 실험 1 — slowCallDurationThreshold

```
스크립트: k6/slow-call-cb-test.js
환경: pg.slow-mode=true (150~300ms, 에러 없음), timelimiter=1000ms
```

| 구간 | 응답 시간 | HTTP 상태 | 설명 |
|------|----------|-----------|------|
| 0~3초 (~20건) | 380~900ms | 200 | CB 윈도우 채우는 중 |
| 3초 이후 | 300~500ms | 503 | slowCallRate 100% > 50% → CB OPEN |

**얻은 것**: `slowCallDurationThreshold`를 설정하지 않으면 "에러는 없는데 느린" 장애를 CB가 전혀 감지하지 못한다. DB 슬로우 쿼리, 외부 API 지연처럼 예외 없이 느린 장애 유형이 여기에 해당한다.

---

### 실험 2 — COUNT_BASED vs TIME_BASED 슬라이딩 윈도우

```
스크립트: k6/sliding-window-type-test.js
시나리오: Phase1(15 req/s, 5초 burst) → Phase2(15초 중단) → Phase3(5 req/s 재개)
```

두 타입의 핵심 차이는 "트래픽 중단이 윈도우에 영향을 주는가"다.

- **COUNT_BASED**: 최근 N건 기준. 새 요청이 없으면 기존 이력이 그대로 유지된다. 15초 중단 동안 Phase1의 실패 기록이 창에 그대로 남아있다.
- **TIME_BASED**: 최근 N초 기준. 시간이 흐르면 오래된 호출이 자동으로 만료된다. 10초 윈도우 설정이면 15초 중단 동안 창이 완전히 비워진다.

| 지표 | COUNT_BASED (size=20) | TIME_BASED (size=10초) |
|------|----------------------|----------------------|
| `cb_open_rate` | 94.90% | 6.12% |
| `phase1_success` | 0.00% | 89.13% |
| `phase3_success` | **8.00%** | **96.03%** |

Phase3 성공률 격차(8% vs 96%)가 핵심이다.

COUNT_BASED에서 Phase3 성공률이 8%에 불과한 이유: 15초 중단 후 Phase3이 재개될 때 CB는 여전히 OPEN 상태다. `wait-duration-in-open-state: 10s`가 경과하면 HALF-OPEN으로 전환되어 `permitted-number-of-calls-in-half-open-state: 5`건만 통과시키는데, 그 5건 중 일부가 성공해도 실패율이 여전히 threshold를 넘어 다시 OPEN으로 돌아간다. 40번 Phase3 요청 중 단 8번만 통과한 것이다.

TIME_BASED에서 Phase3 성공률이 96%인 이유: 15초 중단 동안 10초 윈도우가 만료되어 창이 비어있다. Phase3 재개 시 CB는 CLOSED 상태에서 출발한다. Phase3 로그를 보면 첫 번째 요청부터 `[200] 531ms`로 정상 응답한다. Phase3 도중 새로운 실패가 쌓일 때만 간헐적으로 CB가 열리는(6.12%) 수준이다.

**얻은 것**: COUNT_BASED는 "트래픽이 없어도 과거 이력을 기억"하고, TIME_BASED는 "트래픽이 없으면 윈도우가 자연스럽게 초기화"된다. 재배포·점검 후 재개 시에는 TIME_BASED가 유리하지만, 저트래픽 환경에서는 창이 비어 CB가 예기치 않게 CLOSED로 판단할 수 있다. COUNT_BASED가 기본값인 이유다.

---

### 실험 3 — Retry × CB 상호작용

```
스크립트: k6/retry-cb-test.js
환경: 가상 유저 1명 순차, 40회 반복
```

| 구분 | CB 최초 OPEN 논리 요청 | HTTP 요청 / 40 논리 요청 |
|------|----------------------|--------------------------|
| retry=3 | **#14** | 80건 |
| retry=1 | **#21** | 80건 |

HTTP 요청 수가 동일한 이유: retry는 commerce-api → pg-simulator 사이의 내부 재시도다. k6가 측정하는 HTTP 요청은 k6 → commerce-api 구간이므로 retry 횟수와 무관하게 논리 요청당 2건(주문 생성 + 결제)이다.

**얻은 것**: retry를 늘리면 CB가 더 민감해진다. retry=3이 retry=1보다 약 1.5배 빠르게 CB를 열었다. retry 횟수를 결정할 때 `sliding-window-size`와 함께 고려해야 한다.

---

## Lessons Learned

1. **TimeLimiter와 slowCallRate는 집계 채널이 다르다.** TimeLimiter가 끊어낸 호출은 "응답이 느렸다"는 정보 없이 예외로만 처리되어 failure로 집계된다. `slowCallDurationThreshold` 실험을 하려면 TimeLimiter를 충분히 크게 잡아야 실험 전제가 성립한다. 더 일반적으로, CB 실험 전에 "어느 경로(failureRate vs slowCallRate)로 열리는가"를 Actuator 메트릭으로 먼저 확인하는 습관이 필요하다.

2. **Retry는 CB 민감도를 높인다.** retry 횟수를 늘리면 논리 요청 1건이 CB 윈도우에 여러 건으로 기록되어 CB가 예상보다 빨리 열린다. retry 횟수를 올릴 때는 `sliding-window-size`를 함께 키우거나, retry 대상 예외를 좁게 설정해야 한다. CB OPEN 상태에서 retry하면 HALF-OPEN 프로브 슬롯을 소진해 CB 회복을 방해하므로, CB OPEN 예외는 retry 대상에서 반드시 제외해야 한다.

3. **COUNT_BASED vs TIME_BASED는 "트래픽 중단이 CB를 복구시키는가"로 구분된다.** COUNT_BASED는 트래픽이 없어도 과거 실패를 기억하고, TIME_BASED는 윈도우 시간이 지나면 과거가 사라진다. 어느 쪽이 맞다는 게 아니라, 운영 패턴(재배포 빈도, 트래픽 특성)에 맞게 선택해야 한다.
