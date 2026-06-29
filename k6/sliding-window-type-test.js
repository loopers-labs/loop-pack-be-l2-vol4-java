/**
 * COUNT_BASED vs TIME_BASED 슬라이딩 윈도우 비교 테스트
 *
 * 사전 조건:
 *   1. commerce-api, pg-simulator 실행 중 (pg.slow-mode=false, PG 에러율 40%)
 *   2. DB에 테스트 유저 + 상품 존재
 *
 * 실행:
 *   k6 run -e BASE_URL=http://localhost:8080 \
 *           -e LOGIN_ID=testuser \
 *           -e LOGIN_PW=password123 \
 *           -e PRODUCT_ID=<id> \
 *           k6/sliding-window-type-test.js
 *
 * 시나리오:
 *   Phase 1 (0~5s)  : 15 req/s — 실패 누적, CB OPEN 유도
 *   Phase 2 (5~20s) : 0 req/s  — 완전 중단 (15초 pause)
 *   Phase 3 (20~40s): 5 req/s  — 재개
 *
 * 기대 차이:
 *   COUNT_BASED: Phase 2 동안 창이 그대로 유지 → Phase 3 첫 호출부터 CB 여전히 OPEN(or 즉시 재열림)
 *   TIME_BASED:  Phase 2 동안 10초 창이 만료   → Phase 3 초반 창이 비어 CB가 잠시 CLOSED
 */

import http from 'k6/http';
import { check } from 'k6';
import { Counter, Rate, Trend } from 'k6/metrics';
import exec from 'k6/execution';

const BASE_URL   = __ENV.BASE_URL   || 'http://localhost:8080';
const LOGIN_ID   = __ENV.LOGIN_ID   || 'testuser';
const LOGIN_PW   = __ENV.LOGIN_PW   || 'password123';
const PRODUCT_ID = Number(__ENV.PRODUCT_ID || '1');

const cbOpenCount   = new Counter('cb_open_count');
const cbOpenRate    = new Rate('cb_open_rate');
const payDuration   = new Trend('pay_duration', true);
const phase1Success = new Rate('phase1_success');
const phase3Success = new Rate('phase3_success');

export const options = {
  scenarios: {
    phase1_burst: {
      executor: 'constant-arrival-rate',
      rate: 15,
      timeUnit: '1s',
      duration: '5s',
      preAllocatedVUs: 20,
      startTime: '0s',
    },
    // phase 2: 15초 완전 중단 (5s~20s) — 시나리오 없음
    phase3_resume: {
      executor: 'constant-arrival-rate',
      rate: 5,
      timeUnit: '1s',
      duration: '20s',
      preAllocatedVUs: 10,
      startTime: '20s',
    },
  },
  thresholds: {
    'http_req_duration': ['p(95)<5000'],
  },
};

const HEADERS = {
  'Content-Type': 'application/json',
  'X-Loopers-LoginId': LOGIN_ID,
  'X-Loopers-LoginPw': LOGIN_PW,
};

function pay() {
  const orderRes = http.post(
    `${BASE_URL}/api/v1/orders`,
    JSON.stringify({ items: [{ productId: PRODUCT_ID, quantity: 1 }] }),
    { headers: HEADERS }
  );
  if (orderRes.status !== 200) return null;
  const orderId = orderRes.json('data.id');

  const start = Date.now();
  const res = http.post(
    `${BASE_URL}/api/v1/payments`,
    JSON.stringify({ orderId: String(orderId), cardType: 'SAMSUNG', cardNo: '1234-5678-9012-3456' }),
    { headers: HEADERS, timeout: '6s' }
  );
  const elapsed = Date.now() - start;
  payDuration.add(elapsed);

  const isCbOpen = res.status === 503;
  cbOpenRate.add(isCbOpen ? 1 : 0);
  if (isCbOpen) {
    cbOpenCount.add(1);
    console.log(`[CB OPEN] ${elapsed}ms`);
  } else {
    console.log(`[${res.status}] ${elapsed}ms`);
  }

  check(res, { '응답 있음': (r) => r.status !== 0 });
  return res;
}

export default function () {
  const res = pay();

  // 현재 시나리오 이름으로 phase 구분
  const scenario = exec.scenario.name;
  if (scenario === 'phase1_burst' && res) {
    phase1Success.add(res.status === 200 ? 1 : 0);
  }
  if (scenario === 'phase3_resume' && res) {
    phase3Success.add(res.status === 200 ? 1 : 0);
  }
}
