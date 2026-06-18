# 의사 결정 사항 (상품 목록 필터 및 정렬)

## 1. 브랜드 필터 확장성
*   **결정 사항:** 향후 다중 브랜드 선택이 가능하도록 IN 쿼리로 미리 설계하지 않고, 현재 요구사항에 맞춰 단일 `brandId` 매칭 필터만 제공하기로 결정했습니다.
*   **사유:** YAGNI(You Aren't Gonna Need It) 원칙에 따라 현재 불필요한 복잡도를 줄이기 위함입니다. 다중 선택 기능이 실제로 필요해지는 시점에 IN 쿼리로 확장하는 것이 비용 효율적입니다.

## 2. '좋아요 순' 정렬 동점자 처리
*   **결정 사항:** 좋아요 수가 동일한 상품들의 경우 **최신 등록 순(`latest`)**으로 2차 정렬을 수행하기로 합의했습니다.
*   **사유:** 최신 상품에 대한 노출 기회를 부여하고, 데이터가 비결정적으로 섞여 페이징이 깨지는 현상을 방지하기 위해서입니다.

## 3. '좋아요 순' 정렬 시 좋아요 0개 상품 노출
*   **결정 사항:** 좋아요가 1개도 없는 상품도 결과 목록에 노출되도록 `LEFT JOIN`을 사용하여 카운팅(`COUNT(productLike.id)`) 하기로 결정했습니다.
*   **사유:** 일반적인 이커머스에서 '좋아요 순' 필터는 단순히 정렬 기준을 바꾸는 것이지, 상품 자체를 숨기는 필터링 목적이 아니기 때문입니다. 빈 화면이 노출되는 것을 방지합니다.

## 4. 리스크 고지: 정렬 쿼리의 성능 이슈 (동적 COUNT)
*   **이슈 내용:** 현재 정책 상 `Product` 테이블에 `likeCount`를 반정규화하지 않고 `PRODUCT_LIKES` 테이블과 조인하여 실시간 카운팅으로 순위를 매깁니다. 데이터가 누적될 경우 `LEFT JOIN` + `GROUP BY` 연산은 DB에 큰 부하를 주고 성능을 급격히 저하시킵니다.
*   **향후 대책 (성능 저하 감지 시 도입 검토):**
    1.  인덱스 최적화 (`product_likes.product_id`) 커버링 인덱스 활용
    2.  일정 주기 스케줄러를 통한 반정규화 배치 처리 (`Product.like_count` 갱신)
    3.  Redis 기반의 조회 카운팅 캐시 구성


1. 각각 다른 화면이나 기능에서 단독으로 조회된다 ➡️ 단일 인덱스 각각 생성
2.  WHERE A = ? OR B = ?  구조다 ➡️ 단일 인덱스 각각 생성
3. 거의 항상 두 조건이 세트로 묶여서 검색된다 ➡️ 복합 인덱스
4. 선행 조건으로 거른 데이터가 너무 많아서, 다음 조건이나 정렬(ORDER BY)까지 인덱스에서 처리하고 싶다 ➡️ 복합
   인덱스

> 현재 프로젝트 구조에서 상품 목록 조회 시 (1)검색 조건 x, (2)브랜드 필터 사용, (3)좋아요 순 정렬 사용, (4)브랜드 필터 + 좋아요 순 정렬 4가지 케이스로 api 요청 시 발생하는 쿼리를
알려줘

▸ Thought for 7s, 1.3k tokens
Prioritizing Tool Usage

● Read(C:/seungmin/seungmin_lab/loop-pack-be-l2-vol4-java/apps/commerce-api/src/main/java/com/loopers/application/product/ProductFacade.java) (ctrl+o to expand)

▸ Thought for 3s
Prioritizing Tool Usage
현재 프로젝트의  ProductRepositoryImpl  클래스 내 QueryDSL 로직을 분석하여, 요청 케이스별로 데이터베이스에 날아가는 핵심 원시 쿼리(Raw SQL) 를 정리해 드립니다.

(참고: 모든 페이징 요청에는 전체 데이터 개수를 구하는  COUNT  쿼리와, 브랜드 이름을 매핑하기 위한  SELECT * FROM brand WHERE id IN (...)  쿼리가 추가로 발생하지만, 핵심 성능
병목인 '데이터 조회 쿼리' 위주로 설명해 드립니다.)
──────
### (1) 검색 조건 x (기본 최신순 정렬)

SELECT p.id, p.brand_id, p.name, p.price, p.is_deleted, p.created_at, p.updated_at
FROM product p
WHERE p.is_deleted = false
ORDER BY p.created_at DESC
LIMIT 10 OFFSET 0;

execution: 42 ms, fetching: 349 ms
1,SIMPLE,p,,ALL,,,,,97185,50,Using where; Using filesort

### (2) 브랜드 필터 사용 ( brandId = 1 )

SELECT p.id, p.brand_id, p.name, p.price, p.is_deleted, p.created_at, p.updated_at
FROM product p
WHERE p.brand_id = 1
AND p.is_deleted = false
ORDER BY p.created_at DESC
LIMIT 10 OFFSET 0;

execution: 30 ms, fetching: 332 ms
1,SIMPLE,p,,ALL,,,,,97185,5,Using where; Using filesort

### (3) 좋아요 순 정렬 사용 ( sort=likes_desc )

🚨 [성능 병목 지점]  product_likes  테이블을 실시간으로  LEFT JOIN  하고, 상품별로  GROUP BY  묶은 뒤 좋아요 개수를  COUNT  해서 정렬합니다.

    SELECT p.id, p.brand_id, p.name, p.price, p.is_deleted, p.created_at, p.updated_at
    FROM product p
             LEFT JOIN product_likes pl ON pl.product_id = p.id
    WHERE p.is_deleted = false
    GROUP BY p.id
    ORDER BY count(pl.id) DESC, p.created_at DESC
    LIMIT 10 OFFSET 0;

(execution: 2 s 934 ms, fetching: 330 ms)
1,SIMPLE,p,,ALL,PRIMARY,,,,97185,50,Using where; Using temporary; Using filesort
1,SIMPLE,pl,,index,,uk_product_likes_user_product,16,,995218,100,Using where; Using index; Using join buffer (hash join)
쿼리 실행 순서: from -> join -> where -> group by -> select -> order by -> limit
from: pk가 설정되어있으므로 완료
join: product_likes에 유니크 인덱스가 (userid, productid)로 설정되어 있으므로 사실상 안탐, 순서만 바꿨을때 문제 없나?
where: is_deleted 같은 경우 대다수가 false이므로 일단 보류
group by: pk이므로 완료
select: product entity를 통째로 조회하므로 커버링 인덱스를 적용하고자 억지로 모든 컬럼에 인덱스를 넣기는 애매
order by: 집계 함수에는 인덱스 설정 불가, p.created_at 같은 경우 uuid 같은 형식이 아니라 auto_increment 방식 채번므로 p.id desc로 인덱스 적용 가능

alter table product_likes
drop index uk_product_likes_user_product,
add unique index uk_product_id_user_id (product_id, user_id),
add index idx_user_id (user_id)
(execution: 428 ms, fetching: 347 ms)
1,SIMPLE,p,,index,PRIMARY,PRIMARY,8,,97185,50,Using where; Using temporary; Using filesort
1,SIMPLE,pl,,ref,uk_product_id_user_id,uk_product_id_user_id,8,loopers.p.id,9,100,Using index

is_deleted를 위한 단일 인덱스는 삭제된 상품의 비율이 많지 않을 것이기 때문에 효율이 떨어져서 옵티마이저가 full scan을 택할 가능성이 높음 + 필터링이 거의 안됨
대신 4번 쿼리에서 사용하는 brand_id를 위해 단일 인덱스 생성


1. 인덱스는 디스크에 "미리 저장된 값"을 기반으로 합니다.
   데이터베이스의 인덱스(B-Tree)는 데이터를  INSERT  하거나  UPDATE  할 때, 특정 컬럼의 값을 기준으로 트리를 "미리"
   예쁘게 정렬해 디스크에 저장해 두는 구조입니다.
   하지만  count(pl.id) 는 디스크 어딘가에 저장되어 있는 값이 아닙니다. 쿼리가 실행되는 순간, 조건에 맞는 데이터들을
   긁어모아서 조인하고, 그룹( GROUP BY )으로 묶은 뒤에야 비로소 메모리 상에서 동적으로 계산되어 튀어나오는 값입니다.
   미리 만들어둘 수가 없는 것이죠.

2. 실행 계획 상 어쩔 수 없는 임시 테이블 정렬 (Filesort)
   이 쿼리를 실행하면 데이터베이스는 다음과 같은 순서로 일합니다.
1.  product 와  product_likes 를 조인한다.
2. 조인된 결과 덩어리를  product.id  단위로 묶는다. ( GROUP BY )
3. 각 그룹의 좋아요 개수를 1, 2, 3... 하고 열심히 센다. ( COUNT )
4. 계산이 다 끝나서 나온 결과들을 메모리(또는 임시 디스크)에 올려두고 강제로 정렬 알고리즘(퀵 소트 등)을 돌린다. (
   ORDER BY count )

### (4) 브랜드 필터 + 좋아요 순 정렬 ( brandId = 1 ,  sort=likes_desc )

(2)번의 브랜드 필터와 (3)번의 실시간 JOIN + GROUP BY + COUNT 정렬 로직이 모두 합쳐진 형태입니다.

    SELECT p.id, p.brand_id, p.name, p.price, p.is_deleted, p.created_at, p.updated_at
    FROM product p
             LEFT JOIN product_likes pl ON pl.product_id = p.id
    WHERE p.brand_id = 1
      AND p.is_deleted = false
    GROUP BY p.id
    ORDER BY count(pl.id) DESC, p.created_at DESC
    LIMIT 10 OFFSET 0;

(execution: 795 ms, fetching: 349 ms)
1,SIMPLE,p,,ALL,PRIMARY,,,,97185,5,Using where; Using temporary; Using filesort
1,SIMPLE,pl,,index,,uk_product_likes_user_product,16,,995218,100,Using where; Using index; Using join buffer (hash join)

3번 쿼리 사고과정 그대로
is_deleted를 위한 단일 인덱스는 삭제된 상품의 비율이 많지 않을 것이기 때문에 효율이 떨어져서 옵티마이저가 full scan을 택할 가능성이 높음 + 필터링이 거의 안됨
대신 4번 쿼리에서 사용하는 brand_id를 위해 단일 인덱스 생성( create index idx_brand_id on product (brand_id) )
1,SIMPLE,p,,ref,"PRIMARY,idx_brand_id",idx_brand_id,8,const,201,50,Using where; Using temporary; Using filesort
1,SIMPLE,pl,,ref,uk_product_id_user_id,uk_product_id_user_id,8,loopers.p.id,1,100,Using index
(execution: 8 ms, fetching: 344 ms)


### 💡 10건의 좋아요 수를 한 번에 조회하는 쿼리

    SELECT product_id, COUNT(*) AS like_count
    FROM product_likes
    WHERE product_id IN (1, 2, 3, 4, 5, 6, 7, 8, 9, 10) -- 앞에서 조회한 상품 ID 10개
    GROUP BY product_id;
1. 극한의 최적화 (분기 처리)
   • "좋아요 순 정렬"이 들어오면 👉 조인해서 DTO로 1방 쿼리로 끝낸다.
   • "최신 순 정렬"이 들어오면 👉 상품만 엔티티로 가져오고, 나중에 IN 쿼리로 좋아요 수를 가져와 합친다.
   • (단점: 코드가 복잡해지고 유지보수가 까다로워짐)
2. 일관성 유지 (현재 구조)
   • 정렬 기준이 뭐든 간에, 일단 상품 데이터 딱 10건만 빠르게 가져온다.
   • 그 후 10건의 ID를 모아서 IN 쿼리(이건 매우 가벼운 쿼리입니다)로 좋아요 수를 모아서 합친다.
   • (장점: 쿼리 구조가 일관되고 코드가 깔끔함. 연산 낭비가 약간 있지만 LIMIT 10건에 대한 IN 쿼리라 실무에서도
   충분히 타협 가능한 성능)
1~4번 쿼리에 select에 like_count를 포함시키기 위해 다음 1번 해결책이 좋다고 보았음. 
위의 추가 쿼리가 발생하는데 성능이 매번 377 ms (execution: 15 ms, fetching: 362 ms)만큼 저하되므로 1번이 더 좋은 방식이라고 보았음

---

다만 전제 조건으로, MySQL은 Oracle이나 PostgreSQL과 달리 Materialized View를 기본으로 제공하지 않기 때문에, 별도의
집계 전용 테이블( product_like_stats )을 만들고 배치 프로그램이나 스케줄러를 통해 직접 갱신하는 방식으로 흉내를
내어 구현해야 합니다.
반정규화(컬럼 추가)와 Materialized View(집계 테이블) 방식을 비교했을 때 가장 큰 차이는 **실시간성(정합성)**과
**동시성 제어(Lock)**에 있습니다.
──────
### 1. 쓰기 부하와 Lock 경합 (가장 큰 차이)

• 반정규화 (컬럼 직접 수정):
A 상품에 1초 동안 1,000명의 유저가 동시에 좋아요를 누른다면, 데이터베이스는 A 상품 데이터 1줄(Row)에 1,000번의
UPDATE  쓰기 락(Lock)을 순차적으로 겁니다. 이른바 Hotspot 병목이 발생하여 전체 DB 성능이 멈출 수 있습니다.
• Materialized View (배치 갱신):
좋아요를 누를 때는 가벼운  INSERT ( product_likes  테이블)만 1,000번 발생하므로 락 경합이 1도 없습니다. 그리고
스케줄러가 1분에 딱 한 번 몰아서  UPDATE product_like_stats 를 한 번만 수행합니다. 쓰기 부하를 방어하는 데에는
극강의 성능을 자랑합니다.

### 2. 실시간 데이터 정합성 보장

• 반정규화: 트랜잭션이 하나로 묶여있어 데이터가 100% 실시간으로 정확합니다. 사용자가 좋아요를 누르고 즉시
새로고침해도 무조건 카운트가 올라가 있습니다.
• Materialized View: 갱신 주기에 따라 **데이터의 지연(Lag)**이 발생합니다. 1분 배치로 도는 뷰라면, 내가 방금
좋아요를 눌렀는데도 화면에는 1분 전 통계가 보일 수 있습니다. (Eventual Consistency)

### 3. 운영 및 아키텍처 복잡도

• 반정규화: 애플리케이션의 엔티티 서비스 계층에서 아주 단순하게  product.increaseLikeCount()  코드로 관리됩니다.
코드가 직관적입니다.
• Materialized View: 이걸 구현하려면 Spring Batch를 돌리거나 데이터베이스 내부 이벤트 스케줄러를 설정해야 합니다.
즉, "배치가 실패하면 어떻게 복구할 것인가?" 같은 새로운 시스템 관리 포인트가 추가되어 아키텍처가 복잡해집니다.
──────
### 💡 실무 가이드: 무엇을 선택해야 할까?

1. 반정규화 선택: 아직 카카오톡이나 인스타그램처럼 초당 트래픽이 몰리는 서비스가 아니고, 유저 경험상 실시간 반영이
   매우 중요한 도메인이라면 컬럼 반정규화가 가장 가성비가 좋습니다.
2. Materialized View(또는 Redis) 선택: 아이유 콘서트 티켓팅, 특정 유명 인플루언서의 상품처럼 한순간에 수만 명의
   트래픽이 몰려 DB Lock 경합으로 시스템이 죽을 위험이 있는 대용량 환경이라면 실시간성을 1~2분 정도 포기하더라도
   뷰(통  테이블  배치 방식)나 메모리 캐시(Redis)로 분리하는 아키텍처를 선택해야 합니다.

### 좋아요(Like) 수 비정규화 및 동기화 전략

#### 문제 정의
- **현재 동작/제약:** 상품 목록 조회 시 '좋아요 많은 순' 정렬을 위해 매번 `PRODUCT_LIKES` 테이블을 동적 카운트(`LEFT JOIN` 및 `COUNT`)하여 정렬. 좋아요 동기화 시 락 제어 없음.
- **문제(또는 리스크):** 데이터와 트래픽이 늘어날수록 조인 연산으로 인한 심각한 조회 성능 저하(Read Bottleneck) 유발.
- **성공 기준(완료 정의):** 조인을 제거하여 목록 조회 성능을 최적화하면서도, 좋아요 상태 업데이트 시 쓰기 정합성을 보장.

#### 선택지와 결정
- **고려한 대안:**
    - **A (동적 카운트 유지):** 쓰기 정합성 관리가 불필요하나, 조회 성능 문제 회피 불가.
    - **B (비정규화 + 낙관적 락):** 조회 성능 향상. 단, 인기 상품에 다수의 쓰기 경합 발생 시 충돌/재시도 비용 급증.
    - **C (비정규화 + 비관적 락 단일 트랜잭션):** `PRODUCT` 테이블에 `like_count` 컬럼을 비정규화하고, 좋아요 이력 추가/삭제와 상품의 카운트 증감을 단일 트랜잭션 내에서 `SELECT FOR UPDATE` 배타 락으로 묶어 처리.
- **최종 결정:** **대안 C (비정규화 및 비관적 락 기반 단일 트랜잭션 동기화)**
- **트레이드오프:** 트랜잭션 내 `UPDATE` 및 락 경합으로 쓰기 오버헤드가 발생하지만, 매우 빈번히 호출되는 목록 조회 쿼리의 성능이 비약적으로 향상됨.
- **추후 개선 여지(있다면):** 특정 상품에 한순간 수만 건의 쓰기 요청이 몰려 DB 커넥션 풀이 고갈될 조짐이 보인다면, Redis 등 인메모리 스토어를 활용한 비동기 스케줄링(Write-Back) 아키텍처로 고도화를 검토해야 함.

### (1) 검색 조건 X (기본 최신순 정렬)

explain
SELECT p.id, p.brand_id, p.name, p.price, p.like_count, p.created_at
FROM product p
WHERE p.is_deleted = false
ORDER BY p.id DESC
LIMIT 10 OFFSET 0

1,SIMPLE,p,,index,,PRIMARY,8,,10,50,Using where; Backward index scan
execution: 6 ms, fetching: 326 ms

### (2) 브랜드 필터 사용 ( brandId = 1 , 기본 최신순 정렬)

explain
SELECT p.id, p.brand_id, p.name, p.price, p.like_count, p.created_at
FROM product p
WHERE p.brand_id = 1 AND p.is_deleted = false
ORDER BY p.id DESC
LIMIT 10 OFFSET 0

1,SIMPLE,p,,ref,idx_brand_id,idx_brand_id,8,const,178,50,Using where; Backward index scan
(execution: 4 ms, fetching: 331 ms)

### (3) 좋아요 순 정렬 사용 ( sort = "likes_desc" , 브랜드 필터 X)

explain
SELECT p.id, p.brand_id, p.name, p.price, p.like_count, p.created_at
FROM product p
WHERE p.is_deleted = false
ORDER BY p.like_count DESC, p.id DESC
LIMIT 10 OFFSET 0

1,SIMPLE,p,,ALL,,,,,99660,50,Using where; Using filesort
(execution: 43 ms, fetching: 332 ms)

### (4) 브랜드 필터 + 좋아요 순 정렬 ( brandId = 1 ,  sort = "likes_desc" )

explain
SELECT p.id, p.brand_id, p.name, p.price, p.like_count, p.created_at
FROM product p
WHERE p.brand_id = 1 AND p.is_deleted = false
ORDER BY p.like_count DESC, p.id DESC
LIMIT 10 OFFSET 0

1,SIMPLE,p,,ref,idx_brand_id,idx_brand_id,8,const,178,50,Using where; Using filesort
(execution: 5 ms, fetching: 318 ms)

인덱스 추가시

create index idx_product_like_count on product (like_count desc)
3,4번 둘다 효과 없음

CREATE INDEX idx_product_like_count_id ON product (like_count DESC, id DESC)
3번
1,SIMPLE,p,,index,,idx_product_like_count_id,12,,10,50,Using where
(execution: 3 ms, fetching: 358 ms)
4번 효과없음

CREATE INDEX idx_product_brand_like_count_id ON product (brand_id, like_count DESC, id DESC);
3번 효과없음
4번
1,SIMPLE,p,,ref,"idx_brand_id,idx_product_brand_like_count_id",idx_product_brand_like_count_id,8,const,188,50,Using where
(execution: 6 ms, fetching: 336 ms)

   사용자 접근 시나리오                  | 최적의 인덱스 구성               | 선택 이유
  ---------------------------------------|----------------------------------|----------------------------------------
   A. 특정 브랜드별 조회가 주를 이룰 때  |  idx_product_brand_like_count 만 | 사용자가 특정 브랜드를 선택하고 들어와
   (대부분의 커머스 서비스)              | 생성                             | 좋아요 순으로 보는 트래픽이
|                                  | 지배적이므로 이에 최적화합니다.
   B. 브랜드 구분 없이 홈 화면 등에서    |  idx_product_like_count 만 생성  | 전체 상품에 대한 정렬 쿼리가 많다면
   전체 상품을 좋아요 순으로 많이 볼 때  |                                  | 브랜드 필터 복합 인덱스는 과감히
|                                  | 포기하고 전체 정렬용 단일 인덱스만
|                                  | 유지합니다.

아래 케이스는 전부 복합인덱스 없는 상태에서 실험

3번쿼리변형(offset30)
1,SIMPLE,p,,ALL,,,,,99660,50,Using where; Using filesort
(execution: 46 ms, fetching: 348 ms)

3번쿼리변형(offset5000)
1,SIMPLE,p,,ALL,,,,,99660,50,Using where; Using filesort
(execution: 58 ms, fetching: 341 ms)

4번쿼리변형(offset30, 브랜드별 상품 개수를 셌을떄 가장 많은게 200~300개 정도)
1,SIMPLE,p,,ref,idx_brand_id,idx_brand_id,8,const,188,50,Using where; Using filesort
(execution: 6 ms, fetching: 335 ms)

  서비스 단계 / 트래픽 상황           | 추천 전략                            | 이유
  -------------------------------------|--------------------------------------|--------------------------------------
   현재 단계 (초기 서비스 / 데이터     | 인덱스를 추가하지 않고 유지          | 데이터가 적어 메모리 정렬만으로도
   10만 건 이하)                       |                                      | 성능이 충분합니다. 불필요한 인덱스로
|                                      | 쓰기 오버헤드를 늘리기보단 단순함을
|                                      | 유지하는 것이 낫습니다.
   성장 단계 (데이터 100만 건 이상 /   |  idx_product_brand_like_count_id     | 데이터 규모 증가로 임시 디스크
   CPU 부하 감지 시)                   | 복합 인덱스 추가                     | 정렬(Filesort)이 발생하기 시작할 때,
|                                      | 조회 성능 방어를 위해 추가를
|                                      | 검토합니다.

상품 50만건 기준
복합인덱스 x

3번쿼리(offset0)
1,SIMPLE,p,,ALL,,,,,497356,50,Using where; Using filesort
(execution: 155 ms, fetching: 347 ms)

4번쿼리(offset0)
1,SIMPLE,p,,ref,idx_brand_id,idx_brand_id,8,const,220,50,Using where; Using filesort
(execution: 6 ms, fetching: 375 ms)

3번쿼리변형(offset40000)
1,SIMPLE,p,,ALL,,,,,497356,50,Using where; Using filesort
(execution: 275 ms, fetching: 324 ms)

4번쿼리변형(offset30)
1,SIMPLE,p,,ref,idx_brand_id,idx_brand_id,8,const,220,50,Using where; Using filesort
(execution: 6 ms, fetching: 339 ms)

복합인덱스 o

3번쿼리(offset0)
1,SIMPLE,p,,ALL,,,,,497356,50,Using where; Using filesort
(execution: 157 ms, fetching: 331 ms)

4번쿼리(offset0)
1,SIMPLE,p,,ref,"idx_brand_id,idx_product_brand_like_count_id",idx_product_brand_like_count_id,8,const,220,50,Using where
(execution: 6 ms, fetching: 340 ms)

3번쿼리변형(offset40000)
1,SIMPLE,p,,ALL,,,,,497356,50,Using where; Using filesort
(execution: 251 ms, fetching: 334 ms)

4번쿼리변형(offset30)
1,SIMPLE,p,,ref,"idx_brand_id,idx_product_brand_like_count_id",idx_product_brand_like_count_id,8,const,220,50,Using where
(execution: 5 ms, fetching: 330 ms)


실험 결과를 분석해 보면 데이터 건수 증가나 복합 인덱스 추가 여부와 상관없이 성능 차이가 거의 나지 않는 명확한
이유들이 존재합니다.
크게 3가지 관점에서 원인을 설명해 드릴 수 있습니다.
──────
### 1. 필터링된 데이터(모수)가 너무 적어  Using filesort  비용이 0에 가까움 (4번 쿼리)

가장 결정적인 이유는  brand_id 로 필터링된 상품의 개수(rows)가 너무 적기 때문입니다.
• 복합 인덱스 X (4번):  idx_brand_id 를 타서  type: ref 로 220건의 데이터를 찾은 뒤, 메모리에서 정렬( Using
filesort )을 수행합니다.
• 복합 인덱스 O (4번):  idx_product_brand_like_count_id 를 타서 필터링과 동시에 인덱스를 통해 정렬( Using filesort
사라짐)을 마칩니다.

복합 인덱스를 추가하여  Using filesort 를 없애는 데는 성공했지만, MySQL이 고작 200여 건의 데이터를 메모리에서
정렬하는 데 걸리는 시간은 1ms(밀리초)도 안 될 정도로 찰나의 순간입니다.
정렬해야 할 모수가 수만~수십만 건 단위라면 복합 인덱스가 엄청난 성능 향상을 가져오지만, 200건 정도라면 메모리
정렬(filesort)을 하나 인덱스로 정렬하나 실행 시간( execution )은 똑같이 5~6ms로 차이가 없습니다.

### 2. 3번 쿼리는 복합 인덱스를 아예 타지 못함 (Leftmost Prefix Rule)
3번 쿼리의 실행 계획을 보면  type: ALL  즉, 풀 테이블 스캔을 하고 있습니다.
• 생성하신 복합 인덱스의 순서는  (brand_id, like_count DESC, id DESC)  입니다.
• B-Tree 인덱스의 특성상 선두 컬럼인  brand_id  조건이 쿼리의  WHERE  절에 없으면 이 복합 인덱스를 활용할 수
없습니다.
• 3번 쿼리는 전체 상품 조회(혹은  brand_id  외의 조건 조회)인 것으로 추정되며, 선두 컬럼 조건이 누락되었기 때문에
복합 인덱스 존재 여부와 무관하게 계속 풀 스캔( ALL )과 파일 정렬( Using filesort )을 수행하게 되어 성능이 동일하게
나타나는 것입니다.

### 3. 실제 병목은 DB 실행(Execution)이 아닌 네트워크(Fetching)에 있음

로그를 자세히 보시면 두 쿼리 모두 실행 시간보다 Fetching 시간이 압도적으로 깁니다.

•  execution : 5 ~ 6 ms (DB가 데이터를 찾고 정렬하는 시간)
•  fetching : 330 ~ 370 ms (DB가 클라이언트 프로그램, 즉 DataGrip이나 DBeaver 등으로 데이터를 전송하고 화면에
그리는 시간)

DB 엔진 자체는 5ms 만에 일을 끝냈는데, 데이터를 가져오는 데 300ms 이상이 소요되고 있습니다. 복합 인덱스로 쿼리 실행
시간을 6ms에서 5ms로 1ms 단축시켰다 하더라도, Fetching 시간이 330ms나 걸리기 때문에 체감되는 성능이나 전체 측정
시간에서는 차이가 없게 느껴지는 것입니다.
──────
### 💡 결론 및 개선 방향

1. 복합 인덱스의 진가는 데이터가 한쪽에 쏠려있을 때 나타납니다. 만약 단일  brand_id 에 속한 상품이 200건이 아니라
   10만 건이라면, 인덱스가 없을 때의 10만 건 메모리 정렬( filesort ) 비용이 기하급수적으로 커져서 복합 인덱스 유무에
   따른 속도 차이가 극명하게 발생합니다.
2. 현재처럼 브랜드 당 상품 수가 수백 건 수준으로 잘 분산되어 있다면, 굳이 무거운 복합 인덱스 대신 단일 인덱스(
   idx_brand_id )만 유지하는 것이 쓰기/수정(INSERT/UPDATE) 성능 측면에서 오히려 유리할 수 있습니다. (역정규화된
   like_count 가 자주 UPDATE 될 것이므로 인덱스 오버헤드가 클 수 있습니다.)
3. 3번 쿼리(브랜드 조건이 없는 전체 조회 등) 성능을 개선해야 한다면,  (like_count DESC, id DESC)  로만 이루어진
   별도의 인덱스가 필요합니다.

k6 vu=50, 지속시간 30초, p95 측정
### 📊 인덱스 추가 전/후 p95 비교 결과

   테스트 케이스                 | 인덱스 추가 전 p(95) | 인덱스 추가 후 p(95) | 비교 결과
  -------------------------------|----------------------|----------------------|-------------------------------------
   Case 1 (기본 조회)            | 288.51 ms            | 217.25 ms            | 🚀 약 71ms 단축
   Case 2 (전체 좋아요 정렬)     | 482.53 ms            | 205.97 ms            | 🔥 약 276ms 대폭 단축 (2.3배 속도
|                      |                      | 향상!)
   Case 3 (특정 브랜드 필터)     | 106.43 ms            | 107.95 ms            | ➖ 거의 동일 (오차 범위)
   Case 4 (브랜드 + 좋아요 정렬) | 104.77 ms            | 85.16 ms             | 🚀 약 19ms 단축
──────
### 💡 결과 분석 코멘트

• 놀라운 Case 2 성능 향상: 인덱스를 추가하기 전에는 전체 상품을 읽고 메모리에서 정렬하느라 부하가 걸려  482ms 나
걸렸지만, 이제 정렬된 인덱스를 바로 읽어오면서 ** 206ms **로 응답 속도가 무려 2배 이상 빨라졌습니다!
• Case 4 미세 향상: 브랜드와 좋아요 정렬 조건이 합쳐진 Case 4 역시 이전( 104ms )보다 살짝 더 빨라진  85ms 를
기록하며 인덱스의 효과를 톡톡히 보고 있습니다.


---

캐시

### 1. 상품 상세 API ( getProduct )

상품 상세 데이터는 읽기 비율이 압도적으로 높고, 변경 빈도가 낮습니다. 다만 수정 시 즉시 반영되어야 하는 관리적
요구사항이 있을 수 있습니다.
• 캐시 읽기 전략: write through
• 조회 시 Redis에 데이터가 있으면 반환하고, 없으면 DB에서 조회한 후 캐시에 적재합니다. 스프링의  @Cacheable
이 기본적으로 제공하는 동작입니다.
• 캐시 무효화 전략: 수동 무효화 (@CacheEvict)
•  ProductAdminFacade 의  updateProduct ,  deleteProduct  메서드가 호출될 때 해당 상품의 캐시를
삭제(@CacheEvict)합니다.
• 재고 차감이나 좋아요 증가 시에도 무효화를 할 수 있지만, 이 경우 캐시 효율이 급격히 떨어질 수 있습니다.
(잦은 삭제/재생성 발생). 이 경우 ttl 만료에 의존. 따라서 핵심 상품 정보 변경(Admin) 시에만 즉각 무효화합니다.
• 캐시 키 설계:  product:detail::{id}  (예:  product:detail::123 )
• TTL 설정: 10분
• 상품 기본 정보(이름, 가격 등)는 잘 변하지 않으므로 긴 TTL을 가져가되, 조회 수/좋아요 수 등의 '약간의
지연'은 허용(Eventual Consistency)하는 정책을 취합니다. 만약 중요 정보가 바뀌면 Admin API에서 수동 무효화가
이루어지므로 안전합니다.
- 메모리 초과 시 관리 전략: volatile-lru, ttl이 명시된 삭제가 용이한 데이터를 삭제


### 2. 상품 목록 API ( getProducts )

목록 데이터는 조건( brandId ,  sort ,  page ,  size )이 매우 다양하여 경우의 수가 많습니다. 또한 특정 상품
하나가 변경되었다고 관련된 모든 페이징 캐시를 지우는 것은 성능상 큰 문제가 됩니다.

• 캐시 읽기 전략: write throuh
• 자주 조회되는 1~2페이지나 특정 브랜드 목록의 응답 속도를 극대화합니다.
• 캐시 쓰기 및 무효화 전략: 적용하지 않음 (TTL에 전적으로 의존)
• 상품 하나가 등록/수정/삭제되었다고 해서 관련될지도 모르는 수많은 목록 조건(페이지, 정렬 등) 캐시를 모두
찾아 지울 수 없기 때문에 무효화(Evict) 전략은 사용하지 않습니다.
• 캐시 키 설계:  product:list::{brandId}:{sort}:{pageNumber}:{pageSize}
• (예:  product:list::1:PRICE_DESC:0:20 )
• 파라미터 조합을 고유 키로 활용하여 서로 다른 요청 결과를 독립적으로 적재합니다.
• TTL 설정: 5분
• 캐시 무효화를 하지 않는 대신 TTL을 5분 이내로 짧게 가져갑니다. 신규 상품이 등록되거나 가격이 바뀌더라도
최대 5분 뒤에는 목록에 자동으로 반영되게 함으로써 정합성을 맞춥니다.
- 메모리 초과 관리 전략: volatile-lru, ttl이 명시된 삭제가 용이한 데이터를 삭제


### 3. 장애(캐시 미스/Redis Down) 상황 정상 동작 처리

Spring의  @Cacheable 은 기본적으로 Redis 연결 실패나 타임아웃 발생 시 Exception을 던져 비즈니스 로직(조회)
자체가 실패로 끝납니다. 캐시가 다운되어도 서비스는 살아있어야 합니다.

• 해결책
- Redis 조회 시 에러가 발생하면 로그만 남기고( log.error() ), 에러를 삼킵니다(swallow).
• 프레임워크가 에러를 무시하면 자연스럽게 원본 메서드(DB 직접 조회 로직)를 실행하므로, Redis 장애 시에도
데이터베이스만 버텨준다면 서비스는 정상 동작하게 됩니다.

──────
요약하자면:

1. 상세 조회: 30분 TTL + Admin 갱신 시  수동 무효화
2. 목록 조회: 5분 TTL + 다양한 조건의 캐시 키 구성 (무효화 없음)
3. 가용성: 커스텀  CacheErrorHandler  적용으로 Redis 장애 시 RDB Fallback 처리

상품 상세 정보에 **"재고 수량"**이 포함되어 있다면, 이 문제는 이커머스 백엔드 설계에서 가장 까다로우면서도
흔하게 직면하는 정합성(Consistency) vs 성능(Performance)의 트레이드오프 문제입니다.

결론부터 말씀드리면, 재고가 차감될 때마다 캐시를 무효화하는 것은 추천하지 않습니다.

그 이유와 실무에서 해결하는 방식은 다음과 같습니다.

### 🚨 재고 차감 시 캐시 무효화를 할 경우 발생하는 문제 (Cache Hit Ratio 저하)

인기 있는 상품(예: 한정판 특가 상품)은 1초에도 수십, 수백 건의 재고 차감이 일어납니다.
만약 재고가 1 깎일 때마다 상품 상세 캐시를 날려버리면 다음과 같은 악순환이 발생합니다.

1. A 유저 구매 -> 캐시 삭제
2. B 유저가 상품 조회 -> 캐시 미스 (DB 조회 발생) -> 캐시 새로 생성
3. C 유저 구매 -> 또 캐시 삭제
4. D, E, F 유저가 상품 조회 -> 모두 캐시 미스 (DB 부하 폭증)

결과적으로 엄청난 트래픽이 몰릴 때 캐시가 무용지물이 되며, 한꺼번에 DB로 조회가 몰리는 Cache Stampede(캐시
스탬피드) 현상으로 인해 시스템이 다운될 수 있습니다.
──────
### 💡 실무에서의 해결 전략 (권장)

이커머스 시스템에서는 이 문제를 해결하기 위해 보통 다음 3가지 방법 중 하나를 선택합니다.

1. 재고 데이터만 캐시에서 분리 (가장 정석적인 방법)

• 상품명, 가격, 이미지, 설명 등 안 변하는 정보는 30분 TTL로 강력하게 캐싱합니다.
• 재고 정보만 캐싱하지 않거나 5초 이내의 매우 짧은 TTL을 가져갑니다. 프론트엔드에서는 상품 상세 정보를 먼저
캐시로 빠르게 렌더링하고, 재고 수량만 별도의 가벼운 API(예:  /products/123/stock )로 호출하여 채워 넣습니다.

2. Eventual Consistency 인정 (재고는 참고용으로만 사용)

• 상품 상세 캐시를 무효화하지 않고 냅둡니다.
• 사용자는 화면에서 "재고 5개 남음"을 보게 되지만 실제로는 0개일 수 있습니다.
• 사용자가 "구매하기"를 누르는 주문/결제 시점에 비관적 락(Pessimistic Lock)이나 Redis 분산 락을 통해 DB의 진짜
재고를 확인합니다. 이미 품절되었다면 그 시점에 "재고가 부족하여 구매할 수 없습니다"라는 에러를 반환합니다.
(현재 작성해주신 코드  decreaseStocksWithLock  메서드가 이미 이 역할을 훌륭히 수행하고 있습니다.)

3. "품절(Out of Stock)" 상태일 때만 제한적으로 캐시 무효화

• 재고가 100개 -> 99개로 깎일 때는 캐시를 무효화하지 않습니다.
• 재고가 차감되다가 **0개가 되는 순간(품절 상태 진입)**에만  @CacheEvict 를 호출하여 캐시를 날립니다.
• 이렇게 하면 DB 부하를 막으면서도 유저에게 품절 사실은 즉각적으로 보여줄 수 있습니다.

결론적으로, 현재 시스템 구조를 크게 안 바꾸신다면 **2번 전략(결제 시 락으로 보장하고 조회 캐시는 냅두기)**이나
**3번 전략(품절 시에만 무효화)**을 적용하시는 것이 트래픽을 견디는 데 훨씬 유리합니다.