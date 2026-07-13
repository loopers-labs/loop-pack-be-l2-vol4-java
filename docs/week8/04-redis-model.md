# Redis 데이터 모델 (ERD 대체)

이번 주는 새 RDB 테이블을 만들지 않는다. 대기열의 상태는 전부 Redis에 둔다. 그래서 이 문서는 ERD 대신 Redis 키 모델을 다룬다. 왜 관계형 테이블이 아니라 Redis인지부터 짚는다.

## 왜 대기열을 DB에 두지 않는가

대기열은 DB를 지키려고 만든 장치다. 그런데 그 대기열을 DB 테이블로 두면, 초당 수천 건의 순번 조회(`COUNT`)와 입장 처리(`SELECT FOR UPDATE` + `UPDATE`)가 정작 지키려던 그 DB를 두들긴다. 자기모순이다. Redis는 인메모리라 순번 조회가 마이크로초 단위이고, DB에서 부하를 덜어낸다. 순서 보장·원자적 연산·TTL을 자료구조로 바로 제공하는 것도 이유다.

| 요구 | Redis | DB 테이블 |
| --- | --- | --- |
| 순번 조회 | `ZRANK`, μs, 메모리 | `COUNT(*)` 쿼리, 초당 수천 건 |
| 앞에서 N명 꺼내기 | `ZPOPMIN`, 원자적 | `SELECT FOR UPDATE SKIP LOCKED` + `UPDATE`, 행 잠금 |
| 토큰 만료 | `EX`로 자동 | 별도 만료 컬럼 + 청소 잡 |
| 지키려던 대상 | — | 바로 이 DB |

## 키 목록

| 키 | 타입 | TTL | 용도 |
| --- | --- | --- | --- |
| `order-queue:waiting` | Sorted Set | 없음 | 대기열. score = 진입 시각(ms), member = userId |
| `order-queue:entry-token:{userId}` | String | 300s | 입장 토큰. 발급 `SET`, 검증 `GET`, 주문 후 `DEL` |
| `order-queue:admission-lock` | String | 주기(200ms) | 스케줄러 단일 실행 락. `SET NX PX` |
| `order-queue:active` | Sorted Set | — | (보류) 입장 인원 지표가 필요할 때만. score = 만료 시각 |

모든 접근은 정확한 키로 하는 점 연산이다. `SCAN`이나 `KEYS`로 키를 훑지 않는다 — 키가 많아도 문제되지 않는 이유가 이것이다.

## 연산별 상세

### 대기열 — `order-queue:waiting`

```
ZADD order-queue:waiting NX {진입시각} {userId}   # 진입 (이미 있으면 순번 유지)
ZRANK order-queue:waiting {userId}                # 내 순번 (0-based)
ZCARD order-queue:waiting                         # 전체 대기 인원
ZPOPMIN order-queue:waiting {batchSize}           # 앞에서 N명 꺼내기 (스케줄러)
```

- score를 진입 시각으로 두면 먼저 온 사람이 앞에 온다(공정성). member가 userId라 중복이 자동 제거된다.
- `NX`는 "없을 때만 추가"라, 재진입이 순번을 뒤로 밀지 않게 한다.

### 입장 토큰 — `order-queue:entry-token:{userId}`

```
SET order-queue:entry-token:{userId} {token} EX 300   # 발급 (스케줄러)
GET order-queue:entry-token:{userId}                  # 검증 (주문 API)
DEL order-queue:entry-token:{userId}                  # 주문 완료 후
```

- 유저별 개별 키라, 검증이 O(1)이고 만료가 Redis 자동이다. "주문 후 삭제(단발성)"를 `DEL`로 지킨다.
- 토큰 키의 개수는 대기 인원이 아니라 **발급률 × TTL로 상한**이 걸린다. 대기열이 10만이어도 토큰 키는 그만큼 늘지 않고, 발급된 유저 수(수천~수만)에 그친다.

### 스케줄러 락 — `order-queue:admission-lock`

```
SET order-queue:admission-lock {instanceId} NX PX 200   # 매 주기, 성공한 1대만 발급
```

- TTL을 주기와 같게 둔다. 락을 쥔 인스턴스가 죽어도 다음 주기에 다른 인스턴스가 자연히 이어받는다(이중화).
- `ZPOPMIN`이 원자적이라 락이 없어도 토큰 중복은 생기지 않는다. 락은 발급 속도가 인스턴스 수만큼 커지는 것을 막는 용도다.
