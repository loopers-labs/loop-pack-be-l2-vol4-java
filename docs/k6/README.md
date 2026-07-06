# k6 결제 플로우 부하/통합 테스트

결제(PG 연동) 플로우를 시나리오별로 검증한다. 설계 배경은 [`docs/week6/06-k6-load-test.md`](../week6/06-k6-load-test.md).

| 스크립트 | 시나리오 | 검증 |
| --- | --- | --- |
| `baseline.js` | C1 처리량 | 정상 PG에서 TPS·p95/p99 기준선 |
| `concurrency.js` | A1 동시성 | 같은 주문 동시 결제 → 1건 접수·이중결제 0 |
| `resilience.js` (+ `run-resilience.sh`) | B1/B2 | PG 장애 중 내부 보호·CB OPEN·복구 |
| `lib/common.js` | — | 공용 헬퍼(인증/주문/결제/PG조회) |

## 전제

1. infra: `docker-compose -f docker/infra-compose.yml up`
2. commerce-api(`local`, :8080), pg-simulator(:8082) 기동
3. 시드: `loopers01`(`Passw0rd!`) + 주문 가능한 상품(기본 `productId=1`)·충분한 재고
   - `local`은 `ddl-auto: create`라 기동 시 초기화 → 실행 전에 시드 상태 확인
   - 환경변수로 교체 가능: `PRODUCT_ID`, `QUANTITY`, `LOGIN_ID`, `LOGIN_PW`, `BASE_URL`, `PG_BASE`

## 실행

```bash
# C1 — 처리량 베이스라인
k6 run docs/k6/baseline.js

# A1 — 동시성(이중결제 0). 동시 강도 조절: VUS=100
k6 run docs/k6/concurrency.js
VUS=100 k6 run docs/k6/concurrency.js

# B1/B2 — resilience (장애 주입). PG_START 는 본인 pg-simulator 기동 명령
PG_START="java -jar <pg-simulator>.jar" DURATION=3m DOWN_AT=45 UP_AFTER=60 \
  docs/k6/run-resilience.sh
```

## 장애 주입 메모

pg-simulator는 장애 주입 API가 없고 **프로세스로 기동**되므로, "PG 다운"은 `:8082` 리스너 kill 로 만들고
복귀는 `PG_START` 명령으로 재기동한다(래퍼가 타이밍 조율). 경량 방식이라 **connect 실패**는 되지만
**read 타임아웃**은 못 만든다 — 정밀 시나리오(타임아웃·부분실패)는 후속 toxiproxy 과제.

## B3 — 보정 회수 확인 (resilience 후 수동)

`run-resilience.sh` 종료 뒤:
1. ~30초(reconciler 주기) 대기
2. commerce-api 로그에서 `주문 기준 보정: 무거래 정리` 확인 (CB OPEN에 막혀 PG 미도달 → 키없음 PENDING → 보정이 FAILED 정리)
3. 또는 해당 주문 상태가 `PAYMENT_FAILED`(보상 완료)로 정리됐는지 조회

## 판정 기준(임계값)

- `baseline.js`: `pay_latency` p95<1s·p99<2s, checks>99%
- `concurrency.js`: `accepted_total`==1, `pg_transactions`==1 (이중결제 0)
- `resilience.js`: `pay_latency` p95<3s (장애 중에도 hang 없음). 성공률은 장애 구간이라 관측만.
