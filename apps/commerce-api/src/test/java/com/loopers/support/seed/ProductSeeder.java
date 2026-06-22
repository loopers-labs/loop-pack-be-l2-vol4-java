package com.loopers.support.seed;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 읽기 성능 벤치용 대량 시드 유틸. product 와 product_like_count 를 함께 채운다.
 * like_count 는 멱법칙 비슷한 편향 분포(소수 핫상품)로 생성한다.
 * 의사난수는 고정 시드의 LCG 라 테스트 재현성이 보장된다.
 * (테스트 소스셋은 Lombok 미처리라 생성자를 명시한다.)
 */
@Component
public class ProductSeeder {

    private final JdbcTemplate jdbc;
    private static final int BATCH = 1_000;

    public ProductSeeder(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void seed(int productCount, int brandCount) {
        long seedState = 0x9E3779B97F4A7C15L;
        List<Object[]> products = new ArrayList<>(BATCH);
        List<Object[]> counts = new ArrayList<>(BATCH);
        for (int i = 1; i <= productCount; i++) {
            seedState = seedState * 6364136223846793005L + 1442695040888963407L;
            long r = seedState >>> 33;
            long brandId = (r % brandCount) + 1;
            long price = 1_000 + (r % 1_000) * 100;
            long likeCount = skewed(r);

            products.add(new Object[]{(long) i, brandId, "product-" + i, "desc-" + i, price, 100});
            counts.add(new Object[]{(long) i, likeCount});
            if (products.size() == BATCH) {
                flush(products, counts);
                products.clear();
                counts.clear();
            }
        }
        if (!products.isEmpty()) {
            flush(products, counts);
        }
    }

    /** 90% 콜드(0~9), 9% 중간(10~99), 1% 핫(1000~9999). */
    private long skewed(long r) {
        long bucket = r % 100;
        if (bucket < 90) return r % 10;
        if (bucket < 99) return 10 + (r % 90);
        return 1_000 + (r % 9_000);
    }

    private void flush(List<Object[]> products, List<Object[]> counts) {
        jdbc.batchUpdate(
            "INSERT INTO product (id, brand_id, name, description, price, stock, created_at, updated_at) " +
            "VALUES (?, ?, ?, ?, ?, ?, NOW(), NOW())", products);
        jdbc.batchUpdate(
            "INSERT INTO product_like_count (product_id, like_count, created_at, updated_at) " +
            "VALUES (?, ?, NOW(), NOW())", counts);
    }
}
