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
- **Redis 자체 장애로 검증을 못 하는 경우는 위와 다르게 처리한다** — 자세한 내용은 [Redis 장애 시 — Graceful Degradation](#redis-장애-시--graceful-degradation) 참고. `EntryTokenRepository.find()`/`EntryTokenService.verify()` 내부에서 Redis 연결 예외를 절대 잡아 삼키지 않는다(예: `catch (Exception e) { return false; }`처럼 처리하면 "Redis 장애"가 "토큰 없음"(403)으로 둔갑해버려 유저에게 잘못된 신호를 주고 장애 인지도 어려워진다). 예외는 그대로 전파시켜 `ApiControllerAdvice`의 전용 핸들러가 `ErrorType.SERVICE_UNAVAILABLE`(503, 신규 추가)로 변환한다.
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
  WaitingQueueRepository (interface)  — queue:waiting-queue ZSET
    Long enter(Long userId, long timestampMillis)  // ZADD
    Long rank(Long userId)                          // ZRANK
    Long size()                                     // ZCARD
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

**`WaitingQueueRepository`에 `pollMin`, `EntryTokenRepository`에 `issue`를 두지 않은 이유**: 스케줄러가 "대기열에서 N명을 꺼내는 것"과 "그 N명에게 토큰을 발급하는 것"을 하나의 Lua 스크립트로 원자 처리해야 하는데(자세한 이유는 [스케줄러 설계](#스케줄러-설계) 참고), 이 연산은 두 자료구조를 동시에 건드리는 별개의 관심사라 어느 한쪽 Repository에 억지로 끼워 넣지 않고 `QueueAdmissionRepository`로 분리했다. 그 결과 토큰 발급 경로는 스케줄러의 배치 발급 하나뿐이라, `EntryTokenRepository`는 조회(`find`)와 소비(`delete`)만 담당한다.

### application/queue — QueueFacade가 필요한 이유

`POST /queue/enter`, `GET /queue/position` 모두 `X-Loopers-LoginId`/`X-Loopers-LoginPw` → `UserService.getLoginUser()`로 userId를 확인하는 과정이 선행되어야 한다(`OrderFacade.createOrder`와 동일 패턴). 즉 두 엔드포인트 모두 `user` 도메인과 `queue` 도메인(`WaitingQueueService`, `EntryTokenService`)을 조합하는 유스케이스이므로, Facade 없이 Controller가 domain Service를 직접 호출하는 방식(예: `CouponAdminV1Controller`가 단순 CRUD에 `CouponTemplateService`를 직접 쓰는 경우)은 적용할 수 없다.

```
application/queue/
  QueueFacade (@Component)
    private final UserService userService;
    private final WaitingQueueService waitingQueueService;
    private final EntryTokenService entryTokenService;
    private final QueueProperties queueProperties;

    QueueInfo enter(String loginId, String loginPw) {
        UserModel user = userService.getLoginUser(loginId, loginPw);
        Long rank = waitingQueueService.enter(user.getId());
        return QueueInfo.forEnter(rank);
    }

    QueueInfo getPosition(String loginId, String loginPw) {
        UserModel user = userService.getLoginUser(loginId, loginPw);
        Long rank = waitingQueueService.getRank(user.getId());
        Long size = waitingQueueService.size();
        String token = entryTokenService.find(user.getId()).orElse(null);
        // estimatedWaitSeconds 계산식은 estimatedWaitSeconds 계산 섹션 참고
        Long estimatedWaitSeconds = (long) Math.ceil((double) rank / queueProperties.throughputPerSecond());
        return QueueInfo.forPosition(rank, size, estimatedWaitSeconds, token);
    }

  QueueInfo (record) — position, totalWaiting(nullable), estimatedWaitSeconds(nullable), token(nullable)
```

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
- `infrastructure/queue/QueueAdmissionRepositoryImpl` — Master 템플릿 전용. `resources/scripts/admit-batch.lua`를 `DefaultRedisScript`로 로드해 `ZPOPMIN`+`SET ... EX`를 한 번의 왕복으로 원자 실행 (자세한 설계 배경은 [스케줄러 설계](#스케줄러-설계) 참고)

---

## 스케줄러 설계

### 1. 실행 주기 & 배치 크기(N) 산정

**가정치 기반 계산** (실측 전까지 임시값, 실제 부하테스트로 교체 예정):

| 항목 | 값 | 근거 |
|---|---|---|
| DB 커넥션 풀 기준 | 30 | `maximum-pool-size=40`이 아니라 `minimum-idle=30`을 기준으로 삼음 — 트래픽 폭증 초반에도 즉시 사용 가능한("warm") 커넥션만 안전하게 가정. 나머지 10개는 부하 시점에 새로 열어야 해서(TCP+인증 핸드셰이크 지연) 폭증 대응 용도로는 불확실 |
| 주문 1건 평균 처리시간 | 200ms (가정) | 실측 전 임시값 |
| 이론적 최대 TPS | 150 = 30 ÷ 0.2 | Little's Law(L = λW) — 평균값을 전제로 하는 공식이라 반드시 평균 처리시간을 써야 함 |
| 주문 1건 p99 처리시간 | 400ms (가정, 평균의 2배) | 실측 전 임시값 |
| 안전 마진율 | 50% = 평균/p99 = 200/400 | p99로 느려지는 순간에도 동시 점유 커넥션이 pool(30)을 넘지 않도록 역산 |
| 최종 목표 TPS | 75 = 150 × 50% | |
| 스케줄러 주기 | 100ms | 참고 자료(`round8-please-wait-queue.md`) 원문 값 채택 |
| 배치 크기 N | 7 = 75 × 0.1 = 7.5 → 내림 | 목표 TPS를 넘지 않도록 올림 대신 내림 선택 |
| 실제 적용 TPS | 70 = 7 ÷ 0.1 | [estimatedWaitSeconds 계산](#estimatedwaitseconds-계산식-확정-및-처리량tps-파라미터-출처)의 분모로 재사용 — 스케줄러가 실제로 통과시키는 속도와 유저에게 보여주는 예상 대기시간이 어긋나지 않도록 동일 파라미터를 공유 |

**p95가 아니라 p99를 쓰는 이유**: 일반적인 응답시간 SLA에서 p95를 쓰는 건 그 기준을 벗어나는 요청이 "그 요청 하나만" 느려지고 끝나기 때문이다. 하지만 여기서 기준을 넘는다는 건 커넥션 풀이 실제로 고갈된다는 뜻이라, 그 순간엔 느린 요청 하나가 아니라 **그 시점의 모든 동시 요청**이 영향을 받는다. 실패의 파급 범위가 요청 1건이 아니라 시스템 전체로 번지므로, 5%(p95)보다 1%(p99) 기준으로 더 보수적으로 잡는다. 이 시스템을 만든 원래 동기(DB 커넥션 풀 고갈 → 전체 장애 방지)와도 일치한다.

### 2. Thundering Herd 완화 전략 — 3가지 후보 중 선택

| 전략 | 채택 여부 | 이유 |
|---|---|---|
| ① 발급 간격 분산 (100ms마다 소량씩) | **채택** | 위 표의 배치 크기 산정이 곧 이 전략. 1초에 75명을 한 번에 발급하는 대신 100ms마다 7명씩 나눠 발급해 순간 부하를 평탄화 |
| ② 토큰에 Jitter 부여(활성화 시점 랜덤 딜레이) | **미채택** | ①과 "동시에 몰리는 요청 수를 줄인다"는 같은 문제를 푸는 대안 관계이지 서로 보완하는 layer가 아님. 이미 배치 크기(N=7)가 pool(30)에 비해 충분히 작아서, 7명이 지연 없이 동시에 호출해도 위협이 안 됨 — ①만으로 이미 해결된 문제를 ②로 다시 풀 필요가 없음. 게다가 도입 시 `position=0`과 `token` 존재가 항상 같이 간다는 응답 구조의 전제가 깨져 `QueueInfo`에 "곧 통과" 상태를 별도로 표현해야 하는 비용이 추가로 든다 |
| ③ 주문 API 자체 Rate Limit | **채택** | ①·②와는 다른 범주의 문제(대기열/스케줄러 로직이 계산대로 동작하지 않는 상황)에 대한 최종 안전장치. 대기열이 아무리 정확해도 스케줄러 로직 버그, 향후 다중 인스턴스 스케일아웃 시 인스턴스별 독립 실행(이 프로젝트는 ShedLock 등 분산 락이 없어 `@Scheduled`가 인스턴스 단독 실행을 전제로 함), 대기열 대상이 아닌 다른 엔드포인트(상품 조회 등)發 pool 고갈은 대기열 쪽에서 절대 못 막는다 |

**Rate Limit 문턱값**: 목표 TPS(70)가 아니라 **이론적 최대 TPS(150, 안전마진 미적용)**로 잡는다. 정상 상황(70 근처)에서는 절대 발동하지 않으면서, "계산대로 동작하지 않는" 이상 상황만 걸러내는 최종 백스톱이 목적이라 정상 트래픽의 자연스러운 변동성까지 오탐하지 않도록 여유를 둔다. (다만 정확히 2배 수준(140)의 이상은 150 문턱을 겨우 못 넘어 놓칠 수 있음 — 이런 미세한 이상은 Prometheus/Grafana 지표 관찰로 보완)

### 3. ZPOPMIN과 토큰 발급 사이의 원자성 — Lua 스크립트

**문제**: `ZPOPMIN`(대기열에서 제거)과 `SET ... EX`(토큰 발급)를 별개의 두 왕복으로 처리하면, 그 사이 순간에 ① 다른 클라이언트의 조회가 끼어들면 "대기열에도 없고 토큰도 없는" 상태가 잠깐 관측될 수 있고, ② 두 왕복 사이 네트워크 장애가 나면 그 유저는 대기열에서 이미 빠졌는데 토큰은 영영 발급되지 않아 유실된다.

**해결**: `admit-batch.lua` 스크립트로 `ZPOPMIN`+`SET ... EX`를 하나의 원자적 실행으로 묶는다(`QueueAdmissionRepository.admitBatch`, 자세한 구조는 [도메인 모델 설계](#domainqueue-패키지-구성) 참고). Redis는 싱글 스레드라 스크립트 실행 중에는 다른 클라이언트의 어떤 명령도 끼어들 수 없어서, 외부에서 관측 가능한 상태는 "대기 중(토큰 없음)" 또는 "빠져나감+토큰 있음" 둘 중 하나뿐이다. 네트워크 관점에서도 앱→Redis 왕복이 1번으로 줄어, 스크립트가 Redis에 도달해 실행되면 반드시 끝까지 완료되고(중간에 네트워크가 개입할 지점이 없음), 애초에 도달하지 못하면 `ZPOPMIN`도 실행되지 않아 대기열이 원래 그대로 유지된다.

이 원자성 덕분에 "토큰 발급 실패 시 원래 score로 재-ZADD해 복귀시킨다"는 별도의 보정 로직이 필요 없어진다 — 실패 자체가 전부(all) 아니면 전무(nothing)이기 때문이다. (Lua 스크립트 자체의 로직 버그로 인한 부분 실행은 예외 — 이건 네트워크 장애가 아니라 테스트로 예방해야 할 별개의 리스크)

### 4. 장애 감지 — 스레드풀 경합 예방 + 헬스체크 지표

**사전 예방이 먼저다**: 이 프로젝트에는 `spring.task.scheduling.pool.size` 설정이 없어, Spring Boot 기본값(1)이 그대로 적용된다. 이미 `@Scheduled`로 등록된 `OutboxRelay`(1초 주기)와 `PaymentReconciliationScheduler`(5초 주기)가 이 **단일 스레드**를 공유하는 중인데, 여기에 100ms 주기의 대기열 스케줄러까지 더하면 3개 작업이 스레드 1개를 나눠 쓰게 된다. `fixedDelay`는 이전 실행이 끝난 뒤부터 대기하는 방식이라, 스레드가 1개면 셋은 사실상 순차 실행된다 — 다른 스케줄러가 오래 걸리는 순간 대기열 스케줄러의 100ms 주기가 그대로 밀린다. **그래서 "스케줄러 장애"의 가장 흔한 원인은 코드 버그가 아니라 이 스레드풀 경합일 가능성이 높고, 감지 이전에 `spring.task.scheduling.pool.size`를 최소 3~4로 늘려 예방하는 게 먼저다.**

**헬스체크 지표**: 스케줄러가 마지막으로 성공 실행된 시각을 Micrometer Gauge로 노출한다(`queue.scheduler.last.execution.timestamp`). 매 tick 종료 시 `AtomicLong`을 갱신하고 `MeterRegistry.gauge(...)`로 등록해 Prometheus가 스크레이핑하도록 한다.

**알림 임계치**: 스케줄러 주기(100ms)를 그대로 임계치로 쓰지 않는다 — Prometheus 기본 스크레이핑 주기(보통 10~15초)보다 촘촘한 임계치는 오탐만 만든다. **"마지막 성공 실행으로부터 5~10초 경과"**를 알림 임계치로 잡는다.

**보조 지표**: `ZCARD`(Queue Depth)가 계속 증가하고 줄지 않는 것도 신호가 될 수 있지만, 이것만으로는 "스케줄러가 죽었다"와 "그냥 유입이 폭증했다"를 구분하지 못한다. 그래서 위 실행 시각 지표를 뒷받침하는 보조 지표로만 쓰고, 알림의 주된 판단 기준은 실행 시각 gap으로 둔다.

---

## 대기열 자체의 리스크

참고 자료가 제시한 4가지 리스크 중, 이 프로젝트에서 실제로 서버(백엔드)가 처리해야 하는 항목과 이미 앞선 설계 결정으로 해결된 항목을 구분한다.

| 리스크 | 서버 처리 필요 | 대응 |
|---|---|---|
| 토큰 미사용(자리만 차지) | 예 | TTL(`EX 300`, 5분)로 자동 만료 처리. "만료된 토큰 수만큼 다음 유저에게 추가 발급"하는 로직은 **불필요** — [Policy A](#1-실행-주기--배치-크기n-산정)를 택해 활성 토큰 수와 무관하게 매 tick 고정 개수(N=7)를 발급하므로, 애초에 "활성 개수를 추적해 보충 발급"하는 구조 자체가 없다 |
| 어뷰징(중복 진입) | 예, 이미 해결됨 | `ZADD`의 member=userId 유일성으로 자료구조 차원에서 중복 방지([왜 Redis인가 #2](#2-동시-진입에-대한-순서-보장--중복-방지를-애플리케이션-락-없이)). "비로그인 상태의 디바이스 핑거프린팅" 대응은 이 프로젝트에서 불필요 — [설계 결정 1번](#1-유저-식별-방식)에서 이미 `X-Loopers-LoginId`/`X-Loopers-LoginPw` 기반 로그인을 전제로 했으므로 비로그인 진입 시나리오 자체가 없음 |
| 스케줄러 장애 | 예 | 헬스체크·모니터링(Actuator+Prometheus+Grafana)과 알림이 필요. 구체적인 지표·임계치는 TODO로 남김 |
| 과도한 Polling 부하 | 아니오 | Redis `ZRANK`는 O(log N)이라 참고 자료 예시(초당 5,000건) 수준은 충분히 감당 가능. 순번 구간별 폴링 주기 동적 조정은 클라이언트가 이미 받은 `position` 값으로 스스로 판단하는 프론트엔드 로직이라 서버 응답/로직 변경이 필요 없다. 이 프로젝트에 프론트엔드 앱이 없어(`commerce-api`/`commerce-batch`/`commerce-streamer` 멀티모듈뿐) 스코프 밖이기도 하다 |

---

## Redis 장애 시 — Graceful Degradation

대기열의 핵심 인프라인 Redis 자체가 죽었을 때 어떻게 할지 결정한다.

### 결정: 전면 차단(신규 진입·기존 주문 완료 모두 차단) + 빠른 복구

**후보 3가지의 기각 근거:**

| 대안 | 기각 근거 |
|---|---|
| 대기열 우회(bypass) — 대기열 없이 `/orders` 직접 허용 | 대기열이 없으면 트래픽이 [주문 API 자체 Rate Limit](#2-thundering-herd-완화-전략--3가지-후보-중-선택)(문턱값 150 TPS)에 그대로 부딪혀 초과분이 거부된다. "대기 중" 화면과 "주문 거부"는 체감이 완전히 다르다 — 전자는 유저가 참고 기다리지만 후자는 "구매 실패"로 느껴져 CS 문의가 폭증한다 |
| Fallback 큐 — 로컬 메모리 | OOM 위험도 있지만 더 근본적으로, `commerce-api`가 여러 인스턴스로 뜨면 인스턴스마다 별도의 로컬 큐를 가지게 되어 **"전역적으로 하나의 공정한 순서"라는 대기열의 존재 목적 자체가 깨진다**. 이건 애초에 Redis(공유 상태)를 쓴 이유와 정면으로 배치된다 |
| Fallback 큐 — Kafka | Kafka는 파티션 내 순서 자체는 잘 보장하지만, "내가 지금 몇 번째인지"를 즉시 answer하는 순위 조회(`ZRANK` 상당) 기능이 없다. Fallback으로 전환하면 `GET /queue/position`의 실시간 순번 기능이 죽는다 — [왜 Redis인가 #1](#1-몇-번째인지--상대적-순위-쿼리-그것도-초당-수천-번)에서 Kafka 대신 Redis를 고른 이유와 동일한 논리 |
| Fallback 큐 — DB | 순환 논리다. 애초에 이 대기열을 만든 이유가 DB 부하를 막기 위해서인데, 장애 시 그 DB를 큐 저장소로 다시 쓰는 건 자기모순이다([왜 Redis인가 #4](#4-보호-대상과-대기열-상태-저장소의-분리)와 동일한 논리) |

### 이미 토큰을 발급받은 유저의 처리

**"전면 차단"은 신규 진입(`/queue/enter`)뿐 아니라, 이미 토큰을 발급받고 `/orders` 완료만 남은 유저도 예외 없이 막는다.**

이건 별도로 구현해야 하는 결정 사항이 아니라 **`EntryTokenRepository`/`EntryTokenService`가 Redis 연결 예외를 삼키지만 않으면 자동으로 일어나는 기본 동작**이다. `OrderFacade`의 토큰 검증(`entryTokenService.verify()`)도 결국 Redis 조회이므로, Redis가 죽으면 이 호출이 예외(`RedisConnectionFailureException` 등, Spring Data Redis의 `DataAccessException` 계열)를 던지고 그대로 전파되어야 한다. 이 예외를 여기서 잡아 "토큰 없음"(`FORBIDDEN`)으로 둔갑시키면 정상 상황(토큰이 진짜 없음)과 Redis 장애가 똑같이 403으로 뭉개져 버린다(자세한 내용은 [엔드포인트 계약 3번](#3-post-apiv1orders--기존-api-수정) 참고).

더 근본적으로, 토큰이 **불투명한 문자열로 Redis에만 존재하고 "키가 있는지 없는지"로만 검증**하는 구조라서, Redis가 죽으면 검증할 데이터 소스 자체가 없다 — fail-open(이미 토큰 가진 유저만 봐주기) 로직을 짜고 싶어도 참조할 게 없어 불가능하다. 이를 가능하게 하려면 서명된(self-contained) 토큰(JWT/HMAC로 `userId`+만료시각을 담아 Redis 조회 없이 로컬에서 서명·만료만 검증)으로 토큰 설계 자체를 바꿔야 하는데, 이건 이번 스코프를 넘어서는 큰 변경이라 **"설계상 받아들인 한계"로 명시하고 넘어간다.** 필요성이 커지면 별도로 재검토한다.

### 에러 응답 — 신규 `ErrorType.SERVICE_UNAVAILABLE`(503)

Redis 장애로 인한 실패는 정상적인 비즈니스 검증 실패(`FORBIDDEN` 등)와 의미가 다르므로 별도 카테고리가 필요하다. 기존 5종(`INTERNAL_ERROR`/`BAD_REQUEST`/`NOT_FOUND`/`CONFLICT`/`FORBIDDEN`) 중 "일시적으로 서비스를 이용할 수 없으니 잠시 후 다시 시도하라"에 해당하는 게 없어, 이번엔 새로 추가한다.

```java
SERVICE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, HttpStatus.SERVICE_UNAVAILABLE.getReasonPhrase(), "일시적으로 서비스를 이용할 수 없습니다. 잠시 후 다시 시도해주세요.");
```

`ApiControllerAdvice`에 Redis 연결 예외(`DataAccessException` 계열, Circuit Breaker 도입 후에는 `CallNotPermittedException`도 포함)를 잡아 이 `ErrorType`으로 변환하는 전용 핸들러를 추가한다. Spring은 등록된 핸들러 중 가장 구체적인 예외 타입을 우선 매칭하므로, 기존 캐치올(`handle(Throwable e)` → `INTERNAL_ERROR`)보다 이 핸들러가 항상 먼저 적용된다. `/queue/enter`, `/queue/position`, `/orders` 모두 Redis에 의존하므로 이 핸들러 하나로 세 엔드포인트의 Redis 장애가 공통으로 커버된다.

### 장애 감지 및 복구

Spring Boot Actuator의 Redis `HealthIndicator`를 기반으로, Resilience4j Circuit Breaker로 Redis 호출부를 감싸는 방향을 검토한다 — Redis 응답 없음을 빠르게 감지해 즉시 차단 응답을 주고, 복구가 감지되면 자동으로 정상 흐름을 재개한다.

**Circuit Breaker 파라미터(`failureRateThreshold`, `slidingWindowSize`, `waitDurationInOpenState` 등)를 정하는 근거**: 이런 값들은 표준값을 그대로 가져다 쓰는 게 아니라 보통 세 가지 실측 데이터에서 역산한다.

1. **실패율 임계치(`failureRateThreshold`)·최소 호출 수(`minimumNumberOfCalls`)** — 평소 정상 상태의 베이스라인 에러율에서 나온다. "정상적인 노이즈"와 "진짜 장애"를 가르는 선이라, 실제 운영 중 관찰된 에러율 분포가 있어야 정확히 잡을 수 있다.
2. **슬라이딩 윈도우 크기(`slidingWindowSize`)** — 실제 호출 빈도(TPS)에서 나온다. 호출량 대비 윈도우가 너무 크면 반응이 느리고(진짜 장애도 한참 지나야 감지), 너무 작으면 우연한 2~3번 실패만으로 오탐이 난다.
3. **Open 상태 유지 시간(`waitDurationInOpenState`)** — 의존 대상(Redis)이 실제로 복구되는 데 걸리는 시간(예: Sentinel/Cluster failover 소요 시간)에서 나온다. 임의로 정하면 "아직 복구 안 됐는데 너무 자주 찔러보거나" "이미 복구됐는데 한참 뒤에야 재개"하는 문제가 생긴다.

**오탐 vs 미탐 트레이드오프**: 셋 다 공통으로, 오탐(너무 예민해서 멀쩡한데 차단)과 미탐(너무 둔감해서 진짜 장애를 못 잡음) 중 뭘 더 감수할지 정해야 하는데, [p95 대신 p99를 쓴 이유](#1-실행-주기--배치-크기n-산정)와 같은 논리로 **미탐 쪽 비용(장애를 못 잡아 DB까지 부하가 번짐)이 오탐 쪽 비용(멀쩡한데 살짝 일찍 차단)보다 훨씬 크므로, 더 예민한(sensitive) 쪽으로 기울인다.**

**결정**: 위 세 가지 근거를 뒷받침할 실측 데이터(정상 상태 에러율, 실제 Redis 호출 TPS, 실제 failover 소요 시간)가 이 프로젝트엔 아직 없다. 그래서 **실측 전까지는 Resilience4j 기본값을 채택**하고, 운영 중 관찰되는 실제 에러율·호출량으로 추후 튜닝한다.

---

## estimatedWaitSeconds 계산

### 계산식

```
estimatedWaitSeconds = ceil(position / throughputPerSecond)
```

`position`은 `ZRANK`(0-based) 값을 그대로 쓴다 — 이 값 자체가 "내 앞에 대기 중인 인원 수"와 같으므로 별도 변환이 필요 없다.

**내림이 아니라 올림(ceiling)을 쓰는 이유**: 내림하면 실제 대기시간이 표시값보다 길어질 수 있어(예: 9.98초를 9초로 표시) 유저가 "표시된 시간이 지났는데 왜 아직 안 되지"라고 느끼는 부정적 UX가 생긴다. 올림하면 항상 표시값보다 실제로 빨리 끝나는 방향으로만 어긋나 체감상 안전하다.

### throughputPerSecond 출처 — 스케줄러와 공유

새로 계산하지 않고 [스케줄러 설계](#1-실행-주기--배치-크기n-산정)에서 계산한 실제 적용 TPS(70)를 그대로 재사용한다. 스케줄러의 배치 크기(N)와 이 ETA 계산이 각자 하드코딩된 숫자를 따로 들고 있으면, 나중에 실측치로 교체할 때 한쪽만 갱신하고 다른 쪽을 누락하는 사고가 날 수 있다. 그래서 하나의 설정으로 묶는다.

```yaml
queue:
  scheduler-interval-ms: 100
  throughput-per-second: 70
```

```java
@ConfigurationProperties(prefix = "queue")
public record QueueProperties(long schedulerIntervalMs, long throughputPerSecond) {
    public int batchSize() {
        return (int) (throughputPerSecond * schedulerIntervalMs / 1000);
    }
}
```

- 스케줄러: `@Scheduled(fixedDelayString = "${queue.scheduler-interval-ms}")` + `queueProperties.batchSize()`로 N을 매번 파생 계산(하드코딩하지 않음)
- `QueueFacade.getPosition()`: `queueProperties.throughputPerSecond()`로 나눠 ETA 계산(코드는 [application/queue](#applicationqueue--queuefacade가-필요한-이유) 참고)

---

## 다음 논의 항목 (TODO)

- [ ] 실측 데이터(정상 상태 에러율·실제 Redis 호출 TPS·failover 소요 시간) 확보 후 Circuit Breaker 파라미터 재튜닝
