# Loopers Template (Spring + Java)
Loopers 에서 제공하는 스프링 자바 템플릿 프로젝트입니다.

## Getting Started
현재 프로젝트 안정성 및 유지보수성 등을 위해 아래와 같은 장치를 운용하고 있습니다. 이에 아래 명령어를 통해 프로젝트의 기반을 설치해주세요.
### Environment
`local` 프로필로 동작할 수 있도록, 필요 인프라를 `docker-compose` 로 제공합니다.
```shell
docker-compose -f ./docker/infra-compose.yml up
```
### Monitoring
`local` 환경에서 모니터링을 할 수 있도록, `docker-compose` 를 통해 `prometheus` 와 `grafana` 를 제공합니다.

애플리케이션 실행 이후, **http://localhost:3000** 로 접속해, admin/admin 계정으로 로그인하여 확인하실 수 있습니다.
```shell
docker-compose -f ./docker/monitoring-compose.yml up
```

## About Multi-Module Project
본 프로젝트는 멀티 모듈 프로젝트로 구성되어 있습니다. 각 모듈의 위계 및 역할을 분명히 하고, 아래와 같은 규칙을 적용합니다.

- apps : 각 모듈은 실행가능한 **SpringBootApplication** 을 의미합니다.
- modules : 특정 구현이나 도메인에 의존적이지 않고, reusable 한 configuration 을 원칙으로 합니다.
- supports : logging, monitoring 과 같이 부가적인 기능을 지원하는 add-on 모듈입니다.

```
Root
├── apps ( spring-applications )
│   ├── 📦 commerce-api
│   ├── 📦 commerce-batch
│   └── 📦 commerce-streamer
├── modules ( reusable-configurations )
│   ├── 📦 jpa
│   ├── 📦 redis
│   └── 📦 kafka
└── supports ( add-ons )
    ├── 📦 jackson
    ├── 📦 monitoring
    └── 📦 logging
```

---

## 프로젝트 소개

**2026. 05 ~ 2026. 07 | 루퍼스 백엔드 코스 4기 (개인 프로젝트)**

Spring Boot 기반 이커머스 백엔드. 재고/쿠폰 동시성 제어, 인덱스 튜닝, Resilience4j 기반 결제 연동, Redis 토큰버킷 대기열, Kafka 이벤트 기반 실시간 랭킹, Spring Batch 주간/월간 랭킹까지 단계적으로 확장한 멀티모듈 프로젝트

### 배경

루퍼스 백엔드 부트캠프 10주 과정에서 진행한 프로젝트로, 상품/브랜드/좋아요/주문/쿠폰 기본 도메인 설계에서 시작해 동시성 제어, 인덱스 튜닝, 외부 연동 장애 대응, 대규모 트래픽 대응, 이벤트 기반 랭킹, 배치 집계까지 단계적으로 구현. 기능 구현에 그치지 않고 문제를 재현·검증하고 실측 실험으로 설계 결정을 근거화하는 과정에 중점을 둔 프로젝트

### 구현

**1. 외부 결제 연동 장애 대응**
- Resilience4j 서킷 브레이커를 결제 요청·상태 조회로 분리 설정 — 결제 요청은 재시도 없음(readTimeout 후 PG가 처리 중일 수 있어 중복 결제 위험), 조회는 멱등 재시도(max 3회, fixed 500ms) 적용해 장애 특성에 맞는 복원력 확보
- CB @Order를 Retry 바깥으로 배치해 CB Open 시 불필요한 재시도를 하지 않도록 구조화
- 콜백 미수신 대응: 재결제 시도 시 PG 선조회 + 5분 주기 스케줄러 이중 보완으로 PENDING 주문 자동 복구

**2. Redis 토큰버킷 기반 주문 대기열**
- 스케줄러 1초마다 batchSize개씩 대기열에서 꺼내 입장 토큰 발급(admission 페이스 제어) + Redisson RateLimiter tryAcquire()로 TPS 상한 제어하는 2단 방어 구조
- 토큰은 주문 완료 시에만 삭제 → 429 거부나 주문 실패 시 토큰 유지, 자연스러운 재시도 가능

**3. Kafka 기반 이벤트 실시간 랭킹**
- 좋아요·주문 도메인 이벤트를 Kafka로 발행(commerce-api)하고 commerce-streamer가 소비해 Redis/MySQL에 반영하는 이벤트 기반 랭킹 파이프라인 구축
- 주문 알림 이벤트는 Outbox 패턴으로 발행 유실까지 방지, 좋아요 이벤트는 AFTER_COMMIT 발행으로 롤백 트랜잭션의 팬텀 이벤트를 차단하는 등 도메인 중요도에 따라 발행 신뢰성 수준을 차등 적용
- eventId를 PK로 하는 event_handled 테이블에 처리 완료 시 insert — Kafka at-least-once로 중복 소비 시 PK 충돌로 차단해 product_metrics 이중 반영 방지. Redis 랭킹은 best-effort(실패 시 예외 무시, offset 커밋)

**4. Spring Batch 주간/월간 랭킹 집계**
- weekly MV에서 월간 합산 시 TOP 100 경계 누락 리스크를 파악하고, product_metrics 직접 독립 집계로 정확도 확보
- batch_id 버전 컬럼 + mv_active_version 포인터 테이블 swap으로 배치 실행 중 랭킹 공백 없는 Zero-downtime 교체 구현
- 오늘자 랭킹은 Redis ZSET 실시간, 주간/월간은 배치 재계산 후 TTL 1시간 캐싱으로 이원화해 실시간성과 조회 성능을 동시에 확보

### Trouble Shooting

**1. 동시성 제어 및 검증**
- 멀티 아이템 주문 재고 예약을 SELECT FOR UPDATE 없이 조건부 UPDATE(SET reserved += qty WHERE total - reserved >= qty)로 구현해 데드락 발생 구조 자체를 제거
- 선착순 쿠폰 발급은 재고 100개에 200명 동시 요청 시 정확히 100명만 성공하도록, 쿠폰 1회성 사용 제약은 TOCTOU 레이스 컨디션을 DB UNIQUE 제약 + DataIntegrityViolationException catch로 원자적으로 방어

**2. 인덱스 튜닝 및 옵티마이저 분석**
- 복합 인덱스 2개 추가 시 오히려 조회 시간이 250ms → 2,300ms로 10배 증가하는 역효과를 경험. optimizer_trace로 random I/O 단가를 sequential과 동일하게 취급하는 MySQL cost model의 한계로 원인 추적
- 조회 조건 조합에 맞춘 복합 인덱스 6개 설계 후 0.27ms로 개선

### 성과

- 인덱스 역효과(250ms → 2,300ms)를 optimizer_trace로 근본 원인까지 추적해 6개 인덱스 설계 후 0.27ms로 개선
- 데드락을 재현 테스트로 원인(락 순서 불일치)까지 추적해 해결, 재발 방지
- 배치 실행 중 Zero-downtime 랭킹 교체 및 주간/월간 집계 정확도 확보
