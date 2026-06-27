import http from 'k6/http';
import exec from 'k6/execution';
import { Trend, Rate } from 'k6/metrics';

// Stage 4(장면 2) — 서킷 브레이커 동작 관찰
//
// 부하 중 PG 접수 실패율(타임아웃 50% + 5xx 20% ≈ 70%)이 임계(50%)를 넘어
// CLOSED→OPEN 으로 전이되고, OPEN 동안에는 PG 를 호출하지 않고(=워커 스레드를 점유하지 않고)
// 즉시 fallback(PENDING) 하는지를 본다.
//
// 핵심 산출물 2가지:
//   1) 결제 지연을 "서킷 닫힘 표본"(부하 초기, 첫 N건)과 "서킷 열림 표본"(이후)으로 갈라
//      300ms 타임아웃 대기 → 즉시 단락(수 ms) 으로 떨어지는 자원 회수 효과를 한 런에서 대비.
//   2) prober(결제와 무관한 상품 조회): 톰캣 10스레드 환경(01/02 baseline 과 동일)에서
//      서킷이 스레드를 회수해 부하 중에도 prober 가 무너지지 않는지 — Stage 2 의 "완화"와 대비.
//
// 서킷 상태/오픈 횟수/미허용 호출수(resilience4j_circuitbreaker_*)는
// /actuator/prometheus 폴링으로 교차 확보한다(run-stage2.sh).

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const LOGIN_ID = __ENV.LOGIN_ID || 'looptester';
const LOGIN_PW = __ENV.LOGIN_PW || 'Looptest1234';

const ORDER_START_ID = Number(__ENV.ORDER_START_ID || 1);
const ORDER_COUNT = Number(__ENV.ORDER_COUNT || 3000);
const PRODUCT_COUNT = Number(__ENV.PRODUCT_COUNT || 100);

const WARMUP_SEC = Number(__ENV.WARMUP_SEC || 10);
const LOAD_END_SEC = Number(__ENV.LOAD_END_SEC || 60);

// 결제 지연 표본 분리: 부하 시작 직후 첫 구간은 서킷이 아직 채워지는 중(=CLOSED, PG 직격),
// 충분히 지난 뒤는 서킷이 열린 상태(=OPEN, 단락)로 정착한다고 보고 iterationInTest 로 가른다.
const CLOSED_SAMPLE_MAX = Number(__ENV.CLOSED_SAMPLE_MAX || 40);
const OPEN_SAMPLE_MIN = Number(__ENV.OPEN_SAMPLE_MIN || 150);

const paymentClosed = new Trend('payment_closed_ms', true); // 서킷 CLOSED 표본(첫 N건)
const paymentOpen = new Trend('payment_open_ms', true);     // 서킷 OPEN 정착 표본
const paymentAll = new Trend('payment_all_ms', true);
const paymentFail = new Rate('payment_fail');               // 비2xx
const payment500 = new Rate('payment_500');                 // 5xx 직격(0 이어야 함)

const proberWarmup = new Trend('prober_warmup_ms', true);
const proberLoad = new Trend('prober_load_ms', true);
const proberRecovery = new Trend('prober_recovery_ms', true);
const proberFail = new Rate('prober_fail');

export const options = {
    discardResponseBodies: false,
    scenarios: {
        prober: {
            executor: 'constant-arrival-rate',
            rate: Number(__ENV.PROBER_RATE || 5),
            timeUnit: '1s',
            duration: `${LOAD_END_SEC + 15}s`,
            preAllocatedVUs: 20,
            maxVUs: 60,
            exec: 'prober',
        },
        payment_load: {
            executor: 'constant-arrival-rate',
            startTime: `${WARMUP_SEC}s`,
            rate: Number(__ENV.PAY_RATE || 40),
            timeUnit: '1s',
            duration: `${LOAD_END_SEC - WARMUP_SEC}s`,
            preAllocatedVUs: 100,
            maxVUs: 300,
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

    proberFail.add(res.status !== 200);
}

export function payment() {
    const i = exec.scenario.iterationInTest;
    const orderId = ORDER_START_ID + (i % ORDER_COUNT);
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

    const duration = res.timings.duration;
    paymentAll.add(duration);
    if (i < CLOSED_SAMPLE_MAX) paymentClosed.add(duration);
    else if (i >= OPEN_SAMPLE_MIN) paymentOpen.add(duration);

    paymentFail.add(res.status !== 201);
    payment500.add(res.status === 500);
}
