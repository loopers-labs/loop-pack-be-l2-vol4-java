# Volume 6 — Failure-Ready Systems 과제 계획

> PG 외부 연동의 장애 대응 설계를 학습하고 단계적으로 구현한다.
> 참고: `failure-ready-systems.md`

---

## 체크리스트

### PG 연동 대응

- [ ] PG 연동 API는 RestTemplate 혹은 FeignClient 로 외부 시스템을 호출한다
- [ ] 응답 지연에 대해 타임아웃을 설정하고, 실패 시 적절한 예외 처리 로직을 구현한다
- [ ] 결제 요청에 대한 실패 응답에 대해 적절한 시스템 연동을 진행한다
- [ ] 콜백 방식 + 결제 상태 확인 API를 활용해 적절하게 시스템과 결제정보를 연동한다

### Resilience 설계

- [ ] 서킷 브레이커 혹은 재시도 정책을 적용하여 장애 확산을 방지한다
- [ ] 외부 시스템 장애 시에도 내부 시스템은 정상적으로 응답하도록 보호한다
- [ ] 콜백이 오지 않더라도, 일정 주기 혹은 수동 API 호출로 상태를 복구할 수 있다
- [ ] PG 요청이 타임아웃에 의해 실패되더라도 해당 결제건 정보를 확인하여 정상 반영한다

---

## 진행 순서 (예정)

1. **Timeout 설정** — RestTemplate connect/read timeout
2. **실패 예외 처리** — PG 요청 실패 시 주문 상태 처리
3. **콜백 + 상태 확인 API 연동** — 콜백 미수신 시 조회로 보완
4. **Circuit Breaker** — Resilience4j 적용
5. **Retry 정책** — backoff + fallback
6. **상태 복구 API** — 수동/주기 상태 동기화

---

## 진행 기록

| 항목 | 상태 | 비고 |
|---|---|---|
| Timeout 설정 | ✅ | connectTimeout 1s, readTimeout 3s, yml 관리 |
| 실패 예외 처리 | ✅ | 4xx → CoreException 즉시, 5xx → Retry → CB → fallback |
| 콜백 + 상태 확인 API | ✅ | 재결제 선조회 중복 차단 + PgTransactionStatus enum |
| Circuit Breaker | ✅ | TIME_BASED 60s, 실패율 50%, Open 10s |
| Retry | ✅ | max-attempts 3, 500ms fixed, 5xx만 |
| 상태 복구 | ✅ | PaymentSyncScheduler 5분 주기 PENDING 동기화 |
