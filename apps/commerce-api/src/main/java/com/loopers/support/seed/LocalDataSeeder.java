package com.loopers.support.seed;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 성능 측정용 대량 데이터 시더 (local 전용).
 *
 * <p>분포 설계:
 *
 * <ul>
 *   <li>브랜드 200개 — 상품이 일부 브랜드에 몰리도록 멱분포로 배정한다.
 *   <li>상품 100,000건 — like_count 는 소수 상품에 좋아요가 집중되는 멱분포(Zipf 유사)로 생성한다.
 *   <li>상품의 1.5% 는 soft delete(deleted_at) 처리해 deleted_at IS NULL 인덱스 조건을 검증한다.
 *   <li>product_like 는 표본 5,000건만 생성한다. like_count 와 의도적으로 정합시키지 않는다 (완전 정합에는 수천만 행이
 *       필요). 이 불일치는 성능 측정 목적의 의도된 것이다.
 * </ul>
 *
 * <p>고정 시드(Random(42))로 재실행 시 동일 분포를 보장한다.
 */
@Slf4j
@RequiredArgsConstructor
@Component
@Profile("local")
@ConditionalOnProperty(name = "loopers.seed.enabled", havingValue = "true")
public class LocalDataSeeder implements CommandLineRunner {

    private static final int BRAND_COUNT = 200;
    private static final int PRODUCT_COUNT = 100_000;
    private static final int PRODUCT_LIKE_SAMPLE_COUNT = 5_000;
    private static final int BATCH_SIZE = 1_000;
    private static final long MAX_LIKE_COUNT = 1_000_000L;
    private static final double SOFT_DELETE_RATIO = 0.015;

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(String... args) {
        Long productCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM product", Long.class);
        if (productCount != null && productCount > 0) {
            log.info("[seed] product 테이블에 이미 {}건이 존재하여 시딩을 건너뜁니다.", productCount);
            return;
        }

        Random random = new Random(42);
        long start = System.currentTimeMillis();

        seedBrands(random);
        seedProducts(random);
        seedProductLikes(random);

        log.info("[seed] 시딩 완료 — brand {}건, product {}건, product_like {}건 ({}ms)",
            BRAND_COUNT, PRODUCT_COUNT, PRODUCT_LIKE_SAMPLE_COUNT, System.currentTimeMillis() - start);
    }

    private void seedBrands(Random random) {
        String sql =
            "INSERT INTO brand (name, description, created_at, updated_at, deleted_at)"
                + " VALUES (?, ?, ?, ?, NULL)";
        List<Object[]> rows = new ArrayList<>();
        for (int i = 1; i <= BRAND_COUNT; i++) {
            Timestamp createdAt = randomPastTimestamp(random);
            rows.add(new Object[] {"브랜드-" + i, "브랜드 " + i + " 설명", createdAt, createdAt});
        }
        jdbcTemplate.batchUpdate(sql, rows);
        log.info("[seed] brand {}건 시딩 완료", BRAND_COUNT);
    }

    private void seedProducts(Random random) {
        String sql =
            "INSERT INTO product (name, description, price, stock, brand_id, like_count,"
                + " created_at, updated_at, deleted_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
        List<Object[]> rows = new ArrayList<>(BATCH_SIZE);
        for (int i = 1; i <= PRODUCT_COUNT; i++) {
            long brandId = skewedBrandId(random);
            long likeCount = skewedLikeCount(random);
            long price = (1 + random.nextInt(10_000)) * 100L;
            int stock = random.nextInt(1_001);
            Timestamp createdAt = randomPastTimestamp(random);
            Timestamp deletedAt = random.nextDouble() < SOFT_DELETE_RATIO ? createdAt : null;

            rows.add(new Object[] {
                "상품-" + i, "상품 " + i + " 설명", price, stock, brandId, likeCount,
                createdAt, createdAt, deletedAt
            });

            if (rows.size() == BATCH_SIZE) {
                jdbcTemplate.batchUpdate(sql, rows);
                rows.clear();
                if (i % 10_000 == 0) {
                    log.info("[seed] product {} / {} 건 진행", i, PRODUCT_COUNT);
                }
            }
        }
        if (!rows.isEmpty()) {
            jdbcTemplate.batchUpdate(sql, rows);
        }
        log.info("[seed] product {}건 시딩 완료", PRODUCT_COUNT);
    }

    private void seedProductLikes(Random random) {
        String sql =
            "INSERT INTO product_like (user_id, product_id, created_at, updated_at, deleted_at)"
                + " VALUES (?, ?, ?, ?, NULL)";
        List<Object[]> rows = new ArrayList<>(BATCH_SIZE);
        for (int i = 1; i <= PRODUCT_LIKE_SAMPLE_COUNT; i++) {
            // 사용자당 한 건만 생성하므로 (user_id, product_id) 유니크 제약과 충돌하지 않는다.
            long productId = 1 + random.nextLong(PRODUCT_COUNT);
            Timestamp createdAt = randomPastTimestamp(random);
            rows.add(new Object[] {"seed-user-" + i, productId, createdAt, createdAt});

            if (rows.size() == BATCH_SIZE) {
                jdbcTemplate.batchUpdate(sql, rows);
                rows.clear();
            }
        }
        if (!rows.isEmpty()) {
            jdbcTemplate.batchUpdate(sql, rows);
        }
        log.info("[seed] product_like {}건 시딩 완료", PRODUCT_LIKE_SAMPLE_COUNT);
    }

    /** 일부 브랜드에 상품이 몰리도록 제곱 분포로 브랜드를 배정한다. */
    private long skewedBrandId(Random random) {
        double r = random.nextDouble();
        return 1 + (long) (r * r * (BRAND_COUNT - 1));
    }

    /** 소수 상품에 좋아요가 집중되도록 멱분포(Zipf 유사)로 like_count 를 생성한다. 대부분 0~수십, 극소수만 수십만. */
    private long skewedLikeCount(Random random) {
        double r = random.nextDouble();
        return (long) (Math.pow(r, 30) * MAX_LIKE_COUNT);
    }

    private Timestamp randomPastTimestamp(Random random) {
        return Timestamp.from(
            ZonedDateTime.now().minusMinutes(random.nextInt(365 * 24 * 60)).toInstant());
    }
}
