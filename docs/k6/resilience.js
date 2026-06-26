// =============================================================================
// B1/B2 — Resilience (PG 장애 중 내부 보호 + 회복)
// 정상 부하를 꾸준히 흘리는 동안, 래퍼(run-resilience.sh)가 중간에 PG 를 내렸다
// 다시 올린다. 이 스크립트는 "결과를 분류"하고 "내부가 hang 하지 않는지"만 본다.
//
// 기대 타임라인:
//   [정상]      결제 200(PENDING) 성공
//   [PG 다운]   연결 실패 → 빠른 에러. 실패 누적되면 CB OPEN →
//               이후 요청은 PG 도달 없이 즉시 PAYMENT_GATEWAY_UNAVAILABLE (저지연 fast-reject)
//   [PG 복귀]   open 10s 경과 → HALF_OPEN→CLOSED → 다시 200 성공 (B2 회복)
//
// 핵심 단언: 어느 구간에서도 응답이 오래 매달리지 않는다(타임아웃 2s·CB fast-reject 덕).
// 실행: 직접 말고 run-resilience.sh 를 통해(타이밍 조율). 단독 실행도 가능(장애 없이 정상만 관측).
// =============================================================================

import { check } from 'k6';
import { Counter, Trend } from 'k6/metrics';
import { createOrder, pay, errorCode } from './lib/common.js';

const payLatency = new Trend('pay_latency', true);
const success = new Counter('pay_success');               // 200 PENDING
const unavailable = new Counter('pay_unavailable');       // PG 불가(다운/CB OPEN)
const otherErr = new Counter('pay_other_error');          // 그 외 에러

export const options = {
  scenarios: {
    steady: {
      executor: 'constant-vus', // 일정 부하를 장애 구간 내내 흘린다
      vus: Number(__ENV.VUS || 10),
      duration: __ENV.DURATION || '3m',
    },
  },
  thresholds: {
    // 장애 중에도 내부는 빠르게 응답해야 한다(hang 금지). 연결실패·CB fast-reject 라 저지연 기대.
    // 실패 자체는 장애 구간이라 당연하니 성공률은 임계로 걸지 않는다(관측만).
    pay_latency: ['p(95)<3000'],
  },
};

export default function () {
  // 주문 생성도 결제 흐름의 일부 — 다만 PG 장애와 무관(내부 DB 작업)하므로 실패 시 건너뜀.
  const order = createOrder();
  if (!order.ok) return;

  const res = pay(order.orderNumber);
  payLatency.add(res.timings.duration);

  if (res.status === 200 && res.json('data.status') === 'PENDING') {
    success.add(1);
  } else if (errorCode(res) === 'PAYMENT_GATEWAY_UNAVAILABLE') {
    unavailable.add(1); // PG 다운 또는 CB OPEN → 의도된 보호 응답
  } else {
    otherErr.add(1);
  }

  // 응답이 무엇이든 "정상 응답 객체"로 돌아왔는지(=서버가 안 죽고 응답함)만 체크.
  check(res, { '서버가 응답함(무응답·hang 아님)': (r) => r.status !== 0 });
}
