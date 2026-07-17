package com.loopers.product.application;

import com.loopers.ranking.domain.RankingRepository;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.OptionalLong;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@RequiredArgsConstructor
@Service
public class ProductRankingFacade {

    private static final ZoneId SEOUL_ZONE = ZoneId.of("Asia/Seoul");

    private final ProductFacade productFacade;
    private final RankingRepository rankingRepository;

    public RankedProductDetailInfo getProductDetail(Long productId) {
        ProductDetailInfo product = productFacade.getProductDetail(productId);
        return new RankedProductDetailInfo(product, findTodayRank(productId));
    }

    private Long findTodayRank(Long productId) {
        try {
            OptionalLong rank = rankingRepository.findRank(LocalDate.now(SEOUL_ZONE), productId);
            return rank.isPresent() ? rank.getAsLong() : null;
        } catch (RuntimeException e) {
            log.warn(
                    "[ranking] 상품 상세 랭킹 조회에 실패해 null로 처리합니다. productId={}",
                    productId,
                    e);
            return null;
        }
    }
}
