import http from 'k6/http';
import { check, sleep } from 'k6';
import { Trend, Rate } from 'k6/metrics';

const BASE_URL = 'http://localhost:8080';

// 쿼리 패턴별 응답 시간 커스텀 메트릭
const latestTrend        = new Trend('duration_latest',         true);
const priceAscTrend      = new Trend('duration_price_asc',      true);
const likesDescTrend     = new Trend('duration_likes_desc',     true);
const brandLatestTrend   = new Trend('duration_brand_latest',   true);
const brandPriceTrend    = new Trend('duration_brand_price',    true);
const brandLikesTrend    = new Trend('duration_brand_likes',    true);
const deepLatestTrend    = new Trend('duration_deep_latest',    true);
const deepLikesTrend     = new Trend('duration_deep_likes',     true);

const errorRate = new Rate('error_rate');

export const options = {
    vus: 50,
    duration: '30s',
    thresholds: {
        http_req_failed:          ['rate<0.01'],           // 에러율 1% 미만
        http_req_duration:        ['p(95)<2000'],          // 전체 p95 2초 미만
        'duration_latest':        ['p(95)<500'],
        'duration_price_asc':     ['p(95)<500'],
        'duration_likes_desc':    ['p(95)<500'],
        'duration_brand_latest':  ['p(95)<500'],
        'duration_brand_price':   ['p(95)<500'],
        'duration_brand_likes':   ['p(95)<500'],
        'duration_deep_latest':   ['p(95)<5000'],
        'duration_deep_likes':    ['p(95)<5000'],
    },
};

// 브랜드 ID: DataLoader 기준 1~50
function randomBrandId() {
    return Math.floor(Math.random() * 50) + 1;
}

export default function () {
    const brandId = randomBrandId();

    const scenarios = [
        // shallow pagination (1페이지)
        { url: `${BASE_URL}/api/v1/products?sort=LATEST&page=0&size=20`,                         trend: latestTrend,      label: 'latest' },
        { url: `${BASE_URL}/api/v1/products?sort=PRICE_ASC&page=0&size=20`,                      trend: priceAscTrend,    label: 'price_asc' },
        { url: `${BASE_URL}/api/v1/products?sort=LIKES_DESC&page=0&size=20`,                     trend: likesDescTrend,   label: 'likes_desc' },
        { url: `${BASE_URL}/api/v1/products?brandId=${brandId}&sort=LATEST&page=0&size=20`,      trend: brandLatestTrend, label: 'brand_latest' },
        { url: `${BASE_URL}/api/v1/products?brandId=${brandId}&sort=PRICE_ASC&page=0&size=20`,   trend: brandPriceTrend,  label: 'brand_price_asc' },
        { url: `${BASE_URL}/api/v1/products?brandId=${brandId}&sort=LIKES_DESC&page=0&size=20`,  trend: brandLikesTrend,  label: 'brand_likes_desc' },

        // deep pagination
        { url: `${BASE_URL}/api/v1/products?sort=LATEST&page=2000&size=20`,                      trend: deepLatestTrend,  label: 'deep_latest' },
        { url: `${BASE_URL}/api/v1/products?sort=LIKES_DESC&page=30&size=20`,                    trend: deepLikesTrend,   label: 'deep_likes_desc' },
    ];

    for (const scenario of scenarios) {
        const res = http.get(scenario.url);
        const ok  = check(res, { 'status 200': (r) => r.status === 200 });

        scenario.trend.add(res.timings.duration);
        errorRate.add(!ok);
    }

    sleep(0.1);
}
