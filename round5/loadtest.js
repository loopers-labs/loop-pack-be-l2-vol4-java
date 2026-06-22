import http from 'k6/http';
import { check } from 'k6';

// 동시 사용자 30명이 30초간 상품 목록을 랜덤 조건으로 조회
export const options = {
  vus: 30,
  duration: '30s',
  thresholds: {
    // SLO 가정: 응답의 95%가 200ms 이내여야 한다
    http_req_duration: ['p(95)<200'],
  },
};

const SORTS = ['LATEST', 'PRICE_ASC', 'LIKES_DESC'];

export default function () {
  const brandId = Math.floor(Math.random() * 100) + 1;     // 1~100
  const sort = SORTS[Math.floor(Math.random() * SORTS.length)];
  const page = Math.floor(Math.random() * 11);             // 0~10
  const url = `http://localhost:8080/api/v1/products?brandId=${brandId}&sort=${sort}&page=${page}&size=20`;

  const res = http.get(url);
  check(res, { 'status is 200': (r) => r.status === 200 });
}
