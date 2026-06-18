package com.loopers.domain.product;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 테스트에서 product.like_count 컬럼을 직접 세팅한다.
 * <p>운영에선 좋아요 수 증감이 Redis로 흡수되고 배치가 컬럼에 반영하므로, 정렬·표시 테스트는
 * 그 경로를 우회해 컬럼을 네이티브 UPDATE로 시드한다. product 스키마를 알기에 product 도메인 테스트에 둔다.</p>
 */
@Component
public class LikeCountSeeder {

    private final JdbcTemplate jdbcTemplate;

    public LikeCountSeeder(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void seed(Long productId, long likeCount) {
        jdbcTemplate.update("UPDATE product SET like_count = ? WHERE id = ?", likeCount, productId);
    }
}
