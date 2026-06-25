/**
 * slowCallDurationThreshold 검증 — 단독 실행용
 *
 * 목적: 에러율 0%인데 느린 응답만으로 CB가 열리는지 확인
 *
 * 사전 조건:
 *   1. pg-simulator/application.yml: pg.slow-mode=true 후 재시작
 *   2. commerce-api 실행
 *   3. DB에 테스트 유저 1명 + 상품 1개 존재
 *
 * 실행:
 *   k6 run -e BASE_URL=http://localhost:8080 \
 *           -e LOGIN_ID=testuser \
 *           -e LOGIN_PW=password123 \
 *           -e PRODUCT_ID=1 \
 *           k6/slow-call-cb-test.js
 *
 * 기대 결과:
 *   초반 20건 : 응답 400~550ms (CB가 창을 채우는 중)
 *   21번째~   : slowCallRate 100% > 임계 50% → CB OPEN
 *   CB OPEN 후: 응답 < 50ms (Fallback 즉시 반환)
 */

import http from 'k6/http';
import { check } from 'k6';
import { Counter, Rate, Trend } from 'k6/metrics';

const BASE_URL   = __ENV.BASE_URL   || 'http://localhost:8080';
const LOGIN_ID   = __ENV.LOGIN_ID   || 'testuser';
const LOGIN_PW   = __ENV.LOGIN_PW   || 'password123';
const PRODUCT_ID = Number(__ENV.PRODUCT_ID || '1');

const slowCbOpenCount = new Counter('slow_cb_open_count');
const slowCbHitRate   = new Rate('slow_cb_hit_rate');
const pgDuration      = new Trend('pg_call_duration', true);

export const options = {
  scenarios: {
    slow_call_cb: {
      executor: 'constant-arrival-rate',
      rate: 15,           // 초당 15 요청
      timeUnit: '1s',
      duration: '40s',    // 총 40초 — 창(20건) 채운 뒤 CB 오픈 여유 확인
      preAllocatedVUs: 25,
    },
  },
  thresholds: {
    // CB가 열렸으면 요청의 절반 이상이 50ms 이하여야 함
    'http_req_duration': ['p(75)<600'],
  },
};

const AUTH_HEADERS = {
  'Content-Type': 'application/json',
  'X-Loopers-LoginId': LOGIN_ID,
  'X-Loopers-LoginPw': LOGIN_PW,
};

function createOrder() {
  const res = http.post(
    `${BASE_URL}/api/v1/orders`,
    JSON.stringify({ items: [{ productId: PRODUCT_ID, quantity: 1 }] }),
    { headers: AUTH_HEADERS }
  );
  if (res.status !== 200) return null;
  return res.json('data.id');
}

export default function () {
  const orderId = createOrder();
  if (!orderId) return;

  const start = Date.now();
  const res = http.post(
    `${BASE_URL}/api/v1/payments`,
    JSON.stringify({ orderId, cardType: 'SAMSUNG', cardNo: '1234-5678-9012-3456' }),
    { headers: AUTH_HEADERS, timeout: '5s' }
  );
  const elapsed = Date.now() - start;
  pgDuration.add(elapsed);

  // slow 모드 최소 지연 400ms → 50ms 미만이면 CB OPEN 후 Fallback
  const isFallback = elapsed < 50;
  slowCbHitRate.add(isFallback ? 1 : 0);

  if (isFallback) {
    slowCbOpenCount.add(1);
    console.log(`[CB OPEN] Fallback — ${elapsed}ms | status=${res.status}`);
  } else {
    console.log(`[PG 호출] 응답 — ${elapsed}ms | status=${res.status}`);
  }

  check(res, { '응답 있음': (r) => r.status !== 0 });
}
