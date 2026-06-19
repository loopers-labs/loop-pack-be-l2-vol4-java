package com.loopers.application.productrank;

import com.loopers.domain.productrank.ProductRankRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 읽기모델 product_rank 를 주기적으로 재집계한다.
 * 테스트 프로필에선 등록하지 않아(@Profile("!test")) 통합테스트의 product_rank 를 건드리지 않는다.
 */
@Profile("!test")
@RequiredArgsConstructor
@Component
public class ProductRankScheduler {

    private final ProductRankRepository productRankRepository;

    @Scheduled(fixedDelayString = "${product-rank.refresh-interval-ms:30000}")
    public void tick() {
        productRankRepository.rebuildFromSource();
    }
}
