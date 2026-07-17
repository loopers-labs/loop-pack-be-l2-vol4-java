# 대기열 시스템 — 아키텍처 & 상세 설계

> 대기열 설계 논의를 단일 책임 원칙 관점에서 3개 문서로 나눈 것 중 **"설계 문서"** 파트입니다. 아키텍처 결정 · API 계약 · 코드 구조(도메인 모델, Redis 매핑)처럼 **구현을 이해/진행하는 개발자**가 읽는 내용을 모읍니다.
>
> - 용량·수치 계산: [waiting-queue-capacity-planning.md](waiting-queue-capacity-planning.md)
> - 장애 대응·운영: [waiting-queue-runbook.md](waiting-queue-runbook.md)

## 개요

블랙 프라이데이 같은 트래픽 폭증 상황에서 주문 API 앞단에 Redis 기반 대기열을 두어, 시스템 처리량을 보호하면서 유저에게 공정한 순서와 실시간 피드백을 제공한다.

- 참고 자료: [round8-please-wait-queue.md](../round8-please-wait-queue.md), [round8-quests.md](../round8-quests.md)
- 체크리스트 대상: Step 1(대기열) / Step 2(입장 토큰 & 스케줄러) / Step 3(실시간 순번 조회)

---

## 선착순 vs 대기열 — 순서의 역할이 다르다

- **선착순(R7 쿠폰)**: 순서는 중요하지 않다. **제한만 중요하다.** 내가 성공한 N명 중 5번째였는지 8번째였는지는 의미가 없고, "제한 인원 안에 들었는가"만 결과에 영향을 준다. 그래서 Kafka처럼 컨슈머가 순차 소비하며 제한을 넘는 순간부터 거부하는 방식으로 충분하다.
- **대기열(R8 주문)**: **제한과 순서 모두 중요하다.** 처리량 제한(스케줄러가 한 번에 몇 명 들여보낼지)뿐 아니라, 유저가 대기하는 내내 "내가 몇 번째인지"라는 정확한 순서 자체가 외부에 노출되는 정보이자 공정성의 근거다.

이 차이가 이번 설계에서 score 생성 방식, 재진입 시 순번 갱신 정책, race window 처리처럼 "정확한 순서"에 유독 신경 써야 했던 이유다. 선착순이었다면 신경 쓸 필요가 없었을 디테일들이다.

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

수천 명이 동시에 `POST /queue/enter`를 호출해도 `ZADD`가 원자적이라 정합성이 깨지지 않는다. RDB로 순번을 관리하면 동시 INSERT 시 순번 계산에 경쟁 조건이 생겨 별도 락이 필요해지지만, Sorted Set은 애초에 Set이라 동일 member(userId)가 다시 들어와도 새 항목이 추가되는 게 아니라 기존 항목의 score만 덮어써, 중복 삽입 문제 자체가 자료구조 차원에서 발생하지 않는다.

### 3. 입장 토큰의 자동 만료

"토큰을 받고 쓰지 않으면 TTL 후 자동 만료 → 다음 사람에게 기회"라는 요구사항 자체가 TTL이 인프라 레벨에서 지원되길 요구한다. `SET queue:entry-token:{userId} {token} EX 300` 한 줄로 끝나는 걸, RDB로 하면 만료를 감지해서 지우는 배치 잡을 별도로 돌려야 한다.

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

스케줄러(대기열에서 N명씩 꺼내 토큰 발급)는 API가 아니라 내부 배치(`@Scheduled`)이며, `PaymentReconciliationScheduler`/`OutboxRelay`와 동일한 `fixedDelay` + 예외 격리 패턴을 따른다(`QueueAdmissionScheduler`).

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

### 5. 재진입(re-enter) 시 동작

**결정: 이미 대기열에 있는 유저가 `enter`를 다시 호출하면 매번 timestamp(score)를 최신 값으로 갱신한다. `ZADD`에 `NX`는 사용하지 않는다.**

근거:
- 서버는 재호출의 원인이 "네트워크 타임아웃에 의한 클라이언트 자동 재시도"인지 "유저가 새로고침 등으로 의도적으로 다시 진입한 것"인지 구분할 수 없다. 즉 이 둘을 구분해서 하나만 봐주는 것은 애초에 불가능한 요구이므로, 재호출은 일괄적으로 "지금 다시 줄을 선 것"으로 취급한다.
- 새로고침 시 순번이 뒤로 밀리는 동작은 실제 티켓 대기열 서비스(인터파크, YES24 등)에서도 흔한 정책이다. 유저가 반복 새로고침으로 순번을 확인하거나 편법을 시도할 유인을 없애고, 서버에 불필요한 반복 호출(새로고침 스팸)이 몰리는 것을 억제하는 효과가 있다.
- 트레이드오프: 네트워크가 불안정해 클라이언트가 자동 재시도한 유저도 동일하게 순번이 밀린다. 이 프로젝트는 "네트워크 이슈로부터 유저 보호"보다 "새로고침 스팸 억제 + 실제 서비스 관행과의 일치"를 우선하기로 결정했다.

**구현 방식**: `ZADD queue:waiting-queue {serverTimestampMillis} {userId}` (NX 없음). 이미 존재하는 member면 score가 최신 timestamp로 덮어써지고, 존재하지 않으면 새로 추가된다 — 두 경우 모두 동일한 명령 한 줄로 처리되므로 `enter` 핸들러가 신규/재진입 여부를 분기할 필요가 없다. 이어서 `ZRANK`로 갱신된 순번을 조회해 200 응답을 준다.

**참고**: 스케줄러가 `ZPOPMIN`으로 이미 꺼내간(토큰 발급된) 유저가 다시 `enter`를 호출하는 경우도 이 로직과 동일하게 동작한다 — `queue:waiting-queue`의 member가 아니었으므로 새 timestamp로 새 항목이 추가될 뿐이며, 별도로 구분해서 처리할 필요가 없다.

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
  "position": 512
}
```

- 내부 동작: `loginId/loginPw` → userId 확인 → `ZADD queue:waiting-queue {serverTimestampMillis} {userId}` (score 생성 방식은 [설계 결정 사항 4번](#4-score순번-정렬-키-생성-방식) 참고)
- `ZADD`는 이미 존재하는 member의 score를 덮어쓰는 방식이라 동일 유저가 여러 개의 대기열 항목을 갖지 않음 → 체크리스트 "userId 기반 중복 진입 방지" 충족. 단, 재진입할 때마다 최신 시각으로 순번이 갱신되어 새로고침하면 뒤로 밀리는 정책을 채택함([설계 결정 사항 5번](#5-재진입re-enter-시-동작) 참고)

### 2. `GET /api/v1/queue/position` — 순번 조회 (polling)

```
Headers: X-Loopers-LoginId, X-Loopers-LoginPw
```

```json
// 대기 중
{
  "position": 128,
  "totalWaiting": 1500,
  "estimatedWaitSeconds": 45,
  "token": null
}

// 입장 순서 도달
{
  "position": 0,
  "totalWaiting": 1500,
  "estimatedWaitSeconds": 0,
  "token": "abc-123-def"
}
```

- `ZRANK queue:waiting-queue {userId}` → 순번, `ZCARD queue:waiting-queue` → `totalWaiting`(전체 대기 인원 — `round8-quests.md` Step 1 체크리스트 "전체 대기 인원 조회" 충족용. `estimatedWaitSeconds`는 내 순번만으로 계산되므로 `totalWaiting`을 필요로 하지 않는다)
- `token`은 스케줄러가 이미 `queue:entry-token:{userId}`를 발급해둔 경우에만 채워짐 (`GET queue:entry-token:{userId}`)

### 3. `POST /api/v1/orders` — 기존 API 수정

```
Headers: X-Loopers-LoginId, X-Loopers-LoginPw, X-Loopers-EntryToken
Body: 기존 CreateRequest와 동일
```

- `OrderFacade.createOrder` 진입 전 `queue:entry-token:{userId}` 검증 단계 추가
- **검증 실패 시(정상 상황 — Redis는 정상 응답, 토큰이 없거나 만료됨)**: 기존 `ErrorType.FORBIDDEN`(403)을 재사용한다. 기존 사용처(`OrderModel`, `UserModel`, `IssuedCouponModel` 등)는 전부 "본인 것이 아닌 리소스 접근"이라는 소유권 불일치 케이스지만, `FORBIDDEN`의 메시지("접근 권한이 없습니다")가 "입장 토큰이 없거나 만료되어 이 API에 접근할 권한이 없다"는 우리 케이스도 포괄하는 상위 개념이라 새 `ErrorType`을 추가하지 않는다.
  ```java
  throw new CoreException(ErrorType.FORBIDDEN, "입장 토큰이 없거나 만료되었습니다. 대기열을 통해 다시 진입해주세요.");
  ```
- **Redis 자체 장애로 검증을 못 하는 경우는 위와 다르게 처리한다** — 자세한 내용은 [운영 Runbook: Redis 장애 시 — Graceful Degradation](waiting-queue-runbook.md#redis-장애-시--graceful-degradation) 참고. `EntryTokenRepository.find()`/`EntryTokenService.verify()` 내부에서 Redis 연결 예외를 절대 잡아 삼키지 않는다(예: `catch (Exception e) { return false; }`처럼 처리하면 "Redis 장애"가 "토큰 없음"(403)으로 둔갑해버려 유저에게 잘못된 신호를 주고 장애 인지도 어려워진다). 예외는 그대로 전파시켜 `ApiControllerAdvice`의 전용 핸들러가 `ErrorType.SERVICE_UNAVAILABLE`(503, 신규 추가)로 변환한다.
- 주문 성공 후 `DEL queue:entry-token:{userId}`로 토큰 삭제

---

## DTO 설계

기존 `OrderV1Dto` 컨벤션(record + 정적 `from()` 팩토리)을 따른다.

```java
public class QueueV1Dto {

    public record EnterResponse(
            Long position
    ) {
        public static EnterResponse from(QueueInfo info) {
            return new EnterResponse(info.position());
        }
    }

    public record PositionResponse(
            Long position,
            Long totalWaiting,
            Long estimatedWaitSeconds,
            String token
    ) {
        public static PositionResponse from(QueueInfo info) {
            return new PositionResponse(info.position(), info.totalWaiting(), info.estimatedWaitSeconds(), info.token());
        }
    }
}
```

- `EnterResponse`와 `PositionResponse`를 분리한 이유:
  - `token`: `enter` 시점에는 토큰이 발급될 수 없으므로(방금 진입했으니 순번 0이 아님) 필드 자체가 불필요함.
  - `estimatedWaitSeconds`: 참고 자료 원문(`round8-please-wait-queue.md`) 기준으로도 `enter`는 순번만 반환하고, 예상 대기 시간은 반복 조회되는 `position`에만 붙는다. `enter`는 진입 확인 응답, `position`은 대기 중 지속적으로 갱신되는 피드백이라는 역할 차이를 그대로 따른다.

---

## 도메인 모델 설계

### domain/queue 패키지 구성

Redis의 두 자료구조(ZSET, String+TTL)가 서로 다른 생명주기를 가지므로, 기존 `coupon`(`CouponTemplate*` + `IssuedCoupon*`)·`product`(`Product*` + `ProductStats*`) 도메인처럼 하나의 도메인 패키지 안에 두 세트로 분리한다.

```
domain/queue/
  WaitingQueueRank (record)  — long value (0-based rank). 생성자에서 음수 방어(CoreException/BAD_REQUEST)
  WaitingQueueRepository (interface)  — queue:waiting-queue ZSET
    WaitingQueueRank enter(Long userId, long timestampMillis)  // ZADD 직후 같은 Master 템플릿으로 ZRANK까지 조회
    WaitingQueueRank rank(Long userId)                          // ZRANK
    Long size()                                                 // ZCARD
  WaitingQueueService (@Component)

  EntryTokenRepository (interface)    — queue:entry-token:{userId} String+TTL
    Optional<String> find(Long userId)                    // GET
    void delete(Long userId)                               // DEL
  EntryTokenService (@Component)

  QueueAdmissionRepository (interface) — 스케줄러 전용, ZSET+String을 동시에 다루는 원자적 배치 연산
    record AdmittedEntry(Long userId, String token)
    List<AdmittedEntry> admitBatch(int count, Duration tokenTtl)
```

**Model 클래스는 두지 않는다.** ZSET member(userId+timestamp), String 토큰 모두 JPA `@Entity`/`BaseEntity`로 표현할 상태나 캡슐화할 비즈니스 규칙이 없다 — 정렬·만료는 Redis 자료구조 자체가 처리한다. Repository가 원시 타입(`Long`, `String`, `List`)을 직접 주고받는 것으로 충분하다.

**`WaitingQueueRepository`에 `pollMin`, `EntryTokenRepository`에 `issue`를 두지 않은 이유**: 스케줄러가 "대기열에서 N명을 꺼내는 것"과 "그 N명에게 토큰을 발급하는 것"을 하나의 Lua 스크립트로 원자 처리해야 하는데(자세한 이유는 [ZPOPMIN과 토큰 발급 사이의 원자성](#zpopmin과-토큰-발급-사이의-원자성--lua-스크립트) 참고), 이 연산은 두 자료구조를 동시에 건드리는 별개의 관심사라 어느 한쪽 Repository에 억지로 끼워 넣지 않고 `QueueAdmissionRepository`로 분리했다. 그 결과 토큰 발급 경로는 스케줄러의 배치 발급 하나뿐이라, `EntryTokenRepository`는 조회(`find`)와 소비(`delete`)만 담당한다.

### application/queue — QueueFacade가 필요한 이유

`POST /queue/enter`, `GET /queue/position` 모두 `X-Loopers-LoginId`/`X-Loopers-LoginPw` → `UserService.getLoginUser()`로 userId를 확인하는 과정이 선행되어야 한다(`OrderFacade.createOrder`와 동일 패턴). 즉 두 엔드포인트 모두 `user` 도메인과 `queue` 도메인(`WaitingQueueService`, `EntryTokenService`)을 조합하는 유스케이스이므로, Facade 없이 Controller가 domain Service를 직접 호출하는 방식(예: `CouponAdminV1Controller`가 단순 CRUD에 `CouponTemplateService`를 직접 쓰는 경우)은 적용할 수 없다.

```java
// application/queue/QueueFacade.java (실제 소스 그대로)
public class QueueFacade {

    private final UserService userService;
    private final WaitingQueueService waitingQueueService;
    private final EntryTokenService entryTokenService;
    private final QueueProperties queueProperties;

    public QueueInfo enter(String loginId, String loginPw) {
        UserModel user = userService.getLoginUser(loginId, loginPw);
        WaitingQueueRank rank = waitingQueueService.enter(user.getId());
        return QueueInfo.forEnter(rank.value());
    }

    public QueueInfo getPosition(String loginId, String loginPw) {
        UserModel user = userService.getLoginUser(loginId, loginPw);
        WaitingQueueRank rank = waitingQueueService.getRank(user.getId());
        Long size = waitingQueueService.size();
        String token = entryTokenService.find(user.getId()).orElse(null);

        // 이미 발급받아 대기열(ZSET)에서 빠진 유저는 rank가 null이다 — 더 이상 기다릴 필요가 없으므로 position 0으로 취급한다.
        long position = rank != null ? rank.value() : 0L;
        long estimatedWaitSeconds = (long) Math.ceil((double) position / queueProperties.throughputPerSecond());
        return QueueInfo.forPosition(position, size, estimatedWaitSeconds, token);
    }
}
```

`getRank()`가 반환하는 `WaitingQueueRank`는 스케줄러가 이미 `admitBatch`로 꺼내가 대기열(ZSET)에 더 이상 없는 유저에 대해서는 `null`이다 — 이 케이스를 `position = 0`으로 명시적으로 처리하지 않으면 `estimatedWaitSeconds` 계산에서 `NullPointerException`이 난다.

`application/queue/QueueInfo` (record) — position, totalWaiting(nullable), estimatedWaitSeconds(nullable), token(nullable)

`QueueProperties`(`@ConfigurationProperties`)와 `estimatedWaitSeconds` 계산식 자체의 근거는 [용량 산정 문서](waiting-queue-capacity-planning.md)에서 다룬다 — 이 값이 실측 데이터로 갱신될 때 코드 구조(이 문서)를 안 건드리고 숫자만 바꿀 수 있도록 분리했다.

### POST /orders와의 연결

`OrderFacade`는 `EntryTokenService`에 대한 의존을 추가해, `createOrder` 진입 전 토큰을 검증(`verify`)하고 성공 후 소비(`consume` → 내부적으로 `delete`)한다. `order` 도메인과 `queue` 도메인을 조합하는 것이므로 이 조합도 Facade(`OrderFacade`)에서 처리한다.

---

## Redis 키·연산 매핑

### 키 네이밍

기존 `ProductCacheStore`(`infrastructure/product`)의 `:` 구분자 컨벤션(`product:detail:{id}`, `product:list:...`)을 따라 도메인 prefix를 붙인다.

- `queue:waiting-queue` — ZSET, 단일 키(상수)
- `queue:entry-token:{userId}` — String, 유저별 키

### RedisTemplate 선택 — master/replica 라우팅

`modules/redis`의 `RedisConfig`는 기본(`@Primary`) `RedisTemplate<String, String>`을 `ReadFrom.REPLICA_PREFERRED`로, `@Qualifier(RedisConfig.REDIS_TEMPLATE_MASTER)` 별도 템플릿을 쓰기 전용(`ReadFrom.MASTER`)으로 제공한다. `ProductCacheStore`는 캐시(약한 일관성 허용, fail-open)라 이 구분 없이 기본 템플릿만 쓰지만, 대기열은 지점별로 일관성 요구 수준이 다르다.

`enter()`는 `ZADD`(쓰기) 직후 바로 `ZRANK`로 방금 넣은 순번을 읽어 응답해야 한다(read-your-own-write). `ZADD`와 `ZRANK`는 원자적 파이프라인이 아니라 별개의 두 커맨드라, `ZRANK`를 기본(REPLICA_PREFERRED) 템플릿으로 읽으면 비동기 복제 지연 때문에 방금 추가한 member가 아직 replica에 반영되지 않아 순번이 틀리거나 null이 나올 수 있다. 반면 `GET /queue/position`의 폴링용 조회는 자신이 방금 쓴 게 아니라 1~3초 주기로 반복 조회하는 것이므로, 몇 ms 복제 지연은 체감 불가(설계 결정 4번의 clock skew 논리와 동일)해서 기본 템플릿으로 충분하다.

| 연산 | 컨텍스트 | 템플릿 |
|---|---|---|
| `ZADD`, `ZRANK`(enter 응답용) | `enter()` — 자신이 방금 쓴 값을 바로 읽음 | **Master** |
| `ZRANK`, `ZCARD`(position 조회용) | polling | 기본(REPLICA_PREFERRED) |
| `ZPOPMIN` + `SET ... EX` (Lua 스크립트로 원자 실행) | 스케줄러 배치 발급(`QueueAdmissionRepository.admitBatch`) | Master |
| `DEL` | 토큰 소비 | Master |
| `GET`(entry-token 조회) | enter/position 응답에 토큰 포함 여부 | 기본(REPLICA_PREFERRED) |

### 타입 변환

`RedisTemplate<String, String>`이라 `userId`(Long)는 문자열로 직렬화해서 저장(`String.valueOf(userId)`), 조회 시 `Long.parseLong(member)`으로 역변환한다. Lua 스크립트 내부에서 `ZPOPMIN`으로 꺼낸 member(문자열)도 동일하게 변환해 토큰 발급 대상 키(`queue:entry-token:{userId}`)를 구성한다.

### infrastructure 구현 위치

Redis 전용 domain interface + infrastructure impl 패턴은 이 프로젝트에서 처음 적용되는 사례다(JPA 쪽은 `OrderRepositoryImpl` 등 기존 있음, Redis는 지금까지 `ProductCacheStore`처럼 인터페이스 없이 infrastructure에 직접 두는 방식만 있었음).

- `infrastructure/queue/WaitingQueueRepositoryImpl` — 기본 템플릿과 `@Qualifier(REDIS_TEMPLATE_MASTER)` 템플릿을 모두 주입받아 연산별로 분기
- `infrastructure/queue/EntryTokenRepositoryImpl` — 기본 템플릿(`find`)과 Master 템플릿(`delete`)을 연산별로 분기
- `infrastructure/queue/QueueAdmissionRepositoryImpl` — Master 템플릿 전용. `resources/scripts/admit-batch.lua`를 `DefaultRedisScript`로 로드해 `ZPOPMIN`+`SET ... EX`를 한 번의 왕복으로 원자 실행 (자세한 설계 배경은 [ZPOPMIN과 토큰 발급 사이의 원자성](#zpopmin과-토큰-발급-사이의-원자성--lua-스크립트) 참고)

---

## 스케줄러 아키텍처

배치 크기(N) 산정 근거·TPS 계산은 [용량 산정 문서](waiting-queue-capacity-planning.md#스케줄러-실행-주기--배치-크기n-산정)에서, 장애 감지·헬스체크는 [운영 Runbook](waiting-queue-runbook.md#스케줄러-장애-감지--스레드풀-경합-예방--헬스체크-지표)에서 다룬다. 여기서는 스케줄러의 아키텍처적 결정(전략 선택, 원자성 확보)만 다룬다.

### Thundering Herd 완화 전략 — 3가지 후보 중 선택

| 전략 | 채택 여부 | 이유 |
|---|---|---|
| ① 발급 간격 분산 (100ms마다 소량씩) | **채택** | [용량 산정 문서](waiting-queue-capacity-planning.md#스케줄러-실행-주기--배치-크기n-산정)의 배치 크기 산정이 곧 이 전략. 1초에 75명을 한 번에 발급하는 대신 100ms마다 7명씩 나눠 발급해 순간 부하를 평탄화 |
| ② 토큰에 Jitter 부여(활성화 시점 랜덤 딜레이) | **미채택** | ①과 "동시에 몰리는 요청 수를 줄인다"는 같은 문제를 푸는 대안 관계이지 서로 보완하는 layer가 아님. 이미 배치 크기(N=7)가 pool(30)에 비해 충분히 작아서, 7명이 지연 없이 동시에 호출해도 위협이 안 됨 — ①만으로 이미 해결된 문제를 ②로 다시 풀 필요가 없음. 게다가 도입 시 `position=0`과 `token` 존재가 항상 같이 간다는 응답 구조의 전제가 깨져 `QueueInfo`에 "곧 통과" 상태를 별도로 표현해야 하는 비용이 추가로 든다 |
| ③ 주문 API 자체 Rate Limit | **채택** | ①·②와는 다른 범주의 문제(대기열/스케줄러 로직이 계산대로 동작하지 않는 상황)에 대한 최종 안전장치. 대기열이 아무리 정확해도 스케줄러 로직 버그, 향후 다중 인스턴스 스케일아웃 시 인스턴스별 독립 실행(이 프로젝트는 ShedLock 등 분산 락이 없어 `@Scheduled`가 인스턴스 단독 실행을 전제로 함), 대기열 대상이 아닌 다른 엔드포인트(상품 조회 등)發 pool 고갈은 대기열 쪽에서 절대 못 막는다 |

**Rate Limit 문턱값**: 목표 TPS(70)가 아니라 **이론적 최대 TPS(150, 안전마진 미적용)**로 잡는다(수치 근거는 [용량 산정 문서](waiting-queue-capacity-planning.md#스케줄러-실행-주기--배치-크기n-산정) 참고). 정상 상황(70 근처)에서는 절대 발동하지 않으면서, "계산대로 동작하지 않는" 이상 상황만 걸러내는 최종 백스톱이 목적이라 정상 트래픽의 자연스러운 변동성까지 오탐하지 않도록 여유를 둔다. (다만 정확히 2배 수준(140)의 이상은 150 문턱을 겨우 못 넘어 놓칠 수 있음 — 이런 미세한 이상은 Prometheus/Grafana 지표 관찰로 보완)

### ZPOPMIN과 토큰 발급 사이의 원자성 — Lua 스크립트

**문제**: `ZPOPMIN`(대기열에서 제거)과 `SET ... EX`(토큰 발급)를 별개의 두 왕복으로 처리하면, 그 사이 순간에 ① 다른 클라이언트의 조회가 끼어들면 "대기열에도 없고 토큰도 없는" 상태가 잠깐 관측될 수 있고, ② 두 왕복 사이 네트워크 장애가 나면 그 유저는 대기열에서 이미 빠졌는데 토큰은 영영 발급되지 않아 유실된다.

**해결**: `admit-batch.lua` 스크립트로 `ZPOPMIN`+`SET ... EX`를 하나의 원자적 실행으로 묶는다(`QueueAdmissionRepository.admitBatch`, 자세한 구조는 [도메인 모델 설계](#domainqueue-패키지-구성) 참고). Redis는 싱글 스레드라 스크립트 실행 중에는 다른 클라이언트의 어떤 명령도 끼어들 수 없어서, 외부에서 관측 가능한 상태는 "대기 중(토큰 없음)" 또는 "빠져나감+토큰 있음" 둘 중 하나뿐이다. 네트워크 관점에서도 앱→Redis 왕복이 1번으로 줄어, 스크립트가 Redis에 도달해 실행되면 반드시 끝까지 완료되고(중간에 네트워크가 개입할 지점이 없음), 애초에 도달하지 못하면 `ZPOPMIN`도 실행되지 않아 대기열이 원래 그대로 유지된다.

이 원자성 덕분에 "토큰 발급 실패 시 원래 score로 재-ZADD해 복귀시킨다"는 별도의 보정 로직이 필요 없어진다 — 실패 자체가 전부(all) 아니면 전무(nothing)이기 때문이다. (Lua 스크립트 자체의 로직 버그로 인한 부분 실행은 예외 — 이건 네트워크 장애가 아니라 테스트로 예방해야 할 별개의 리스크)

### 테스트에서 자동 tick을 끄는 방법 — 프로퍼티 vs 개별 mock

**문제**: 100ms 주기 스케줄러가 테스트 실행 중에도 그대로 살아있으면, 테스트가 대기열에 넣어둔 유저를 assertion 전에 스케줄러가 먼저 발급/제거해버려 비결정적으로 실패할 수 있다. 처음에는 이 문제를 겪는 개별 테스트 클래스마다 `@MockitoBean(name = "taskScheduler")`로 `TaskScheduler` 빈을 mock해 `@Scheduled` 등록 자체를 무력화했다.

**그런데 이 개별 mock 방식이 새로운 문제를 만들었다**: Spring 테스트 컨텍스트 캐시는 `@MockitoBean` 같은 빈 오버라이드 조합이 다르면 별도의 `ApplicationContext`를 새로 만든다. `taskScheduler`를 mock한 큐 테스트 4개만 별도 컨텍스트로 격리됐고, mock하지 않은 나머지 수십 개의 `@SpringBootTest`는 진짜 스케줄러가 살아있는 컨텍스트를 공유했다. `modules/redis`의 `RedisTestContainersConfig`는 Redis 컨테이너를 `static final`로 테스트 JVM 전체가 공유하므로, 그 "진짜 스케줄러가 있는" 컨텍스트가 캐시에 남아있는 동안 100ms마다 실제로 `admitBatch`를 실행해 공유 Redis의 대기열 상태를 건드렸다 — `./gradlew test`로 전체 스위트를 돌릴 때만 큐 테스트가 비결정적으로 실패하고 개별 실행하면 통과하는 원인이었다.

**해결**: 개별 mock 대신 `queue.scheduler-enabled` 프로퍼티(기본값 `true`)로 자동 tick 자체를 끈다. `application.yml`의 `test` 프로필 전용 블록에서만 `false`로 오버라이드한다(`local` 프로필은 실제 개발 확인을 위해 그대로 켜둠). 이를 위해 `@Scheduled` 트리거와 비즈니스 로직을 분리한다.

```java
@Scheduled(fixedDelayString = "${queue.scheduler-interval-ms}")
public void scheduledAdmit() {
    if (queueProperties.schedulerEnabled()) {
        admit();
    }
}

public void admit() {
    // 기존 배치 발급 로직 — 테스트가 직접 호출하는 대상은 이 메서드
}
```

모든 `@SpringBootTest`가 test 프로필에서 동일하게 자동 tick이 꺼지므로, 큐 테스트 4개의 `@MockitoBean(name = "taskScheduler")`는 더 이상 필요 없어 제거했다. 그 결과 이 테스트들도 다른 통합/E2E 테스트와 동일한 컨텍스트 시그니처를 갖게 되어, 불필요하게 격리돼 있던 컨텍스트도 함께 사라졌다.
