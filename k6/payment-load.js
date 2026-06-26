import http from 'k6/http';
import { check } from 'k6';
import { Counter } from 'k6/metrics';
import { seed, orderAndPay, recordPayment, paySubThresholds } from './lib/helpers.js';

/**
 * resilience4j(pgClient 서킷브레이커) 기본 부하 테스트
 *
 * 시나리오: 100명의 유저가 여러 상품으로 주문 후 결제. 초당 30회 결제.
 *
 * 실행 전제:
 *   - infra(docker/infra-compose.yml) 기동
 *   - pg-simulator 기동 (http://localhost:8082)  — 요청 40% 실패 + 처리 1~5s 후 콜백
 *   - commerce-api 기동 (http://localhost:8080, 관리 8081)
 *
 * 실행:
 *   k6 run k6/payment-load.js
 *   k6 run -e RATE=30 -e DURATION=2m -e USERS=100 k6/payment-load.js
 */

const BASE = __ENV.BASE_URL || 'http://localhost:8080';
const MGMT = __ENV.MGMT_URL || 'http://localhost:8081';
const RATE = Number(__ENV.RATE || 30);
const DURATION = __ENV.DURATION || '1m';
const USERS = Number(__ENV.USERS || 100);
const RUN = __ENV.RUN_ID || `lt${Date.now()}`;

// ITERATIONS 가 지정되면 "총 요청 수(count)" 모드(shared-iterations)로,
// 아니면 "초당 RATE 회(rate)" 모드(constant-arrival-rate)로 실행한다.
// 주의: options.scenarios 가 있으면 k6 는 CLI 의 -i/--iterations 를 무시하므로 env 로 받는다.
const ITERATIONS = __ENV.ITERATIONS ? Number(__ENV.ITERATIONS) : null;
const VUS = Number(__ENV.VUS || 50);
const MAX_DURATION = __ENV.MAX_DURATION || '10m';

const orderFail = new Counter('order_failed');

const scenario = ITERATIONS
  ? {
      // 총 ITERATIONS 회를 VUS 개로 나눠 실행 (rate 제어 없이 VU 가 가능한 만큼 빠르게).
      executor: 'shared-iterations',
      vus: VUS,
      iterations: ITERATIONS,
      maxDuration: MAX_DURATION,
    }
  : {
      executor: 'constant-arrival-rate',
      rate: RATE,
      timeUnit: '1s',
      duration: DURATION,
      // 결제는 콜백 대기로 수 초 걸린다 → 동시 in-flight ≈ RATE × 평균지연. 넉넉히.
      preAllocatedVUs: 200,
      maxVUs: 400,
    };

export const options = {
  scenarios: { payments: scenario },
  thresholds: {
    http_req_failed: ['rate<0.6'],
    http_req_duration: ['p(95)<11000'],
    // 결제 결과 카테고리별 건수/응답시간을 요약에 분해 출력.
    ...paySubThresholds(),
  },
};

export function setup() {
  return seed(BASE, { runId: RUN, users: USERS });
}

export default function (data) {
  const r = orderAndPay(BASE, data);
  if (r.orderFailed) {
    orderFail.add(1);
    return;
  }
  recordPayment(r.res, r.status);
  // 200(정상/비즈니스실패), 500(PG 요청 실패), 503(서킷 OPEN) 은 모두 '핸들링된 응답'.
  check(r.res, { 'payment handled': (res) => [200, 500, 503].includes(res.status) });
}

export function teardown() {
  const res = http.get(`${MGMT}/actuator/circuitbreakers`);
  if (res.status === 200) console.log(`CircuitBreakers: ${res.body}`);
  else console.log(`(actuator/circuitbreakers 미노출: ${res.status}) Prometheus/Grafana 로 확인하세요.`);
}
