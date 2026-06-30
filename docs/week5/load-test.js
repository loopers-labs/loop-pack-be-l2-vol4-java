// 상품 목록 조회 부하 테스트 (캐시 효과 비교용)
//
// 캐시 적중(첫 페이지):  k6 run -e TARGET='/api/v1/products?sort=LIKES_DESC&page=0&size=20' load-test.js
// 캐시 미적용(딥 페이지): k6 run -e TARGET='/api/v1/products?sort=LIKES_DESC&page=1&size=20' load-test.js
//   (page=0 만 캐싱하므로 page=1 은 매 요청 DB 조회 → "캐시 없을 때"를 같은 코드로 재현)
//
// VUS·DURATION 조정: -e VUS=100 -e DURATION=30s

import http from 'k6/http';
import { check } from 'k6';

const BASE = __ENV.BASE_URL || 'http://localhost:8080';
const TARGET = __ENV.TARGET || '/api/v1/products?sort=LIKES_DESC&page=0&size=20';

export const options = {
    vus: __ENV.VUS ? parseInt(__ENV.VUS) : 50,
    duration: __ENV.DURATION || '30s',
    thresholds: {
        http_req_failed: ['rate<0.01'],
        http_req_duration: ['p(95)<500'],
    },
};

export default function () {
    const res = http.get(`${BASE}${TARGET}`);
    check(res, { 'status is 200': (r) => r.status === 200 });
}
