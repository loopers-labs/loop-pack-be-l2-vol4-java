import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate, Counter, Trend } from 'k6/metrics';

const BASE_URL = 'http://localhost:8080';
const LOGIN_ID = 'chaos-tester';
const LOGIN_PW = 'pAssWord1!';

const cbRejectedRate = new Rate('cb_rejected');       // 503 — CB가 차단한 요청
const paymentAcceptedRate = new Rate('payment_accepted'); // 200 — PG에 요청 전달됨
const conflictRate = new Rate('conflict');             // 409 — 이미 처리된 주문
const responseTrend = new Trend('payment_duration', true);

export const options = {
    scenarios: {
        payment_chaos: {
            executor: 'constant-arrival-rate',
            rate: 10,
            timeUnit: '1s',
            duration: '3m',
            preAllocatedVUs: 30,
            maxVUs: 50,
        },
    },
    thresholds: {
        cb_rejected: ['rate<0.5'],           // CB 차단율 50% 미만
        payment_duration: ['p(95)<3000'],
    },
};

export function setup() {
    const adminHeaders = {
        'Content-Type': 'application/json',
        'X-Loopers-Ldap': 'loopers.admin',
    };

    // admin으로 재고 있는 상품 10개 생성
    const productIds = [];
    for (let i = 0; i < 10; i++) {
        const res = http.post(`${BASE_URL}/api-admin/v1/products`, JSON.stringify({
            brandId: 1,
            name: `카오스-상품-${i}`,
            price: 10000,
            stock: 99999,
        }), { headers: adminHeaders });
        if (res.status === 200) {
            productIds.push(JSON.parse(res.body).data.id);
        }
    }

    if (productIds.length === 0) {
        throw new Error('상품 생성 실패');
    }
    console.log(`상품 생성 완료: ${productIds}`);

    // 유저 생성
    http.post(`${BASE_URL}/api/v1/users`, JSON.stringify({
        loginId: LOGIN_ID,
        loginPassword: LOGIN_PW,
        name: '카오스테스터',
        birthday: '1990-01-01',
        email: 'chaos@test.com',
    }), { headers: { 'Content-Type': 'application/json' } });

    const authHeaders = {
        'Content-Type': 'application/json',
        'X-Loopers-LoginId': LOGIN_ID,
        'X-Loopers-LoginPw': LOGIN_PW,
    };

    // 주문 300개 생성
    const orderIds = [];
    for (let i = 0; i < 300; i++) {
        const productId = productIds[i % productIds.length];
        const res = http.post(`${BASE_URL}/api/v1/orders`, JSON.stringify({
            items: [{ productId: productId, quantity: 1 }],
        }), { headers: authHeaders });

        if (res.status === 201) {
            orderIds.push(JSON.parse(res.body).data.orderId);
        }
    }

    console.log(`주문 생성 완료: ${orderIds.length}건`);
    return { orderIds };
}

export default function (data) {
    if (!data.orderIds || data.orderIds.length === 0) return;

    const orderId = data.orderIds[Math.floor(Math.random() * data.orderIds.length)];

    const headers = {
        'Content-Type': 'application/json',
        'X-Loopers-LoginId': LOGIN_ID,
        'X-Loopers-LoginPw': LOGIN_PW,
    };

    const start = Date.now();
    const res = http.post(`${BASE_URL}/api/v1/payments`, JSON.stringify({
        orderId: orderId,
        cardType: 'SAMSUNG',
        cardNo: '1234-5678-9012-3456',
    }), { headers });

    responseTrend.add(Date.now() - start);

    cbRejectedRate.add(res.status === 503);
    paymentAcceptedRate.add(res.status === 200);
    conflictRate.add(res.status === 409);

    check(res, {
        '200 or 503 or 409': (r) => r.status === 200 || r.status === 503 || r.status === 409,
    });
}