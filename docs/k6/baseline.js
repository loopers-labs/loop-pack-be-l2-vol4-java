// =============================================================================
// C1 — 처리량/지연 베이스라인
// 정상 부하에서 "주문 생성 → 결제 요청"을 반복해 TPS·p95/p99 기준선을 잰다.
//
// 주의: pg-simulator 는 동기 POST 에서 ~30% 무작위로 500 을 던진다(내장 불안정).
//   500 이면 PG 에 거래가 안 생기고 → 우리 결제는 PENDING(키없음) 으로 남아 reconciler 가 정리한다.
//   따라서 "100% 200"은 불가능 — 접수율은 '관측 지표'로 두고, 단언은
//   (1) 우리 시스템 응답성(지연), (2) 응답이 항상 "정상접수 또는 알려진 PG오류"(이상 에러 없음) 로 한다.
// 실행: k6 run docs/k6/baseline.js   (스모크: k6 run --vus 5 --duration 15s ...)
// =============================================================================

import { check, sleep } from 'k6';
import { Trend, Rate } from 'k6/metrics';
import { createOrder, pay } from './lib/common.js';

const payLatency = new Trend('pay_latency', true);
const acceptRate = new Rate('pay_accept_rate'); // 접수(200 PENDING) 비율 — PG 불안정 반영, 관측용

export const options = {
  stages: [
    { duration: '30s', target: 20 },
    { duration: '1m', target: 20 },
    { duration: '30s', target: 0 },
  ],
  thresholds: {
    // 우리 시스템 응답성(SLO 후보). PG 가 500 을 줘도 빨리 돌려줘야 한다.
    pay_latency: ['p(95)<1000', 'p(99)<2000'],
    // 모든 응답은 "정상 접수" 또는 "알려진 PG 오류(500)" 둘 중 하나여야 한다(예상 못 한 에러 0).
    checks: ['rate>0.99'],
  },
};

export default function () {
  // 1) 결제할 주문 생성(매 반복 새 주문 — 주문당 결제 1건 규칙).
  const order = createOrder();
  if (!check(order, { '주문 생성 성공': (o) => o.ok })) return;

  // 2) 결제 요청 → 접수(200 PENDING) 기대, 단 PG 불안정으로 500 도 나올 수 있음.
  const res = pay(order.orderNumber);
  payLatency.add(res.timings.duration);

  const accepted = res.status === 200 && res.json('data.status') === 'PENDING';
  acceptRate.add(accepted); // 접수율 관측(예: ~0.7)

  // PG 의 ~30% 500 은 예상된 결과 → "접수 또는 PG 오류(500)"면 정상으로 본다.
  check(res, {
    '예상된 응답(접수 또는 PG 500)': (r) => accepted || r.status === 500,
  });

  sleep(1);
}
