# Payment 도메인 요구사항

- 작성일: 2026-06-24
- 상태: 확정

---

## 1. 제품 개요

주문에 대한 카드 결제 기능을 추가한다.  
외부 PG(Payment Gateway) 시스템과 연동하며, 사용자가 결제 요청 시 PG에 승인을 요청하고  
PG의 비동기 처리 결과를 수신하여 주문 상태를 갱신한다.

---

## 2. 사용자 시나리오

### 유저
1. 주문 ID와 카드 정보를 입력해 결제를 요청한다.
2. 결제 결과(성공/실패)를 응답으로 받는다.
3. 결제 결과가 지연될 경우 PENDING 응답을 받는다.

---

## 3. 유저 스토리

| # | Actor | 기능 | 인수 조건 |
|---|-------|------|----------|
| US-01 | User | 결제 요청 | 유효한 주문 + 카드 정보로 PG 결제 요청, 최대 10초 내 결과 반환 |

---

## 4. 기능 요구사항

### 4-1. 결제 요청

| 항목 | 규칙 |
|------|------|
| `orderId` | 필수. 존재하는 주문, 요청자 본인 소유, PENDING 상태여야 함 |
| `cardType` | 필수. SHINHAN / SAMSUNG / KB / HYUNDAI / LOTTE / WOORI / HANA / BC |
| `cardNo` | 필수. `XXXX-XXXX-XXXX-XXXX` 형식 |
| 중복 결제 | 동일 orderId에 PENDING 또는 SUCCESS 결제가 이미 존재하면 거부 |
| 응답 대기 | PG 콜백 수신 시까지 최대 10초 대기. 초과 시 PG 1회 직접 조회(1차 Poll) 후 결과 반환. 1차 Poll도 PENDING이면 PENDING 반환 |

### 4-2. 결제 금액

결제 금액은 주문의 `finalAmount`를 사용한다. 별도 입력값을 받지 않는다.

---

## 5. 비기능 요구사항

| 항목 | 내용 |
|------|------|
| PG 호출 타임아웃 | connectTimeout 500ms / readTimeout 1s |
| 서킷브레이커 | Resilience4j. 10건 중 5건 실패 시 OPEN (15초 대기 후 HALF_OPEN) |
| 결제 대기 타임아웃 | CompletableFuture 10초. 초과 시 1차 Poll 후 결과 반환 |
| 1차 Poll 타임아웃 | 15초. timeout 이후 PG 최종 상태 직접 확인 |
| PENDING 자동 복구 | Batch Scheduler (현재 구현 범위 제외 — 추후 commerce-batch 모듈) |
| 최종 실패 처리 | 30분 이상 PENDING 지속 시 FAILED 처리 + 슬랙 알림 (Scheduler 구현 시 적용) |

---

## 6. API 엔드포인트

API 명세 및 Request / Response 상세는 [02-design.md](02-design.md) 섹션 3을 참조한다.

---

## 7. PG Simulator 특성

PG 시스템은 로컬에서 실행 가능한 `pg-simulator` 모듈로 제공된다. (별도 SpringBootApp)  
PG 시스템은 비동기 결제 기능을 제공한다. 요청과 실제 처리가 분리되어 있으며,  
처리 완료 후 요청 시 전달한 `callbackUrl`로 결과를 POST한다.

| 항목 | 값 |
|------|-----|
| 요청 성공률 | 60% |
| 요청 지연 | 100ms ~ 500ms |
| 처리 지연 | 1s ~ 5s |
| 처리 결과 | 성공 70% / 한도 초과 20% / 잘못된 카드 10% |
| 콜백 방식 | 처리 완료 후 `callbackUrl`로 POST |

### PG Simulator API

#### 결제 요청

```http
POST {{pg-simulator}}/api/v1/payments
X-USER-ID:
Content-Type: application/json

{
  "orderId": "1351039135",
  "cardType": "SAMSUNG",
  "cardNo": "1234-5678-9814-1451",
  "amount": "5000",
  "callbackUrl": "http://localhost:8080/api/v1/payments/callback"
}
```

#### 결제 정보 확인

```http
GET {{pg-simulator}}/api/v1/payments/20250816:TR:9577c5
X-USER-ID:
```

#### 주문에 엮인 결제 정보 조회

```http
GET {{pg-simulator}}/api/v1/payments?orderId=1351039135
X-USER-ID:
```
