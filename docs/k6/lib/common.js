// =============================================================================
// 공용 헬퍼 — 모든 시나리오(baseline/concurrency/resilience)가 공유한다.
// 인증 헤더, 주문 생성, 결제 요청, PG 거래 조회를 한곳에 모아 각 시나리오는
// "무엇을 검증하는가"에만 집중하게 한다.
// =============================================================================

import http from 'k6/http';

// --- 설정값 (환경변수로 덮어쓸 수 있음: BASE_URL=... k6 run ...) -------------
export const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080'; // commerce-api
export const PG_BASE = __ENV.PG_BASE || 'http://localhost:8082';   // pg-simulator (사후 검증용)

// commerce-api 인증: 로그인 아이디/비번 헤더(필터가 userId 로 해석). 기본은 시드 사용자.
const LOGIN_ID = __ENV.LOGIN_ID || 'loopers01';
const LOGIN_PW = __ENV.LOGIN_PW || 'Passw0rd!';

// 주문 항목: 시드된 상품 id/수량. 재고가 충분해야 함(환경변수로 교체 가능).
const PRODUCT_ID = Number(__ENV.PRODUCT_ID || 1);
const QUANTITY = Number(__ENV.QUANTITY || 1);

// 결제 카드(브랜드는 SAMSUNG|KB|HYUNDAI, PG 선택과 무관한 결제수단일 뿐).
const CARD_TYPE = __ENV.CARD_TYPE || 'SAMSUNG';

// commerce-api 호출용 공통 헤더(인증 포함).
export function authHeaders() {
  return {
    'Content-Type': 'application/json',
    'X-Loopers-LoginId': LOGIN_ID,
    'X-Loopers-LoginPw': LOGIN_PW,
  };
}

// PG 거래 카드번호는 매번 달라도 무방 — 형식만 맞추면 된다(VU/iter 로 유일성 부여).
export function cardNo() {
  const n = Math.floor(1000 + Math.random() * 8999);
  return `1234-5678-9814-${n}`;
}

// -----------------------------------------------------------------------------
// 주문 생성: POST /api/v1/orders → orderNumber 반환.
// 결제의 선행조건(결제 가능한 주문)을 만든다. 실패하면 null 을 돌려 호출부가 판단.
// -----------------------------------------------------------------------------
export function createOrder() {
  const body = JSON.stringify({
    items: [{ productId: PRODUCT_ID, quantity: QUANTITY }],
    recipientName: '부하테스트',
    recipientPhone: '010-0000-0000',
    zipcode: '12345',
    address1: '서울시 강남구',
    address2: '101동',
  });
  const res = http.post(`${BASE_URL}/api/v1/orders`, body, {
    headers: authHeaders(),
    tags: { name: 'create_order' }, // 메트릭에서 결제와 구분되도록 태그
  });
  if (res.status !== 200) {
    return { ok: false, status: res.status, body: res.body };
  }
  return { ok: true, orderNumber: res.json('data.orderNumber') };
}

// -----------------------------------------------------------------------------
// 결제 요청: POST /api/v1/payments. orderId 필드값은 우리 orderNumber 다.
// 응답을 그대로 돌려줘 각 시나리오가 상태코드/지연/에러코드를 직접 판정한다.
// -----------------------------------------------------------------------------
export function pay(orderNumber) {
  const body = JSON.stringify({
    orderId: orderNumber,
    cardType: CARD_TYPE,
    cardNo: cardNo(),
  });
  return http.post(`${BASE_URL}/api/v1/payments`, body, {
    headers: authHeaders(),
    tags: { name: 'pay' },
  });
}

// 응답 바디에서 우리 에러코드를 꺼낸다(ApiResponse.fail 의 meta.errorCode).
export function errorCode(res) {
  try {
    return res.json('meta.errorCode');
  } catch (e) {
    return null;
  }
}

// -----------------------------------------------------------------------------
// 사후 검증용: PG 에 해당 주문으로 생긴 거래 수를 조회한다(이중결제 확인).
// X-USER-ID 는 pg-simulator 규약 헤더(아무 값이나 일관되게).
// -----------------------------------------------------------------------------
export function pgTransactionCount(orderNumber) {
  const res = http.get(`${PG_BASE}/api/v1/payments?orderId=${orderNumber}`, {
    headers: { 'X-USER-ID': '1' },
    tags: { name: 'pg_inquiry' },
  });
  if (res.status !== 200) return -1; // 조회 실패는 -1 로 구분
  const txs = res.json('data.transactions');
  return Array.isArray(txs) ? txs.length : 0;
}
