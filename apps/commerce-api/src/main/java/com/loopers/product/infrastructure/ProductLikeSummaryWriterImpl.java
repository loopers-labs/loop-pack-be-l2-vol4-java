package com.loopers.product.infrastructure;

import com.loopers.product.application.ProductLikeSummaryWriter;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
public class ProductLikeSummaryWriterImpl implements ProductLikeSummaryWriter {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void initialize(Long productId, Long brandId) {
        jdbcTemplate.update("""
                insert into product_like_summary(product_id, brand_id, like_count)
                values (?, ?, 0)
                on duplicate key update product_id = product_id
                """,
            productId,
            brandId);
    }
}
