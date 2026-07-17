# 04. Redis 데이터 모델 — 대기열 (Virtual Waiting Room)

> **⚠️ 설계 변경 (2026-07-08): 정원제 → 방류형(rate-based)**
> 초기 설계(아래 원문)는 **정원제** — 매 주기 `maxActive − activeCount`(빈자리)만큼 리필해 활성 인원을 상한으로 유지하는 back-pressure 모델이었다. 이후 **방류형**으로 전환했다: 매 **M초**마다 대기열 앞에서 **N명을 고정 방류**하고, 활성 점유량으로 gate하지 않는다(throughput = N/M). 두 노브 `release-size(N)`·`scheduler-interval-seconds(M)`를 yml로 직접 튜닝한다.
> - **활성 카운트(`active:users`)는 gate가 아니라 관측·재진입 판정용**으로만 남는다(§2.5).
> - **다중 인스턴스 안전성**: 방류형은 정원제의 gap 계산 같은 self-limiting이 없어 인스턴스마다 N명씩 pop하면 방류량이 곱해진다. 이를 **Lua 안의 고정 윈도우 레이트리밋**(`waiting:release:{floor(now/M)}` 카운터, M초 윈도우당 방류 ≤ N)으로 막는다 — ShedLock 없이 lock-free(§3.1).
> 아래 본문은 원 설계를 보존하되, 방류형 반영 지점을 각 절에 인라인 표기(`[방류형]`)했다.

[`01-requirements.md`](./01-requirements.md) §5·§9와 [`02-sequence-diagrams.md`](./02-sequence-diagrams.md)의 Redis 조작을 키 설계 수준으로 확정한다. 이 주차는 RDB 테이블을 추가하지 않는다(week7 ERD 불변). 모든 상태는 Redis에 둔다.

## 0. 전제 — 기존 Redis 인프라 (검증됨)

`modules/redis/config/redis/RedisConfig.java` 기준:

- **Master-Replica 정적 구성** (`RedisStaticMasterReplicaConfiguration`).
- `defaultRedisTemplate` (`@Primary`) → `ReadFrom.REPLICA_PREFERRED` — **읽기가 복제본으로 가서 복제 지연(stale) 가능**.
- `masterRedisTemplate` (`@Qualifier("redisTemplateMaster")`) → `ReadFrom.MASTER` — 항상 마스터 읽기.
- 직렬화: 키/값 모두 `StringRedisSerializer` (`RedisTemplate<String,String>`).

> **핵심 설계 규칙 (정합성)**: 대기열의 **순서·활성 카운트·토큰 검증**은 복제 지연을 허용하면 안 된다(중복 발급·상한 초과·유효 토큰 오거부 위험). 따라서 **쓰기 + 정합성이 중요한 읽기(스케줄러의 ZCARD/ZPOPMIN, 토큰 검증 GET, 진입 시 중복 체크)는 `masterRedisTemplate`을 사용한다.** 오직 **순번 조회(rank)** 만 복제 지연을 감수하고 `defaultRedisTemplate`(replica-preferred)로 읽어도 된다 — 이미 1~2초 캐싱(D5)으로 근사값이 허용되기 때문. 아래 각 키에 사용 템플릿을 명시한다.

---

## 1. 키 목록

| 키 | 자료구조 | 사용 템플릿 | TTL | 용도 |
| --- | --- | --- | --- | --- |
| `waiting:queue` | ZSET | **master** | 없음(영속) | 대기 순서. member=userId, score=seq |
| `waiting:seq` | String(INCR) | **master** | 없음 | 진입 순서 단조 증가 시퀀스(FIFO 타이브레이커) |
| `waiting:release:{window}` | String(INCRBY) | **master** | ~3M | **[방류형]** M초 윈도우별 방류 누계. 레이트리밋(윈도우당 ≤ N) |
| `pass:{token}` | String | **master** | 30s (D1 개정) | 토큰→userId. 가드 검증·자동 만료 |
| `user-pass:{userId}` | String | **master** | 30s | userId→token 역참조(중복 발급 방지·재조회) |
| `active:users` | ZSET | **master** | 없음(원소별 score=만료시각) | 활성 인원 카운트. **[방류형]** gate 아님 — 관측·재진입(READY) 판정용 |
| `rank:cache:{userId}` | String(JSON) | default(replica 허용) | 1~2s (D5) | 순번 조회 결과 캐시 |

> 키 네임스페이스에 `waiting:` / `pass:` / `active:` 접두어를 두어 향후 이벤트·상품별 큐 분리 시 `waiting:{eventId}:queue` 형태로 확장 가능하게 한다(01 Scope: 단일 글로벌 큐로 시작).

---

## 2. 키별 상세

### 2.1 `waiting:queue` — 대기 순서 (ZSET)

- **member** = userId, **score** = `waiting:seq`에서 받은 단조 증가 정수.
- 진입: `ZADD waiting:queue NX <seq> <userId>` — NX로 이미 있는 유저의 score를 덮어쓰지 않음(멱등, NFR-2).
- 순번: `ZRANK waiting:queue <userId>` → 0-based 앞선 인원 수(aheadCount). rank(1-based) = ZRANK + 1.
- 배치 pop: `ZPOPMIN waiting:queue <k>` → score 최솟값(가장 먼저 진입) k명(FIFO, NFR-1).
- 총 대기: `ZCARD waiting:queue`.

**score를 시각(ms) 대신 시퀀스로 두는 이유**: 같은 ms에 다수 진입 시 score 충돌 → ZSET은 동점 시 member(문자열) 사전순으로 정렬해 진입 순서가 뒤바뀐다. `INCR`로 전역 유일·단조 seq를 부여하면 결정적 FIFO가 보장된다(NFR-1). `INCR`은 마스터 원자 연산.

### 2.2 `waiting:seq` — 진입 시퀀스 (String)

- `INCR waiting:seq` → 다음 seq. 원자적, 경합 안전.
- 오버플로우: Long 범위라 실질적 무한. 이벤트 종료 후 큐 비면 리셋(선택).

### 2.3 `pass:{token}` — 입장 토큰 (String + TTL)

- 값 = ownerUserId. `SET pass:{token} <userId> EX 60`.
- 토큰은 불투명 문자열(UUID v4 또는 난수 base64). 추측 불가.
- 가드 검증(§02 S2-2): `GET pass:{token}` →
  - nil → 만료/미존재 → 403.
  - ownerUserId ≠ X-USER-ID → 불일치 → 403(토큰 도용 차단).
  - 일치 + 존재 → 통과.
- 소모(주문 성공 확정, D3): `DEL pass:{token}`.
- **TTL이 곧 만료 정책** — 미사용 토큰은 Redis가 자동 회수(FR-4). 별도 만료 스케줄러 불필요.

### 2.4 `user-pass:{userId}` — 역참조 (String + TTL)

- 값 = token. `SET user-pass:{userId} <token> EX 60`.
- 용도: ① 한 유저가 이미 활성인지 O(1) 확인(스케줄러 재발급·enter 시 READY 판정 보조), ② 소모 시 token을 몰라도 userId로 찾아 `pass:{token}`까지 정리, ③ 클라이언트 토큰 유실 시 재조회.
- 소모 시 함께 `DEL`.

### 2.5 `active:users` — 활성 세트 (ZSET, D6 핵심)

- **member** = userId, **score** = 만료시각(epoch ms) = 발급시각 + TTL.
- 발급: `ZADD active:users <now+60000> <userId>`.
- 소모(주문 성공): `ZREM active:users <userId>`.
- ~~**정원제 정확 카운트 (스케줄러 매 주기)**~~ (원 설계):
  1. `ZREMRANGEBYSCORE active:users 0 <now>` — 만료 원소 일괄 제거.
  2. `ZCARD active:users` → 현재 활성 인원 `activeCount`.
  3. `batchSize = max(0, maxActive − activeCount)` (P-5, D2).
- **[방류형] 변경**: 활성 카운트는 **더 이상 방류량을 정하지 않는다**. 방류량은 §3.1 Lua의 윈도우 예산(`N − 이번_윈도우_방류누계`)이 정한다. `active:users`는 이제 (a) 관측(admin `activeCount`), (b) 진입 시 이미 활성인 유저 O(1) 판정(`isActive` → 즉시 READY, 재큐잉 방지) 두 용도로만 쓴다. 만료 청소(`ZREMRANGEBYSCORE`)는 그대로 유지해 이 두 값을 정확히 유지한다.

> **[방류형] 정합성**: 활성 상한이 없어졌으므로 "back-pressure 보수성" 논의는 무의미하다. 대신 동시 DB 부하 = 유효 토큰 보유자 수 ≈ (방류 레이트 N/M) × TTL(리틀의 법칙)로 결정된다. 그래서 **N/M과 TTL을 DB가 견디는 동시성 이하로 잡는 것**이 방류형의 안전 조건이다(06 재해석·안전캡 논의 참조).

### 2.6 `rank:cache:{userId}` — 순번 캐시 (String, D5)

- 값 = `{status, rank, aheadCount, eta, pollAfterSeconds}` JSON. `SET rank:cache:{userId} <json> EX 2`.
- 조회(FR-6): 캐시 히트면 Redis ZSET 조회 생략(폴링 폭주 흡수, NFR-7).
- **replica 읽기 허용**: rank는 근사값이어도 UX 무해 → `defaultRedisTemplate`. 단 캐시 자체 write는 아무 노드나 무방(짧은 TTL).

---

## 3. 원자성 & 경쟁 조건

### 3.1 배치 발급의 원자성 (§02 S2-1 note)

`ZPOPMIN`으로 큐에서 뺀 뒤 토큰 3키(`pass`·`user-pass`·`active`) 저장 중 장애 시, 유저가 큐에서도 빠지고 토큰도 없는 **유실**이 생길 수 있다.

**채택: Lua 스크립트로 pop+발급 원자화.** 스케줄러가 `EVAL`로 아래를 한 번에 실행한다.

**[방류형] 현행 스크립트** — 활성 상한(ZCARD gate) 대신 **고정 윈도우 레이트리밋**으로 방류량을 정한다. `pass`·`user-pass` 저장까지 Lua 안에서 처리해 3키 발급이 한 번에 원자화된다(원 설계의 "애플리케이션이 뒤이어 SET" 분리 없음).

```lua
-- KEYS[1]=waiting:queue, KEYS[2]=active:users
-- ARGV[1]=now(ms), ARGV[2]=N(release-size), ARGV[3]=intervalMs(M*1000), ARGV[4]=ttlSeconds,
--   ARGV[5]=hardMax(0=비활성), ARGV[6]=windowPrefix, ARGV[7]=passPrefix, ARGV[8]=userPassPrefix,
--   ARGV[9..]=후보 토큰(방류분만 소비). 반환=[userId, ...]
local now = tonumber(ARGV[1]); local n = tonumber(ARGV[2]); local intervalMs = tonumber(ARGV[3])
local ttl = tonumber(ARGV[4]); local hardMax = tonumber(ARGV[5])
local windowKey = ARGV[6] .. math.floor(now / intervalMs)     -- 고정 윈도우(M초 단위)
local already = tonumber(redis.call('GET', windowKey) or '0')
local budget = n - already                                    -- ① 윈도우 방류 여유분(레이트)
if budget <= 0 then return {} end
redis.call('ZREMRANGEBYSCORE', KEYS[2], 0, now)               -- 활성 만료 청소
if hardMax > 0 then                                           -- ② 안전망: 활성 상한 여유분(선택 B)
  local active = redis.call('ZCARD', KEYS[2])
  local headroom = hardMax - active
  if headroom < budget then budget = headroom end            --   ①②중 작은 쪽으로 조임
  if budget <= 0 then return {} end
end
local popped = redis.call('ZPOPMIN', KEYS[1], budget)         -- 앞에서 budget명(FIFO)
local cnt = #popped / 2
if cnt == 0 then return {} end
local expireAt = now + ttl * 1000
local issued = {}
for i = 1, cnt do
  local uid = popped[(i-1)*2 + 1]
  local token = ARGV[8 + i]
  redis.call('SET', ARGV[7] .. token, uid, 'EX', ttl)         -- pass:{token}
  redis.call('SET', ARGV[8] .. uid, token, 'EX', ttl)         -- user-pass:{userId}
  redis.call('ZADD', KEYS[2], expireAt, uid)                  -- active:users
  issued[i] = uid
end
redis.call('INCRBY', windowKey, cnt)                          -- 윈도우 방류 누계
redis.call('PEXPIRE', windowKey, intervalMs * 3)              -- 윈도우 지나면 소멸
return issued
```

**안전망(선택 B, `hard-max-active`)**: 방류형은 활성 gate가 없어 처리 정체 시 활성이 폭주할 수 있다. `hardMax>0`이면 위 ②처럼 `hardMax − 현재활성`을 방류량 상한에 추가해, 활성이 상한에 닿으면 방류를 조인다(0=비활성=순수 방류형). 이는 주 제어(레이트 N/M)가 아니라 **비상 브레이크**이므로 정상 footprint(≈ N/M×TTL)보다 넉넉히 잡는다. DB 동시부하는 활성 수가 아니라 주문레이트×처리시간으로 정해지므로, 이 상한은 footprint/폭주 방어용이다.

**원자성 보장**: `GET 윈도우 → (ZCARD) → ZPOPMIN → 발급 → INCRBY`가 Redis 싱글스레드로 통째 원자 실행된다. 여러 인스턴스가 같은 M초 윈도우에 동시 호출해도 `budget`이 공유 카운터로 줄어들어 **윈도우당 방류 총합 ≤ N**이 보장된다(초과 방류 없음). 후보 토큰은 N개 미리 만들어 넘기고 실제 방류분(`cnt`)만 소비한다.

> ~~원 설계 주석: ShedLock으로 스케줄러 단일 실행(NFR-5)이라 다중 인스턴스 경쟁 없음~~ → **[방류형] 폐기**. 정원제는 gap 계산이 self-limiting이라 락이 옵션이었지만(2026-07-08 Lua 원자화로 ShedLock 제거), 방류형은 인스턴스마다 독립적으로 N명 pop → 방류량이 인스턴스 수만큼 곱해진다. **윈도우 레이트리밋이 이 곱셈을 막는 핵심 장치**이며, 덕분에 ShedLock을 되살리지 않고 lock-free를 유지한다(윈도우 경계에서 최대 2N까지 순간 초과 가능 — admission control 허용 오차).

### 3.2 멱등 진입 경쟁 (FR-1)

동일 유저가 짧은 간격 두 번 enter → 둘 다 `ZADD NX`. NX 덕분에 두 번째는 no-op, score 유지(순번 안 밀림). 서로 다른 유저 동시 진입은 각자 `INCR`로 유일 seq를 받아 충돌 없음.

### 3.3 토큰 검증-소모 경쟁

같은 토큰으로 주문 API 중복 호출(더블 클릭) → 가드는 `GET`으로 통과시키지만, 주문 로직은 기존 멱등/재고 제어(week6~7)가 판정. 성공 확정은 한 번만 일어나고 그때 `DEL`. 이미 DEL된 뒤 재요청은 `GET` nil → 403(정상 차단).

---

## 4. 장애 시 동작 (D7, fail-closed)

| 상황 | 동작 |
| --- | --- |
| enter 중 Redis 불가 | 503, 진입 실패(재시도 안내) |
| rank 조회 중 Redis 불가 | 503 또는 마지막 캐시(있으면). 신규 계산 불가 |
| 가드 검증 중 Redis 불가 | **주문 진입 차단(503)** — fail-open 금지. 대기열이 백엔드 보호막이므로 |
| 스케줄러 발급 중 Redis 불가 | 이번 주기 스킵, 로그·메트릭. 다음 주기 재시도 |

---

## 5. 관측 (D8)

`GET /api/v1/admin/waiting-queue/status`가 노출하고 Prometheus로도 내보낼 지표:

- `waiting_queue_size` = `ZCARD waiting:queue` (대기 인원)
- `waiting_active_count` = `ZCARD active:users`(청소 후) (활성 인원) — **[방류형] gate 아님, 관측용**
- `waiting_release_size`(N), `waiting_release_interval_sec`(M), `waiting_throughput_per_sec`(=N/M) (정책 파생값)
- `waiting_tokens_issued_total`, `waiting_tokens_expired_total`, `waiting_tokens_consumed_total` (카운터)
- `waiting_eta_seconds`(대기열 꼬리 기준 예상 대기) — 튜닝 관측용

> week7의 Grafana/Prometheus 스크레이프 배선을 재사용한다(신규 인프라 없음).
