# Round 6 Quests

> `round6` `task` `BE_L2`

---

## Implementation Quest

외부 시스템(PG) 장애 및 지연에 대응하는 Resilience 설계를 학습하고 적용한다.  
`pg-simulator` 모듈을 활용하여 다양한 비동기 시스템과의 연동 및 실패 시나리오를 구현·점검한다.

### Must-Have (무조건)

- [ ] Fallback
- [ ] Timeout
- [ ] CircuitBreaker

### Nice-To-Have (시간이 허락하면)

- [ ] Retryer

---

## 나의 시니어 파트너

외부 시스템과 연동되는 기능 설계를 분석하고, 개발자와의 질의응답을 통해 구조를 명확히 하며,  
**상태 불일치·트랜잭션 경계·장애 시나리오** 관점에서 리스크를 드러낼 수 있는 Skills를 작성한다.

### 작성할 스킬

경로: `~/.claude/skills/analyze-external-integration/SKILL.md`

스킬이 분석할 때 따라야 하는 흐름:

1. **기능이 아니라 "불확실성" 관점으로 재해석** — 외부 시스템은 항상 지연·실패·중복 실행·응답 유실이 가능하다고 가정한다
2. **트랜잭션 경계 검증** — 외부 호출 실패 시 내부 상태, 내부 커밋 이후 외부 호출 실패 시 복구 가능성, 외부 성공 후 내부 실패 시 정합성
3. **상태 기반으로 구조를 다시 본다** — 호출 흐름이 아니라 상태 전이 중심, 내부/외부 상태가 어긋날 수 있는 지점 명시
4. **중복 요청 및 재시도 가능성 분석** — 동일 요청이 두 번 실행될 경우 문제, 멱등성(Idempotency) 고려 여부
5. **장애 시나리오 최소 3가지 이상** — 정상 흐름보다 실패 흐름 우선, 각 시나리오별 데이터 정합성·상태 불일치·복구 가능성 분석
6. **해결책은 정답처럼 제시하지 않는다** — 현재 구조의 장점과 리스크를 분리하고, 대안은 선택지 형태로 복잡도·운영 부담과 함께 제시
7. **톤 & 스타일** — 코드 수정안 직접 제시 금지, 설계 비판 아닌 리스크 드러내는 리뷰 톤, 구현보다 책임·경계·상태 일관성 중심

---

## 결제 기능 추가

주문에 대한 결제 기능을 `commerce-api`에 추가한다.  
주문항목과 결제 수단을 입력받아 외부 결제 시스템과 연동 후 결제 처리하는 API를 작성한다.

```http
POST {{commerce-api}}/api/v1/payments
X-Loopers-LoginId: {loginId}
X-Loopers-LoginPw: {loginPw}
Content-Type: application/json

{
  "orderId": "1351039135",
  "cardType": "SAMSUNG",
  "cardNo": "1234-5678-9814-1451"
}
```

---

## 결제 시스템 연동 (pg-simulator)

PG 시스템은 로컬에서 실행 가능한 `pg-simulator` 모듈로 제공된다. (별도 SpringBootApp)

### PG 비동기 결제 특성

> 비동기 결제란, 요청과 실제 처리가 분리되어 있음을 의미한다.

| 항목 | 값 |
|------|-----|
| 요청 성공 확률 | 60% |
| 요청 지연 | 100ms ~ 500ms |
| 처리 지연 | 1s ~ 5s |
| 처리 결과 - 성공 | 70% |
| 처리 결과 - 한도 초과 | 20% |
| 처리 결과 - 잘못된 카드 | 10% |

### pg-simulator API

```http
### 결제 요청
POST {{pg-simulator}}/api/v1/payments
X-USER-ID: {userId}
Content-Type: application/json

{
  "orderId": "1351039135",
  "cardType": "SAMSUNG",
  "cardNo": "1234-5678-9814-1451",
  "amount": 5000,
  "callbackUrl": "http://localhost:8080/api/v1/examples/callback"
}

### 결제 정보 확인
GET {{pg-simulator}}/api/v1/payments/20250816:TR:9577c5
X-USER-ID: {userId}

### 주문에 연결된 결제 정보 조회
GET {{pg-simulator}}/api/v1/payments?orderId=1351039135
X-USER-ID: {userId}
```

---

## 과제 핵심 고민 포인트

- 외부 시스템에 대해 적절한 **타임아웃 기준**을 고려하고 적용한다
- 외부 시스템의 **응답 지연 및 실패**에 대처할 방법을 고민한다
- PG 결제 결과를 적절하게 시스템과 연동하고, **주문 상태를 안전하게 처리**할 방법을 고민한다
- **서킷브레이커**를 통해 외부 시스템 지연·실패에 대응하여 서비스 전체가 무너지지 않도록 보호한다

---

## Checklist

### PG 연동 대응

- [ ] PG 연동 API는 RestTemplate 혹은 FeignClient로 외부 시스템을 호출한다
- [ ] 응답 지연에 대해 타임아웃을 설정하고, 실패 시 적절한 예외 처리 로직을 구현한다
- [ ] 결제 요청에 대한 실패 응답에 대해 적절한 시스템 연동을 진행한다
- [ ] 콜백 방식 + 결제 상태 확인 API를 활용해 적절하게 시스템과 결제 정보를 연동한다

### Resilience 설계

- [ ] 서킷 브레이커 혹은 재시도 정책을 적용하여 장애 확산을 방지한다
- [ ] 외부 시스템 장애 시에도 내부 시스템은 정상적으로 응답하도록 보호한다
- [ ] 콜백이 오지 않더라도, 일정 주기 혹은 수동 API 호출로 상태를 복구할 수 있다
- [ ] PG에 대한 요청이 타임아웃에 의해 실패되더라도 해당 결제 건에 대한 정보를 확인하여 정상적으로 시스템에 반영한다

---

## Technical Writing Quest

피드백 & 라이팅 과제 — **테크노트 or 블로그** 중 편한 방법으로 작성하고, 해당 링크를 제출한다.  
멘토님들이 이를 기반으로 피드백 및 RT 선정을 진행한다.

### 방법 1. GitHub Issues

`New Issue` → 아래 4개 포맷 중 하나 선택해서 작성

| 포맷 | 설명 |
|------|------|
| Design Doc | 설계 의사결정 중심 |
| Retrospective | 과제 회고·트러블슈팅 |
| Challenge Story | 도전 → 해결 압축 서사 |
| Benchmark Report | A vs B 직접 측정 |

제목 형식: `[포맷] 키워드 (N주차 · K팀 · 이름)`

Issue 링크 (Java): `https://github.com/loopers-labs/loop-pack-be-l2-vol4-java/issues/new/choose`

### 방법 2. Blog

이번 주차 관련 내용을 자유롭게 작성한다.  
"내가 어떤 판단을 하고 왜 그렇게 구현했는지"를 글로 정리한다.

**블로그 주제 추천**

- PG 응답이 느려서 서킷브레이커가 열렸다...?
- 응답이 안 와서 실패 처리했는데, PG에선 결제가 됐다고...?
- 주문 상태는 Pending인데, 사용자는 결제 안내를 받았다
- PG 장애 하나로 주문 전체가 멈춰버렸다
- 결제가 실패하면 주문을 무조건 롤백해야 할까?
- 재시도 횟수는 몇 번이 적절했을까?
- 폴백 처리를 어떻게 했지?
