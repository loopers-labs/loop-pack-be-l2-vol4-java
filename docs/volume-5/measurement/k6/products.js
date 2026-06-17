import http from 'k6/http';
import { check } from 'k6';

// 표준 시나리오 S1~S4 를 SCENARIO 환경변수로 선택한다.
// 부하 모델: 동시 사용자 VUS(기본 50)명이 각자 요청→응답→즉시 다음 요청을 DURATION(기본 30s)
//           동안 쉬지 않고 반복한다(항상 50건이 처리 중인 상태). 앞 워밍업 구간은 분석에서 버린다.
//           표본 분포에서 p50/p95/p99 를 본다. 단건이 30초를 넘기면 DNF.
//
// 시나리오: S1 좋아요순·전역·1p / S2 좋아요순·인기브랜드·1p / S3 최신순·전역·1p / S4 상세·단건
//
// 실행 예:
//   SCENARIO=S3 k6 run products.js                                   # 동시 50명, 30s
//   SCENARIO=S2 BRAND_ID=847 k6 run products.js
//   SCENARIO=S4 POPULAR_PRODUCT_ID=45577 VUS=50 DURATION=30s k6 run products.js

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const BRAND_ID = __ENV.BRAND_ID || '';                       // 인기 브랜드 id (S2)
const POPULAR_PRODUCT_ID = __ENV.POPULAR_PRODUCT_ID || '1';  // 상세(S4) 대상 상품 id
const SIZE = __ENV.SIZE || '20';

const SCENARIOS = {
  S1: () => listUrl({ sort: 'likes_desc', page: '0' }),
  S2: () => listUrl({ sort: 'likes_desc', page: '0', brandId: BRAND_ID }),
  S3: () => listUrl({ sort: 'latest', page: '0' }),
  S4: () => `${BASE_URL}/api/v1/products/${POPULAR_PRODUCT_ID}`,
};

function listUrl({ sort, page, brandId }) {
  const params = [`sort=${sort}`, `page=${page}`, `size=${SIZE}`];
  if (brandId) {
    params.push(`brandId=${brandId}`);
  }
  return `${BASE_URL}/api/v1/products?${params.join('&')}`;
}

export const options = {
  scenarios: {
    load: {
      executor: 'constant-vus',
      vus: Number(__ENV.VUS || 50),
      duration: __ENV.DURATION || '30s',
      gracefulStop: '15s',
    },
  },
};

const scenarioId = __ENV.SCENARIO || 'S1';

export default function () {
  const urlFactory = SCENARIOS[scenarioId];
  if (!urlFactory) {
    throw new Error(`알 수 없는 SCENARIO: ${scenarioId} (S1~S4 중 하나)`);
  }
  const res = http.get(urlFactory());
  check(res, { 'status is 200': (r) => r.status === 200 });
}
