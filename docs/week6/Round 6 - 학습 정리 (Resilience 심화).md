---
date: 2026-06-24
type: note
title: Round 6 - 학습 정리 (Resilience 심화)
description: Round 6를 함께 파고들며 정리한 외부 연동 회복력 설계 심화 노트. 장애 전파부터 Timeout/Retry/CB/Fallback, 멱등성·콜백/폴링·상태 머신·동시성까지 PENDING을 중심으로 연결한 학습 기록.
tags: [loopers, resilience, circuit-breaker, idempotency, state-machine, retry, fallback, concurrency]
timestamp: 2026-06-24
---

# Round 6 — 학습 정리 (Resilience 심화)

> [!abstract] 한 줄 요약
> **외부 시스템은 반드시 느려지고·실패하고·중복되고·응답을 잃는다.**
> 그러니 "성공 경로"를 짜는 게 아니라, **모든 어긋남에서 결국 정합성으로 수렴하는 상태 기계**를 짜는 것 — 그게 Round 6다.
> 모든 장치가 결국 **`PENDING`(= "우리가 모른다"를 정직하게 표현하는 상태)** 을 중심으로 연결된다.

---

## 0. 출발점 — 장애 전파(Cascading Failure)

> "PG가 느린 건데 왜 *내* 서버가 죽는가?"

```
우리 서버 (톰캣 스레드풀 200개)
   ├─ 사용자 A 결제 ──→ PG ... (5초째 무응답) ⏳ 스레드 점유
   ├─ 사용자 B 결제 ──→ PG ... ⏳ 점유
   └─ ... 200명째 ──→ ⏳ 전부 점유
→ 201번째: 결제와 무관한 메인 페이지조차 못 엶 😱
```

- PG를 기다리는 스레드가 **하나도 안 풀려나가** → 결제와 무관한 요청까지 받을 자원이 고갈.
- 외부의 **지연**이 우리 서버의 **자원 고갈**로 번지는 것 = 장애 전파.
- Round 6의 모든 전략은 결국 **"이 전파를 어디서 끊을 것인가"** 에 대한 답.

> [!note] "스레드를 2000개로 늘리면?" 이 근본 해결이 아닌 이유
> 1. **메모리**: 스레드당 ~1MB 스택 → 2000개면 ~2GB, OOM에 근접
> 2. **컨텍스트 스위칭**: CPU는 한정 → 스레드만 많으면 전환 비용 폭증, 처리량 오히려 하락
> 3. **근본 원인은 그대로**: PG가 계속 느리면 2000개도 똑같이 점유당함. 죽는 시점만 미룰 뿐.
>
> 비유: 막힌 싱크대에 큰 대야를 받친 격. 물(요청)이 계속 들고 배수구(PG)가 막혀 있으면 대야 크기와 무관하게 넘친다. → **"안 빠지는 물을 언제 포기할 거냐"** 가 본질 → **Timeout**.

---

## 1. Timeout — "언제 포기하고 자원을 회수할 것인가"

- 본질은 *성공시키는 것*이 아니라 **자원(스레드/커넥션) 회수**.
- **실패보다 지연이 더 무섭다** — 명확한 실패는 스레드가 바로 풀리지만, "영영 안 오는 응답"은 Timeout 없으면 영원히 스레드를 안 돌려준다.

### 타임아웃은 한 종류가 아니다 (여러 레이어)

**A. HTTP 클라이언트 레벨**

| 종류 | 발동 시점 | 비유 |
| --- | --- | --- |
| Connection Timeout | TCP 연결 수립 자체 실패 (PG 다운) | 전화 신호가 안 감 |
| Read(Socket) Timeout | 연결됐는데 응답이 안 옴 (PG 과부하) | 전화는 됐는데 상대가 말이 없음 |
| Write Timeout | 요청 본문 전송이 안 끝남 (큰 payload) | 내 말을 전송하다 끊김 |
| **Connection Request Timeout** ⭐ | 커넥션 **풀**에서 빈 커넥션 얻기까지 대기 | 공용 전화기가 다 쓰여서 순서 대기 |

> ⭐ Connection Request Timeout을 안 걸면, Connect/Read를 잘 걸어도 **풀 단계에서 스레드 고갈이 재현**된다.

**B. 인프라/드라이버 레벨** — DB·Redis도 "외부 시스템"이다

| 대상 | 설정 | 의미 |
| --- | --- | --- |
| JPA/HikariCP | `connection-timeout` | 풀에서 커넥션 얻는 최대 대기 (대기 없이 바로 실패하도록 필수) |
| | `validation-timeout` | 커넥션 유효성 검사 제한 |
| Redis(Lettuce) | `commandTimeout` | 명령 실행 제한 (없으면 무기한 대기) |

**C. 트랜잭션/비즈니스 레벨**

| 종류 | 의미 |
| --- | --- |
| Transaction Timeout | `@Transactional(timeout = N)` — N초 넘으면 롤백 |
| TimeLimiter (Resilience4j) | 비동기 호출 한 건의 전체 시간 상한 |

> 한 군데라도 비면 거기서 스레드/커넥션이 샌다. **요청이 거치는 모든 풀·외부 경계마다** 타임아웃이 있어야 한다.

---

## 2. 멱등성 (Idempotency)

> **멱등** = 여러 번 해도 한 번 한 것과 결과가 같다.
> - 엘리베이터 버튼(5층 한 번/열 번 → 5층 한 번 도착) ✅
> - 자판기 +1 버튼(누른 만큼 증가) ❌

- ❌ 멱등하지 않은 결제: "5,000원 결제" 두 번 → 10,000원 빠짐
- ✅ 멱등한 결제: "5,000원 결제" 두 번 → 5,000원만 빠짐

> [!important] 핵심 전제
> **네트워크에서 "같은 요청이 두 번 도착"하는 건 버그가 아니라 정상 상황이다.**
> (타임아웃 후 재시도, 따닥 클릭, LB 재전송, 콜백 중복…)
> → 선택지는 "중복을 막을까"가 아니라 **"중복이 와도 안전하게 만들어둘까"** → 후자가 멱등성.

### 두 가지 멱등성 모델

| 모델 | 키 발급 주체 | 예 |
| --- | --- | --- |
| A. 클라이언트가 키 생성 | 우리(`Idempotency-Key` 헤더로 보냄) | Stripe, Toss |
| B. 서버가 거래 ID 발급 | PG가 응답으로 줌 (`TR:xxxxx`) | **이 과제 PG-Simulator** |

이 과제의 조회 API `GET /payments/20250816:TR:9577c5` 의 `TR:9577c5`가 **PG가 발급하는 거래 식별자** → 모델 B에 가깝다.

> [!warning] 모델 B의 치명적 빈틈
> 타임아웃 상황에선 **거래 ID를 담은 응답 자체를 못 받는다.** PG는 ID를 만들었는데 우리는 못 받음 → 무슨 ID로 조회하지? (닭-달걀 문제)
>
> → 둘 중 하나가 더 필요:
> 1. **`orderId`로 되짚기** — 우리가 *이미 가진* 식별자로 조회 (`GET /payments?orderId=...`)
> 2. **콜백** — PG가 `callbackUrl`로 결과를 먼저 통보

> 통찰: 추적은 "응답으로 받은 거래 ID" 하나에만 의존하면 안 된다.
> **`orderId`(우리가 잃어버리지 않는 닻) + 거래 ID(PG가 주는 정밀한 핸들)** 둘 다 필요.

---

## 3. 콜백 vs 폴링 — 비동기 결과를 받아내는 두 방향

PG가 **비동기**(처리 지연 1~5초)라 "요청"과 "결과 확인"이 분리된다.

```
■ 콜백 (Push): "끝나면 네가 알려줘"  — 진동벨 📳
   우리 ←─"성공!"── PG  (callbackUrl로 PG가 찾아옴)

■ 폴링 (Pull): "내가 주기적으로 물어볼게"  — 주방에 직접 확인
   우리 ─"됐어?"→ PG : "아직" / "아직" / "성공!"
```

| | 콜백(Push) | 폴링(Pull) |
| --- | --- | --- |
| 응답 속도 | 빠름 ✅ | 느림(주기까지 모름) |
| 불필요한 호출 | 없음 ✅ | 많음 ❌ |
| 구현 복잡도 | 높음(공개 엔드포인트 필요) | 낮음 ✅ |
| 신뢰성 | **안 오면 영영 모름** ❌ | 우리가 통제 ✅ |

> [!danger] 콜백의 함정 — "안 오는 콜백"
> 재배포 중 유실 / 네트워크 유실 / 우리 엔드포인트 500 / PG 발송 버그 …
> → 콜백이 안 오면 **주문이 영원히 PENDING에 갇힘.**
> → 체크리스트: *"콜백이 오지 않더라도 일정 주기/수동 API로 상태를 복구할 수 있다."*

### 실무 정답: 콜백 + 폴링 병행 (역할 분담)

```
콜백 = 1차 방어선(빠른 정상 경로) → 대부분 즉시 처리
폴링 = 안전망(reconciliation)    → 콜백 놓친 PENDING을 주기적으로 쓸어담아 정합성 복구
```

**폴링(safety net) 설계의 핵심 디테일:**
- **grace period**: "방금 PENDING 된 것"을 즉시 조회하면 안 됨(콜백에 일할 기회를 줘야). 스펙의 **처리 지연 1~5초에서 역산** → "정상이면 끝났어야 할 시간"(예: 10초 이상 지난 PENDING)부터 폴링.
- PG가 **"처리 중"** → 건드리지 말고 다음 주기에 재확인. 단 **상한선**(예: 10분)을 넘으면 → 격리 + 알림(영원한 PENDING 방지).
- PG가 **"그런 주문 없음"** → 요청 미도달(스펙상 요청 성공 60% = 40%는 미도달) = 돈 안 빠짐 = **재시도 안전**.

> [!tip] "조회"의 진짜 목적
> 무작정 재시도(이중결제 위험) vs 무작정 포기(돈 빠졌는데 주문 누락) 사이에서, **조회가 "재시도해도 되는지"의 판단 근거**를 준다.
>
> | PG 응답 | 의미 | 행동 |
> | --- | --- | --- |
> | 처리 중 | 도달함, 결과 미정 | 건드리지 마 (재시도 ❌) |
> | 주문 없음 | 미도달, 돈 안 빠짐 | 재시도 안전 ✅ |
> | 성공/실패 | 결과 확정 | 그 결과로 확정 |

---

## 4. 상태 머신 — 호출 흐름이 아니라 상태 전이로

> 비동기 + 불확실성이 끼면 "순서도"로는 "요청은 보냈는데 결과를 모르는 상태"를 그릴 수 없다.
> → **"지금 어떤 상태고, 어떤 사건이 오면 어디로 가는가"** 로 본다.

### 상태는 두 종류 — 어긋남이 모든 사고의 근원

```
■ 내부 도메인 상태 (우리 DB)    ← 통제 가능
■ 외부 시스템 상태 (PG가 아는)  ← 통제 불가, 추측만
```

목표: **내부 상태를 어떻게 굴려야 외부와 어긋나도 안전하고, 결국 일치(reconcile)시킬 수 있는가.**

### 핵심 상태

| 상태 | 의미 | 외부(PG)는? |
| --- | --- | --- |
| `PENDING` | 요청은 보냈으나 **결과 모름** | 불확실 |
| `PAID` | 성공 확정 | 일치 ✅ |
| `FAILED` | 실패 확정(한도/카드/미도달) | 일치 ✅ |
| `UNKNOWN`/`STUCK` | 너무 오래 PENDING — **사람이 봐야 함** | 격리 🚨 |

### 전이도

```
            [결제 요청 시작]
                  │
    ┌──────────► PENDING ◄────── (조회: "처리 중") 그대로, 재확인
    │ (처리 중)    │
    ├──────────────┤
    ▼              ▼              ▼                  ▼
콜백/조회=성공  콜백/조회=실패   조회="주문 없음"    너무 오래(grace 초과)
    ▼              ▼          (미도달=돈 안 빠짐)        ▼
  PAID ✅       FAILED ❌            │            UNKNOWN/STUCK 🚨
                                     └→ 재시도 안전 → 다시 PENDING
```

### 상태 머신이 강제하는 3가지 안전 규칙

1. **종료 상태 불변(terminal)**: `PAID`/`FAILED`는 확정 → 중복 콜백/조회가 와도 안 바뀜 = **상태 레벨의 멱등성**.
2. **허용된 전이만**: `PENDING→PAID` O, `PAID→PENDING` X.
3. **"모름"을 상태로 인정**: PENDING/UNKNOWN으로 *억지 단정*을 막는다 = 상태 불일치 방지의 근본.

---

## 5. 동시성 제어 — "락이 필요하다"를 정밀하게

> 콜백 스레드와 폴링 스레드가 **동시에** 같은 주문을 PENDING→PAID로 바꾸려 하면?

```
t1 콜백: 조회 → PENDING
t2 폴링:           조회 → PENDING    ← 둘 다 아직 PENDING으로 봄
t3 콜백: PAID로 UPDATE + 후처리(포인트/재고)
t4 폴링: PAID로 UPDATE + 후처리 또 실행 💥  (포인트 2배 적립 등)
```

이게 **race condition.** 진짜 문제는 PAID 중복이 아니라 **PAID에 딸린 후처리의 중복 실행.**

> [!warning] "PAID면 안 바꾼다" 규칙만으론 부족
> t1·t2에 **둘 다 PENDING**으로 읽었으므로 둘 다 "전이 허용"을 통과한다.
> "읽기→판단→쓰기" 사이의 틈 = **check-then-act 갭**. 불변식은 맞지만 *원자적으로* 검사·적용 못 한 것이 문제.

### 락의 종류

| 방식 | 동작 | 적합 |
| --- | --- | --- |
| ① 비관적 락 | `SELECT ... FOR UPDATE`로 행 잠금, 다른 스레드 대기 | 충돌 잦을 때 |
| ② 낙관적 락 | `version` 컬럼, `WHERE version=?`로 충돌 감지(0건이면 패배) | 충돌 드물 때(결제) |
| ③ **조건부 UPDATE** | `UPDATE ... SET status='PAID' WHERE status='PENDING'` | **가장 실용적** |

> [!tip] ③ 조건부 UPDATE가 깔끔한 이유
> ```sql
> UPDATE payment SET status='PAID' WHERE order_id=? AND status='PENDING'
> ```
> 검사(`WHERE status='PENDING'`)와 적용을 **단일 UPDATE로 원자화** → check-then-act 갭 제거.
> **affected rows로 승패 판별**: 1이면 내가 전이시킨 것 → 후처리 실행 / 0이면 남이 이미 함 → **후처리 스킵.**
> → 콜백·폴링이 동시에 와도 후처리는 **정확히 한 번.**

### 트랜잭션 격리 수준은 이 문제를 풀어줄까? — "완화는 해도 해결은 못 한다"

격리 수준(isolation level) = **동시에 도는 트랜잭션들이 서로의 작업을 얼마나 볼 수 있는가**의 다이얼. 약할수록 빠르고, 강할수록 안전.

```
READ UNCOMMITTED  ← 약함 (남의 커밋 안 된 값도 봄)
READ COMMITTED    ← 커밋된 값만 봄        (PostgreSQL·Oracle 기본)
REPEATABLE READ   ← 한 Tx 안에선 같은 행 늘 같은 값 (MySQL InnoDB 기본)
SERIALIZABLE      ← 강함 (사실상 순서대로 실행한 것처럼)
```

우리 시나리오를 트랜잭션으로 감싸면:
```
Tx A(콜백): BEGIN → SELECT status(PENDING) → UPDATE PAID + 후처리 → COMMIT
Tx B(폴링): BEGIN → SELECT status(PENDING) → UPDATE PAID + 후처리 → COMMIT
```

> [!warning] READ COMMITTED / REPEATABLE READ로는 못 막는다
> ```
> A: BEGIN, SELECT → PENDING
> B: BEGIN, SELECT → PENDING   (A가 아직 COMMIT 안 했으니 B에겐 여전히 PENDING)
> A: UPDATE→PAID, COMMIT
> B: UPDATE→PAID, COMMIT        (B는 자기가 읽은 PENDING을 근거로 그대로 진행) 💥
> ```
> 원인: 둘 다 **일반 SELECT(non-locking read)** 로 확인 → 락을 안 걸어서 **check-then-act 갭이 트랜잭션 레벨에서도 그대로 재현.**
> 흔한 오해: "REPEATABLE READ면 안전" → ❌. RR은 "한 Tx 안에서 같은 행을 다시 읽어도 안 바뀐다"만 보장. 오히려 **자기가 읽은 옛 스냅샷(PENDING)을 끝까지 믿게 만들어** 덮어쓸 위험.

- **SERIALIZABLE**: 막아준다. 단 하나를 블로킹하거나 충돌 감지로 **롤백**(PostgreSQL `40001` serialization failure)시킴 → **애플리케이션이 직접 재시도**해야 하고 동시성이 크게 떨어짐 → 결제 트래픽 전체에 쓰기엔 과함.

> [!important] 결론 — 격리 수준에 의존하지 마라
> race condition을 "격리 수준을 높여" 푸는 건 비싸고 불확실하다. 대신 **명시적 락 / 조건부 UPDATE로 *그 행에 대해서만* 원자성을 보장**하는 게 정석. (= 위 락 3종이 바로 "격리 수준에 안 기대고 직접 원자성을 만드는" 방법)

**왜 락 3종은 격리 수준과 무관하게 동작하나:**

| 방법 | 격리 수준과의 관계 |
| --- | --- |
| 비관적 락 `SELECT ... FOR UPDATE` | **읽기부터 행을 잠금** → READ COMMITTED에서도 안전. 격리 수준 안 올려도 됨 |
| 조건부 UPDATE `WHERE status='PENDING'` | UPDATE는 항상 **현재 커밋된 최신 행**에 락 걸고 적용 → 격리 수준 무관 |
| 낙관적 락 `WHERE version=?` | 격리 수준 대신 version으로 직접 충돌 검출 |

```
■ SELECT FOR UPDATE 가 갭을 읽기 단계에서 끊는 원리
A: SELECT ... FOR UPDATE → 행 쓰기 락 획득 (PENDING)
B: SELECT ... FOR UPDATE → ⏸️ A가 잠갔으니 대기 (블로킹)
A: UPDATE PAID, COMMIT → 락 해제
B: 대기 풀림 → 최신값 PAID 로 읽힘 → "이미 PAID네" → 후처리 스킵 ✅
   (일반 SELECT면 B가 옛 PENDING을 봤을 것 — locking read라 최신값을 보게 됨)
```

> [!note] UPDATE는 격리 수준과 무관하게 "최신값"에 쓴다
> SELECT는 과거 스냅샷을 읽을 수 있지만, **UPDATE/DELETE는 항상 "현재 커밋된 최신 행"을 잠그고 그 위에서 동작**(DB가 write-write 충돌을 막아야 하므로).
> → `UPDATE ... WHERE status='PENDING'`을 둘이 동시에 날려도: 먼저 도달이 잠그고 PAID(1건) → 나중은 대기 후 **최신값(PAID)** 기준 재평가 → 안 걸림(0건). 그래서 affected rows로 승자 판별 가능, 격리 수준을 안 만져도 됨.

> 한 줄: **격리 수준은 "동시성을 얼마나 허용할까"의 거시 다이얼, race condition 방어는 "이 행만큼은 한 번에 한 명만"의 미시 제어.** 후자를 전자(SERIALIZABLE)로 풀면 전체 동시성을 희생 → **명시적 락/조건부 UPDATE로 국소적으로** 푸는 게 맞다.

> [!note] 락은 "단일 DB" 전제
> ①②③은 같은 DB가 중재 → DB가 하나면 서버 여러 대여도 안전.
> 단 "스케줄러가 같은 주문을 중복으로 집어드는 것" 자체를 줄이려면 분산 락(Redis 등)이 별도 필요(과제 범위 밖).

---

## 6. Retry — "다시 해본다"의 함정들

### 함정 ① 무엇을 재시도할까 (가장 중요)

| 종류 | 예 | 재시도? |
| --- | --- | --- |
| 일시적(transient) | 네트워크 순단, 503, Connect Timeout, **요청 미도달(40%)** | ✅ |
| 영구적(permanent) | 잘못된 카드, 한도 초과, 400 | ❌ (백번 해도 동일) |

→ `retry-exceptions`로 **재시도할 예외를 명시.** (잘못된 카드를 재시도하면 PG만 3번 더 두드림)

### 함정 ② 언제 재시도할까 — Backoff & Jitter

```
Fixed       : 1s → 1s → 1s
Exponential : 1s → 2s → 4s   ← 실패 누적될수록 더 물러나 줌 (기본 정석)
+ Jitter    : 1s → 1.8s → 4.3s  ← 무작위를 섞어 타이밍 분산
```

> [!danger] Thundering Herd (천둥 같은 무리)
> 서버 10대가 같은 사건(PG 다운)으로 **동시에** 트리거되면, exponential backoff로 *간격*은 벌어져도 *동시성*은 그대로 → PG가 살아나려는 순간마다 10배 스파이크를 맞고 **다시 죽는** 악순환.
> (다리 위 군인들의 발 맞춘 행진이 공명으로 다리를 무너뜨리는 것과 같음)
>
> → **Jitter**가 박자를 흐트러뜨려 부하를 완만하게 분산.
> **Backoff는 "빈도"를, Jitter는 "타이밍의 동시성"을 푼다.** 분산 환경에선 backoff만으론 부족, **반드시 jitter 병행.**

### 함정 ③ 재시도와 멱등성
- 재시도하려면 **멱등성이 전제** (없으면 이중결제).
- **타임아웃 후 재시도는 특히 위험** — "응답 못 받음" ≠ "처리 안 됨".
- 결제에서 Retry는 **조회로 "주문 없음"(미도달) 확인 후**가 가장 안전.

---

## 7. Circuit Breaker — "이제 그만 두드려"

> Retry = "이번엔 될 거야"(낙관). PG가 완전히 죽으면 독이 됨(어차피 안 될 걸 계속 두드려 자원 점유).
> CB = "계속 실패하네? 한동안 시도조차 말자"(현실 인정). 누전 차단기처럼 회로를 끊음.

### 3상태 + 전이 조건

```
        실패율 임계치 초과
   ┌──────────────────────────────┐
   │                              ▼
[CLOSED] ◄─시험 통과─ [HALF-OPEN] ◄─일정 시간 경과─ [OPEN]
 정상.통과.            일부만 시험 통과              모든 요청 즉시 차단
 실패율 집계           성공→CLOSED / 실패→OPEN        (PG 안 부르고 바로 fallback)
```

- **CLOSED**: 정상 통과 + 실패율 집계.
- **OPEN**: 회로 끊김. 요청 와도 PG 안 부르고 **즉시 fallback** → 스레드가 PG에서 안 막힘 = **자원 고갈 원천 차단**(핵심 효과).
- **HALF-OPEN**: 일정 시간 후 몇 건만 "시험 통과" → 회복 자동 감지. (폴링의 grace period와 같은 발상)

> [!important] "느린 응답도 실패다"
> 에러는 안 나는데 5초씩 걸리는 PG → "성공"으로 세면 CB가 안 열려 자원이 천천히 말라 죽음.
> → `slowCallDurationThreshold` + `slowCallRateThreshold`로 **지연도 Open 트리거로 승격.**

### Resilience4j CB 설정 속성 (실무)

```yaml
resilience4j:
  circuitbreaker:
    instances:
      paymentService:
        slidingWindowType: TIME          # ① TIME vs COUNT
        slidingWindowSize: 30s           # ② 창 크기
        minimumNumberOfCalls: 20         # ③ 판단 최소 표본
        failureRateThreshold: 60         # ④ 실패율 임계치
        slowCallDurationThreshold: 2s    # ⑤ '느린 호출' 기준
        slowCallRateThreshold: 50        # ⑤ 느린 호출 비율 임계
        waitDurationInOpenState: 10s     # ⑥ Open 유지 시간
        permittedNumberOfCallsInHalfOpenState: 5  # ⑦ 시험 호출 수
        automaticTransitionFromOpenToHalfOpenEnabled: true
        recordExceptions:                # ⑧ 실패로 셀 예외
          - java.net.SocketTimeoutException
          - org.springframework.web.client.HttpServerErrorException  # 5xx
        ignoreExceptions:                # ⑧ 실패로 안 셀 예외
          - org.springframework.web.client.HttpClientErrorException  # 4xx
```

| 속성 | 역할 | 트레이드오프 / 함정 |
| --- | --- | --- |
| ① `slidingWindowType` | 실패율 집계 단위 | COUNT(트래픽 일정·내부 MSA) vs **TIME(외부 결제 API — 트래픽 변동에 강함)** |
| ② `slidingWindowSize` | 창 크기 | 크면 안정적·감지 느림 / 작으면 빠름·과민(깜빡임) |
| ③ `minimumNumberOfCalls` | 판단 최소 표본 | 없으면 첫 1건 실패=100%로 즉시 Open(무의미) |
| ④ `failureRateThreshold` | Open 트리거 % | 높으면 장애 방치 / 낮으면 flapping. **여러 API를 한 CB에 섞으면 평균에 묻혀 안 열림** |
| ⑤ `slowCall*` | 지연도 실패로 | "안 죽었지만 느린" PG를 잡는 핵심 |
| ⑥ `waitDurationInOpenState` | Open 유지 | 보통 5초. 회복 시간은 이 값보다 ⑦로 조절 권장 |
| ⑦ `permittedNumberOfCallsInHalfOpenState` | 시험 호출 수 | 크면 회복 안 된 PG에 부담 / 작으면 운 좋은 몇 건에 속음. 권장 TPS의 10~20% |
| ⑧ `record/ignoreExceptions` | 무엇을 실패로 셀까 | **4xx(잘못된 카드)는 ignore** — PG 장애가 아니라 정당한 거부 |

> [!warning] ④의 함정 — 평균에 묻힌다
> A API(1000건, 실패 2%=20건) + B API(200건, 실패 90%=180건)를 한 CB에 섞으면
> 전체 실패율 = 200/1200 ≈ 16% → threshold 20%면 **B가 죽어도 CB가 안 열림.**
> → **보호 대상별로 CB를 분리**하거나 threshold를 낮춰라.

> [!note] CB는 인스턴스마다 독립
> TPS 1000을 10대가 처리 → 각 CB는 약 100 TPS만 봄(공유 안 함).
> 설정값은 "한 대 기준"으로 잡고 전체 효과는 ×대수. (Half-Open 허용 5 × 10대 = 실제 50건이 PG를 찔러봄 — thundering herd와 연결)

> [!note] 특별 상태 2개 + Open 예외
> 3상태(CLOSED/OPEN/HALF_OPEN) 외에: `DISABLED`(항상 통과, 점검용) / `FORCED_OPEN`(항상 차단, 운영자 강제).
> OPEN일 때 호출하면 → `CallNotPermittedException` → **이게 Fallback이 받는 예외.**

---

## 8. Fallback — "막혔을 때 무엇을 돌려줄 것인가"

> CB가 OPEN이라 PG를 못 부른 상황. 사용자에게 뭐라고 응답할까?

> [!danger] "결제 실패"라고 하면 안 된다
> 실제 상황은 "PG가 바빠서 시도조차 안 한 것" — 결제 실패 여부를 **우리는 모른다.**
> "실패"로 단정하면 → 잠시 후 CB 닫히고 그 주문이 처리될 수도 → "실패라더니 결제됐어요?" 상태 불일치 + 혼란.

### 정답: PENDING + "결제 처리 중" 안내

```
CB OPEN → PG 못 부름
  → 주문을 PENDING으로 기록 (콜백/폴링이 나중에 정합성 맞춤)
  → 사용자: "결제를 처리 중입니다. 결과는 곧 안내드릴게요" 📩
  → 콜백/폴링으로 확정되면 → PAID/FAILED 알림
```

> Fallback은 *끝*이 아니라 **안전한 시작점(PENDING)으로 떨궈주는 장치.** 모를 때는 모른다고(=처리 중) 답하는 게 가장 정직·안전. (상태 머신의 "모름을 인정"과 동일 철학)

### Fallback의 일반 원칙 — 단계적 후퇴(graceful degradation)

| 유형 | 예 | 결제 적용 |
| --- | --- | --- |
| 대체 상태 안내 | "처리 중입니다" | ✅ PENDING |
| 캐시된 값 | 환율 죽으면 마지막 환율 | 부적합(돈은 옛값 못 씀) |
| 기본값/축소 | 추천 죽으면 인기상품 | — |
| 빠른 실패(명확 거부) | "잠시 후 재시도" | 영구 실패(잘못된 카드)엔 이게 맞음 |

```java
public PaymentResponse fallback(PaymentRequest req, Throwable t) {
    // t가 CallNotPermittedException(CB OPEN) → PENDING + "처리 중"
    // t가 잘못된 카드 예외       → 명확한 실패 거부
    return new PaymentResponse("결제 처리 중입니다", PaymentStatus.PENDING);
}
```

> Fallback은 "무조건 부드럽게"가 아니라 **"상황(t)에 맞게"** — CB OPEN이면 PENDING, 영구 실패면 명확한 거부.

---

## 전체 그림 — 세 무기가 한 흐름으로

```
결제 요청
   ├─[CB CLOSED?]─ 정상 → PG 호출 ─┬─ 성공 → PAID
   │                               ├─ 일시적 실패 → [Retry] backoff+jitter
   │                               └─ 계속 실패 → CB 실패율 누적 → 임계치 → CB OPEN
   └─[CB OPEN?]── PG 안 부름 → [Fallback] → PENDING + "처리 중"
                                              └→ (콜백/폴링이 정합성 복구)
```

> **Retry는 *끈기*(일시적이면 다시), CB는 *판단*(계속 실패면 그만), Fallback은 *품위 있는 후퇴*(못 줄 땐 모른다고 정직하게).**
> 셋 다 결국 **"자원을 지키면서, 모르는 걸 단정하지 않고, PENDING으로 안전하게 떨군다"** 로 수렴한다.

---

## 관련 노트
- [[Round 6 - Failure-Ready Systems (Learning)]] — 과제 원문 기반 개념 정리
- [[Round 6 - Quests]] — 구현·라이팅 과제
- [[Round 6 - 서킷브레이커]] — 2기 루퍼스(아카이브) CB 설정 속성 원자료 (Martin Fowler / AWS / Resilience4j)
- [[20-Projects/23-Loopers/26-06-Vol4/index|26-06-Vol4 인덱스]]
