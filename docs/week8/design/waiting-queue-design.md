# 대기열 시스템 설계

## 개요

블랙 프라이데이 같은 트래픽 폭증 상황에서 주문 API 앞단에 Redis 기반 대기열을 두어, 시스템 처리량을 보호하면서 유저에게 공정한 순서와 실시간 피드백을 제공한다.

- 참고 자료: [round8-please-wait-queue.md](../round8-please-wait-queue.md), [round8-quests.md](../round8-quests.md)
- 체크리스트 대상: Step 1(대기열) / Step 2(입장 토큰 & 스케줄러) / Step 3(실시간 순번 조회)

이 문서는 설계 논의를 진행하며 계속 갱신한다.

---

## 선착순 vs 대기열 — 순서의 역할이 다르다

- **선착순(R7 쿠폰)**: 순서는 중요하지 않다. **제한만 중요하다.** 내가 성공한 N명 중 5번째였는지 8번째였는지는 의미가 없고, "제한 인원 안에 들었는가"만 결과에 영향을 준다. 그래서 Kafka처럼 컨슈머가 순차 소비하며 제한을 넘는 순간부터 거부하는 방식으로 충분하다.
- **대기열(R8 주문)**: **제한과 순서 모두 중요하다.** 처리량 제한(스케줄러가 한 번에 몇 명 들여보낼지)뿐 아니라, 유저가 대기하는 내내 "내가 몇 번째인지"라는 정확한 순서 자체가 외부에 노출되는 정보이자 공정성의 근거다.

이 차이가 이번 설계에서 score 생성 방식, 재시도 시 순번 보존, race window 처리처럼 "정확한 순서"에 유독 신경 써야 했던 이유다. 선착순이었다면 신경 쓸 필요가 없었을 디테일들이다.

---

## 백프레셔 구현 방식 판단 흐름

트래픽이 몰릴 때 백프레셔를 구현하는 선택지를 일반화하면 아래 순서로 좁혀진다.

1. **거부(Rate Limiting) vs 보관 후 처리(Queuing)** — 초과 요청을 즉시 실패시킬지, 받아서 나중에 처리할지.
2. **Queuing을 택했다면**, 결국 "먼저 저장해두고 하나씩 꺼내서 처리"하는 구조다. 저장 수단은 순서 있게 기록할 수 있으면 무엇이든 후보가 된다 — Kafka, Redis, DB 등.
3. **동기 vs 비동기 처리는 도구가 아니라 "유저가 결과를 즉시 확인해야 하는가"가 결정한다.**
   - 즉시 확인이 필요하면 → 처리를 뒤로 미룰 수 없으므로 애초에 버퍼링 자체가 성립하지 않는다. 요청-응답 사이클 안에서 동기로 완결돼야 한다.
   - 나중에 확인해도 되면 → 버퍼링 후 워커(컨슈머)가 순차 처리하는 비동기 구조가 가능해진다.
4. **DB 부하를 고려하면 큐 저장소 후보에서 DB는 배제된다.** 보호하려는 대상(DB)을 큐 저장소로 다시 쓰는 건 자기모순이다 → Kafka 또는 Redis가 남는다.
5. **Kafka와 Redis 중에서는 "순서가 필요한가"가 아니라 "유저 개인의 실시간 순위 노출이 필요한가"로 갈린다.**
   - 처리 순서 보장만 필요(유저가 자기 등수를 몰라도 됨) → Kafka로 충분. 파티션 내 순차 소비로 처리 순서는 자연히 보장된다.
   - 유저 개인의 실시간 순위(예: "지금 512번째")를 노출해야 함 → Redis Sorted Set이 독보적이다. `ZRANK`가 O(log N)으로 즉시 순위를 제공하는 반면, Kafka는 오프셋 기반이라 개별 유저의 상대 순위를 실시간으로 뽑아내는 데 자연스럽지 않다.

| | R7 쿠폰 (선착순) | R8 주문 (대기열) |
|---|---|---|
| 1. 거부 vs 보관 | 보관 | 보관 |
| 3. 즉시 확인 필요? | 아니오 → 비동기 가능 | 진입 자체는 아니오(폴링 가능), 실제 주문 처리는 예(동기 필요) |
| 4. DB 배제 후 남는 후보 | Kafka / Redis | Kafka / Redis |
| 5. 실시간 개인 순위 노출 필요? | 불필요 | 필요 |
| **결론** | **Kafka** | **Redis Sorted Set** |

---

## 왜 Redis인가

대기열 요구사항이 가진 4가지 특성이 각각 Redis의 특정 기능과 정확히 맞물린다.

### 1. "몇 번째인지" = 상대적 순위 쿼리, 그것도 초당 수천 번

`GET /queue/position`은 단순 상태 조회가 아니라 "전체 대기자 중 내 위치"를 매번 계산해야 한다. RDB로 하면 `SELECT COUNT(*) WHERE entered_at < my_entered_at`류의 쿼리를 폴링 주기(1~3초)마다 대기 인원 수만큼 날려야 하는데, 애초에 이 대기열을 만든 이유가 DB 부하를 막기 위해서라는 걸 생각하면 모순이다. Redis Sorted Set의 `ZRANK`는 O(log N)으로 이 상대 순위를 인메모리에서 즉시 반환하므로, 보호 대상(DB)과 완전히 분리된 곳에서 이 조회 트래픽을 흡수할 수 있다.

### 2. 동시 진입에 대한 순서 보장 + 중복 방지를 애플리케이션 락 없이

수천 명이 동시에 `POST /queue/enter`를 호출해도 `ZADD`가 원자적이라 정합성이 깨지지 않는다. RDB로 순번을 관리하면 동시 INSERT 시 순번 계산에 경쟁 조건이 생겨 별도 락이 필요해지지만, Sorted Set은 애초에 Set이라 동일 member(userId) 중복 삽입 처리(`NX`)도 자료구조 차원에서 자연스럽게 해결된다.

### 3. 입장 토큰의 자동 만료

"토큰을 받고 쓰지 않으면 TTL 후 자동 만료 → 다음 사람에게 기회"라는 요구사항 자체가 TTL이 인프라 레벨에서 지원되길 요구한다. `SET entry-token:{userId} {token} EX 300` 한 줄로 끝나는 걸, RDB로 하면 만료를 감지해서 지우는 배치 잡을 별도로 돌려야 한다.

### 4. 보호 대상과 대기열 상태 저장소의 분리

가장 근본적인 이유. 이 시스템의 목적 자체가 "DB 커넥션 풀이 고갈되지 않게 유저를 순서대로 들여보내는 것"인데, 그 대기열 상태를 DB에 두면 대기열 관리 자체가 보호하려던 DB에 부하를 얹는 자기모순이 된다. Redis를 별도 인프라로 둬야 "게이트키퍼가 게이트 대상과 같은 자원을 공유하지 않는" 구조가 성립한다.

---

## 구현 대상 API

체크리스트를 만족시키기 위해 필요한 API는 **신규 2종 + 기존 API 수정 1건**이다.

| API | 종류 | 비고 |
|---|---|---|
| `POST /api/v1/queue/enter` | 신규 | 대기열 진입 |
| `GET /api/v1/queue/position` | 신규 | 순번 조회 (polling) |
| `POST /api/v1/orders` | 기존 수정 | 입장 토큰 헤더 검증 추가 |

스케줄러(대기열에서 N명씩 꺼내 토큰 발급)는 API가 아니라 내부 배치(`@Scheduled`)이며, `PaymentReconciliationScheduler`/`OutboxRelay`와 동일한 `fixedDelay` + 예외 격리 패턴을 따를 예정이다.

---

## 설계 결정 사항

### 1. 유저 식별 방식

**결정: 기존 `X-Loopers-LoginId` / `X-Loopers-LoginPw` 헤더 재사용**

`OrderV1Controller`와 동일한 컨벤션을 따른다. `queue/enter`, `queue/position` 모두 이 헤더로 `UserService`를 통해 userId를 확인한다.

- 장점: 기존 인증 컨벤션과 일관성 유지
- 트레이드오프: polling API(`GET /queue/position`)가 매 호출마다 loginId/loginPw 기반 인증 조회를 반복하게 됨 — 대기 인원이 많고 polling 주기가 짧을 경우 인증 조회 자체가 부하 요인이 될 수 있음. 추후 캐싱/경량 식별자 도입 여지는 남겨둔다.

### 2. 입장 토큰 헤더 이름

**결정: `X-Loopers-EntryToken`**

문서 원문은 `X-Entry-Token`이지만, 프로젝트의 `AuthHeaders`(`X-Loopers-LoginId`, `X-Loopers-LoginPw`) 컨벤션에 맞춰 `X-Loopers-EntryToken`으로 통일한다.

### 3. 토큰 발급 → 주문 API 호출 흐름

**결정: 클라이언트가 두 API를 순차 호출한다. 서버 간 리다이렉트는 사용하지 않는다.**

근거:
- `GET /queue/position`은 polling 기반 JSON API이며, 토큰은 응답 바디에 담겨 전달된다. 서버가 클라이언트에게 능동적으로 push할 방법이 없으므로(SSE 미적용 시), 클라이언트가 응답을 읽고 다음 행동을 스스로 트리거해야 한다.
- HTTP 리다이렉트(3xx)는 주문 아이템(`items`, `couponId`)이 담긴 요청 바디를 실어 보낼 수 없어 이 시나리오에 맞지 않는다.

```
[클라이언트] → GET /api/v1/queue/position (polling)
             ← { "position": 0, "estimatedWaitSeconds": 0, "token": "abc-123" }
             → POST /api/v1/orders (Header: X-Loopers-EntryToken: abc-123)
```

### 4. Score(순번 정렬 키) 생성 방식

**결정: 요청을 수신한 앱 서버에서, 다른 로직(인증 등)보다 먼저 캡처한 서버 타임스탬프(`Instant.now().toEpochMilli()`)를 score로 사용한다. 클라이언트가 보낸 값이나 Redis 서버 측 단조증가 값(`INCR`)은 사용하지 않는다.**

검토했던 대안과 기각 근거:

- **클라이언트가 보낸 타임스탬프**: 클라이언트 시계는 조작·오차가 가능해 신뢰할 수 없다.
- **Redis `INCR` 기반 단조증가 시퀀스**: 얼핏 더 엄밀해 보이지만, "요청이 앱 서버에 도착한 순서"와 "그 요청 처리 스레드가 Redis에 커맨드를 보내는 순서"는 별개다. 스레드 스케줄링·GC 포즈·커넥션 풀 대기·네트워크 RTT 때문에 나중에 도착한 요청이 먼저 Redis에 닿을 수 있다. 오히려 Redis까지의 왕복을 한 번 더 거치는 만큼 비결정성이 하나 더 늘어난다.
- **결론**: 요청 수신 즉시, 다른 처리보다 먼저 캡처하는 로컬 타임스탬프가 실제 도착 순서에 가장 가깝다.

남는 오차(멀티 인스턴스 clock skew, 동일 밀리초 tie-break — Redis ZSET은 score가 같으면 member 문자열 사전순으로 정렬)는 무시 가능한 수준으로 판단한다. 대기열의 목적은 나노초 단위 정밀한 순서 보장이 아니라 "대략적인 FCFS + 부하 제어"이며, 유저는 몇 ms 내의 순서 역전을 체감할 수 없다.

### 5. 재시도(retry) 시 동작

**결정: `enter` 요청이 중복(유저의 더블클릭, 네트워크 타임아웃에 의한 클라이언트 자동 재시도 등)으로 들어와도 timestamp(score)는 갱신하지 않는다.**

근거:
- 재시도 요청은 원래 요청보다 항상 나중에 도착한다. 이때 score를 갱신하면 재시도할수록 score가 커져 대기열에서 계속 뒤로 밀리게 되는데, 이는 "먼저 온 사람이 앞 순번"이라는 공정성 원칙에 위배된다. 네트워크가 불안정한 유저가 오히려 불이익을 받는 셈이다.
- `ZADD ... NX`는 이미 존재하는 member(userId)의 score를 건드리지 않으므로 이 문제를 자료구조 차원에서 해결한다.

**구현 방식**: `ZADD NX`는 member가 이미 있으면 에러 없이 0(추가된 개수)을 반환할 뿐이다. `enter` 핸들러는 이 반환값을 분기 처리할 필요 없이, 성공/no-op 여부와 무관하게 항상 이어서 `ZRANK`로 현재 순번을 조회해 200 응답을 준다. 즉 `enter`는 자연스럽게 멱등(idempotent) API가 된다 — "이미 대기열에 있으면 기존 순번을 그대로 알려주는 재진입"으로 동작.

**구분되는 케이스**: 스케줄러가 `ZPOPMIN`으로 이미 꺼내간(토큰 발급된) 유저는 더 이상 `waiting-queue`의 member가 아니므로, 이 유저가 다시 `enter`를 호출하면 `NX`가 정상적으로 새 timestamp로 새 항목을 추가한다. 이는 재시도가 아니라 진짜 재진입이므로 의도된 동작이다.

---

## 엔드포인트 계약

### 1. `POST /api/v1/queue/enter` — 대기열 진입

```
Headers: X-Loopers-LoginId, X-Loopers-LoginPw
Body: 없음
```

```json
// 200 OK
{
  "position": 512,
  "estimatedWaitSeconds": 180
}
```

- 내부 동작: `loginId/loginPw` → userId 확인 → `ZADD waiting-queue NX {serverTimestampMillis} {userId}` (score 생성 방식은 [설계 결정 사항 4번](#4-score순번-정렬-키-생성-방식) 참고)
- `NX` 옵션으로 이미 대기열에 있는 유저의 재진입 시 순번(score)이 밀리지 않도록 함 → 체크리스트 "userId 기반 중복 진입 방지" 충족

### 2. `GET /api/v1/queue/position` — 순번 조회 (polling)

```
Headers: X-Loopers-LoginId, X-Loopers-LoginPw
```

```json
// 대기 중
{
  "position": 128,
  "estimatedWaitSeconds": 45,
  "token": null
}

// 입장 순서 도달
{
  "position": 0,
  "estimatedWaitSeconds": 0,
  "token": "abc-123-def"
}
```

- `ZRANK waiting-queue {userId}` → 순번, `ZCARD waiting-queue` → 전체 대기 인원(체크리스트 항목, `estimatedWaitSeconds` 산식 내부에서 사용)
- `token`은 스케줄러가 이미 `entry-token:{userId}`를 발급해둔 경우에만 채워짐 (`GET entry-token:{userId}`)

### 3. `POST /api/v1/orders` — 기존 API 수정

```
Headers: X-Loopers-LoginId, X-Loopers-LoginPw, X-Loopers-EntryToken
Body: 기존 CreateRequest와 동일
```

- `OrderFacade.createOrder` 진입 전 `entry-token:{userId}` 검증 단계 추가
- 검증 실패 시 처리 방식(기존 `ErrorType` 재사용 여부, 신규 `ErrorType` 필요 여부)은 추후 논의
- 주문 성공 후 `DEL entry-token:{userId}`로 토큰 삭제

---

## DTO 설계

기존 `OrderV1Dto` 컨벤션(record + 정적 `from()` 팩토리)을 따른다.

```java
public class QueueV1Dto {

    public record EnterResponse(
            Long position,
            Long estimatedWaitSeconds
    ) {
        public static EnterResponse from(QueueInfo info) {
            return new EnterResponse(info.position(), info.estimatedWaitSeconds());
        }
    }

    public record PositionResponse(
            Long position,
            Long estimatedWaitSeconds,
            String token
    ) {
        public static PositionResponse from(QueueInfo info) {
            return new PositionResponse(info.position(), info.estimatedWaitSeconds(), info.token());
        }
    }
}
```

- `EnterResponse`와 `PositionResponse`를 분리한 이유: `enter` 시점에는 토큰이 발급될 수 없으므로(방금 진입했으니 순번 0이 아님) `token` 필드 자체가 불필요함.

---

## 다음 논의 항목 (TODO)

- [ ] `domain/queue` 도메인 모델 설계 (`QueueService`, `QueueRepository` 인터페이스, `QueueInfo` 애플리케이션 DTO)
- [ ] Redis 키·연산 매핑 확정 (`waiting-queue` ZSET, `entry-token:{userId}` String + TTL)
- [ ] 스케줄러(입장 토큰 배치 발급) 설계 — 배치 크기 산정 근거, Thundering Herd 완화 여부
- [ ] `POST /orders` 토큰 검증 실패 시 에러 처리 방식 (`ErrorType` 재사용 vs 신규 추가)
- [ ] `estimatedWaitSeconds` 계산식 확정 및 처리량(TPS) 파라미터 출처
