# 결제 요청 API(`POST /api/v1/payments`) Resilience 보완 체크리스트

> `Round 6 - 학습 정리 (Resilience 심화).md` 기준으로 현재 구현(`PaymentService`, `PaymentGatewayImpl`, `PaymentGatewayFeignConfig`)을 점검한 결과.
> 항목 번호는 우선순위가 아니라 발견 순서. 1·5는 서로 묶여 있어 같이 다뤄야 한다.

---

## [x] 1. Timeout 미설정

- **현황(해결 전)**: `PaymentGatewayFeignConfig`에 `ErrorDecoder`/`Retryer` 빈만 있고 `feign.Request.Options`(connect/read timeout)가 없음 → Feign 기본값(connect 10s / read 60s) 그대로 사용 중.
- **문제**: PG 요청 지연 스펙은 100~500ms인데, 타임아웃이 그보다 100배 길게 잡혀 있음. PG가 느려지면 우리 쪽 스레드가 그만큼 오래 잡혀 §0(장애 전파) 패턴에 노출됨.
- **참고**: 노트 §1 "Timeout — 언제 포기하고 자원을 회수할 것인가"
- **조치**: `PaymentGatewayProperties`에 `connectTimeoutMillis`/`readTimeoutMillis` 추가, `application.yml`에 `connect-timeout-millis: 1000` / `read-timeout-millis: 2000` 설정, `PaymentGatewayFeignConfig`에 `Request.Options` 빈 추가. 값 산정 기준: read timeout = 스펙 최대 지연(500ms)의 약 4배 여유, connect timeout = 로컬 네트워크 기준 1초(그 이상이면 PG 다운으로 간주).
- **남은 위험**: timeout을 짧게 잡으면 정상이지만 느린 응답까지 실패로 간주해 `Retryer`가 재시도를 트리거할 가능성이 더 커짐 — 이건 5번(pg-simulator의 `orderId` 멱등성 미비)과 직결되므로 5번에서 같이 다뤄야 함.
- **관련 파일**: `PaymentGatewayProperties.java`, `PaymentGatewayFeignConfig.java`, `application.yml`

## [ ] 2. CircuitBreaker 부재 (round6 Must-Have)

- **현황**: `build.gradle.kts`에 resilience4j 의존성 자체가 없음. PG 실패 시 `Retryer.Default(100, 300, 3)`(`PaymentGatewayFeignConfig.java:18-20`)으로 재시도만 함.
- **문제**: PG가 완전히 죽어도(재시도 3회까지 다 실패) 다음 요청이 올 때마다 또 PG를 두드림 — "그만 두드려" 장치가 없어 자원 고갈 방지가 안 됨.
- **참고**: 노트 §7 "Circuit Breaker"
- **관련 파일**: `apps/commerce-api/build.gradle.kts`, `PaymentGatewayImpl.java`

## [ ] 3. 실패를 "결제 실패"처럼 응답함

- **현황**: PG 호출 실패 시 `PaymentGatewayException`(`ErrorType.INTERNAL_ERROR`)이 컨트롤러까지 그대로 전파 → 사용자는 500 에러를 받음.
- **문제**: 실제로는 "PG가 응답을 못 줬다"일 뿐 결제 성패는 모르는 상황인데, 그걸 "에러"로 단정해서 응답함. 노트가 강조하는 "모를 때는 모른다고(PENDING) 답한다" 원칙과 어긋남.
- **참고**: 노트 §8 "Fallback — 막혔을 때 무엇을 돌려줄 것인가"
- **관련 파일**: `apps/commerce-api/src/main/java/com/loopers/application/payment/PaymentFacade.java`, `PaymentService.java:15-24`

## [ ] 4. 영구 실패(4xx)가 FAILED로 전이되지 않음 — 가장 확실한 버그

- **현황**: `PaymentGatewayErrorDecoder`는 4xx를 `CoreException(BAD_REQUEST)`로 변환해서 던지는데, 이 타입은 `PaymentGatewayImpl`의 catch 블록(`DecodeException`/`RetryableException`/`FeignException`, `PaymentGatewayImpl.java:45,48,51`) 중 어디에도 안 걸려서 그대로 빠져나감.
- **문제**: `PaymentService.pay()`(`PaymentService.java:16`)에서 이미 저장된 PENDING 레코드(`transactionKey` 없음)가 FAILED로 갱신되지 않고 **영원히 PENDING으로 남음**. 노트가 경고하는 "영원한 PENDING" 상태가 코드에서 바로 재현됨.
- **참고**: 노트 §4 "상태 머신 — 호출 흐름이 아니라 상태 전이로"
- **관련 파일**: `PaymentGatewayImpl.java`, `PaymentGatewayErrorDecoder.java`, `PaymentService.java`

## [x] 5. 재시도가 멱등하지 않음 (1번과 묶임)

- **현황(해결 전)**: pg-simulator `PaymentApplicationService.createTransaction()`(`apps/pg-simulator/.../PaymentApplicationService.kt:28-47`)은 `orderId` 중복 체크가 없어서, 호출될 때마다 새 `transactionKey`로 새 거래를 만듦.
- **문제**: 지금은 우연히 안전함 — pg-simulator의 "40% 요청 실패"(`PaymentApi.kt`)가 `createTransaction()` 호출 *전*에 터지기 때문에 재시도해도 거래가 1번만 생성됨. **하지만 1번(타임아웃)을 추가하는 순간 깨짐.** PG가 거래를 만들었는데 응답만 늦게 와서 read timeout이 나면, Feign `Retryer`가 자동 재시도하면서 같은 주문에 대해 두 번째 거래를 만들 수 있음.
- **추가로 확인한 것**: "5xx는 거래 생성 전이라 안전"이라는 판단은 pg-simulator 소스를 직접 읽었기 때문에 가능했던 것 — 실제 PG라면 응답 코드만으로 내부 상태를 알 수 없으므로 일반화할 수 없는 가정이었음. 그래서 4xx(영구 실패) 외에 `RetryableException`으로 분류되는 모든 경우(5xx든 네트워크 타임아웃이든)를 동일하게 처리하기로 결정.
- **조치**: 노트 §2/§6/§3이 제시하는 "재시도 전 orderId로 조회" 패턴을 적용.
  - 조회 로직은 `PaymentGatewayImpl`(infra) 안의 `private findByOrderNumber(userNumber, orderNumber)`로 둠 — "PG와 어떻게 안전하게 대화할까"는 게이트웨이 어댑터의 책임이고 `PaymentService`는 도메인 의미만 다루도록 경계 유지. 처음엔 `PaymentGateway`(domain) 인터페이스에 노출했었는데, 현재 호출자가 `PaymentGatewayImpl` 자기 자신뿐이라(스케줄러는 아직 없음) "가설적인 향후 요구사항을 위해 설계하지 않는다" 원칙에 따라 private로 내림. 스케줄러를 실제로 만들 때 인터페이스로 끌어올리면 됨.
  - `PaymentGatewayFeignClient`에 pg-simulator의 `GET /api/v1/payments?orderId=...` 조회 메서드 추가.
  - `PaymentGatewayErrorDecoder`가 404를 `ErrorDecoder.Default`에 위임해 Feign의 타입 있는 예외 `FeignException.NotFound`로 흘려보냄(다른 4xx는 기존처럼 `CoreException(BAD_REQUEST)`) — `methodKey` 문자열 매칭 대신 타입으로 구분해 더 안전함.
  - `PaymentGatewayFeignConfig`의 `Retryer`를 `NEVER_RETRY`로 변경 — Feign이 우리 코드보다 먼저 자동 재시도해버리는 것을 막음.
  - `PaymentGatewayImpl.requestPayment()`를 "시도 → `RetryableException` 발생 → orderId 조회 → 주문 없음이면 백오프 후 재시도(최대 3회) / 처리중·성공·실패면 그 결과로 확정"하는 루프로 재작성.
- **남은 위험**: `DecodeException`(PG가 2xx로 응답했지만 본문 디코딩 실패)도 사실 "거래는 생성됐는데 우리가 키를 못 읽은" 케이스라 같은 조회 보완이 필요할 수 있음 — 이번엔 범위에 넣지 않고 기존처럼 즉시 실패 처리로 남김. 체크리스트 4번(영구 실패 시 FAILED 미전이)도 아직 별도로 남아 있음.
- **참고**: 노트 §2 "멱등성", §6 함정③ "재시도와 멱등성", §3 "조회의 진짜 목적"
- **관련 파일**: `PaymentGateway.java`, `PaymentGatewayFeignClient.java`, `PaymentGatewayErrorDecoder.java`, `PaymentGatewayFeignConfig.java`, `PaymentGatewayImpl.java`
