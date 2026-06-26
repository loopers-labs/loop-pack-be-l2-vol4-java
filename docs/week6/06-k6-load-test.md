# k6 부하/통합 테스트 — 결제(PG 연동) 플로우

결제 플로우를 **resilience(B) > 동시성 정합성(A) > 처리량(C)** 우선순위로 k6 시나리오화한다.
스크립트 본체는 `docs/k6/`에 있고, 이 문서는 설계 의도·실행법·결과 기록 틀이다.

## 시나리오

| ID | 분류 | 검증 | 스크립트 |
| --- | --- | --- | --- |
| **B1** | resilience | PG 다운 중 내부가 hang 없이 빠르게 응답, 실패 누적 후 CB OPEN → 이후 즉시 `PAYMENT_GATEWAY_UNAVAILABLE`(저지연 fast-reject) | `resilience.js` |
| **B2** | resilience | simulator 복귀 + open 10s 경과 → HALF_OPEN→CLOSED → 결제 성공률 회복 | `resilience.js` |
| **B3** | resilience | CB OPEN에 막혀 PG에 안 닿은 주문이 PENDING(키없음)으로 남고 → 복귀 후 reconciler(@Scheduled 30s)가 `inquireByOrder`=빈 결과 → FAILED+보상 | 사후 검증(로그·주문 상태) |
| **A1** | 동시성 | 같은 orderNumber 에 N개 동시 결제 → 정확히 1건 접수, 나머지 409, PG 거래수=1(이중결제 0·유니크 제약) | `concurrency.js` |
| **C1** | 처리량 | 정상 PG에서 주문+결제 부하 → TPS·p95/p99 기준선 | `baseline.js` |

## 장애 주입 (경량)

PG에 장애 주입 제어가 없고(요청 필드 = orderId/cardType/cardNo/amount/callbackUrl), simulator는 **프로세스로 기동**된다.
그래서 "PG 다운"은 **`:8082` 리스너를 kill** 해 만들고, 복귀는 **재기동 명령으로 다시 띄운다**. k6(JS)는 프로세스를 못 죽이므로
`run-resilience.sh` 래퍼가 타이밍을 조율한다: k6 백그라운드 시작 → `DOWN_AT`초 후 PG kill → `UP_AFTER`초 후 재기동 → k6 종료 대기.

- 한계: 경량 방식은 **connect 실패**(연결 거부)는 만들지만 **read 타임아웃**은 못 만든다(죽은 포트는 연결 자체가 안 됨).
  read 타임아웃·부분 실패율·500-후-복구 같은 정밀 시나리오가 필요하면 후속으로 toxiproxy를 도입한다(현재 범위 외).

## 전제 환경

1. infra 기동: `docker-compose -f docker/infra-compose.yml up` (MySQL/Redis/Kafka)
2. commerce-api 기동(`local`), pg-simulator 기동(`:8082`)
3. 시드: `loopers01`(`Passw0rd!`) 사용자 + 주문 가능한 상품(예: productId 1,2)과 충분한 재고
   - `local`은 `ddl-auto: create`라 매 기동 초기화 → 시드가 휘발될 수 있으니 실행 전 확인. 없으면 시드 후 진행.
   - k6 `setup()`이 주문 1건을 만들어 전제(인증·상품·재고)를 fail-fast로 점검한다.

## 인증·요청 형태 (스크립트가 의존하는 계약)

- 인증 헤더: `X-Loopers-LoginId: loopers01`, `X-Loopers-LoginPw: Passw0rd!`
- 주문 생성: `POST /api/v1/orders` → `data.orderNumber`
- 결제 요청: `POST /api/v1/payments` `{ orderId: <orderNumber>, cardType: SAMSUNG|KB|HYUNDAI, cardNo }` → `202/200 data.status=PENDING`
- PG 주문 조회(사후 검증): `GET {pg}/api/v1/payments?orderId=<orderNumber>` (헤더 `X-USER-ID`)

## 실행법

```bash
# 처리량 베이스라인
k6 run docs/k6/baseline.js

# 동시성 정합성 (이중결제 0)
k6 run docs/k6/concurrency.js

# resilience (장애 주입 오케스트레이션) — PG_START 는 본인 simulator 기동 명령
PG_START="<pg-simulator 기동 명령>" docs/k6/run-resilience.sh
```

## 실행에서 드러난 사실 (2026-06-26)

1. **pg-simulator는 동기 POST 에서 ~30~50% 무작위 500 을 던진다**(내장 불안정, 제어 불가). 500 이면 PG 에 거래가 안 생기고 → 우리 결제는 PENDING(키없음) 으로 남아 reconciler 가 정리한다. 그래서 "100% 성공"은 불가능 — 테스트는 이 실패율을 예상된 것으로 다룬다.
2. **PG 500 의 클라이언트 응답이 갈린다**: CB 가 닫혀 있을 때 PG 500 → `Internal Server Error`(generic 500). CB 가 열린 뒤 → `PAYMENT_GATEWAY_UNAVAILABLE`. (개선 여지: PG 5xx 를 일관된 결제 에러로 번역)
3. **CB 는 실패율이 높을 때 실제로 OPEN 된다** — 자연 불안정만으로도 50% 임계를 넘기면 열려 fast-reject 한다(아래 B 결과).
4. **동시 50 결제에서 PG 거래는 1건** — `order_number` 유니크 제약이 이중결제를 막는다.

## 결과 기록

| 시나리오 | 핵심 지표 | 판정 |
| --- | --- | --- |
| **C1** baseline | checks 100%, pay p95≈0.7s·p99≈0.8s, 접수율 ~57%(PG 불안정 반영) | ✅ 응답성 OK |
| **A1** concurrency(50 동시/같은 주문) | accepted=1, conflict=49, **PG 거래=1** | ✅ 이중결제 0 |
| **B1** resilience(자연 불안정) | CB OPEN → fast-reject(`PAYMENT_GATEWAY_UNAVAILABLE`) 248건, hang 0, p95≈0.7s | ✅ CB 보호·내부 정상 응답 |
| **B2/B3** 통제 down→up | 미완 — 단일 박스에서 kill+2nd JVM respawn 시 OOM 으로 commerce-api 동반 다운. 안전하게 하려면 PG 재기동을 IDE 가 맡고(2nd JVM spawn 금지) 부하를 낮춰 재시도 | ⏸ 보류 |

> **B2/B3 재시도 시 주의**: `run-resilience.sh` 의 PG 재기동(`PG_START`)이 별도 무거운 JVM 을 띄운다. 단일 개발 머신에선 메모리 압박으로 다른 JVM(commerce-api)이 OOM 될 수 있으니, PG 재기동은 IDE/외부에서 처리하고 래퍼는 kill·타이밍만 담당하도록 분리하는 편이 안전하다.
