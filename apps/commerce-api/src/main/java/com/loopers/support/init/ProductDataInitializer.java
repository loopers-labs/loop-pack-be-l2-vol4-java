package com.loopers.support.init;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

@Slf4j
@Profile("local")
@Component
@RequiredArgsConstructor
public class ProductDataInitializer implements CommandLineRunner {

    private final JdbcTemplate jdbcTemplate;

    private static final int BRAND_COUNT = 20;
    private static final int PRODUCT_COUNT = 100_000;
    private static final int BATCH_SIZE = 1_000;

    @Override
    public void run(String... args) {
        if (hasProductData()) {
            log.info("[DataInitializer] 이미 상품 데이터가 존재합니다. 초기화를 건너뜁니다.");
            return;
        }

        log.info("[DataInitializer] 테스트 데이터 생성 시작");
        long start = System.currentTimeMillis();

        insertBrands();
        List<Long> brandIds = fetchBrandIds();
        insertProducts(brandIds);
        List<Long> productIds = fetchProductIds();
        insertStocks(productIds);

        long elapsed = System.currentTimeMillis() - start;
        log.info("[DataInitializer] 완료 — 브랜드 {}개, 상품 {}개, 재고 {}개 생성 ({}ms)", BRAND_COUNT, PRODUCT_COUNT, productIds.size(), elapsed);
    }

    private boolean hasProductData() {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM products", Integer.class);
        return count != null && count > 0;
    }

    private void insertBrands() {
        String sql = "INSERT INTO brands (name, description, created_at, updated_at) VALUES (?, ?, NOW(), NOW())";
        List<Object[]> params = new ArrayList<>();
        for (int i = 1; i <= BRAND_COUNT; i++) {
            params.add(new Object[]{
                String.format("브랜드-%02d", i),
                String.format("브랜드-%02d 설명", i)
            });
        }
        jdbcTemplate.batchUpdate(sql, params);
        log.info("[DataInitializer] 브랜드 {}개 생성 완료", BRAND_COUNT);
    }

    private List<Long> fetchBrandIds() {
        return jdbcTemplate.queryForList("SELECT id FROM brands WHERE deleted_at IS NULL", Long.class);
    }

    private void insertProducts(List<Long> brandIds) {
        String sql = """
            INSERT INTO products (name, description, price, brand_id, like_count, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            """;

        Random random = new Random();
        ZonedDateTime twoYearsAgo = ZonedDateTime.now().minusYears(2);
        long secondsRange = ChronoUnit.SECONDS.between(twoYearsAgo, ZonedDateTime.now());

        List<Object[]> batch = new ArrayList<>(BATCH_SIZE);
        for (int i = 1; i <= PRODUCT_COUNT; i++) {
            long brandId = brandIds.get(random.nextInt(brandIds.size()));
            long price = (random.nextInt(1000) + 1) * 1000L; // 1,000 ~ 1,000,000원
            // 제곱 분포: 대부분 낮은 좋아요 수, 소수만 높은 값 (실제 트래픽과 유사)
            long likeCount = (long) (Math.pow(random.nextDouble(), 2) * 10_000);
            Timestamp createdAt = Timestamp.from(
                twoYearsAgo.toInstant().plusSeconds((long) (random.nextDouble() * secondsRange))
            );

            batch.add(new Object[]{
                String.format("상품-%06d", i),
                "상품 설명 " + i,
                price,
                brandId,
                likeCount,
                createdAt,
                createdAt
            });

            if (batch.size() == BATCH_SIZE) {
                jdbcTemplate.batchUpdate(sql, batch);
                batch.clear();
            }
        }
        if (!batch.isEmpty()) {
            jdbcTemplate.batchUpdate(sql, batch);
        }
        log.info("[DataInitializer] 상품 {}개 생성 완료", PRODUCT_COUNT);
    }

    private List<Long> fetchProductIds() {
        return jdbcTemplate.queryForList("SELECT id FROM products WHERE deleted_at IS NULL", Long.class);
    }

    private void insertStocks(List<Long> productIds) {
        String sql = """
            INSERT INTO stocks (product_id, total_stock, reserved_stock, created_at, updated_at)
            VALUES (?, ?, 0, NOW(), NOW())
            """;

        Random random = new Random();
        List<Object[]> batch = new ArrayList<>(BATCH_SIZE);
        for (Long productId : productIds) {
            int totalStock = random.nextInt(501); // 0 ~ 500개
            batch.add(new Object[]{ productId, totalStock });

            if (batch.size() == BATCH_SIZE) {
                jdbcTemplate.batchUpdate(sql, batch);
                batch.clear();
            }
        }
        if (!batch.isEmpty()) {
            jdbcTemplate.batchUpdate(sql, batch);
        }
        log.info("[DataInitializer] 재고 {}개 생성 완료", productIds.size());
    }
}
