package com.loopers.application.product;

/**
 * 상품 상세 — Product + Brand 조합 결과 (Aggregate 간 ID 참조를 Facade에서 조립).
 * 재고는 수치를 노출하지 않고 있음/없음만 제공한다(UC-04 정책). 좋아요 수(likesCount)는 read model
 * (product_metrics)에서 조합된 값이다. liked는 식별된 User의 좋아요 여부.
 * 인스턴스는 캐시 표현(CachedProductDetail)의 toInfo 에서 조립한다.
 *
 * <p><b>rank / rankYesterday</b>: 각각 오늘·어제(KST) 일간 랭킹 순위(1-based, 랭킹에 없으면 null).
 * 둘 다 캐시 밖에서 덧붙인다 — rank 는 매 조회 바뀌는 실시간 값이고, rankYesterday 는 값 자체는 고정이지만
 * 캐시 키에 일자가 없어 자정을 넘기면 stale 이 된다(같은 이유로 캐시에 넣지 않는다).
 *
 * <p>두 값을 함께 주는 이유는 <b>추세</b>다. 상승/하락/신규진입/이탈은 두 순위의 조합으로만 판별되고,
 * 어느 한쪽이 null 인 경우가 의미를 갖는다(아래). 그래서 서버가 델타 하나로 압축하지 않고 원본 둘을 준다.
 * <ul>
 *   <li>rank=1, rankYesterday=3 → 2계단 상승</li>
 *   <li>rank=1, rankYesterday=null → 오늘 신규 진입</li>
 *   <li>rank=null, rankYesterday=1 → 오늘 이탈(어제 1위였으나 오늘 활동 없음)</li>
 *   <li>둘 다 null → 이틀간 랭킹 무관</li>
 * </ul>
 */
public record ProductDetailInfo(
    Long id,
    String name,
    String description,
    String imageUrl,
    Long price,
    boolean inStock,
    Long likesCount,
    Long brandId,
    String brandName,
    boolean liked,
    Long rank,
    Long rankYesterday
) {
}
