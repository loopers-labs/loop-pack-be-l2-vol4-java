package com.loopers.support.dataloader;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

@Slf4j
@Component
@Profile("local")
@RequiredArgsConstructor
public class ProductDataLoader implements ApplicationRunner {

    private static final int BRAND_COUNT = 50;
    private static final int PRODUCT_COUNT = 100_000;
    private static final int BATCH_SIZE = 2_000;

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) {
        insertBrands();
        insertProducts();
    }

    private void insertBrands() {
        log.info("[DataLoader] 브랜드 {}개 삽입 시작", BRAND_COUNT);
        String sql = "INSERT INTO brand (name, description, created_at, updated_at) VALUES (?, ?, NOW(), NOW())";

        List<Object[]> params = new ArrayList<>();
        for (int i = 1; i <= BRAND_COUNT; i++) {
            params.add(new Object[]{"브랜드_" + i, "브랜드_" + i + " 공식 브랜드"});
        }
        jdbcTemplate.batchUpdate(sql, params);
        log.info("[DataLoader] 브랜드 {}개 삽입 완료", BRAND_COUNT);
    }

    private static final double DELETED_RATIO = 0.3;

    private void insertProducts() {
        log.info("[DataLoader] 상품 {}개 삽입 시작 (삭제 비율 {}%)", PRODUCT_COUNT, (int) (DELETED_RATIO * 100));
        String sql = "INSERT INTO product (brand_id, name, price, like_count, created_at, updated_at, deleted_at) VALUES (?, ?, ?, ?, NOW(), NOW(), ?)";

        ThreadLocalRandom random = ThreadLocalRandom.current();
        List<Object[]> batch = new ArrayList<>(BATCH_SIZE);

        for (int i = 1; i <= PRODUCT_COUNT; i++) {
            long brandId = random.nextLong(1, BRAND_COUNT + 1);
            String name = "상품_" + i;
            BigDecimal price = BigDecimal.valueOf(random.nextInt(1, 10_001) * 100L); // 100 ~ 1,000,000
            long likeCount = random.nextLong(0, 10_001); // 0 ~ 10,000
            Object deletedAt = random.nextDouble() < DELETED_RATIO ? "2024-01-01 00:00:00" : null;

            batch.add(new Object[]{brandId, name, price, likeCount, deletedAt});

            if (batch.size() == BATCH_SIZE) {
                jdbcTemplate.batchUpdate(sql, batch);
                batch.clear();
                log.info("[DataLoader] 상품 {}개 삽입 중...", i);
            }
        }

        if (!batch.isEmpty()) {
            jdbcTemplate.batchUpdate(sql, batch);
        }

        log.info("[DataLoader] 상품 {}개 삽입 완료", PRODUCT_COUNT);
    }
}