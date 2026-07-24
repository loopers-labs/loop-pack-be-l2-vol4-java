# Jenkins Ranking Batch

Jenkins는 코드 검증과 실제 Ranking Batch 실행을 서로 다른 Pipeline으로 운영한다.

```text
Jenkinsfile
└─ Testcontainers 기반 테스트와 Boot Jar 검증

jenkins/Jenkinsfile.product-ranking
└─ 영속 MySQL과 Redis를 사용하는 WEEKLY·MONTHLY 정기 실행
```

실제 Batch 실행에 Testcontainers를 사용하지 않는다. `product_metrics` 원천과
Spring Batch의 `BATCH_*` 재시작 이력은 실행 사이에도 유지되어야 한다.

## 1. Jenkins Agent

### CI Agent: `java21-docker`

- Linux
- JDK 21
- Git
- Docker CLI와 Docker daemon 접근 권한
- Gradle·Maven 의존성과 Testcontainers 이미지를 내려받을 네트워크

`Jenkinsfile`은 `:modules:ranking:test`와 `:apps:commerce-batch:test`를 실행한다.
테스트가 MySQL과 Redis Testcontainers를 직접 시작하므로 별도 Docker Compose는 올리지 않는다.

Docker로 실행한 Jenkins Controller에 호스트의 Docker Socket을 마운트하면
호스트의 root에 준하는 권한이 생긴다. Controller 대신 격리된 Build Agent에
Docker 권한을 부여한다.

### Ranking Agent: `java21-ranking`

- Linux
- JDK 21
- Git
- GNU coreutils (`date`)
- 영속 MySQL과 Redis에 대한 네트워크 접근
- Gradle과 Maven 의존성을 내려받을 네트워크 또는 사전 캐시

정기 실행 Pipeline은 Docker가 필요하지 않다. Jenkins가 컨테이너 안에서 실행된다면
`localhost`는 Jenkins 컨테이너 자신을 가리키므로 실제 서비스 주소를 환경 변수로 설정한다.
DB Credentials를 사용하는 Ranking Agent는 다른 Job과 Unix 계정을 공유하지 않는
격리된 단일 Executor로 운영한다.

## 2. Jenkins Job 구성

두 Job 모두 `Pipeline script from SCM`으로 생성한다.

| Jenkins Job | Script Path | 권장 용도 |
|---|---|---|
| Ranking Batch CI | `Jenkinsfile` | PR과 브랜치 검증 |
| Product Ranking Schedule | `jenkins/Jenkinsfile.product-ranking` | 정기·수동 Ranking 실행 |

정기 실행 Job은 Multibranch Pipeline으로 만들지 않는다. Jenkinsfile의
`02:00 KST` Cron이 브랜치마다 등록되면 같은 Ranking이 동시에 실행될 수 있다.
하나의 Standalone Pipeline Job이 코드 리뷰와 CI를 통과한 보호된 배포 브랜치만
바라보도록 설정한다. `volume-10`에서 Pipeline 연결을 시험할 때는 운영 DB가 아닌
격리된 검증용 DB Credentials만 사용한다.
운영 Job의 수동 Build 권한은 담당 운영자에게만 부여한다. Pipeline은 입력받은
`SOURCE_COMMIT`이 Checkout한 보호 브랜치 HEAD의 조상인지 검사해 다른 브랜치의
코드가 운영 DB Credentials 범위에서 실행되는 것을 막는다.

정기 실행 Pipeline은 지정한 브랜치에서 Boot Jar만 만들고 테스트를 다시 실행하지 않는다.
배포 기준 브랜치로 병합되기 전에 `Jenkinsfile`의 Ranking Batch CI가 통과하도록
브랜치 보호 규칙을 둔다. 이 Pipeline은 Ranking Batch 범위만 검증하므로
API를 포함한 전체 애플리케이션 회귀 검증을 대신하지 않는다.

## 3. Credentials와 환경 변수

Jenkins Credentials에 다음 두 항목을 만든다.

| Credentials ID | 종류 | 값 |
|---|---|---|
| `ranking-mysql-jdbc-url` | Secret text | `jdbc:mysql://<host>:3306/<database>` |
| `ranking-mysql-credentials` | Username with password | Ranking DB 계정 |

비밀번호는 명령행 인자로 전달하지 않는다. Pipeline이 Credentials를
Spring Boot 환경 변수로 바인딩하며 콘솔에도 값을 출력하지 않는다.

Ranking Job의 Folder 또는 Agent 환경에는 다음 값을 설정한다.

```text
REDIS_MASTER_HOST
REDIS_MASTER_PORT
REDIS_REPLICA_1_HOST
REDIS_REPLICA_1_PORT
```

현재 Ranking Snapshot은 Redis를 직접 읽지 않지만 `commerce-batch`가 Redis 설정도
함께 구성하므로 애플리케이션 기동에 위 값이 필요하다.

대상 MySQL에는 업무 테이블과 Spring Batch의 `BATCH_*` 테이블이 미리 준비되어 있어야 한다.
Pipeline은 다음 설정을 고정해 운영 스키마를 만들거나 변경하지 않는다.

```text
SPRING_PROFILES_ACTIVE=local-dev
SPRING_BATCH_JDBC_INITIALIZE_SCHEMA=never
SPRING_JPA_HIBERNATE_DDL_AUTO=none
RANKING_BATCH_CHUNK_SIZE=1000
```

`local-dev`는 Jenkins 콘솔 로그를 사용하기 위한 프로필이다. MySQL 접속 정보는
위 Jenkins Credentials 환경 변수가 덮어쓰며, DDL과 Batch 스키마 자동 생성은
별도 환경 변수로 차단한다.

같은 JobInstance를 재시작하는 동안 `RANKING_BATCH_CHUNK_SIZE`를 바꾸지 않는다.

## 4. 정기 실행과 수동 재시작

정기 실행은 매일 `02:00 KST`에 전날까지의 WEEKLY, MONTHLY Ranking을 순서대로 만든다.

```text
WEEKLY 프로세스 종료
→ MONTHLY 프로세스 실행
```

WEEKLY가 실패해도 프로세스가 종료되면 MONTHLY는 독립적으로 실행한다.
둘 중 하나라도 실패하면 두 실행을 모두 시도한 뒤 Jenkins Build를 실패로 표시한다.
Pipeline 전체에 자동 Retry나 강제 Timeout을 두지 않는다.

| Parameter | 정기 실행 | 실패 재시작 | 완료 후 재집계 |
|---|---|---|---|
| `RUN_PERIODS` | `BOTH` | 실패한 기간 | 재집계할 기간 |
| `AGGREGATION_END_DATE` | 빈 값 | 실패 실행과 같은 `yyyyMMdd` | 대상 날짜 |
| `REVISION` | `1` | 실패 실행과 같은 값 | 기존보다 큰 값 |
| `SOURCE_COMMIT` | Cron에서만 빈 값 | 실패 실행과 같은 Git SHA | 검증된 Git SHA |

빈 `AGGREGATION_END_DATE`는 Jenkins가 Build를 예약한 시각의 서울 날짜를 기준으로 전날을 계산한다.
Jenkins 빌드 번호나 현재 시각을 JobParameter에 자동으로 추가하지 않는다.
Cron 실행의 빈 `SOURCE_COMMIT`은 Pipeline Job에 설정된 배포 브랜치의 현재 HEAD를 사용한다.
수동 실행은 `SOURCE_COMMIT` 입력이 필수다.
실행한 전체 SHA는 Jenkins Build 설명에 기록되며, 실패 재시작에서는 같은 SHA를 입력해
동일한 코드로 남은 Chunk를 처리한다. 재시작에 필요한 과거 커밋을 찾을 수 있도록
Jenkins SCM Checkout은 재시작 보존 기간보다 긴 Git 이력을 가져와야 한다.

실패 재시작 예시는 다음과 같다.

```text
RUN_PERIODS=WEEKLY
AGGREGATION_END_DATE=20260723
REVISION=1
SOURCE_COMMIT=146819a69
```

앞의 세 JobParameter와 물리 타입이 같아야 JDBC JobRepository가 마지막 성공 Chunk 다음부터
재시작한다. `SOURCE_COMMIT`은 Spring Batch JobParameter가 아니라 동일한 실행 코드를
보장하기 위한 Jenkins Parameter다.
이미 완료된 결과를 다시 계산할 때만 `REVISION`을 증가시킨다.

## 5. Cleanup

`productRankingCleanupJob`은 Ranking Snapshot과 별도 Job으로 유지한다.
현재 애플리케이션에는 삭제 가능한 `targetSnapshotId`를 외부에 제공하는 탐색 명령이 없다.
Jenkins가 SQL로 대상을 직접 찾으면 삭제 정책이 애플리케이션과 Jenkins에 중복되므로
이번 Pipeline에서는 Cleanup을 억지로 연결하지 않는다.
