# Product Ranking Batch 성능 하네스

이 하네스는 로컬 Docker Compose MySQL에 결정적인 합성 `product_metrics`를 적재하고, 실제 one-shot Batch 이미지를 실행한 뒤 Spring Batch 메타 데이터와 MySQL·컨테이너 지표를 파일로 남긴다.

## 안전 범위

- 별도 DB 이름은 반드시 `_perf`로 끝나야 하며, `PERF_CONFIRM_DATABASE_RESET`에 같은 DB 이름을 다시 입력하지 않으면 실행되지 않는다.
- `docker/infra-compose.yml`의 `mysql` 컨테이너 내부 root 계정만 데이터 준비와 지표 수집에 사용한다.
- 기본 실행은 전용 `loopers_product_ranking_perf` DB만 삭제 후 재생성한다. 애플리케이션 기본 DB인 `loopers`는 건드리지 않는다.
- DB를 유지하는 재시작 모드에서도 `product_metrics`의 지정된 상품 ID 범위만 삭제·재생성한다. 기본 범위는 `8000000000000001 ~ 8000000001000000`이다.
- 운영 DB나 공유 QA DB를 대상으로 실행하지 않는다.
- 결과와 로그에는 DB 비밀번호를 기록하지 않는다.

## 실행

월말 최악 조건의 기본 예시는 다음과 같다. `2026-05-31`은 일요일이자 31일 월말이므로 주간 7일과 월간 31일 집계를 동시에 측정한다.

```bash
PERF_DATABASE_NAME=loopers_product_ranking_perf \
PERF_CONFIRM_DATABASE_RESET=loopers_product_ranking_perf \
PERF_TARGET_DATE=20260531 \
PERF_DAILY_PRODUCTS=100000 \
PERF_DAYS=31 \
PERF_DATA_SEED=42 \
PERF_PRODUCT_ID_BASE=8000000000000000 \
PERF_CHUNK_SIZE=1000 \
PERF_LOG_INTERVAL=100 \
PERF_CPUS=2.0 \
PERF_MEMORY=2g \
PERF_BUILD_IMAGE=true \
./performance/product-ranking/batch/run.sh
```

기본값은 일간 10만 상품, 31일, seed 42, 5초 단위 컨테이너 샘플링이다. 합성 데이터는 같은 날짜·규모·seed에서 항상 동일하다. 최대 일간 상품 수는 100만이다.

성능 DB의 `product_metrics`는 이전 라운드와 동일하게 surrogate PK `id`, UNIQUE `(metric_date, product_id)`, 날짜 인덱스와 주문 부가 컬럼을 포함한다. Batch가 사용하지 않는 부가 컬럼도 유지해 실제 row·index 크기에 가까운 조건으로 측정한다.

반복 실행은 새 JobInstance가 필요하므로 하네스가 시간 기반 `rebuildSequence`를 생성한다. 실패한 실행을 재시작할 때는 첫 실행의 `manifest.env`에 기록된 `rebuildSequence`, `chunk_size`, 데이터 규모와 seed를 그대로 지정한다. 재시작 도중 Chunk 크기를 바꾸면 저장된 Reader 위치와 커밋 경계가 달라질 수 있으므로 허용하지 않는다.

```bash
PERF_DATABASE_NAME=loopers_product_ranking_perf \
PERF_CONFIRM_DATABASE_RESET=loopers_product_ranking_perf \
PERF_RESET_DATABASE=false \
PERF_TARGET_DATE=20260531 \
PERF_REBUILD_SEQUENCE=20260723153000 \
PERF_BUILD_IMAGE=false \
BATCH_IMAGE=loopers/commerce-batch:perf \
./performance/product-ranking/batch/run.sh
```

주요 선택 환경변수는 다음과 같다.

| 환경변수 | 기본값 | 설명 |
|---|---:|---|
| `PERF_DAILY_PRODUCTS` | `100000` | 날짜별 상품/행 수, 최대 100만 |
| `PERF_DAYS` | `31` | 생성할 날짜 수, 최대 31 |
| `PERF_DATA_SEED` | `42` | 메트릭 분포를 결정하는 seed |
| `PERF_PRODUCT_ID_BASE` | `8000000000000000` | 합성 상품 ID의 시작 기준값. API와 결합 측정할 때만 격리 DB의 실제 상품 ID 범위에 맞춤 |
| `PERF_BUILD_IMAGE` | `true` | BootJar와 Batch 이미지를 다시 빌드할지 여부 |
| `PERF_START_INFRA` | `true` | Compose MySQL을 기동할지 여부 |
| `PERF_DATABASE_NAME` | `loopers_product_ranking_perf` | 반드시 `_perf`로 끝나는 전용 DB 이름 |
| `PERF_RESET_DATABASE` | `true` | 전용 DB를 삭제 후 재생성할지 여부. 실패 Job 재시작은 `false` |
| `PERF_PREPARE_DATASET` | `true` | 합성 원천을 삭제·재생성할지 여부. `false`이면 기존 행 수와 스키마를 검증해 재사용하며 `PERF_RESET_DATABASE=false`가 필수 |
| `PERF_SAMPLE_INTERVAL_SECONDS` | `5` | `docker stats` 수집 주기 |
| `PERF_CHUNK_SIZE` | `100` | Reader page/fetch와 Chunk commit 크기 |
| `PERF_LOG_INTERVAL` | `100` | 몇 번째 Chunk마다 성능 로그를 남길지 지정. `0`은 Chunk 로그 비활성화 |
| `PERF_CPUS` | `2.0` | Batch 컨테이너 CPU 제한 |
| `PERF_MEMORY` | `2g` | Batch 컨테이너 메모리 제한 |
| `PERF_OUTPUT_DIR` | `build/performance/...` | 결과 디렉터리. 기존 디렉터리는 덮어쓰지 않음 |
| `BATCH_IMAGE` | `loopers/commerce-batch:perf` | 실행할 이미지 태그 |

## 결과 파일

결과는 기본적으로 `build/performance/product-ranking/<targetDate>-<rebuildSequence>-<attempt>/`에 생성된다.

| 파일 | 내용 |
|---|---|
| `manifest.env` | 입력 규모, seed, Git SHA, 이미지 ID, 실행시간과 최종 상태 |
| `dataset.tsv` | 실제 원천 행·날짜·상품 수 |
| `batch.log` | one-shot Batch 로그 |
| `chunk-metrics.csv` | `PERF_CHUNK` 로그에서 추출한 Chunk별 소요시간과 read/write 증분 |
| `container-stats.tsv` | CPU, 메모리, Block/Network I/O, PID 시계열 |
| `container-result.tsv` | 시작·종료 시각, 종료 코드, OOM 여부 |
| `database-report.tsv` | Job/Step 소요시간, read/write/commit/rollback, 처리량, MV checksum, 테이블 크기 |
| `job-execution.csv` | Job 소요시간과 전체 read/write 처리량 |
| `step-executions.csv` | Step별 소요시간, read/write/commit/rollback/skip, 처리량 |
| `mysql-status-delta.tsv` | Buffer pool read, fsync, log bytes, 임시 테이블 등 실행 전후 차이 |

하네스는 컨테이너 종료 코드와 `BATCH_JOB_EXECUTION.STATUS=COMPLETED`, 주·월간 스냅샷 행 수를 자동 검증한다. 성능 합격 여부는 이 결과를 설계 문서의 SLO와 비교해 판정한다.

Chunk 지연의 p50/p95/p99가 필요하면 `PERF_LOG_INTERVAL=1`로 모든 Chunk를 기록한다. 전체 처리량 기준선은 로그 I/O 영향을 줄이기 위해 `100` 또는 `0`으로 별도 실행하고 두 결과를 구분한다.

API 동시 부하처럼 Batch 시작 시점을 바로 맞춰야 할 때는 성공한 동일 규모·seed 실행 뒤 `PERF_RESET_DATABASE=false PERF_PREPARE_DATASET=false`로 원천을 재사용한다. 이 모드는 기존 데이터의 대상 날짜 범위·행 수·상품 ID 범위와 UNIQUE 인덱스를 검증하며, 조건이 다르면 Batch를 시작하기 전에 실패한다.

Batch가 실패하더라도 컨테이너 삭제 전에 `batch.log`, `container-result.tsv`, `manifest.env`의 종료 코드와 소요시간을 먼저 저장한다. DB 장애로 후속 SQL 수집까지 실패하면 `collection-errors.log`에 원인을 남긴다.

컨테이너 CPU와 MySQL은 같은 Docker 호스트의 다른 워크로드 영향을 받는다. 비교 실험에서는 Docker agent 사양, MySQL buffer pool, warm/cold 상태, Git SHA, 이미지 ID, 입력 seed를 동일하게 유지한다.
