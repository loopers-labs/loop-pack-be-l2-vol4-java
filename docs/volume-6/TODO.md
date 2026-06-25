# Volume 6 — PG 연동 Resilience TODO

> 이 문서는 **살아있는 계획서(가설)** 다. 단계 진행 중 측정·구현 결과가 가정과 어긋나면(예: 타임아웃을 줄였는데 서킷이 더 자주 열림) plan을 사실에 맞게 고친다.
> 대상: 주문에 대한 카드 결제 API (`POST /api/v1/payments`) + 외부 결제 모듈(`pg-simulator`, 포트 8082) 연동.

---

## 0. 목표 & 성공 기준

> **무방비 동기 연동에서 출발 → 결함을 수치로 박제 → Resilience4j로 한 겹씩 보강**한다. 매 단계 "왜 이 수치/전략인가"를 PG 사양 역산과 장애 재현으로 증명한다. 단순 "동작"이 아니라 **PG가 느려지고·실패하고·중복되고·응답을 잃어도 내부는 산다**가 목표.

핵심 철학:

- **기능적 완결성 먼저, 튜닝은 그 다음.** Stage 0에서 happy path를 끝까지 흐르게 한 뒤, Stage 1에서 결함을 박제하고, 그 위에 Resilience를 얹는다.
- 모든 장치는 결국 **`PENDING`("우리가 결과를 모른다"를 정직하게 표현하는 상태)** 으로 수렴 → 콜백/폴링이 정합성을 복구한다.
- 외부 시스템은 **항상 지연되고·실패하고·중복되고·성공했어도 응답을 잃을 수 있다**고 가정한다.

### 성공 기준 (요구사항 체크리스트 매핑)

| 체크리스트 | 충족 단계 |
|---|---|
| PG 연동은 FeignClient로 외부 시스템을 호출한다 | Stage 0 |
| 응답 지연에 타임아웃을 설정하고, 실패 시 적절한 예외 처리 | Stage 2 |
| 결제 요청 실패 응답을 적절히 시스템에 연동한다 | Stage 0(저장) + Stage 3(PENDING 흡수) |
| 콜백 + 상태 확인 API로 결제정보를 시스템과 연동한다 | Stage 0(콜백) + Stage 6(조회) |
| 서킷 브레이커 / 재시도로 장애 확산을 방지한다 | Stage 4(CB) + Stage 5(Retry) |
| 외부 장애 시에도 내부는 정상적으로 응답한다 | Stage 3(Fallback) |
| 콜백이 안 와도 주기/수동 API로 상태를 복구한다 | Stage 6 |
| 타임아웃으로 실패해도 결제건을 확인해 정상 반영한다 | Stage 2 + Stage 6(조회 교차검증) |

---

## 1. 핵심 결정 (확정)

| 주제 | 결정 | 근거 |
|---|---|---|
| HTTP 클라이언트 | **FeignClient** | 선언적이라 Resilience 어노테이션과 결합이 깔끔. Spring Cloud BOM 이미 존재. 타임아웃은 **HTTP 클라이언트 레벨**에서 거는 것이 단순·안전 |
| Resilience 부착 위치 | **infra 전담 어댑터(`PgClient` 구현)에만** | 애플리케이션/Facade에 인프라 어노테이션이 침투하지 않게 격리 |
| 멱등 단위 | **우리 키 = `orderId`** (주문 1건 = 결제 1건, UNIQUE) / **`transactionKey`는 PG가 발급하는 외부 핸들을 매핑 저장** | 외부 시스템은 신뢰할 수 없으니 우리가 잃어버리지 않는 키(`orderId`)를 정합성의 닻으로 둔다. PG 키는 정밀 조회용 핸들 |
| 외부 타입 격리 | **`CardType` 등 외부 enum을 우리 도메인 enum으로 정의·매핑** (PG enum 직접 차용 금지) | PG가 1개여도 각 PG가 같은 값을 줄 보장이 없다. 도메인 코드를 외부 스펙에서 분리 |
| 결제 상태 모델 | **`PENDING` / `SUCCESS` / `FAILED` + 상태 전이 가드** | 최소 모델. 종료 상태(SUCCESS/FAILED)는 불변 → 상태 레벨 멱등성 |
| 주문 연동 범위 | **결제 결과 → 주문 `CREATED`→`PAID`/`PAYMENT_FAILED` 전이까지**. 재고/쿠폰 보상 복원은 범위 밖 | 체크리스트 충족 최소. Resilience 학습에 집중 |
| 결과 불명 처리 | 타임아웃·서킷 OPEN 등 **결과 불명은 "실패" 단정 없이 `PENDING` 유지 → 폴링 보정, 상한 초과 시 `STUCK` 격리+알림**(자동 취소 안 함) | "응답 못 받음 ≠ 처리 안 됨". 타임아웃이어도 PG에선 성공했을 수 있다. pg-simulator엔 취소/환불 API가 없어 더더욱 단정 금지 |
| 비즈니스 거절 처리 | 한도 초과·잘못된 카드 등 **확정 거절은 `FAILED` 확정 + 서킷 집계에서 ignore** | 백 번 재시도해도 동일. 의도된 거절로 서킷이 열리면 안 됨 |
| 타임아웃 값 방향 | **접수/처리 분리형이라 요청 호출은 "접수 응답" 기준으로 짧게(연결 ~1s / 응답 1~2s)** | 우리 PG는 요청(100~500ms)과 처리(1~5s, 콜백)를 분리한다. 동기 승인형 PG라면 길게(10s) 잡지만 우리는 처리 완료를 동기로 기다리지 않는다 |
| 서킷 임계치 산정 | **PG 사양 역산으로 초기값 근거 + k6 민감도 테스트로 검증**(절대값 맹신 X). 집계 단위(COUNT/TIME)는 트래픽 특성으로 판단 | 요청 성공 60% × 처리 성공 70% 같은 사양으로 출발하되, 우리 서버가 과민/둔감한지 부하로 확인 |
| 동시성 제어 | **조건부 UPDATE**(`... WHERE status='PENDING'`) + affected rows로 승자 판별 | 콜백·폴링 동시 확정 race를 격리 수준에 안 기대고 그 행만 원자화 → 후처리 정확히 1회 |
| 측정 | **k6 2장면**(타임아웃 전후 스레드 고갈 / 부하 중 서킷 OPEN) + **장애 재현 통합테스트** + **서킷 메트릭 노출** | 라이팅 수치 증거. vol5급 측정 하니스는 과함 |
| 멀티 PG | **`PgClient` 인터페이스로 경계만 설계**, 오케스트레이션(서킷 OPEN 시 다른 PG로)은 구현 안 함 | 추상화 경계를 남겨 두되 범위는 단일 PG로 제한 |

---

## 2. 단계 사다리 (naive → resilient)

핵심 불변 원칙: **완결 → 박제 → 보강.** 각 보강 단계는 직전 단계의 구체적 결함을 겨냥하고, 효과를 측정/테스트로 증명한다.

```mermaid
flowchart TD
    S0["Stage 0 · 토대\nPayment 도메인 + PG 연동 (resilience 0)"] --> S1
    S1["Stage 1 · 장애 전파 재현\n무방비 상태를 k6로 박제 (As-Is)"] -->|"reports: 01-baseline"| S2
    S2["Stage 2 · Timeout\n접수 응답 기준 타임아웃 + 외부 호출 트랜잭션 밖으로"] -->|"reports: 02-timeout"| S3
    S3["Stage 3 · Fallback\n결과 불명 → PENDING 흡수 (내부는 정상 응답)"] --> S4
    S4["Stage 4 · Circuit Breaker\n역산+민감도 · ignore 4xx · 메트릭 노출"] -->|"reports: 03-circuit"| S5
    S5["Stage 5 · Retry (Nice-to-Have)\n유저=빠른실패 · retry 정책 정의"] --> S6
    S6["Stage 6 · 폴링 Reconciliation\n콜백 유실 보정 · PG 교차검증 · STUCK 격리"] --> S7
    S7["Stage 7 · 동시성\n조건부 UPDATE로 후처리 1회"] --> DOC["Writing Quest\n단계별 '왜'를 AS-IS/TO-BE로"]
```

---

## 3. 횡단 규약 (모든 단계 공통)

- **외부 경계는 신뢰하지 않는다.** 콜백 데이터를 그대로 믿지 말고 PG 조회로 교차검증한다. (우리 측 `PaymentCoreRelay`는 콜백 전송 실패 시 로그만 남기고 재발송하지 않는다 — 즉 콜백 유실은 가정이 아니라 상수다. 폴링 안전망이 필수인 이유.)
- **결제 상태는 RDB에 보관**하고 주기적으로 보정한다(휘발성 저장소에만 의존하지 않는다).
- **PG 호출과 상태 전이는 충분히 로깅**한다 — 호출 전/후, 전이 사유, 폴링 분기 결과까지. 보정 루프의 `catch`를 **빈 블록으로 두지 않는다**(최소 로깅). 단 이벤트 소싱 같은 풀스택 로깅은 범위 밖.
- **외부 I/O는 DB 트랜잭션 밖**에서(커밋 이후 또는 별도 트랜잭션) 수행해 커넥션 점유·롱 트랜잭션을 피한다.

---

## 4. 측정·보고 규약

> 장애 전파·서킷 동작은 **수치로 증명**한다. 이 데이터가 라이팅의 증거다.

- **위치**: `docs/volume-6/measurement/k6/`(시나리오), `docs/volume-6/reports/`(결과)
- **k6 2장면**:
  - **장면 1 — 스레드 고갈**: 타임아웃 적용 전/후, PG 지연 상황에서 결제와 무관한 요청(예: 상품 조회)의 응답이 어떻게 변하는지. → `reports/01-baseline.md`(전), `reports/02-timeout.md`(후)
  - **장면 2 — 서킷 OPEN**: 부하 중 실패율이 임계치를 넘어 `CLOSED→OPEN` 전이가 일어나고, OPEN 동안 PG를 호출하지 않고 즉시 fallback 하는지. 서킷 메트릭(오픈 횟수)과 함께 관찰. → `reports/03-circuit.md`
- **결정론 vs 실측**: pg-simulator의 내장 확률(요청 실패 40%·처리 지연 1~5s)을 그대로 쓸지, 어댑터를 스텁으로 교체해 지연/실패율을 고정할지는 측정 시 선택(서킷 임계치 민감도는 결정론 제어가 깔끔).
- **장애 재현 통합테스트**: `@MockitoBean`으로 타임아웃/네트워크 예외를 주입하고, `CircuitBreakerRegistry`로 상태 전이를 단언한다. 라이브러리 동작("실패율 N%면 열림")이 아니라 **우리 fallback 계약**(PENDING 저장·주문 전이)을 검증한다.

---

## Stage 0 — 토대: Payment 도메인 + PG 연동 (resilience 0)

**목표:** 결제 요청 → 접수(PENDING) → 콜백 → 결제/주문 확정의 happy path를 **장애 복구 장치 없이** 끝까지 흐르게 한다.

- [X] **Payment 도메인** — `PaymentModel`(`orderId` UNIQUE, `userId`, `cardType`, `cardNo`, `amount`, `status` PENDING/SUCCESS/FAILED, `transactionKey` nullable=PG 매핑, `reason` nullable) + 상태 전이 메서드(전이 가드 포함) + `PaymentRepository` (PAY-1)
- [X] **우리 `CardType` enum 정의** + PG `CardType`과의 매핑(도메인 ↔ 어댑터 경계에서만 변환) (PAY-1)
- [X] **`PaymentGateway` 포트 인터페이스**(도메인) — 결제 요청. 멀티 PG를 염두에 둔 추상화 경계 (PAY-1, *거래 단건/주문별 조회는 Stage 6 폴링에서 추가*)
- [X] **`PaymentGateway` FeignClient 구현**(infra 어댑터) — `X-USER-ID` 헤더 주입. **타임아웃·재시도·서킷 없음(의도적)** (PAY-1)
- [X] **결제 요청 API** `POST /api/v1/payments`(Controller/Dto/Facade) — `orderId`+`cardType`+`cardNo` 입력 → 주문/금액 확인 → PG 접수 요청 → `transactionKey` 매핑·`PENDING` 저장 → 접수 응답 (PAY-1)
- [X] **콜백 엔드포인트** `POST`(8080, PG가 `callbackUrl`로 통보) — 결과 수신 → 결제 상태 전이 + 주문 상태 전이(`PAID`/`PAYMENT_FAILED`) (PAY-2)
- [ ] pg-simulator 실행 + `.http`로 happy path 수동 검증

**의도적 결함(이후 단계에서 제거):** 타임아웃 X · 재시도 X · 서킷 X · fallback X · 폴링 X.

**검증:** happy path 1건이 요청 → 접수(PENDING) → 콜백 → SUCCESS/주문 PAID 까지 흐른다.

---

## Stage 1 — 장애 전파 재현 (측정 원점)

**목표:** 무방비 상태가 외부 지연 하나로 어떻게 무너지는지 수치로 박제한다. → `reports/01-baseline.md`

- [ ] **타임아웃 없는** 현재 구현으로 측정
- [ ] k6 장면 1: PG 응답 지연 시 톰캣 스레드가 점유되어 **결제와 무관한 요청까지 응답이 붕괴**하는지 관찰(동시 N명)
- [ ] 관찰: 요청 실패 40%가 **사용자 에러(500)로 직결**되는 것
- [ ] **`reports/01-baseline.md` 작성** (As-Is 원점)

**검증:** 무방비 상태의 스레드 고갈/에러율 곡선을 확보. 모든 비교의 원점이 된다.

---

## Stage 2 — Timeout + 예외 처리 + 트랜잭션 경계

**목표:** "안 빠지는 물을 언제 포기할 것인가"를 정해 자원(스레드/커넥션)을 회수한다. → `reports/02-timeout.md`

- [ ] **Feign connect/read 타임아웃** — 접수 응답 기준으로 짧게(연결 ~1s / 응답 1~2s). + 커넥션 풀에서 대기 없이 실패하도록 connection-request 타임아웃
- [ ] 타임아웃/요청 실패 시 **명확한 예외**로 사용자에게 즉시 응답
- [ ] **외부 호출을 `@Transactional` 밖으로** — `PENDING`을 먼저 커밋한 뒤 PG 호출(커밋 이후/별도 트랜잭션). 커넥션 점유 해소
- [ ] k6 장면 1 재측정: 스레드가 회수되어 무관 요청이 생존하는지 → `reports/02-timeout.md`

**검증:** 타임아웃 전후 비교에서 스레드 고갈이 해소된다. → 여기서 *"끊었는데 PG에선 결제됐으면?"* 이라는 결과 불명 문제가 드러나며 Stage 3·6의 동기가 된다.

---

## Stage 3 — Fallback (결과 불명 → PENDING 흡수)

**목표:** 외부 장애를 "결제 실패"로 단정하지 않고, 내부는 정상적으로 응답한다.

- [ ] **Resilience4j 도입** (`resilience4j-spring-boot3` + `spring-boot-starter-aop`)
- [ ] 어댑터에 **fallback 계약** — 타임아웃/요청 실패(결과 불명) 시 `PENDING` + "결제 처리 중" 안내로 흡수(서킷은 느슨한 기본값으로 시작, 본격 튜닝은 Stage 4). 이후 CB/Retry가 쌓이면 fallbackMethod는 **최외곽에 둔다**(Stage 4 참고)
- [ ] 비즈니스 거절(한도 초과/잘못된 카드)은 fallback이 아니라 콜백 결과로 `FAILED` 확정 — 상황(`t`)에 맞게 분기
- [ ] 장애 재현 통합테스트: fallback이 PENDING으로 저장하는지(계약) 단언

**검증:** PG 요청 실패/타임아웃에도 내부는 200 + `PENDING` 으로 응답한다. (체크리스트: 외부 장애 시 내부 정상 응답)

---

## Stage 4 — Circuit Breaker

**목표:** 계속 실패하는 PG를 "이제 그만 두드린다"로 차단해 자원 고갈을 원천 차단한다. → `reports/03-circuit.md`

- [ ] CircuitBreaker 설정 — 집계 단위(COUNT/TIME, 트래픽 근거) · sliding-window · failure-rate · **slow-call**(느린 응답도 실패) · wait-in-open · half-open permitted
- [ ] **설정값 역산** — 요청 성공 60% × 처리 성공 70% 등 PG 사양에서 초기값 근거를 잡고, **k6 민감도 테스트로 과민/둔감 검증**해 조정
- [ ] **record/ignore-exceptions** — 5xx/타임아웃/네트워크는 record, **4xx·비즈니스 예외는 ignore**(의도된 거절로 서킷이 열리지 않게)
- [ ] **aspect-order CB(1) > Retry(2)** + **fallbackMethod는 최외곽(CB)에 부착** — 재시도 묶음을 서킷이 1회로 카운트하고, **재시도가 모두 소진된 뒤에야 fallback이 발동**하게 한다(안쪽 어노테이션에 fallback을 달면 예외를 먼저 삼켜 Retry가 동작하지 않는 함정 회피). Retry 본체는 Stage 5
- [ ] **서킷 메트릭을 actuator/prometheus로 노출**(상태/오픈 횟수)
- [ ] k6 장면 2: 부하 중 `CLOSED→OPEN` 전이 + OPEN 동안 PG 미호출·즉시 fallback 관찰 → `reports/03-circuit.md`
- [ ] 통합테스트: `transitionToOpenState()`로 강제 OPEN 후 fallback 계약 단언

**검증:** 부하 중 서킷이 열리고, OPEN 동안 PG를 부르지 않고 즉시 PENDING으로 떨군다. 메트릭에 오픈 횟수가 보인다.

---

## Stage 5 — Retry (Nice-to-Have)

**목표:** 일시적 실패는 다시 시도하되, Retry가 장애를 키우지 않게 한다.

> **경로 분리.** 유저 요청 경로와 보정(스케줄러) 경로는 retry 전략이 다르다. 보정 경로의 backoff+jitter는 스케줄러가 서는 **Stage 6에서 배선**하고, 이 단계에서는 **정책 정의 + 유저 경로**까지만 한다.

- [ ] **유저 요청 경로 = 빠른 실패** — 재시도 미적용(또는 최소). 스레드를 외부 I/O 재시도로 오래 점유하지 않는다
- [ ] **retry 정책 정의** — `retry-exceptions`를 5xx/SocketTimeout/네트워크로 한정, 4xx·비즈니스 예외는 ignore. 보정 경로용 Exponential backoff + jitter(Thundering Herd 회피) 값까지 정의해 둔다
- [ ] **Feign 자체 재시도 비활성화**(`Retryer.NEVER_RETRY`) — Resilience4j로 단일화(이중 재시도 방지)
- [ ] 멱등 전제 확인 — 결제 재시도는 조회로 "주문 없음(미도달)"을 확인한 뒤가 안전

**검증:** 유저 경로가 빠르게 실패하고, 영구 실패(잘못된 카드)는 재시도하지 않으며, 이중 재시도가 없다. (보정 경로 backoff 적용·검증은 Stage 6)

---

## Stage 6 — 콜백 유실 대비 폴링 (Reconciliation)

**목표:** 콜백이 오지 않아도, 타임아웃으로 결과를 못 받았어도 정합성을 복구한다.

- [ ] **`@Scheduled` 폴러** — "일정 시간 경과한 `PENDING`만" 조회(grace period: 처리 지연 1~5s를 역산해 "정상이면 끝났어야 할 시간" 이후부터)
- [ ] **건별 `REQUIRES_NEW`** 로 부분 실패 격리 (한 건 실패가 다른 건에 영향 없게)
- [ ] **PG 조회 결과 분기** — 처리 중 → 다음 주기 재확인 / 주문 없음(미도달=돈 안 빠짐) → 재요청 안전 / 성공·실패 → 그 결과로 확정
- [ ] **콜백 PG 교차검증** — 콜백 데이터를 그대로 믿지 않고 PG 조회로 재확인
- [ ] **상한 초과 PENDING → `STUCK` 격리 + 알림**(영원한 PENDING 방지, 자동 취소는 하지 않음)
- [ ] **수동 reconcile API**(관리자) — 주기 폴링 외에 수동으로도 상태 복구
- [ ] **보정 경로에 Retry 배선** — 폴링/스케줄러의 PG 재조회·재요청에 Stage 5에서 정의한 Exponential backoff + jitter를 적용(유저 경로의 빠른 실패와 분리)

**검증:** 콜백을 의도적으로 누락시켜도 폴링이 `PENDING`을 정합성 복구한다. (체크리스트: 콜백 미수신 복구 / 타임아웃 실패건도 조회로 정상 반영)

---

## Stage 7 — 동시성: 조건부 UPDATE

**목표:** 콜백 스레드와 폴링 스레드가 같은 결제건을 동시에 확정해도 후처리가 중복되지 않게 한다.

- [ ] **조건부 UPDATE** — `UPDATE payment SET status=? WHERE id=? AND status='PENDING'` 로 전이를 원자화
- [ ] **affected rows로 승자 판별** — 1이면 내가 전이시킨 것 → 후처리(주문 상태 전이) 실행 / 0이면 남이 이미 함 → 후처리 스킵
- [ ] 통합테스트: 콜백+폴링 동시 진입에도 주문 전이/후처리가 **정확히 1회**

**검증:** 동시 확정 상황에서도 주문 상태 전이가 한 번만 일어난다(중복 후처리 없음).

---

## 의도적으로 안 하는 것 + 알려진 한계

> "안 한 것"과 "그래서 생기는 한계"를 정직하게 남긴다 — 라이팅·PR 본문의 좋은 소재.

- **TimeLimiter / RateLimiter / Bulkhead / MQ 재처리 큐** — 타임아웃은 HTTP 클라이언트 레벨에서 충분. 비즈니스 로직을 비동기로 감싸 TimeLimiter를 걸지 않는다.
- **결제 실패 시 재고/쿠폰 보상 복원 없음** — 주문 생성 시점에 이미 재고가 차감되므로, 결제 실패 시 재고가 복원되지 않는 한계가 남는다(실무라면 보상/예약 모델/재고 안내 알림이 필요). → *"결제가 실패하면 주문을 무조건 롤백해야 할까"* 라이팅 주제.
- **가주문→진주문 전환 / "따닥" 중복 주문 정리** — 주문 생성 단계의 멱등성은 범위 밖(주문은 이미 생성된 상태로 받는다). 결제 단계 멱등(`orderId`)만 다룬다.
- **멀티 PG 오케스트레이션** — 인터페이스 경계만 두고 구현하지 않는다.
- **카오스 엔지니어링 풀스택 / 프론트 결과 대기 UX(폴링·SSE)** — `@MockitoBean`·슬립 + k6 수준으로 작게 시작한다.

---

## Writing Quest

> `reports/`의 수치와 단계별 의사결정("왜 그렇게 판단했는가")을 근거로 작성. 블로그 또는 GitHub Issue 4포맷(Design Doc / Retrospective / Challenge Story / Benchmark Report) 중 택1.

라이팅 씨앗 (단계 → 주제):

- Stage 1·2·4 → **"PG 장애 하나로 주문 전체가 멈췄다"** (장애 전파 + 타임아웃/서킷)
- Stage 2·6 → **"응답이 안 와서 실패 처리했는데 PG에선 결제됐다"** (결과 불명 + 멱등성 + 조회)
- Stage 3 → **"주문은 PENDING인데 사용자는 결제 안내를 받았다"** (Fallback 철학)
- Stage 4 → **타임아웃을 "PG는 10초"가 아니라 짧게 잡은 이유** (접수/처리 분리형 PG의 함의)
- Stage 5 → **"재시도 횟수는 몇 번이 적절했을까"** (Retry Storm·backoff·jitter)
- 시그니처 → **설정값을 PG 사양으로 역산하고 k6 민감도로 검증** (Benchmark Report 포맷에 최적)

---

## 선택 작업 — 시니어 파트너 Skill

- [ ] (선택) `~/.claude/skills/analyze-external-integration/SKILL.md` — 외부 시스템 연동 설계를 트랜잭션 경계·상태 일관성·실패 시나리오·멱등성 관점에서 분석하는 리뷰용 Skill 작성.

---

## 산출물 트리

```
docs/volume-6/
  TODO.md                 ← (이 문서, 작업 계획·진행 체크)
  measurement/k6/         ← 장애 전파·서킷 시나리오 스크립트
  reports/
    01-baseline.md        ← 무방비 As-Is (스레드 고갈)
    02-timeout.md         ← 타임아웃 적용 후
    03-circuit.md         ← 부하 중 서킷 OPEN
```
