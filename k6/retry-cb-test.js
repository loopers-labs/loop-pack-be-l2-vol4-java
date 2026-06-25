/**
 * Retry × CB 상호작용 테스트
 *
 * 목적:
 *   Retry가 CB 슬라이딩 윈도우를 더 빠르게 채워 CB를 더 일찍 열리게 만드는지 확인한다.
 *   논리 요청(logical request) 기준으로 CB가 몇 번째에 처음 열리는지 비교한다.
 *
 * 원리:
 *   CB는 "개별 PG 호출(attempt)" 단위로 슬라이딩 윈도우를 채운다.
 *   retry=3이면 논리 요청 1건이 최대 3번의 PG 호출 → 윈도우가 평균 1.56배 빠르게 채워진다.
 *
 *   현재 설정 (sliding-window-size=20, slow-call-rate-threshold=50%, 100ms 초과 시 slow call):
 *     retry=3: ~13 논리 요청 만에 CB OPEN (20 / 1.56 ≈ 13)
 *     retry=1: ~20 논리 요청 만에 CB OPEN
 *
 * 실행 방법:
 *   # 1단계 — retry=3 (기본값, application.yml에 pg.retry-max-attempts 없어도 됨)
 *   k6 run -e BASE_URL=http://localhost:8080 \
 *           -e LOGIN_ID=testuser \
 *           -e LOGIN_PW=Pass1234! \
 *           -e PRODUCT_ID=<id> \
 *           k6/retry-cb-test.js
 *
 *   # 2단계 — retry=1 (application.yml에 pg.retry-max-attempts=1 추가 후 재시작)
 *   k6 run (동일 커맨드)
 *
 * 비교 포인트:
 *   로그에서 "[CB 최초 OPEN] 논리 요청 #N" 라인을 찾아 N을 비교한다.
 *   retry=3일 때 N이 더 작으면 가설 입증.
 */

import http from 'k6/http';
import { check } from 'k6';
import { Counter, Gauge } from 'k6/metrics';
import exec from 'k6/execution';

const BASE_URL   = __ENV.BASE_URL   || 'http://localhost:8080';
const LOGIN_ID   = __ENV.LOGIN_ID   || 'testuser';
const LOGIN_PW   = __ENV.LOGIN_PW   || 'Pass1234!';
const PRODUCT_ID = Number(__ENV.PRODUCT_ID || '1');

// 논리 요청 카운터 (k6 공유 카운터 — 모든 VU 합산)
const logicalReqs   = new Counter('logical_reqs');
const cbOpenCount   = new Counter('cb_open_count');
const firstCbOpenAt = new Gauge('first_cb_open_at_logical_req');

let firstCbOpened = false;

export const options = {
  // 1 VU 순차 실행 → 요청 번호가 선형으로 증가해 "몇 번째 요청에서 CB가 열렸는가"를 명확히 관찰 가능
  vus: 1,
  iterations: 40,
  thresholds: {
    'http_req_duration': ['p(95)<8000'],
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
  if (orderRes.status !== 200) {
    console.log(`[ORDER FAIL] status=${orderRes.status}`);
    return null;
  }
  const orderId = orderRes.json('data.id');

  const res = http.post(
    `${BASE_URL}/api/v1/payments`,
    JSON.stringify({ orderId: String(orderId), cardType: 'SAMSUNG', cardNo: '1234-5678-9012-3456' }),
    { headers: HEADERS, timeout: '10s' }
  );
  return res;
}

export default function () {
  const iterationNum = exec.vu.iterationInInstance + 1;
  logicalReqs.add(1);

  const res = pay();
  if (!res) return;

  const isCbOpen = res.status === 503;

  if (isCbOpen) {
    cbOpenCount.add(1);
    if (!firstCbOpened) {
      firstCbOpened = true;
      firstCbOpenAt.add(iterationNum);
      console.log(`[CB 최초 OPEN] 논리 요청 #${iterationNum}`);
    } else {
      console.log(`[CB OPEN] #${iterationNum}`);
    }
  } else {
    console.log(`[${res.status}] #${iterationNum}`);
  }

  check(res, { '응답 있음': (r) => r.status !== 0 });
}
