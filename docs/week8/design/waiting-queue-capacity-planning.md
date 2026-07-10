# 대기열 시스템 — 용량 산정

> 대기열 설계 논의를 단일 책임 원칙 관점에서 3개 문서로 나눈 것 중 **"용량 산정"** 파트입니다. 코드 구조가 아니라 **수치(TPS, 배치 크기, 안전 마진)** 중심이라, 실측 데이터가 들어올 때마다 이 문서만 갱신하면 되도록 분리했습니다.
>
> - 아키텍처·API·코드 구조: [waiting-queue-architecture.md](waiting-queue-architecture.md)
> - 장애 대응·운영: [waiting-queue-runbook.md](waiting-queue-runbook.md)

## 개요

대기열 스케줄러의 배치 크기(N)와 `estimatedWaitSeconds` 응답 필드는 둘 다 "초당 처리량(TPS)"이라는 같은 숫자에서 파생된다. 이 문서는 그 숫자를 어떻게 계산했는지, 그리고 두 사용처가 어떻게 하나의 설정값을 공유하는지를 다룬다.

**모든 수치는 가정치다** — 실측 부하테스트 전까지의 임시값이며, 실측 데이터가 나오는 대로 이 문서만 갱신한다.

---

## 스케줄러 실행 주기 & 배치 크기(N) 산정

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
| 실제 적용 TPS | 70 = 7 ÷ 0.1 | [estimatedWaitSeconds 계산](#estimatedwaitseconds-계산)의 분모로 재사용 — 스케줄러가 실제로 통과시키는 속도와 유저에게 보여주는 예상 대기시간이 어긋나지 않도록 동일 파라미터를 공유 |

**p95가 아니라 p99를 쓰는 이유**: 일반적인 응답시간 SLA에서 p95를 쓰는 건 그 기준을 벗어나는 요청이 "그 요청 하나만" 느려지고 끝나기 때문이다. 하지만 여기서 기준을 넘는다는 건 커넥션 풀이 실제로 고갈된다는 뜻이라, 그 순간엔 느린 요청 하나가 아니라 **그 시점의 모든 동시 요청**이 영향을 받는다. 실패의 파급 범위가 요청 1건이 아니라 시스템 전체로 번지므로, 5%(p95)보다 1%(p99) 기준으로 더 보수적으로 잡는다. 이 시스템을 만든 원래 동기(DB 커넥션 풀 고갈 → 전체 장애 방지)와도 일치한다.

이 배치 크기 산정과 Thundering Herd 완화 전략(①발급 간격 분산, ③Rate Limit 문턱값=150)의 아키텍처적 맥락은 [설계 문서: 스케줄러 아키텍처](waiting-queue-architecture.md#스케줄러-아키텍처)를 참고.

---

## estimatedWaitSeconds 계산

### 계산식

```
estimatedWaitSeconds = ceil(position / throughputPerSecond)
```

`position`은 `ZRANK`(0-based) 값을 그대로 쓴다 — 이 값 자체가 "내 앞에 대기 중인 인원 수"와 같으므로 별도 변환이 필요 없다.

**내림이 아니라 올림(ceiling)을 쓰는 이유**: 내림하면 실제 대기시간이 표시값보다 길어질 수 있어(예: 9.98초를 9초로 표시) 유저가 "표시된 시간이 지났는데 왜 아직 안 되지"라고 느끼는 부정적 UX가 생긴다. 올림하면 항상 표시값보다 실제로 빨리 끝나는 방향으로만 어긋나 체감상 안전하다.

### throughputPerSecond 출처 — 스케줄러와 공유

새로 계산하지 않고 [위 스케줄러 배치 크기 산정](#스케줄러-실행-주기--배치-크기n-산정)에서 계산한 실제 적용 TPS(70)를 그대로 재사용한다. 스케줄러의 배치 크기(N)와 이 ETA 계산이 각자 하드코딩된 숫자를 따로 들고 있으면, 나중에 실측치로 교체할 때 한쪽만 갱신하고 다른 쪽을 누락하는 사고가 날 수 있다. 그래서 하나의 설정으로 묶는다.

```yaml
queue:
  scheduler-interval-ms: 100
  throughput-per-second: 70
```

```java
@ConfigurationProperties(prefix = "queue")
public record QueueProperties(
        long schedulerIntervalMs,
        long throughputPerSecond,
        @DefaultValue("true") boolean schedulerEnabled
) {
    public int batchSize() {
        return (int) (throughputPerSecond * schedulerIntervalMs / 1000);
    }
}
```

- 스케줄러: `@Scheduled(fixedDelayString = "${queue.scheduler-interval-ms}")` + `queueProperties.batchSize()`로 N을 매번 파생 계산(하드코딩하지 않음)
- `QueueFacade.getPosition()`: `queueProperties.throughputPerSecond()`로 나눠 ETA 계산(코드는 [설계 문서: application/queue](waiting-queue-architecture.md#applicationqueue--queuefacade가-필요한-이유) 참고)
- `schedulerEnabled`(기본값 `true`)는 배치 크기·ETA 계산과는 무관한 설정으로, `test` 프로필에서만 `false`로 오버라이드해 테스트 중 자동 tick을 끈다(근거는 [설계 문서: 테스트에서 자동 tick을 끄는 방법](waiting-queue-architecture.md#테스트에서-자동-tick을-끄는-방법--프로퍼티-vs-개별-mock) 참고) — 이 문서가 다루는 "TPS/배치 크기 산정"과는 다른 관심사이므로 값 자체의 산정 근거는 없다

---

## 다음 논의 항목 (TODO)

- [ ] 실측 데이터(정상 상태 에러율·실제 Redis 호출 TPS·failover 소요 시간) 확보 후 [Circuit Breaker 파라미터](waiting-queue-runbook.md#장애-감지-및-복구) 재튜닝 — 이 문서의 TPS 실측치와 함께 갱신
