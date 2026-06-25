import http from 'k6/http';
import { check } from 'k6';

// 생애주기 데모용 — 일정 비율(25 req/s)로 70초간 결제 접수.
// 단계별로 pg-simulator를 죽였다/살렸다 하며 CLOSED→OPEN→HALF_OPEN→CLOSED를 관찰한다.
const MAX = Number(__ENV.MAXORDER || 50);
const RATE = Number(__ENV.RATE || 25);
const DUR = __ENV.DUR || '70s';

export const options = {
  scenarios: {
    steady: {
      executor: 'constant-arrival-rate',
      rate: RATE,
      timeUnit: '1s',
      duration: DUR,
      preAllocatedVUs: 60,
      maxVUs: 200,
    },
  },
};

const HEADERS = {
  'Content-Type': 'application/json',
  'X-Loopers-LoginId': __ENV.LOGIN_ID || 'loopers01',
  'X-Loopers-LoginPw': __ENV.LOGIN_PW || 'Loopers123!',
};

export default function () {
  const orderId = 1 + Math.floor(Math.random() * MAX);
  const body = JSON.stringify({ orderId, cardType: 'SAMSUNG', cardNo: '1234-5678-9012-3456' });
  const res = http.post('http://localhost:8080/api/v1/payments', body, { headers: HEADERS });
  check(res, { '2xx': (r) => r.status >= 200 && r.status < 300 });
}
