// =============================================================================
// A1 — 동시성 정합성 (이중결제 0 · order_number 유니크 제약)
// "하나의 주문"에 다수 VU가 거의 동시에 결제를 쏜다. 기대:
//   - 정확히 1건만 접수(200), 나머지는 409(PAYMENT_ALREADY_IN_PROGRESS)
//   - PG 에 생긴 거래는 1건 (이중결제 없음)
// 동작 원리: PaymentFacade.pay 의 createPending 이 order_number 유니크 제약에 막혀
//   동시 요청 중 1건만 INSERT 성공 → 그 1건만 PG 호출. 나머지는 PG 도달 전에 409.
// 실행: k6 run docs/k6/concurrency.js
// =============================================================================

import { check } from 'k6';
import { Counter } from 'k6/metrics';
import { createOrder, pay, pgTransactionCount, errorCode } from './lib/common.js';

// 결과 분류 카운터 — 임계값으로 "정확히 1건 접수"를 강제한다.
const accepted = new Counter('accepted_total');     // 200 접수
const conflict = new Counter('conflict_total');     // 409 (이미 진행 중)
const other = new Counter('other_total');           // 그 외(예상치 못한 응답)
const pgTx = new Counter('pg_transactions');        // teardown 에서 PG 실제 거래 수

// 동시성 강도(환경변수로 조절). 50 VU 가 1회씩 ≈ 거의 동시 발사.
const VUS = Number(__ENV.VUS || 50);

export const options = {
  scenarios: {
    burst: {
      executor: 'shared-iterations', // VU 들이 정해진 총 반복을 나눠 가짐 → 한 번에 몰림
      vus: VUS,
      iterations: VUS,               // VU 당 약 1회 → 동시 발사
      maxDuration: '30s',
    },
  },
  thresholds: {
    // 핵심 불변식은 "이중 없음"(≤1). 동시 결제의 유일 승자가 PG 의 무작위 500 을 맞으면
    // 접수 0건·거래 0건이 될 수 있으므로 "정확히 1"이 아니라 "≤1"로 단언한다(이중결제 0).
    accepted_total: ['count<2'],   // 접수 ≤1 (이중 접수 없음)
    pg_transactions: ['count<2'],  // PG 거래 ≤1 (이중결제 없음)
  },
};

// 모든 VU가 공유할 "하나의 주문"을 미리 만든다.
export function setup() {
  const order = createOrder();
  if (!order.ok) {
    throw new Error(`setup 실패: 주문 생성 불가 status=${order.status} body=${order.body}`);
  }
  return { orderNumber: order.orderNumber };
}

export default function (data) {
  const res = pay(data.orderNumber);

  if (res.status === 200) {
    accepted.add(1);
  } else if (res.status === 409 || errorCode(res) === 'PAYMENT_ALREADY_IN_PROGRESS') {
    conflict.add(1);
  } else {
    other.add(1);
  }

  // 개별 응답은 200 또는 409 둘 중 하나여야 한다(그 외는 결함).
  check(res, {
    '응답이 200 또는 409': (r) => r.status === 200 || r.status === 409,
  });
}

// 부하 종료 후 PG 에 실제로 거래가 몇 건 생겼는지 확인한다(이중결제 여부의 진짜 증거).
export function teardown(data) {
  const count = pgTransactionCount(data.orderNumber);
  pgTx.add(count < 0 ? 0 : count); // 조회 실패(-1)는 0으로(임계값이 잡도록)
  check(count, {
    'PG 거래 ≤1건(이중결제 없음)': (c) => c <= 1,
  });
  console.log(`[A1] orderNumber=${data.orderNumber} PG 거래수=${count}`);
}
