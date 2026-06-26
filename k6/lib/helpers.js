import http from 'k6/http';
import { fail } from 'k6';

export const PG_CARDS = ['SAMSUNG', 'KB', 'HYUNDAI']; // pg-simulator 허용 카드만
const ADMIN = { 'X-Loopers-Ldap': 'loopers.admin', 'Content-Type': 'application/json' };
const JSON_HDR = { 'Content-Type': 'application/json' };

export function auth(u) {
  return {
    'X-Loopers-LoginId': u.userId,
    'X-Loopers-LoginPw': u.password,
    'Content-Type': 'application/json',
  };
}

export function randCardNo() {
  const g = () => String(1000 + Math.floor(Math.random() * 9000));
  return `${g()}-${g()}-${g()}-${g()}`;
}

/** 브랜드 1 + 고재고 상품 5 + 유저 N 생성. default/scenario 로 넘길 데이터 반환. */
export function seed(base, { runId, users = 100, products = 5 } = {}) {
  let res = http.post(`${base}/api-admin/v1/brands`,
    JSON.stringify({ name: `brand-${runId}`, description: 'load test' }), { headers: ADMIN });
  if (res.status !== 200) fail(`brand 생성 실패: ${res.status} ${res.body}`);
  const brandId = res.json('data.id');

  const productIds = [];
  for (let i = 0; i < products; i++) {
    res = http.post(`${base}/api-admin/v1/products`, JSON.stringify({
      brandId, name: `p-${runId}-${i}`, description: 'lt', price: 1000 + i * 500, quantity: 100000000,
    }), { headers: ADMIN });
    if (res.status !== 201 && res.status !== 200) fail(`product 생성 실패: ${res.status} ${res.body}`);
    productIds.push(res.json('data.id'));
  }

  // userId 는 영문+숫자만(언더스코어 불가), name 은 한글만(^[가-힣]+$) 허용된다.
  const userList = [];
  for (let i = 0; i < users; i++) {
    const u = { userId: `u${runId}n${i}`, password: 'abc123!@#' };
    const res = http.post(`${base}/api/v1/users`, JSON.stringify({
      userId: u.userId, password: u.password, name: '테스트',
      birthDate: '1995-06-10', email: `u${i}${runId}@test.com`,
    }), { headers: JSON_HDR });
    if (res.status !== 200) fail(`user 가입 실패: ${res.status} ${res.body}`);
    userList.push(u);
  }
  return { users: userList, productIds };
}

/** 주문 생성 → 결제. 결제 응답(res)과 orderId 를 돌려준다. 주문 실패 시 {orderFailed:true}. */
export function orderAndPay(base, data) {
  const u = data.users[Math.floor(Math.random() * data.users.length)];
  const headers = auth(u);

  // 한 주문 내 상품은 중복 없이(distinct) 뽑는다. 같은 productId 가 두 번 들어가면
  // 서버의 Collectors.toMap 이 Duplicate key 로 500 을 던지기 때문.
  const pool = [...data.productIds];
  for (let i = pool.length - 1; i > 0; i--) { // Fisher-Yates shuffle
    const j = Math.floor(Math.random() * (i + 1));
    [pool[i], pool[j]] = [pool[j], pool[i]];
  }
  const itemCount = Math.min(1 + Math.floor(Math.random() * 3), pool.length);
  const items = pool.slice(0, itemCount).map((productId) => ({
    productId,
    quantity: 1 + Math.floor(Math.random() * 3),
  }));

  let res = http.post(`${base}/api/v1/orders`, JSON.stringify({ items }), { headers });
  if (res.status !== 201) return { orderFailed: true };
  const orderId = res.json('data.orderId');

  res = http.post(`${base}/api/v1/payments`, JSON.stringify({
    orderId, cardType: PG_CARDS[Math.floor(Math.random() * PG_CARDS.length)], cardNo: randCardNo(),
  }), { headers });

  return { res, status: classifyPayment(res) };
}

/**
 * 결제 응답을 분류해 pay_result 태그로 쓴다.
 *  - 200 + SUCCESS                  → SUCCESS
 *  - 200 + FAILED(한도초과/잘못된카드) → FAILED_LIMIT_EXCEEDED / FAILED_INVALID_CARD / FAILED_OTHER
 *  - 500 (PG 결제 요청 실패)         → PG_REQUEST_FAILED
 *  - 503 (서킷브레이커 OPEN)         → CIRCUIT_OPEN
 *  - 그 외                          → HTTP_<status>
 */
export function classifyPayment(res) {
  if (res.status === 500) return 'PG_REQUEST_FAILED';
  if (res.status === 503) return 'CIRCUIT_OPEN';
  if (res.status !== 200) return `HTTP_${res.status}`;

  const dataStatus = res.json('data.status');
  if (dataStatus !== 'FAILED') return dataStatus || 'UNKNOWN';

  const reason = res.json('data.reason') || '';
  if (reason.includes('한도')) return 'FAILED_LIMIT_EXCEEDED';
  if (reason.includes('카드')) return 'FAILED_INVALID_CARD';
  return 'FAILED_OTHER';
}
