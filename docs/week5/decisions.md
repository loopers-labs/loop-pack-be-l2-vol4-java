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

