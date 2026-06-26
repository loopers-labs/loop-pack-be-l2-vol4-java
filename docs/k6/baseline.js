// =============================================================================
// C1 — 처리량/지연 베이스라인
// 정상 PG 상태에서 "주문 생성 → 결제 요청"을 반복해 TPS·p95/p99 기준선을 잰다.
// 장애 주입 없음. 다른 시나리오(resilience) 결과를 해석할 기준점이 된다.
// 실행: k6 run docs/k6/baseline.js
// =============================================================================

import { check, sleep } from 'k6';
import { Trend } from 'k6/metrics';
import { createOrder, pay } from './lib/common.js';

// 결제 요청만의 지연을 따로 본다(주문 생성 지연과 섞이지 않게).
const payLatency = new Trend('pay_latency', true);

export const options = {
  // 부하 프로파일: 30s 동안 20 VU 까지 올리고, 1분 유지, 30s 하강.
  stages: [
    { duration: '30s', target: 20 },
    { duration: '1m', target: 20 },
    { duration: '30s', target: 0 },
  ],
  // 통과 기준(SLO 후보). 넘으면 k6 가 실패로 표시 → 기준선 합의에 사용.
  thresholds: {
    pay_latency: ['p(95)<1000', 'p(99)<2000'], // 결제 접수 p95<1s, p99<2s
    checks: ['rate>0.99'],                       // 체크 99% 이상 통과
  },
};

export default function () {
  // 1) 결제할 주문을 만든다(매 반복 새 주문 — 주문당 결제 1건 규칙 때문).
  const order = createOrder();
  if (!check(order, { '주문 생성 성공': (o) => o.ok })) {
    return; // 주문 실패면 이 반복은 결제까지 가지 않음
  }

  // 2) 결제 요청 → 접수(PENDING) 기대.
  const res = pay(order.orderNumber);
  payLatency.add(res.timings.duration);
  check(res, {
    '결제 접수(200)': (r) => r.status === 200,
    '상태 PENDING': (r) => r.json('data.status') === 'PENDING',
  });

  sleep(1); // VU 당 think-time(과도한 핫루프 방지)
}
