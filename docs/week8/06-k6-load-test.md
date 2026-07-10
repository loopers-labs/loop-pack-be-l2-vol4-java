# k6 부하 테스트 — 대기열 & 처리량 산정

대기열의 목적은 처리량을 하류가 감당하는 선으로 누르는 것이다. 그래서 부하 테스트의 1차 목표는 통과/실패가 아니라 **숫자를 재는 것** — 주문 API가 무너지기 시작하는 지점을 찾아 `batch-size`를 정한다. 스크립트 본체는 `docs/k6/`에 두고, 이 문서는 설계 의도·실행법·결과 기록 틀이다.

## 측정으로 정하는 값

`01-requirements.md`의 175 TPS는 발제 추정치다. 다음 절차로 실제 값을 대체한다.

```
1. (Before) 큐 없이 주문 API 맨몸에 부하를 램프업
   → Micrometer Timer(주문 처리시간)와 HikariCP active 커넥션을 관찰
   → p99 지연이 꺾이거나 풀이 포화되는 지점 = 안전 TPS
2. batch-size = 안전TPS × (interval-ms / 1000)
3. (After) 큐를 얹고 1만 명 버스트를 던져
   → 주문 API가 그 안전 TPS 근처로 평탄하게 유지되는지 확인
```

## 시나리오

| ID | 분류 | 검증 | 스크립트 |
| --- | --- | --- | --- |
| **T1** | 한계 TPS | 큐 우회, 주문 API 직접 램프업 → p99·HikariCP active로 안전 TPS 도출 | `order-knee.js` |
| **Q1** | 순서 보장 | N명 동시 진입 → 진입 시각 순서대로 순번이 매겨지는지, 중복 진입이 하나로 합쳐지는지 | `queue-enqueue.js` |
| **Q2** | 발급률 상한 | 1만 명 버스트 → 스케줄러가 batch-size 속도로만 토큰을 발급하는지(주문 유입이 평탄한지) | `queue-admission.js` |
| **Q3** | 토큰 만료 | 토큰 발급 후 미사용 → TTL 초과 시 주문이 거부되는지 | `queue-token-ttl.js` |
| **Q4** | 최종 방어 | 토큰 보유자 다수가 동시 주문 → 커넥션 풀(40)이 동시성을 상한하고 초과분은 3초 대기로 흡수하는지(붕괴·타임아웃 폭증 없이) | `queue-order-burst.js` |

## 관측 지표

부하 중 아래를 함께 수집한다. 트레이싱이 아니라 메터로 충분하다(기존 Micrometer + Prometheus).

| 지표 | 소스 | 보는 이유 |
| --- | --- | --- |
| 주문 처리시간 p50/p95/p99 | Micrometer `Timer` | 지연이 급증하는 지점 탐지 |
| HikariCP active/pending | `hikaricp_connections_*` | 커넥션 풀 포화 여부 |
| 입장(발급) 수 / 초 | Micrometer `Counter` | 실제 발급률이 batch-size와 맞는지 |
| Queue Depth | `ZCARD` | 유입 > 처리량이면 계속 증가 |
| 커넥션 풀 대기/타임아웃 | HikariCP (`hikaricp_connections_pending`, `_timeout_total`) | 풀이 버스트를 흡수하는지(대기)·과부하로 넘치는지(타임아웃) |

## 전제 환경

1. infra 기동: `docker-compose -f docker/infra-compose.yml up` (MySQL/Redis/Kafka)
2. commerce-api 기동(`local`)
3. 시드: 로그인 유저 다수(`X-USER-ID`용)와 주문 가능한 상품·재고. `local`은 `ddl-auto: create`라 매 기동 초기화되니 실행 전 확인.
4. 대기열 설정은 `order-queue.admission.*`를 시나리오별로 바꿔가며 스윕한다.

## 결과 기록 틀

| 실행일 | interval-ms | batch-size | 안전 TPS | p99 | 비고 |
| --- | --- | --- | --- | --- | --- |
| | | | | | |

> 측정 후 `01-requirements.md`의 175/35 초기값을 실측치로 갱신하고, 그 근거(한계가 나온 지점·지표)를 이 표에 남긴다.
