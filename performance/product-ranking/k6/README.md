# Ranking API k6 performance test

`GET /api/v1/rankings`의 `daily`, `weekly`, `monthly` 경로를 동시에 검증하는 독립 k6 하네스다. 기본 부하 비율은 일간 70%, 주간 20%, 월간 10%이며, 애플리케이션 빌드 의존성을 추가하지 않는다.

## Prerequisites

- k6 0.52 이상
- 실행 중인 `commerce-api`
- 지정 날짜의 Redis 일간 랭킹과 주·월간 MV 스냅샷

기본적으로 빈 결과를 실패로 판단한다. 스냅샷 미생성 경로 자체를 측정할 때만 `REQUIRE_DATA=false`를 사용한다.

### Prepare a local fixture

로컬 `docker/infra-compose.yml` 환경에서는 성능 측정 전에 동일 상품으로 일간·주간·월간 랭킹 fixture를 만들 수 있다. 먼저 MySQL과 Redis를 기동하고 MV DDL 및 상품 데이터를 준비한다.

```bash
docker compose --project-name docker \
  --file docker/infra-compose.yml \
  up -d mysql redis-master redis-readonly
```

MV 테이블이 없다면 `database/schema/product-ranking-aggregation.sql`을 로컬 `loopers` DB에 먼저 적용해야 한다. fixture 스크립트가 테이블을 자동 생성하거나 운영 스키마를 변경하지 않는다.

아래 명령은 활성 상품 ID 100개를 조회해 지정 날짜의 `mv_product_rank_weekly`, `mv_product_rank_monthly`, `ranking:all:yyyyMMdd`만 교체한다. 다른 날짜와 상품 원본 데이터는 변경하지 않는다.

```bash
PERF_CONFIRM_API_FIXTURE=YES \
PERF_API_DATABASE_NAME=loopers \
TARGET_DATE=20260722 \
FIXTURE_COUNT=100 \
./performance/product-ranking/k6/prepare-ranking-api-fixture.sh
```

`PERF_CONFIRM_API_FIXTURE=YES`는 로컬 fixture 교체를 위한 필수 확인값이다. `PERF_API_DATABASE_NAME`의 기본값은 `loopers`이며, 다른 DB를 지정할 때는 안전을 위해 이름이 `_perf`로 끝나야 한다. `FIXTURE_COUNT`는 기본 100이고 1 이상 100 이하만 허용한다. 활성 상품이 요청 개수보다 적으면 변경 전에 실패하므로 상품을 먼저 seed하거나 개수를 낮춘다. MV가 없거나 MySQL·Redis에 연결할 수 없는 경우에도 명확한 오류로 중단한다. 스크립트는 로컬 Compose 컨테이너의 환경변수로 MySQL에 접속하며 자격증명을 출력하지 않는다.

fixture의 개수까지 검증하면서 부하 테스트를 실행하려면 세 period의 예상 `totalCount`를 함께 지정한다.

```bash
TARGET_DATE=20260722 \
EXPECTED_DAILY_TOTAL_COUNT=100 \
EXPECTED_WEEKLY_TOTAL_COUNT=100 \
EXPECTED_MONTHLY_TOTAL_COUNT=100 \
./performance/product-ranking/k6/run-ranking-api.sh
```

## Run

```bash
TARGET_DATE=20260722 \
BASE_URL=http://localhost:8080 \
./performance/product-ranking/k6/run-ranking-api.sh
```

Batch와 API의 자원 경합을 확인하려면 같은 데이터와 부하 설정으로 Batch 실행 전, Chunk 처리 중, MV 게시 중, 실행 후에 각각 실행한다.

```bash
TARGET_DATE=20260722 \
DURATION=10m \
DAILY_RPS=70 \
WEEKLY_RPS=20 \
MONTHLY_RPS=10 \
./performance/product-ranking/k6/run-ranking-api.sh
```

필요하면 기간마다 다른 스냅샷 날짜를 지정할 수 있다.

```bash
TARGET_DATE=20260722 \
DAILY_DATE=20260722 \
WEEKLY_DATE=20260721 \
MONTHLY_DATE=20260720 \
./performance/product-ranking/k6/run-ranking-api.sh
```

| 환경변수 | 기본값 | 설명 |
|---|---:|---|
| `BASE_URL` | `http://localhost:8080` | API base URL |
| `TARGET_DATE` | 필수 | 공통 조회일 `yyyyMMdd` |
| `DAILY_DATE` / `WEEKLY_DATE` / `MONTHLY_DATE` | `TARGET_DATE` | 기간별 조회일 override |
| `DURATION` | `2m` | 각 시나리오 실행시간 |
| `DAILY_RPS` / `WEEKLY_RPS` / `MONTHLY_RPS` | `14` / `4` / `2` | 기간별 목표 RPS |
| `DAILY_VUS` / `WEEKLY_VUS` / `MONTHLY_VUS` | `20` / `10` / `10` | 기간별 사전 할당 VU. 실행 중 VU 상한은 각각 이 값의 2배 |
| `PAGE` / `SIZE` | `1` / `20` | 1-base 페이지와 페이지 크기 |
| `REQUIRE_DATA` | `true` | `totalCount > 0`과 비어 있지 않은 `items` 검증 여부 |
| `EXPECTED_DAILY_TOTAL_COUNT` | 미지정 | 일간 응답의 정확한 `totalCount`. 양수이면 non-empty도 함께 검증 |
| `EXPECTED_WEEKLY_TOTAL_COUNT` | 미지정 | 주간 응답의 정확한 `totalCount`. 양수이면 non-empty도 함께 검증 |
| `EXPECTED_MONTHLY_TOTAL_COUNT` | 미지정 | 월간 응답의 정확한 `totalCount`. 양수이면 non-empty도 함께 검증 |
| `SUMMARY_PATH` | `build/reports/k6/ranking-api-summary.json` | 정규화 결과와 원본 k6 summary를 저장할 경로 |

추가 k6 옵션은 wrapper 인자로 넘긴다.

```bash
TARGET_DATE=20260722 \
./performance/product-ranking/k6/run-ranking-api.sh --no-color
```

## Fixed acceptance thresholds

threshold는 환경변수로 완화할 수 없도록 테스트 코드에 고정한다.

| 대상 | period별 기준 |
|---|---:|
| API duration p95 | `< 100ms` |
| API duration p99 | `< 250ms` |
| 상태·응답 계약·데이터 검증 실패율 | `< 0.1%` |
| k6 check 성공률 | `> 99.9%` |
| 요청 실행 여부 | `count > 0` |
| 도달하지 못한 iteration | 전체 `0` |

각 HTTP 및 사용자 정의 metric에는 `period=daily|weekly|monthly` 태그가 붙는다. 종료 시 콘솔에 기간별 요청 수, 실패율, p95, p99를 출력하고 JSON 파일에는 테스트 설정, 기간별 요약, threshold 판정 및 원본 k6 summary를 함께 기록한다. 하나라도 기준에 미달하면 k6 프로세스가 0이 아닌 종료 코드를 반환한다.

MV 게시 원자성을 부하 상태에서 확인할 때는 기존 스냅샷과 새 스냅샷을 모두 100행으로 준비하고 `EXPECTED_WEEKLY_TOTAL_COUNT=100`, `EXPECTED_MONTHLY_TOTAL_COUNT=100`으로 실행한다. 게시 Tasklet 실행 중 `totalCount`가 0 또는 1~99로 관찰되면 해당 period의 contract failure와 threshold 실패로 기록된다. 이 검증은 행 개수 기준이므로 같은 100행 안에서 상품이나 점수가 바뀌는 게시 정합성은 별도 결과 checksum 검증으로 보완해야 한다.
