/**
 * 결제 과부하 테스트 — k6
 *
 * 사전 준비
 * 1. k6 설치: https://k6.io/docs/getting-started/installation/
 * 2. commerce-api + pg-simulator 실행
 * 3. DB에 테스트 유저 1명 + 상품 1개 존재
 * 4. 시나리오 3(Race Condition)용 주문을 PENDING_PAYMENT 상태로 1개 미리 생성 후 ID 확인
 *
 * 실행 (전체)
 * k6 run \
 *   -e BASE_URL=http://localhost:8080 \
 *   -e LOGIN_ID=testuser \
 *   -e LOGIN_PW=password123 \
 *   -e PRODUCT_ID=1 \
 *   -e RACE_ORDER_ID=42 \
 *   k6/payment-load-test.js
 *
 * 시나리오 5 (slowCall CB) 단독 실행 방법
 * 1. pg-simulator application.yml에서 pg.slow-mode=true 설정 후 재시작
 * 2. k6 run --include-default-scenarios=false -e SCENARIO=slow_call_cb \
 *      -e BASE_URL=http://localhost:8080 ... k6/payment-load-test.js
 *    (주의: --include-default-scenarios 옵션은 k6 0.40+ 필요)
 *    또는 시나리오 함수만 직접 export해서 실행
 */

import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter, Rate, Trend } from 'k6/metrics';

// ── 환경변수 ────────────────────────────────────────────────────────────────
const BASE_URL    = __ENV.BASE_URL    || 'http://localhost:8080';
const LOGIN_ID    = __ENV.LOGIN_ID    || 'testuser';
const LOGIN_PW    = __ENV.LOGIN_PW    || 'password123';
const PRODUCT_ID  = Number(__ENV.PRODUCT_ID  || '1');
const RACE_ORDER_ID = Number(__ENV.RACE_ORDER_ID || '0'); // 시나리오 3 전용

// ── 커스텀 메트릭 ────────────────────────────────────────────────────────────
const paymentSuccess   = new Rate('payment_success_rate');
const fallbackHitRate  = new Rate('fallback_hit_rate');       // CB OPEN → Fallback 비율 (에러율 기반 CB)
const cbOpenCount      = new Counter('cb_open_count');        // Fallback 발동 횟수 (에러율 기반 CB)
const pgDuration       = new Trend('pg_call_duration', true); // PG 포함 결제 응답 시간
const slowCbHitRate    = new Rate('slow_cb_hit_rate');        // slowCall CB OPEN → Fallback 비율
const slowCbOpenCount  = new Counter('slow_cb_open_count');   // slowCall CB Fallback 발동 횟수

// ── 시나리오 설정 ─────────────────────────────────────────────────────────────
export const options = {
  scenarios: {
    /**
     * 시나리오 1 — 기준선 (Baseline)
     * 목적: 정상 부하에서의 응답 시간·성공률 기준값 측정
     * VU 5개 × 30초 → PG 40% 실패, 100~500ms 지연이 기본 흐름에 미치는 영향 확인
     */
    baseline: {
      executor: 'constant-vus',
      exec: 'fullFlow',
      vus: 5,
      duration: '30s',
      tags: { scenario: 'baseline' },
    },

    /**
     * 시나리오 2 — 커넥션 풀 포화 (Connection Pool Saturation)
     * 목적: HikariCP maximum-pool-size(40)을 초과하는 동시 요청으로 풀 고갈 임계 확인
     * VU 50개 → 풀(40)을 10개 초과. connection-timeout(3s) 내에 에러가 발생해야 함
     * 관찰 포인트: p95 응답 시간이 3~4초 이내인지, 무기한 대기 없이 실패하는지
     */
    connection_pool_saturation: {
      executor: 'constant-vus',
      exec: 'fullFlow',
      vus: 50,
      duration: '30s',
      startTime: '35s',
      tags: { scenario: 'conn_pool' },
    },

    /**
     * 시나리오 3 — 동일 주문 동시 결제 (Race Condition / 비관적 락 검증)
     * 목적: 같은 orderId에 10개 요청이 동시에 들어올 때 비관적 락이 1건만 통과시키는지 검증
     * 기대 결과: 200 성공 1건, 400(BAD_REQUEST: 결제를 시작할 수 없는 주문 상태) 9건
     * 사전 조건: RACE_ORDER_ID = PENDING_PAYMENT 상태인 주문 ID
     */
    race_condition: {
      executor: 'shared-iterations',
      exec: 'raceCondition',
      vus: 10,
      iterations: 10,
      startTime: '70s',
      maxDuration: '15s',
      tags: { scenario: 'race' },
    },

    /**
     * 시나리오 4 — CircuitBreaker 오픈 유발 (CB Open, 에러율 기반)
     * 목적: 초당 30 요청으로 PG 실패율 50% 임계를 넘겨 CB가 열리는지 확인
     * CB 설정: failure-rate-threshold=60 → 20건 중 13건 이상 실패 시 OPEN
     * PG 에러율 40%이므로 운에 따라 열릴 수 있음. 빠른 속도로 반복해 유도
     * CB OPEN 판별: 응답 시간 < 50ms (PG 호출이 없으므로 즉시 응답)
     * 관찰 포인트: cb_open_count 카운터 증가, fallback_hit_rate 상승
     */
    circuit_breaker: {
      executor: 'constant-arrival-rate',
      exec: 'triggerCircuitBreaker',
      rate: 30,
      timeUnit: '1s',
      duration: '30s',
      preAllocatedVUs: 40,
      startTime: '90s',
      tags: { scenario: 'cb' },
    },

    /**
     * 시나리오 5 — slowCall CB 오픈 유발 (에러 없이 느린 응답만으로 CB OPEN)
     * 목적: failure-rate가 0%여도 slow-call-rate-threshold(50%)를 넘으면 CB가 열리는지 확인
     *
     * 사전 조건 (필수):
     *   pg-simulator/application.yml 에서 pg.slow-mode=true 설정 후 pg-simulator 재시작
     *   → PG 에러 없음, 응답 400~550ms (300ms 기준 초과 → 전부 '느린 호출'로 집계)
     *
     * 기대 결과:
     *   - 20건 집계 시점에 slowCallRate = 100% (>> 50% 임계) → CB OPEN
     *   - CB OPEN 이후: 응답 시간 < 50ms (PG를 아예 안 부르므로 즉시 Fallback)
     *   - 에러율은 0%인데 CB가 열림 → slowCallDurationThreshold 효과 확인
     *
     * 관찰 포인트:
     *   - 처음 20건은 400~550ms (창 채우는 중)
     *   - 21번째 즈음부터 CB OPEN → 응답 시간이 50ms 이하로 급락
     *   - slow_cb_open_count 카운터 증가
     */
    slow_call_cb: {
      executor: 'constant-arrival-rate',
      exec: 'triggerSlowCallCircuitBreaker',
      rate: 20,
      timeUnit: '1s',
      duration: '30s',
      preAllocatedVUs: 30,
      startTime: '125s',
      tags: { scenario: 'slow_cb' },
    },
  },

  thresholds: {
    // 기준선: p99 < 2s (PG 최대 지연 500ms + TimeLimiter 600ms + 마진)
    'http_req_duration{scenario:baseline}': ['p(99)<2000'],

    // 커넥션 풀 포화: connection-timeout(3s) + 마진 → p95 < 4s
    // 이 임계를 넘기면 무기한 대기가 발생하고 있다는 신호
    'http_req_duration{scenario:conn_pool}': ['p(95)<4000'],

    // CB OPEN 이후 Fallback: p50 < 200ms (PG 호출 없이 즉시 응답해야 함)
    'http_req_duration{scenario:cb}': ['p(50)<200'],

    // slowCall CB: 창(20건) 채운 후 CB가 열렸으면 p75 < 200ms (대부분 즉시 Fallback)
    'http_req_duration{scenario:slow_cb}': ['p(75)<200'],
  },
};

// ── 공통 헤더 ─────────────────────────────────────────────────────────────────
const AUTH_HEADERS = {
  'Content-Type': 'application/json',
  'X-Loopers-LoginId': LOGIN_ID,
  'X-Loopers-LoginPw': LOGIN_PW,
};

// ── 헬퍼 ──────────────────────────────────────────────────────────────────────

/** 주문 생성 → orderId 반환. 실패 시 null */
function createOrder() {
  const res = http.post(
    `${BASE_URL}/api/v1/orders`,
    JSON.stringify({
      items: [{ productId: PRODUCT_ID, quantity: 1 }],
    }),
    { headers: AUTH_HEADERS }
  );

  const ok = check(res, {
    'order created (200)': (r) => r.status === 200,
  });

  if (!ok) return null;
  return res.json('data.id');
}

/** 결제 요청 → 응답 반환 */
function requestPayment(orderId) {
  const start = Date.now();
  const res = http.post(
    `${BASE_URL}/api/v1/payments`,
    JSON.stringify({
      orderId: orderId,
      cardType: 'SAMSUNG',
      cardNo: '1234-5678-9012-3456',
    }),
    { headers: AUTH_HEADERS, timeout: '10s' }
  );
  pgDuration.add(Date.now() - start);
  return res;
}

// ── 시나리오 함수 ─────────────────────────────────────────────────────────────

/**
 * 시나리오 1 & 2: 주문 생성 → 결제 전체 흐름
 * baseline, connection_pool_saturation 에서 공용
 */
export function fullFlow() {
  const orderId = createOrder();
  if (!orderId) {
    sleep(0.5);
    return;
  }

  const res = requestPayment(orderId);

  const succeeded = check(res, {
    'payment 200': (r) => r.status === 200,
  });
  paymentSuccess.add(succeeded ? 1 : 0);

  sleep(0.1);
}

/**
 * 시나리오 3: 동일 주문에 동시 결제 요청
 * 10개 VU가 동시에 같은 orderId를 결제 시도
 * 비관적 락(SELECT FOR UPDATE)이 동작하면 1개만 성공, 9개는 400
 */
export function raceCondition() {
  if (RACE_ORDER_ID === 0) {
    console.error('[race_condition] RACE_ORDER_ID 환경변수를 설정해주세요 (-e RACE_ORDER_ID=<id>)');
    return;
  }

  const res = requestPayment(RACE_ORDER_ID);

  const isSuccess  = res.status === 200;
  const isBlocked  = res.status === 400; // 비관적 락 → 이미 IN_PAYMENT

  check(res, {
    '성공 또는 락 차단(400)': () => isSuccess || isBlocked,
  });

  paymentSuccess.add(isSuccess ? 1 : 0);

  if (isSuccess) {
    console.log(`[race] 승자 VU: orderId=${RACE_ORDER_ID} status=200`);
  }
}

/**
 * 시나리오 4: CB 오픈 유발 (에러율 기반)
 * 주문 생성 → 빠른 결제 요청 반복으로 PG 실패율 60% 초과 유도
 * CB OPEN 판별: 응답 시간이 50ms 미만이면 PG 호출이 없었다는 뜻 → Fallback 발동
 */
export function triggerCircuitBreaker() {
  const orderId = createOrder();
  if (!orderId) return;

  const start = Date.now();
  const res = http.post(
    `${BASE_URL}/api/v1/payments`,
    JSON.stringify({
      orderId: orderId,
      cardType: 'SAMSUNG',
      cardNo: '1234-5678-9012-3456',
    }),
    { headers: AUTH_HEADERS, timeout: '5s' }
  );
  const elapsed = Date.now() - start;
  pgDuration.add(elapsed);

  // PG 최소 지연이 100ms이므로, 50ms 미만 응답 = Fallback(CB OPEN)
  const isFallback = elapsed < 50;
  fallbackHitRate.add(isFallback ? 1 : 0);
  if (isFallback) {
    cbOpenCount.add(1);
    console.log(`[cb] Fallback 발동 — ${elapsed}ms, status=${res.status}`);
  }

  check(res, {
    '응답 있음 (200 or error)': (r) => r.status !== 0,
  });
}

/**
 * 시나리오 5: slowCall CB 오픈 유발 (에러 없이 느린 응답만으로 CB OPEN)
 *
 * 사전 조건: pg-simulator를 pg.slow-mode=true 로 재시작해야 함
 *   → PG 에러 없음, 응답 400~550ms (전부 slow-call-duration-threshold 300ms 초과)
 *
 * 기대 동작:
 *   초반 20건: 400~550ms 응답 (창 채우는 중, slowCallRate=100% 집계)
 *   21번째~:   slowCallRate 100% > threshold 50% → CB OPEN
 *   CB OPEN 후: 응답 < 50ms (PG 안 부르고 즉시 Fallback)
 *
 * 이 시나리오의 의미: 에러가 전혀 없어도 CB가 열린다 = "느린 PG"도 장애로 인식
 */
export function triggerSlowCallCircuitBreaker() {
  const orderId = createOrder();
  if (!orderId) return;

  const start = Date.now();
  const res = http.post(
    `${BASE_URL}/api/v1/payments`,
    JSON.stringify({
      orderId: orderId,
      cardType: 'SAMSUNG',
      cardNo: '1234-5678-9012-3456',
    }),
    { headers: AUTH_HEADERS, timeout: '5s' }
  );
  const elapsed = Date.now() - start;
  pgDuration.add(elapsed);

  // slow 모드 PG 최소 지연이 400ms이므로, 50ms 미만 = CB OPEN 후 즉시 Fallback
  const isFallback = elapsed < 50;
  slowCbHitRate.add(isFallback ? 1 : 0);
  if (isFallback) {
    slowCbOpenCount.add(1);
    console.log(`[slow_cb] slowCall CB Fallback 발동 — ${elapsed}ms, status=${res.status}`);
  } else {
    console.log(`[slow_cb] PG 응답 — ${elapsed}ms, status=${res.status}`);
  }

  check(res, {
    '응답 있음 (200 or error)': (r) => r.status !== 0,
  });
}
