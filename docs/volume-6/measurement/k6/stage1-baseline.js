import http from 'k6/http';
import exec from 'k6/execution';
import { Trend, Rate, Counter } from 'k6/metrics';

// Stage 1 — 장애 전파 재현 (측정 원점)
//
// 무방비(타임아웃 없음) 상태에서 PG 요청 지연이 톰캣 워커 스레드를 점유하면,
// 결제와 무관한 요청(prober)까지 응답이 어떻게 무너지는지 한 번의 실행으로 박제한다.
//
// 타임라인(기본 90s):
//   [0s ~15s)  warmup  : prober 만 흐른다 → 깨끗한 baseline (p50/p95/p99)
//   [15s~75s)  load    : 결제 부하 급증 → 스레드 고갈 → prober 열화
//   [75s~90s]  recovery: 결제 부하 종료 → prober 회복 관찰
//
// prober 의 phase 별 지연 분포와 실패율, 그리고 결제 요청의 500 직결률이 핵심 산출물이다.

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const LOGIN_ID = __ENV.LOGIN_ID || 'looptester';
const LOGIN_PW = __ENV.LOGIN_PW || 'Looptest1234';

// 시드된 주문 id 범위(1..ORDER_COUNT). 결제는 orderId 당 1회만 접수 가능(이후 409)하므로
// iteration 마다 전역 카운터로 유니크 id 를 소비한다.
const ORDER_START_ID = Number(__ENV.ORDER_START_ID || 1);
const ORDER_COUNT = Number(__ENV.ORDER_COUNT || 3000);
// 시드된 상품 id 범위(1..PRODUCT_COUNT). prober 가 무작위로 단건 조회한다.
const PRODUCT_COUNT = Number(__ENV.PRODUCT_COUNT || 100);

const WARMUP_SEC = Number(__ENV.WARMUP_SEC || 15);
const LOAD_END_SEC = Number(__ENV.LOAD_END_SEC || 75);

// prober 지연을 phase 별로 분리 기록 → 요약에서 전/중/후 p50·p95·p99 를 바로 읽는다.
const proberWarmup = new Trend('prober_warmup_ms', true);
const proberLoad = new Trend('prober_load_ms', true);
const proberRecovery = new Trend('prober_recovery_ms', true);
const proberFail = new Rate('prober_fail');       // 비200(타임아웃·거부 포함)
const proberTimeout = new Counter('prober_timeout'); // status 0 = 클라이언트 타임아웃/소켓 끊김

const paymentFail = new Rate('payment_fail');     // 결제 접수 실패(비2xx)
const payment500 = new Rate('payment_500');       // 그 중 500 직결(무방비의 증거)

export const options = {
    discardResponseBodies: false,
    scenarios: {
        // 결제와 무관한 사용자 요청: 전 구간 고정 도착률.
        // 스레드 고갈로 응답이 밀리면 도착률을 못 맞춰 dropped_iterations 로도 드러난다.
        prober: {
            executor: 'constant-arrival-rate',
            rate: Number(__ENV.PROBER_RATE || 5),
            timeUnit: '1s',
            duration: `${LOAD_END_SEC + 15}s`,
            preAllocatedVUs: 20,
            maxVUs: 60,
            exec: 'prober',
        },
        // 결제 부하: warmup 이후 점증.
        payment_load: {
            executor: 'ramping-vus',
            startTime: `${WARMUP_SEC}s`,
            startVUs: 0,
            stages: [
                { duration: '10s', target: Number(__ENV.PAY_VUS || 20) },
                { duration: '40s', target: Number(__ENV.PAY_VUS || 20) },
                { duration: '10s', target: 0 },
            ],
            exec: 'payment',
        },
    },
    summaryTrendStats: ['avg', 'min', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
};

function phaseOf() {
    const t = exec.instance.currentTestRunDuration / 1000;
    if (t < WARMUP_SEC) return 'warmup';
    if (t < LOAD_END_SEC) return 'load';
    return 'recovery';
}

export function prober() {
    const productId = ORDER_START_ID + Math.floor(Math.random() * PRODUCT_COUNT);
    const res = http.get(`${BASE_URL}/api/v1/products/${productId}`, {
        timeout: `${__ENV.PROBER_TIMEOUT || '10'}s`,
        tags: { kind: 'prober' },
    });

    const phase = phaseOf();
    if (phase === 'warmup') proberWarmup.add(res.timings.duration);
    else if (phase === 'load') proberLoad.add(res.timings.duration);
    else proberRecovery.add(res.timings.duration);

    const ok = res.status === 200;
    proberFail.add(!ok);
    if (res.status === 0) proberTimeout.add(1);
}

export function payment() {
    // iterationInTest 는 테스트 전체에서 단조증가 → 유니크 orderId 보장.
    const orderId = ORDER_START_ID + (exec.scenario.iterationInTest % ORDER_COUNT);
    const res = http.post(
        `${BASE_URL}/api/v1/payments`,
        JSON.stringify({ orderId: orderId, cardType: 'SAMSUNG', cardNo: '1234-5678-9814-1451' }),
        {
            headers: {
                'Content-Type': 'application/json',
                'X-Loopers-LoginId': LOGIN_ID,
                'X-Loopers-LoginPw': LOGIN_PW,
            },
            timeout: `${__ENV.PAY_TIMEOUT || '60'}s`,
            tags: { kind: 'payment' },
        },
    );

    const ok = res.status === 201;
    paymentFail.add(!ok);
    payment500.add(res.status === 500);
}
