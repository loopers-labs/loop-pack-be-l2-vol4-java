/**
 * Race Condition 테스트 — 비관적 락 단독 검증
 *
 * 실행 전 준비:
 *   CB 비활성화 상태로 commerce-api 재시작
 *   ./gradlew :apps:commerce-api:bootRun --args='--spring.cloud.openfeign.circuitbreaker.enabled=false'
 *
 * 실행:
 *   docker run --rm -v "C:\Users\USER\loop-pack-be-l2-vol4-java\k6:/scripts" grafana/k6 run \
 *     -e BASE_URL=http://host.docker.internal:8080 \
 *     -e LOGIN_ID=loadtest -e LOGIN_PW=Pass123! -e PRODUCT_ID=1 \
 *     /scripts/race-condition-test.js
 *
 * 기대 결과:
 *   - 200(성공) 1건, 400(비관적 락 차단) 9건
 *   - race_success_count = 1
 *   - race_blocked_count = 9
 */

import http from 'k6/http';
import { check } from 'k6';
import { Counter, Trend } from 'k6/metrics';

const BASE_URL   = __ENV.BASE_URL   || 'http://localhost:8080';
const LOGIN_ID   = __ENV.LOGIN_ID   || 'loadtest';
const LOGIN_PW   = __ENV.LOGIN_PW   || 'Pass123!';
const PRODUCT_ID = Number(__ENV.PRODUCT_ID || '1');

const successCount = new Counter('race_success_count');
const blockedCount = new Counter('race_blocked_count');
const otherCount   = new Counter('race_other_count');   // 200도 400도 아닌 응답
const raceDuration = new Trend('race_duration', true);

export const options = {
  scenarios: {
    race_condition: {
      executor: 'shared-iterations',
      exec: 'raceCondition',
      vus: 10,
      iterations: 10,
      maxDuration: '30s',
    },
  },
  thresholds: {
    // 10건 중 최소 9건은 200 또는 400이어야 함 (1건은 성공, 9건은 락 차단)
    checks: ['rate>=0.9'],
    // 락 경합이 있어도 응답은 빠르게 와야 함
    'race_duration': ['p(99)<5000'],
  },
};

const AUTH_HEADERS = {
  'Content-Type': 'application/json',
  'X-Loopers-LoginId': LOGIN_ID,
  'X-Loopers-LoginPw': LOGIN_PW,
};

export function setup() {
  const res = http.post(
    `${BASE_URL}/api/v1/orders`,
    JSON.stringify({ items: [{ productId: PRODUCT_ID, quantity: 1 }] }),
    { headers: AUTH_HEADERS }
  );

  if (res.status !== 200) {
    throw new Error(`주문 생성 실패: status=${res.status} body=${res.body}`);
  }

  const orderId = res.json('data.id');
  console.log(`[setup] PENDING_PAYMENT 주문 생성 완료 → orderId=${orderId}`);
  return { orderId };
}

export function raceCondition(data) {
  const start = Date.now();
  const res = http.post(
    `${BASE_URL}/api/v1/payments`,
    JSON.stringify({
      orderId:  data.orderId,
      cardType: 'SAMSUNG',
      cardNo:   '1234-5678-9012-3456',
    }),
    { headers: AUTH_HEADERS, timeout: '10s' }
  );
  raceDuration.add(Date.now() - start);

  const isSuccess = res.status === 200;
  const isBlocked = res.status === 400;

  check(res, {
    '200(결제 성공) 또는 400(락 차단)': () => isSuccess || isBlocked,
  });

  if (isSuccess) {
    successCount.add(1);
    console.log(`[race] 승자 — orderId=${data.orderId} elapsed=${Date.now() - start}ms`);
  } else if (isBlocked) {
    blockedCount.add(1);
  } else {
    otherCount.add(1);
    console.log(`[race] 예상 외 응답: status=${res.status} body=${res.body.substring(0, 200)}`);
  }
}
