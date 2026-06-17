# 00 · 측정 환경 & 데이터 분포 (고정 기록)

> 이후 모든 단계(01~04) 측정의 **기준 환경**. 한 번 고정하고, 바뀌면 여기만 갱신한다.
> 측정 도구는 `../measurement/`, 보고서 양식은 `TEMPLATE.md`.

## 1. 하드웨어 / 런타임

| 항목 | 값 |
|---|---|
| OS | macOS (Darwin 25.1.0), Apple Silicon (arm64) |
| CPU / RAM | Apple M1 Pro (8 core) / 16GB |
| Java | 21.0.5 |
| MySQL | 8.0.46 (docker, `docker/infra-compose.yml`, named volume `docker_mysql-8-data` 영속) |
| `innodb_buffer_pool_size` | **128MB** (기본값) |
| Redis | 7.0 (master 6379 / replica 6380) |
| k6 | v1.3.0 |

> MySQL/Redis 는 호스트 포트 3306/6379/6380 로 노출. 데이터는 named volume 에 영속되어 컨테이너 재시작에도 보존된다.

## 2. 프로파일 전략 (측정 전용)

| 프로파일 | ddl-auto | 용도 |
|---|---|---|
| `local,seed` | **create** | 1회 시드. 스키마를 엔티티에서 생성 + 데이터 적재 후 비웹(`web-application-type:none`)으로 종료 |
| `perf` | **none** | 측정 부팅. 같은 MySQL 에 붙어 데이터 보존, SQL 로깅 off |

```bash
# (1) 1회 시드 — 스키마 재생성 + 데이터 적재 후 자동 종료 (※ 데이터가 이미 있으면 불필요)
SPRING_PROFILES_ACTIVE=local,seed ./gradlew :apps:commerce-api:bootRun

# (2) 측정 부팅 — 데이터 보존(ddl-auto:none)
SPRING_PROFILES_ACTIVE=perf ./gradlew :apps:commerce-api:bootRun
```

이후 스키마 변경(컬럼·인덱스 추가)은 **명시적 SQL(ALTER)** 로 적용한다 — 그 자체가 EXPLAIN 학습 표면이 된다.

## 3. 데이터 로더

`com.loopers.support.seed.MeasurementDataSeeder` (`@Profile("seed")` ApplicationRunner, JdbcTemplate 배치).
**고정 시드**(brand=11, product=22, like=33)로 재실행해도 동일 분포가 재현된다. `products` 가 비어있지 않으면 적재를 건너뛴다.

| 엔티티 | 건수 | 분포 |
|---|---|---|
| brands | 1,000 | 브랜드별 상품 수를 로그정규(σ=0.8)로 배정, 합계 보정 |
| users | 5,000 | — |
| products | 100,000 | 브랜드별 편차 + 브랜드를 시간순 인터리브 → `id` 단조증가 = 최신순 |
| likes | 2,905,713 | 상품당 Pareto(x_min=10, α=1.5), 상품별 unique user, 최대 5,000 |

## 4. 실제 분포 (적재 직후 sanity, `measurement/sql/00-sanity.sql`)

- **브랜드별 상품 수**: min 6 · max 1,134 · avg 100.0 · stddev 96 → 롱테일 편차
- **인기 브랜드 Top**: `847`(1,134) · 64(834) · 97(777) · 258(604) · 217(529)
- **희귀 브랜드**: 27(6) · 975(7) · 994(7) · 837(7)
- **상품당 좋아요**: min 10 · max 5,000 · avg 29.1
- **좋아요 히스토그램**: `<50`=90,960 · `50–99`=5,845 · `100–499`=2,925 · `500–999`=175 · `1000+`=95 → 파워법칙
- **좋아요 Top 상품**: `45577`, 19979, 26485, 37046, 41530 (각 5,000)
- **테이블 크기**: products 10.5MB · likes **357.7MB**(데이터 173.7 + 인덱스 184.0) · users 2.0MB · brands 0.1MB
  → `likes` 357.7MB > 버퍼풀 128MB. 좋아요 반복 스캔이 버퍼풀에 다 안 담겨 디스크 I/O 까지 겹친다.

## 5. 측정 파라미터 (확정)

| 파라미터 | 값 | 근거 |
|---|---|---|
| `BRAND_ID` (S2) | **847** | 가장 인기 브랜드(1,134개) → 필터+정렬·Full Scan 분기 관찰 |
| `POPULAR_PRODUCT_ID` (S4) | **45577** | 좋아요 Top(5,000) → 상세/캐시 핫 상품 |
| `SIZE` | **20** | API 기본 |
| 동시 사용자 `VUS` | **50** | 부하 모델 고정. §6 참조 |
| `DURATION` | **30s** | 표본 확보. 앞 워밍업 구간은 버림 |

## 6. 측정 규약 (모든 단계 공통)

### 6-1. 시나리오 세트 (4개, 모든 단계 동일하게 측정 → 비교 가능)

| ID | 정렬 | 브랜드 필터 | 페이지 | 무슨 화면인가 / 의도 |
|---|---|---|---|---|
| **S1** | 좋아요순(LIKES_DESC) | 없음(전역) | 1페이지 | 메인 "좋아요 많은 순" 첫 페이지 — 서브쿼리 폭발의 주인공 |
| **S2** | 좋아요순(LIKES_DESC) | 인기 브랜드(847) | 1페이지 | 브랜드관 좋아요순 — 필터+정렬, 복합 인덱스 주인공 |
| **S3** | 최신순(LATEST) | 없음(전역) | 1페이지 | 기본 정렬, 일상 케이스 |
| **S4** | (상세) | productId 단건(45577) | — | 상세 페이지 — 캐시 주인공 |

### 6-2. 두 가지 렌즈 — DB 안(EXPLAIN) vs 사용자 체감(API)

| 렌즈 | 무엇 | 어떻게 | DNF 규칙 |
|---|---|---|---|
| **실행계획** | "왜 빠르고 느린가" | `EXPLAIN`(추정, 비실행) + `EXPLAIN ANALYZE FORMAT=TREE`(실측, 단일 쿼리) | 단건 30초 초과면 ANALYZE 생략, **DNF** 표기. EXPLAIN(추정) 표는 비실행이라 항상 채운다 |
| **API 성능** | "현실에서 얼마나 버티나" | k6 **동시 50명**(`constant-vus`), 각자 쉼 없이 반복, 30초 | 응답이 계속 30초를 넘기면 **DNF / 타임아웃** |

- **EXPLAIN(추정)과 EXPLAIN ANALYZE(실측)는 따로 표로** 보여주고, 그 아래 "왜 둘이 다른가"(추정 행수 vs 실측 행수)를 한두 줄로 설명한다.
- **API 동시 50명의 정의**: 요청 사이 멈춤 없이 항상 50건이 처리 중. 표본 분포에서 **p50 / p95 / p99 + 에러율**을 기록한다.
- 두 렌즈는 1:1로 같을 필요가 없다(ANALYZE=단일 쿼리, API=50명 경합). 서로 다른 관점이라 **함께** 봐야 한다.

## 7. 측정 절차

**EXPLAIN** (`measurement/sql/`):
```bash
# 추정(계획): explain-baseline.sql 그대로. 실측: 같은 쿼리를 EXPLAIN ANALYZE FORMAT=TREE 로 (S3·S4)
docker exec -i docker-mysql-1 mysql -uapplication -papplication loopers < measurement/sql/explain-baseline.sql
```

**k6** (`measurement/k6/products.js`):
```bash
SCENARIO=S3 BRAND_ID=847 POPULAR_PRODUCT_ID=45577 k6 run measurement/k6/products.js
```
p50/p95/p99 와 에러율을 기록한다. 단건이 30초를 넘는 시나리오는 DNF 로 적고 측정하지 않는다.
