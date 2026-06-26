import http from 'k6/http';
import { Counter } from 'k6/metrics';
import { seed, orderAndPay } from './lib/helpers.js';

/**
 * 서킷브레이커 OPEN 전이를 "확실히" 유발하는 프로파일.
 *
 * 왜 기본 프로파일로는 잘 안 열리나:
 *   - pg-simulator 요청 실패율은 40% 인데 failure-rate-threshold 는 50% → 임계치 미달.
 *   - requestPayment 호출 자체는 <1s(요청지연 100~500ms) 라 slow-call(2s) 도 거의 미발동.
 *
 * 그래서 두 가지를 같이 쓴다:
 *   (a) 부하 버스트(ramping-arrival-rate)로 readTimeout(1s) 실패를 추가 유발 → 실패율 ↑
 *   (b) commerce-api 를 CB 임계치 낮춰 띄우면 결정적으로 OPEN (아래 실행법 참고)
 *
 * ▶ 결정적으로 OPEN 시키려면 commerce-api 를 아래처럼 띄운다(코드 수정 없이 prop override):
 *
 *   ./gradlew :apps:commerce-api:bootRun --args='\
 *     --resilience4j.circuitbreaker.instances.pgClient.failure-rate-threshold=35 \
 *     --resilience4j.circuitbreaker.instances.pgClient.slow-call-duration-threshold=300ms \
 *     --resilience4j.circuitbreaker.instances.pgClient.wait-duration-in-open-state=10s'
 *
 *   - failure-rate-threshold=35  → PG 40% 실패만으로도 OPEN
 *   - slow-call-duration-threshold=300ms → PG 요청지연(100~500ms) 상당수가 slow 로 집계
 *   - wait-duration=10s          → OPEN→HALF_OPEN→CLOSED 전이를 짧은 테스트에서 관찰
 *
 * 실행:
 *   k6 run k6/payment-cb-open.js
 */

const BASE = __ENV.BASE_URL || 'http://localhost:8080';
const MGMT = __ENV.MGMT_URL || 'http://localhost:8081';
const RUN = __ENV.RUN_ID || `cb${Date.now()}`;
const USERS = Number(__ENV.USERS || 100);

const payResult = new Counter('pay_result');
const cbState = new Counter('cb_state');        // tag(state) 별 관측 횟수

export const options = {
  scenarios: {
    // (a) 낮은→높은 부하 버스트로 PG 실패/타임아웃을 몰아 친다.
    burst: {
      executor: 'ramping-arrival-rate',
      startRate: 10,
      timeUnit: '1s',
      preAllocatedVUs: 300,
      maxVUs: 600,
      stages: [
        { target: 20, duration: '15s' },
        { target: 80, duration: '45s' }, // 스파이크 → 윈도우 내 실패율 급등 유도
        { target: 80, duration: '30s' },
        { target: 10, duration: '20s' }, // 회복 구간 → HALF_OPEN/CLOSED 복귀 관찰
      ],
    },
    // (b) 1초마다 서킷브레이커 상태를 폴링해 전이를 로그로 가시화.
    cbWatch: {
      executor: 'constant-arrival-rate',
      exec: 'watch',
      rate: 1,
      timeUnit: '1s',
      duration: '110s',
      preAllocatedVUs: 1,
      maxVUs: 1,
    },
  },
};

export function setup() {
  return seed(BASE, { runId: RUN, users: USERS });
}

export default function (data) {
  const r = orderAndPay(BASE, data);
  if (r.orderFailed) return;
  payResult.add(1, { status: r.status });
}

let lastState = null;
export function watch() {
  const res = http.get(`${MGMT}/actuator/circuitbreakers`);
  if (res.status !== 200) return;
  // actuator/circuitbreakers 응답: { circuitBreakers: { pgClient: { state, failureRate, ... } } }
  const cb = res.json('circuitBreakers.pgClient');
  if (!cb) return;
  const state = cb.state;
  cbState.add(1, { state });
  if (state !== lastState) {
    console.log(`[CB] state ${lastState} -> ${state} | failureRate=${cb.failureRate} slowCallRate=${cb.slowCallRate} buffered=${cb.bufferedCalls}`);
    lastState = state;
  }
}

export function teardown() {
  const res = http.get(`${MGMT}/actuator/circuitbreakers`);
  if (res.status === 200) console.log(`Final CircuitBreakers: ${res.body}`);
}
