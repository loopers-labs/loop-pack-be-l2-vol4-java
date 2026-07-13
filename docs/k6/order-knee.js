// =============================================================================
// order API 한계 TPS(knee) 측정
//   dev/token 으로 큐를 우회해 토큰만 즉시 받고 → POST /orders 를 램프업하며
//   지연(p95/p99)이 꺾이고 에러가 생기는 지점(안전 처리량 한계)을 찾는다.
//   이 값의 70% 가 스케줄러 batch-size 근거가 된다.
//
// 전제: commerce-api(컨테이너, cpu/mem limit) + mysql(limit) + 시드(상품 131075, load-1..1000)
// 실행: k6 run docs/k6/order-knee.js
//   빠른 스모크: k6 run --stage 10s:30 docs/k6/order-knee.js
// =============================================================================

import http from 'k6/http';
import { check } from 'k6';
import { Trend, Rate, Counter } from 'k6/metrics';

const BASE = __ENV.BASE_URL || 'http://localhost:8080';
const PRODUCT_ID = Number(__ENV.PRODUCT_ID || 131075);
const USERS = Number(__ENV.USERS || 1000);

const orderLatency = new Trend('order_latency', true);
const orderOk = new Rate('order_ok');
// 신뢰 가능한 집계를 위한 명시 카운터 — ok%/지연만으로는 "건강한 처리량"과
// "거의 안 돌아간 표본"을 구분할 수 없다(토큰 실패로 조기 return + 드롭된 iteration).
const orderAttempts = new Counter('order_attempts'); // POST /orders 실제 시도 수
const order2xx = new Counter('order_2xx');            // 그중 200
const tokenFail = new Counter('token_fail');          // 토큰을 못 받아 주문에 도달 못한 수

// RATE 를 주면 그 고정 초당 주문율로 DURATION 동안 측정(한계 탐색용).
// 안 주면 계단식 램프.
const RATE = Number(__ENV.RATE || 0);
const DURATION = __ENV.DURATION || '25s';

export const options = RATE > 0
  ? {
      scenarios: {
        fixed: {
          executor: 'constant-arrival-rate',
          rate: RATE,
          timeUnit: '1s',
          duration: DURATION,
          preAllocatedVUs: Math.max(50, RATE),
          maxVUs: Math.max(200, RATE * 4),
        },
      },
    }
  : {
      scenarios: {
        knee: {
          executor: 'ramping-arrival-rate',
          startRate: 20,
          timeUnit: '1s',
          preAllocatedVUs: 100,
          maxVUs: 600,
          stages: [
            { duration: '30s', target: 50 },
            { duration: '30s', target: 100 },
            { duration: '30s', target: 200 },
            { duration: '30s', target: 350 },
            { duration: '30s', target: 500 },
            { duration: '20s', target: 0 },
          ],
        },
      },
    };

function headers(userNo, token) {
  // 로그인된 세션 가정 — dev 헤더 인증. 매 요청 BCrypt(~70ms CPU)가 측정을 오염시키지 않게 한다.
  const h = {
    'Content-Type': 'application/json',
    'X-USER-ID': String(userNo + 1),   // load-N 의 userId = N+1
  };
  if (token) h['X-Entry-Token'] = token;
  return h;
}

export default function () {
  const userNo = ((__VU - 1) % USERS) + 1;

  // 1) 큐 우회 토큰 발급 (order API 순수 측정)
  const tokenRes = http.post(`${BASE}/api/v1/queue/dev/token`, null, { headers: headers(userNo) });
  const token = tokenRes.json('data');
  if (!token) { tokenFail.add(1); return; }

  // 2) 주문
  const body = JSON.stringify({
    items: [{ productId: PRODUCT_ID, quantity: 1 }],
    recipientName: '부하', recipientPhone: '010-0000-0000',
    zipcode: '12345', address1: '서울', address2: '101',
    userCouponId: null,
  });
  const res = http.post(`${BASE}/api/v1/orders`, body, { headers: headers(userNo, token) });

  orderLatency.add(res.timings.duration);
  const ok = res.status === 200;
  orderOk.add(ok);
  orderAttempts.add(1);
  if (ok) order2xx.add(1);
  check(res, { 'order 200': (r) => r.status === 200 });
}

// 한 줄 요약을 stdout 으로 — 스윕 스크립트가 파싱한다.
// attempts/DURATION = 실제 달성 처리량, 2xx/attempts = 진짜 성공률, dropped = k6 가 VU 부족으로 못 쏜 수.
export function handleSummary(data) {
  const m = data.metrics;
  const g = (name, field) => (m[name] && m[name].values && m[name].values[field] != null ? m[name].values[field] : 0);
  const round = (x) => Math.round(x * 100) / 100;
  const line =
    `RESULT attempts=${g('order_attempts', 'count')} ok2xx=${g('order_2xx', 'count')} ` +
    `okrate=${round(g('order_ok', 'rate') * 100)}% ` +
    `p50=${round(g('order_latency', 'med'))}ms p95=${round(g('order_latency', 'p(95)'))}ms ` +
    `dropped=${g('dropped_iterations', 'count')} tokfail=${g('token_fail', 'count')}`;
  return { stdout: line + '\n' };
}
