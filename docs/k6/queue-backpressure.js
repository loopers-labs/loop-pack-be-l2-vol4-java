// =============================================================================
// 대기열을 거친 back-pressure 검증
//   유저 VU 가 enter → 순번 폴링(pollAfterMs 준수) → 토큰 수신 후
//     70% 는 주문(그중 절반은 쿠폰 사용), 30% 는 토큰만 들고 만료.
//   유입은 스케줄러 방출률(10/s)을 크게 넘긴다 — 대기열은 쌓이되
//   order API 는 한계(12/s) 아래에서 평탄해야 한다.
//
// 전제: commerce-api 컨테이너 batch-size=2, 시드(load-1..1000, 유저별 쿠폰 1장)
// 실행: k6 run docs/k6/queue-backpressure.js   (VUS/DURATION 은 env 로 조절)
// =============================================================================

import http from 'k6/http';
import { sleep } from 'k6';
import { Trend, Rate, Counter } from 'k6/metrics';

const BASE = __ENV.BASE_URL || 'http://localhost:8080';
const PRODUCT_ID = Number(__ENV.PRODUCT_ID || 131075);
const VUS = Number(__ENV.VUS || 300);
const MAX_WAIT_S = Number(__ENV.MAX_WAIT_S || 120); // 토큰 대기 상한
const USER_BASE = Number(__ENV.USER_BASE || 2);     // load-1 의 userId (VU N → userId N+1)
const ENTER_SPREAD_S = Number(__ENV.ENTER_SPREAD_S || 6); // 진입 분산 구간(초) — 순간 커넥션 스파이크 완화
const HOLD_RATIO = Number(__ENV.HOLD_RATIO ?? 0.3);       // 토큰만 들고 주문 안 하는 비율(0 이면 전원 주문)

const timeToToken = new Trend('time_to_token', true);
const orderLatency = new Trend('order_latency', true);
const orderOk = new Rate('order_ok');
const ordered = new Counter('users_ordered');
const held = new Counter('users_held');
const couponUsed = new Counter('orders_with_coupon');

export const options = {
  scenarios: {
    flood: {
      executor: 'per-vu-iterations',
      vus: VUS,
      iterations: 1,
      maxDuration: `${MAX_WAIT_S + 60}s`,
    },
  },
};

function headers(extra) {
  // 로그인된 세션 상태를 가정 — dev 헤더 인증(X-USER-ID)으로 폴링마다 도는 BCrypt 를 우회한다
  return Object.assign({
    'Content-Type': 'application/json',
    'X-USER-ID': String(USER_BASE + __VU - 1),
  }, extra);
}

export default function () {
  // 진입을 수 초에 걸쳐 분산 — 수백~천 VU 가 동시에 커넥션을 열어 호스트(Colima)를 넘어뜨리는 것을 막는다.
  // 유입 속도(≈VUS/ENTER_SPREAD_S)는 방출률을 여전히 크게 넘어 큐는 깊게 쌓인다.
  sleep(Math.random() * ENTER_SPREAD_S);

  // 1) 진입 — 초반에 몰려 유입이 방출률을 크게 넘는다
  const enterRes = http.post(`${BASE}/api/v1/queue/enter`, null, { headers: headers(), tags: { name: 'enter' } });
  if (enterRes.status !== 200) return;
  const enteredAt = Date.now();

  // 2) 순번 폴링 — 서버가 주는 pollAfterMs 를 그대로 따른다
  let token = null;
  while (Date.now() - enteredAt < MAX_WAIT_S * 1000) {
    const res = http.get(`${BASE}/api/v1/queue/position`, { headers: headers(), tags: { name: 'position' } });
    if (res.status === 200) {
      const data = res.json('data');
      if (data.token) { token = data.token; break; }
      sleep((data.pollAfterMs || 2000) / 1000);
    } else {
      sleep(2);
    }
  }
  if (!token) return; // 대기 상한 초과
  timeToToken.add(Date.now() - enteredAt);

  // 3) 주문 / 보유 후 만료 (HOLD_RATIO 로 조절 — 0 이면 전원 주문)
  if (Math.random() < HOLD_RATIO) { held.add(1); return; }

  // 주문자의 절반은 쿠폰 사용
  let userCouponId = null;
  if (Math.random() < 0.5) {
    const res = http.get(`${BASE}/api/v1/users/me/coupons`, { headers: headers(), tags: { name: 'coupons' } });
    const coupons = res.status === 200 ? res.json('data.coupons') : null;
    const available = (coupons || []).find((c) => c.status === 'AVAILABLE');
    if (available) userCouponId = available.id;
  }

  const body = JSON.stringify({
    items: [{ productId: PRODUCT_ID, quantity: 1 }],
    recipientName: '부하', recipientPhone: '010-0000-0000',
    zipcode: '12345', address1: '서울', address2: '101',
    userCouponId,
  });
  const res = http.post(`${BASE}/api/v1/orders`, body, {
    headers: headers({ 'X-Entry-Token': token }), tags: { name: 'order' },
  });
  orderLatency.add(res.timings.duration);
  orderOk.add(res.status === 200);
  ordered.add(1);
  if (res.status === 200 && userCouponId) couponUsed.add(1);
}

// batch-size 비교용 한 줄 요약 — 스케줄러 방출률이 다를 때 order API 가 얼마나 건강한지 본다.
export function handleSummary(data) {
  const m = data.metrics;
  const g = (n, f) => (m[n] && m[n].values && m[n].values[f] != null ? m[n].values[f] : 0);
  const r = (x) => Math.round(x * 100) / 100;
  const line =
    `RESULT ordered=${g('users_ordered', 'count')} held=${g('users_held', 'count')} ` +
    `coupon=${g('orders_with_coupon', 'count')} okrate=${r(g('order_ok', 'rate') * 100)}% ` +
    `order_p50=${r(g('order_latency', 'med'))}ms order_p95=${r(g('order_latency', 'p(95)'))}ms ` +
    `ttt_p95=${r(g('time_to_token', 'p(95)'))}ms`;
  return { stdout: line + '\n' };
}
