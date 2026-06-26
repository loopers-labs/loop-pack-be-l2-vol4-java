import http from 'k6/http';
import { check } from 'k6';
import { Trend } from 'k6/metrics';
import exec from 'k6/execution';

// 스레드 점유 실험 — 느린 PG 부하 중에 '결제와 무관한' 엔드포인트(상품 조회)가 같이 느려지는지 측정.
//  payment 시나리오: POST /api/v1/payments — 느린 PG가 워커 스레드를 오래 점유.
//  probe   시나리오: GET /api/v1/products/1 — 결제와 무관한 싼 조회. 이 지연이 collateral damage.
// CB-OFF로 돌리면 probe_latency가 치솟고(스레드 고갈), CB-ON이면 probe_latency가 낮게 유지된다.
const BASE = __ENV.BASE || 'http://localhost:8080';
const PAY_RATE = Number(__ENV.PAY_RATE || 25);
const PROBE_RATE = Number(__ENV.PROBE_RATE || 5);
const MAX = Number(__ENV.MAXORDER || 1050);
const DUR = __ENV.DUR || '90s';

const PAY_MODE = __ENV.PAY_MODE || 'rate'; // 'rate'=constant-arrival-rate, 'vus'=constant-vus(고정 동시성, 포화 유지용)
const VUS = Number(__ENV.VUS || 24);

const probeLatency = new Trend('probe_latency', true);

const HEADERS = {
  'Content-Type': 'application/json',
  'X-Loopers-LoginId': __ENV.LOGIN_ID || 'loopers01',
  'X-Loopers-LoginPw': __ENV.LOGIN_PW || 'Loopers123!',
};

const paymentScenario = PAY_MODE === 'vus'
  ? { executor: 'constant-vus', exec: 'payment', vus: VUS, duration: DUR }
  : { executor: 'constant-arrival-rate', exec: 'payment', rate: PAY_RATE, timeUnit: '1s', duration: DUR, preAllocatedVUs: 100, maxVUs: 400 };

export const options = {
  scenarios: {
    payment: paymentScenario,
    probe: {
      executor: 'constant-arrival-rate',
      exec: 'probe',
      rate: PROBE_RATE, timeUnit: '1s', duration: DUR,
      preAllocatedVUs: 20, maxVUs: 100,
    },
  },
};

export function payment() {
  // 매 요청이 새 주문 1건을 쓰도록 순차 유니크 — 이미 PENDING된 주문 재선택(멱등 빠른응답)을 막아 스레드 점유를 유지
  const orderId = 1 + (exec.scenario.iterationInTest % MAX);
  const body = JSON.stringify({ orderId, cardType: 'SAMSUNG', cardNo: '1234-5678-9012-3456' });
  http.post(`${BASE}/api/v1/payments`, body, { headers: HEADERS });
}

export function probe() {
  const res = http.get(`${BASE}/api/v1/products/1`);
  probeLatency.add(res.timings.duration);
  check(res, { 'probe 2xx': (r) => r.status >= 200 && r.status < 300 });
}
