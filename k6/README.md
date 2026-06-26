# k6 부하 테스트 — resilience4j(pgClient 서킷브레이커)

100명의 유저가 여러 상품으로 주문 후 결제하는 상황(초당 30회 결제)을 모사해
PG 연동 서킷브레이커(`pgClient`)의 동작을 관찰한다.

## 구성

| 파일 | 용도 |
|------|------|
| `lib/helpers.js` | 공통: 시드(브랜드/상품/유저), 주문→결제 호출 |
| `payment-load.js` | 기본 부하: 초당 30회 결제 (constant-arrival-rate) |
| `payment-cb-open.js` | 서킷브레이커 OPEN 을 유발하는 버스트 프로파일 + CB 상태 폴링 |
| `../docker/grafana/provisioning/dashboards/resilience4j-payment.json` | Grafana 대시보드(자동 프로비저닝) |

## 사전 준비

```shell
# 1) 인프라 + 모니터링
docker-compose -f ./docker/infra-compose.yml up -d
docker-compose -f ./docker/monitoring-compose.yml up -d   # Grafana :3000 (admin/admin)

# 2) PG 시뮬레이터 (요청 40% 실패 + 처리 1~5s 후 콜백)
./gradlew :apps:pg-simulator:bootRun

# 3) commerce-api (앱 :8080, 관리/actuator :8081)
./gradlew :apps:commerce-api:bootRun

# 4) k6 설치 (mac)
brew install k6
```

## 실행

```shell
# 기본 부하 (초당 30회, 1분)
k6 run k6/payment-load.js
k6 run -e RATE=30 -e DURATION=2m -e USERS=100 k6/payment-load.js

# 서킷브레이커 OPEN 유발 (버스트). 결정적으로 열려면 commerce-api 를 임계치 낮춰 기동:
./gradlew :apps:commerce-api:bootRun --args='\
  --resilience4j.circuitbreaker.instances.pgClient.failure-rate-threshold=35 \
  --resilience4j.circuitbreaker.instances.pgClient.slow-call-duration-threshold=300ms \
  --resilience4j.circuitbreaker.instances.pgClient.wait-duration-in-open-state=10s'
k6 run k6/payment-cb-open.js
```

## 결과 관찰

- **콘솔**: `payment-cb-open.js` 는 CB 상태 전이를 `[CB] state X -> Y` 로 출력. teardown 이 최종 상태 dump.
- **actuator**: `http://localhost:8081/actuator/circuitbreakers`, `.../circuitbreakerevents`, `.../health`
  (commerce-api `application.yml` 에서 노출 추가됨)
- **Grafana**: `http://localhost:3000` → 폴더 `Loopers` → `Resilience4j · Payment (pgClient)`
  - CB State / Failure·Slow rate / Calls by kind / Not-permitted(fast-fail)

### 결제 결과(k6) 패널을 Grafana 로 보려면

k6 메트릭을 Prometheus 로 remote-write 한다(Prometheus 에 `--web.enable-remote-write-receiver` 필요).

```shell
K6_PROMETHEUS_RW_SERVER_URL=http://localhost:9090/api/v1/write \
K6_PROMETHEUS_RW_TREND_STATS=p(95),p(99),avg \
k6 run -o experimental-prometheus-rw k6/payment-load.js
```

→ 대시보드의 "결제 결과 분포 (k6 pay_result)" 패널에 SUCCESS / FAILED / HTTP_5xx 분포가 표시된다.

## 알아둘 점 (resilience4j 관점)

1. **결제는 콜백 대기로 수 초 블로킹**된다(`pg.callback-timeout-seconds=10s`). 30rps × 평균지연 →
   동시 in-flight 가 100+. Tomcat 스레드풀(max 200)·DB 커넥션풀이 병목이 되는지 함께 본다.
2. **기본 설정으론 CB 가 잘 안 열린다**: PG 요청 실패율 40% < threshold 50%, 그리고 `requestPayment`
   호출은 <1s 라 slow-call(2s) 도 거의 미발동. → `payment-cb-open.js` + 임계치 override 로 유발.
3. 진짜 1~5s 지연은 **비동기 콜백**이라 CB 가 감싼 호출 시간에 잡히지 않는다.
