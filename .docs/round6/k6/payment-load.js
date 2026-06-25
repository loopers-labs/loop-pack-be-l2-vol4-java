import http from 'k6/http';
import { check } from 'k6';

// PG 결제 접수 부하 테스트.
// A) pg-simulator 정상(40% 실패+지연): 재시도가 흡수 → 서킷 CLOSED, 지연만 상승
// B) pg-simulator 다운(100% 실패): 서킷 OPEN → fast-fail fallback(PENDING) → 지연 급락
// 관측: k6의 http_req_duration p95/p99, + http://localhost:8081/actuator/prometheus 의
//       resilience4j_circuitbreaker_state / _failure_rate
export const options = {
  scenarios: {
    ramp: {
      executor: 'ramping-arrival-rate',
      startRate: 10,
      timeUnit: '1s',
      preAllocatedVUs: 100,
      maxVUs: 400,
      stages: [
        { target: 30, duration: '15s' },
        { target: 120, duration: '30s' },
        { target: 120, duration: '15s' },
      ],
    },
  },
};

const BASE = 'http://localhost:8080';
const HEADERS = {
  'Content-Type': 'application/json',
  'X-Loopers-LoginId': __ENV.LOGIN_ID || 'loopers01',
  'X-Loopers-LoginPw': __ENV.LOGIN_PW || 'Loopers123!',
};

export default function () {
  const orderId = 1 + Math.floor(Math.random() * 50); // 시드한 주문 1~50
  const body = JSON.stringify({ orderId, cardType: 'SAMSUNG', cardNo: '1234-5678-9012-3456' });
  const res = http.post(`${BASE}/api/v1/payments`, body, { headers: HEADERS });
  check(res, { '2xx': (r) => r.status >= 200 && r.status < 300 });
}
