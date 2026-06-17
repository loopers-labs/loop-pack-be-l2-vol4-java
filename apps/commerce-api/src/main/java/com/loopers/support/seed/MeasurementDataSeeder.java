package com.loopers.support.seed;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 읽기 성능 측정용 대용량 데이터 적재기.
 * `seed` 프로파일에서만 활성화되며, 1회 실행으로 측정 기준 데이터를 채운 뒤 종료한다.
 * 고정 시드를 사용해 재실행해도 동일한 분포가 재현된다. (스키마는 ddl-auto:create 가 생성)
 */
@Component
@Profile("seed")
public class MeasurementDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(MeasurementDataSeeder.class);

    private static final int BRAND_COUNT = 1_000;
    private static final int USER_COUNT = 5_000;
    private static final int PRODUCT_COUNT = 100_000;

    private static final double BRAND_SIZE_SIGMA = 0.8;
    private static final int MIN_LIKES_PER_PRODUCT = 10;
    private static final double LIKE_PARETO_ALPHA = 1.5;
    private static final int MAX_LIKES_PER_PRODUCT = USER_COUNT;

    private static final long BRAND_SEED = 11L;
    private static final long PRODUCT_SEED = 22L;
    private static final long LIKE_SEED = 33L;

    private static final int CHUNK = 10_000;
    private static final String DUMMY_PASSWORD = "$2a$10$0123456789012345678901uVwXyZabcdeFGHIJKLMNOPQRSTUv";

    private final JdbcTemplate jdbcTemplate;

    public MeasurementDataSeeder(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        Integer existing = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM products", Integer.class);
        if (existing != null && existing > 0) {
            log.info("[seed] products 가 이미 {}건 존재하여 적재를 건너뜁니다.", existing);
            return;
        }

        long startedAt = System.currentTimeMillis();
        Timestamp now = Timestamp.from(Instant.now());

        seedBrands(now);
        seedUsers(now);
        int[] brandIdByProduct = seedProducts();
        long totalLikes = seedLikes();

        log.info("[seed] 완료 — brands={} users={} products={} likes={} ({}초)",
            BRAND_COUNT, USER_COUNT, PRODUCT_COUNT, totalLikes, (System.currentTimeMillis() - startedAt) / 1000);
        log.info("[seed] 분포 sanity 는 reports/00-setup.md 의 쿼리로 검증하세요. (brandIdByProduct[0]={})", brandIdByProduct[0]);
    }

    private void seedBrands(Timestamp now) {
        String sql = "INSERT INTO brands (name, description, created_at, updated_at) VALUES (?, ?, ?, ?)";
        List<Object[]> batch = new ArrayList<>(BRAND_COUNT);
        for (int i = 1; i <= BRAND_COUNT; i++) {
            batch.add(new Object[] {"브랜드" + i, "측정용 브랜드 " + i, now, now});
        }
        jdbcTemplate.batchUpdate(sql, batch);
        log.info("[seed] brands {}건 적재", BRAND_COUNT);
    }

    private void seedUsers(Timestamp now) {
        String sql = "INSERT INTO users (login_id, encrypted_password, name, birth_date, email, created_at, updated_at) "
            + "VALUES (?, ?, ?, ?, ?, ?, ?)";
        List<Object[]> batch = new ArrayList<>(CHUNK);
        for (int i = 1; i <= USER_COUNT; i++) {
            batch.add(new Object[] {
                "user" + i, DUMMY_PASSWORD, "유저", "1990-01-01", "user" + i + "@loopers.test", now, now
            });
            if (batch.size() == CHUNK) {
                jdbcTemplate.batchUpdate(sql, batch);
                batch.clear();
            }
        }
        if (!batch.isEmpty()) {
            jdbcTemplate.batchUpdate(sql, batch);
        }
        log.info("[seed] users {}건 적재", USER_COUNT);
    }

    /**
     * 브랜드별 상품 수를 로그정규 분포로 편차 있게 배정하고(합계는 PRODUCT_COUNT 로 보정),
     * 브랜드를 시간순으로 인터리브해 적재한다. id 가 IDENTITY 단조증가이므로 id 순서 ≈ 최신순이 된다.
     */
    private int[] seedProducts() {
        Random rng = new Random(PRODUCT_SEED);
        int[] brandIdByProduct = buildBrandAssignment(rng);

        String sql = "INSERT INTO products (brand_id, name, description, price, stock, created_at, updated_at) "
            + "VALUES (?, ?, ?, ?, ?, ?, ?)";
        Instant base = Instant.now().minusSeconds(PRODUCT_COUNT);
        List<Object[]> batch = new ArrayList<>(CHUNK);
        for (int i = 0; i < PRODUCT_COUNT; i++) {
            int brandId = brandIdByProduct[i];
            int price = 1_000 + rng.nextInt(1_000_000);
            int stock = rng.nextInt(1_000);
            Timestamp createdAt = Timestamp.from(base.plusSeconds(i));
            batch.add(new Object[] {brandId, "상품" + (i + 1), "측정용 상품 설명 " + (i + 1), price, stock, createdAt, createdAt});
            if (batch.size() == CHUNK) {
                jdbcTemplate.batchUpdate(sql, batch);
                batch.clear();
            }
        }
        if (!batch.isEmpty()) {
            jdbcTemplate.batchUpdate(sql, batch);
        }
        log.info("[seed] products {}건 적재", PRODUCT_COUNT);
        return brandIdByProduct;
    }

    private int[] buildBrandAssignment(Random rng) {
        double[] weights = new double[BRAND_COUNT];
        double weightSum = 0;
        for (int b = 0; b < BRAND_COUNT; b++) {
            weights[b] = Math.exp(rng.nextGaussian() * BRAND_SIZE_SIGMA);
            weightSum += weights[b];
        }

        int[] sizes = new int[BRAND_COUNT];
        int assigned = 0;
        int largest = 0;
        for (int b = 0; b < BRAND_COUNT; b++) {
            sizes[b] = Math.max(1, (int) Math.round(PRODUCT_COUNT * weights[b] / weightSum));
            assigned += sizes[b];
            if (sizes[b] > sizes[largest]) {
                largest = b;
            }
        }
        sizes[largest] += (PRODUCT_COUNT - assigned);

        int[] assignment = new int[PRODUCT_COUNT];
        int cursor = 0;
        for (int b = 0; b < BRAND_COUNT; b++) {
            int brandId = b + 1;
            for (int k = 0; k < sizes[b]; k++) {
                assignment[cursor++] = brandId;
            }
        }
        shuffle(assignment, rng);
        return assignment;
    }

    /**
     * 상품마다 파워법칙(Pareto)으로 좋아요 수를 정해, 상품별로 서로 다른 사용자를 무작위로 매핑한다.
     * (user_id, product_id) 유니크 제약을 지키기 위해 상품 단위로 사용자 풀을 부분 셔플해 추출한다.
     */
    private long seedLikes() {
        Random rng = new Random(LIKE_SEED);
        int[] userPool = new int[USER_COUNT];
        for (int u = 0; u < USER_COUNT; u++) {
            userPool[u] = u + 1;
        }

        String sql = "INSERT INTO likes (user_id, product_id, created_at, updated_at) VALUES (?, ?, ?, ?)";
        Timestamp now = Timestamp.from(Instant.now());
        List<Object[]> batch = new ArrayList<>(CHUNK);
        long total = 0;
        for (int p = 1; p <= PRODUCT_COUNT; p++) {
            int likeCount = paretoLikeCount(rng);
            partialShuffle(userPool, likeCount, rng);
            for (int k = 0; k < likeCount; k++) {
                batch.add(new Object[] {userPool[k], p, now, now});
                total++;
                if (batch.size() == CHUNK) {
                    jdbcTemplate.batchUpdate(sql, batch);
                    batch.clear();
                }
            }
            if (p % 20_000 == 0) {
                log.info("[seed] likes 진행 — products {}/{}, 누적 likes {}", p, PRODUCT_COUNT, total);
            }
        }
        if (!batch.isEmpty()) {
            jdbcTemplate.batchUpdate(sql, batch);
        }
        log.info("[seed] likes {}건 적재 (평균 {}/상품)", total, total / PRODUCT_COUNT);
        return total;
    }

    private int paretoLikeCount(Random rng) {
        double u = rng.nextDouble();
        if (u <= 0) {
            u = Double.MIN_VALUE;
        }
        double value = MIN_LIKES_PER_PRODUCT / Math.pow(u, 1.0 / LIKE_PARETO_ALPHA);
        return (int) Math.min(MAX_LIKES_PER_PRODUCT, Math.round(value));
    }

    private void partialShuffle(int[] array, int count, Random rng) {
        int n = array.length;
        for (int i = 0; i < count; i++) {
            int j = i + rng.nextInt(n - i);
            int tmp = array[i];
            array[i] = array[j];
            array[j] = tmp;
        }
    }

    private void shuffle(int[] array, Random rng) {
        for (int i = array.length - 1; i > 0; i--) {
            int j = rng.nextInt(i + 1);
            int tmp = array[i];
            array[i] = array[j];
            array[j] = tmp;
        }
    }
}
