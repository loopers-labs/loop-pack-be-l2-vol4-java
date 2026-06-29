/**
 * 커넥션 풀 포화 테스트 — HikariCP 한계 측정
 *
 * 실행 전 준비:
 *   CB 비활성화 상태로 commerce-api 재시작
 *   ./gradlew :apps:commerce-api:bootRun --args='--spring.cloud.openfeign.circuitbreaker.enabled=false'
 *
 * 실행:
 *   docker run --rm -v "C:\Users\USER\loop-pack-be-l2-vol4-java\k6:/scripts" grafana/k6 run \
 *     -e BASE_URL=http://host.docker.internal:8080 \
 *     -e LOGIN_ID=loadtest -e LOGIN_PW=Pass123! -e PRODUCT_ID=1 \
 *     /scripts/connection-pool-test.js
 *
 * 관찰 포인트:
 *   - HikariCP maximum-pool-size: 40, connection-timeout: 3s
 *   - warmup(20 VU)  → 풀 여유 있음, 빠른 응답 기대
 *   - saturation(50 VU) → 풀 초과, 3s 대기 후 타임아웃 에러 발생 가능
 *   - overload(80 VU)   → 풀의 2배, 타임아웃 에러 급증 기대
 *   - payment_duration p95가 단계별로 어떻게 올라가는지 비교
 */

import http from 'k6/http';
import { check } from 'k6';
import { Rate, Trend } from 'k6/metrics';

const BASE_URL   = __ENV.BASE_URL   || 'http://localhost:8080';
const LOGIN_ID   = __ENV.LOGIN_ID   || 'loadtest';
const LOGIN_PW   = __ENV.LOGIN_PW   || 'Pass123!';
const PRODUCT_ID = Number(__ENV.PRODUCT_ID || '1');

const paymentDuration     = new Trend('payment_duration', true);
const orderSuccessRate    = new Rate('order_success_rate');
const poolTimeoutRate     = new Rate('pool_timeout_rate'); // 3s 초과 = 풀 고갈 의심

export const options = {
  scenarios: {
    /**
     * Phase 1 — 워밍업 (20 VU)
     * 풀(40) 여유 있음 → 정상 응답 기대
     */
    warmup: {
      executor: 'constant-vus',
      exec: 'fullFlow',
      vus: 20,
      duration: '20s',
      tags: { phase: 'warmup' },
    },

    /**
     * Phase 2 — 포화 (50 VU, 풀 40 초과)
     * 결제 요청 시 PG 응답 대기(100~500ms) 동안 커넥션 점유
     * → 10개가 풀 밖에서 대기, connection-timeout(3s) 이내 실패해야 함
     */
    saturation: {
      executor: 'constant-vus',
      exec: 'fullFlow',
      vus: 50,
      duration: '30s',
      startTime: '25s',
      tags: { phase: 'saturation' },
    },

    /**
     * Phase 3 — 과부하 (80 VU, 풀의 2배)
     * 타임아웃 에러 급증, pool_timeout_rate 상승 기대
     */
    overload: {
      executor: 'constant-vus',
      exec: 'fullFlow',
      vus: 80,
      duration: '20s',
      startTime: '60s',
      tags: { phase: 'overload' },
    },
  },

  thresholds: {
    // 워밍업: p95 < 2s (PG 최대 지연 500ms + TimeLimiter 600ms + 여유)
    'payment_duration{phase:warmup}': ['p(95)<2000'],
    // 포화: connection-timeout(3s) 안에 응답 or 실패해야 함 → p95 < 4s
    'payment_duration{phase:saturation}': ['p(95)<4000'],
  },
};

const AUTH_HEADERS = {
  'Content-Type': 'application/json',
  'X-Loopers-LoginId': LOGIN_ID,
  'X-Loopers-LoginPw': LOGIN_PW,
};

export function fullFlow() {
  // 1. 주문 생성
  const orderRes = http.post(
    `${BASE_URL}/api/v1/orders`,
    JSON.stringify({ items: [{ productId: PRODUCT_ID, quantity: 1 }] }),
    { headers: AUTH_HEADERS }
  );

  const orderOk = check(orderRes, { '주문 생성 200': (r) => r.status === 200 });
  orderSuccessRate.add(orderOk ? 1 : 0);

  if (!orderOk) {
    console.log(`[pool] 주문 생성 실패: status=${orderRes.status}`);
    return;
  }

  const orderId = orderRes.json('data.id');

  // 2. 결제 요청 — PG 호출(100~500ms) 동안 DB 커넥션 점유
  //    50+ VU가 동시에 이 구간에 있으면 풀(40) 초과 → 대기 발생
  const start = Date.now();
  const payRes = http.post(
    `${BASE_URL}/api/v1/payments`,
    JSON.stringify({
      orderId,
      cardType: 'SAMSUNG',
      cardNo:   '1234-5678-9012-3456',
    }),
    { headers: AUTH_HEADERS, timeout: '15s' }
  );
  const elapsed = Date.now() - start;
  paymentDuration.add(elapsed);

  // 3s 초과 응답 = 커넥션 풀 대기(connection-timeout) 의심
  const isPoolTimeout = elapsed >= 3000;
  poolTimeoutRate.add(isPoolTimeout ? 1 : 0);

  if (isPoolTimeout) {
    console.log(`[pool] 커넥션 대기 초과 — phase에서 elapsed=${elapsed}ms status=${payRes.status}`);
  }

  check(payRes, {
    '결제 응답 수신': (r) => r.status !== 0,
  });
}
