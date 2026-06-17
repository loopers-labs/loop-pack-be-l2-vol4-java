import http from 'k6/http';
import { check, sleep } from 'k6';
import { Trend, Rate, Counter } from 'k6/metrics';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

// 시나리오별 응답 시간 메트릭
const coldDuration = new Trend('duration_cache_miss', true);
const warmDuration = new Trend('duration_cache_hit',  true);
const errorRate    = new Rate('error_rate');
const notFound     = new Counter('not_found_count');

// cold: 분산된 ID → 대부분 캐시 미스
// warm: 고정 소풀 ID → TTL 내 반복 조회 → 대부분 캐시 히트
const COLD_POOL_SIZE = 5000;  // 넓은 범위
const WARM_POOL_SIZE = 20;    // 좁은 범위 (TTL 10분 내에 반복 히트)
const MAX_PRODUCT_ID = 100000;

export const options = {
    scenarios: {
        // Phase 1 — 캐시 미스: VU·이터레이션 조합으로 넓은 ID 범위 분산 접근
        cold_start: {
            executor: 'constant-vus',
            vus: 50,
            duration: '20s',
            env: { SCENARIO: 'cold' },
            tags: { scenario: 'cold_start' },
        },
        // Phase 2 — 캐시 히트: cold 종료 후 5초 뒤 시작, 좁은 ID 풀 집중 접근
        warm_cache: {
            executor: 'constant-vus',
            vus: 50,
            duration: '20s',
            startTime: '25s',
            env: { SCENARIO: 'warm' },
            tags: { scenario: 'warm_cache' },
        },
    },
    thresholds: {
        // http_req_failed 미사용: 소프트 삭제 상품 ~30%가 404를 반환하므로 error_rate 커스텀 메트릭으로 대체
        'error_rate':          ['rate<0.01'],   // 5xx 등 실제 에러만 검사
        'duration_cache_miss': ['p(95)<2000'],
        'duration_cache_hit':  ['p(95)<100'],   // 캐시 히트는 100ms 미만 기대
    },
};

// cold 시나리오: VU와 iteration 조합으로 넓은 풀에서 분산 접근
function coldProductId() {
    const idx = (__VU * 1000 + __ITER) % COLD_POOL_SIZE;
    return (idx % MAX_PRODUCT_ID) + 1;
}

// warm 시나리오: 소풀 내 랜덤 접근 → 이미 캐싱된 ID 반복 히트
function warmProductId() {
    return (Math.floor(Math.random() * WARM_POOL_SIZE)) + 1;
}

export function setup() {
    // warm 풀 ID(1~20)를 미리 워밍업 — cold 시나리오 전에 캐시에 올림
    console.log('=== warm 풀 사전 워밍업 시작 (ID 1~' + WARM_POOL_SIZE + ') ===');
    for (let id = 1; id <= WARM_POOL_SIZE; id++) {
        const res = http.get(`${BASE_URL}/api/v1/products/${id}`);
        if (res.status !== 200 && res.status !== 404) {
            console.warn(`워밍업 실패 id=${id}, status=${res.status}`);
        }
    }
    console.log('=== warm 풀 워밍업 완료 ===');
}

export default function () {
    const scenario = __ENV.SCENARIO;

    if (scenario === 'cold') {
        const productId = coldProductId();
        const res = http.get(`${BASE_URL}/api/v1/products/${productId}`, {
            tags: { phase: 'cold' },
        });

        const ok = check(res, {
            'cold: status 200 or 404': (r) => r.status === 200 || r.status === 404,
        });

        coldDuration.add(res.timings.duration);
        errorRate.add(!ok);
        if (res.status === 404) notFound.add(1);

    } else {
        const productId = warmProductId();
        const res = http.get(`${BASE_URL}/api/v1/products/${productId}`, {
            tags: { phase: 'warm' },
        });

        const ok = check(res, {
            'warm: status 200 or 404': (r) => r.status === 200 || r.status === 404,
        });

        warmDuration.add(res.timings.duration);
        errorRate.add(!ok);
        if (res.status === 404) notFound.add(1);
    }

    sleep(0.05);
}

export function handleSummary(data) {
    try {
        const miss = data.metrics['duration_cache_miss'];
        const hit  = data.metrics['duration_cache_hit'];

        if (!miss || !hit) return {};

        const missValues = miss.values || miss;
        const hitValues  = hit.values  || hit;

        const missP50 = (missValues['p(50)'] ?? missValues['med'] ?? 0).toFixed(2);
        const missP95 = (missValues['p(95)'] ?? 0).toFixed(2);
        const hitP50  = (hitValues['p(50)']  ?? hitValues['med'] ?? 0).toFixed(2);
        const hitP95  = (hitValues['p(95)']  ?? 0).toFixed(2);

        const missP95Val = parseFloat(missP95);
        const hitP95Val  = parseFloat(hitP95);
        const speedup = hitP95Val > 0 ? (missP95Val / hitP95Val).toFixed(1) : 'N/A';

        console.log('\n========== 캐시 성능 비교 ==========');
        console.log(`캐시 미스  p50=${missP50}ms  p95=${missP95}ms`);
        console.log(`캐시 히트  p50=${hitP50}ms  p95=${hitP95}ms`);
        console.log(`p95 기준 ${speedup}배 빠름`);
        console.log('=====================================\n');
    } catch (e) {
        console.warn('handleSummary 오류: ' + e.message);
    }

    return {};
}