package com.loopers.benchmark;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 좋아요 수 조회 전략 비교 벤치마크.
 *  - 집계        : 별도 likes 테이블을 매번 GROUP BY (현재 코드)
 *  - 집계+인덱스 : likes(product_id, deleted_at) 인덱스만 추가한 집계
 *  - 비정규화    : products.like_count 컬럼을 읽음 (product에 카운트를 두는 1안 / likes→product 동기화하는 3안의 읽기)
 * Testcontainers MySQL 기준이라 절대 수치는 prod 와 다르지만 A/B 상대 비교에는 유효하다.
 * 일반 스위트에 포함시키지 않고 필요할 때만 수동 실행한다.
 * 실행: ./gradlew :apps:commerce-api:test --tests "*LikeCountBenchmarkTest"
 */
@SpringBootTest
class LikeCountBenchmarkTest {

    private static final int PRODUCTS = 10_000;
    private static final int[] LIKE_SIZES = {50_000, 200_000, 500_000};
    private static final int WARMUP = 3;
    private static final int ITERS = 11;
    private static final long POPULAR_PRODUCT_ID = 1L;

    // 목록: 판매중 상품을 좋아요 수 내림차순 (운영 쿼리와 동일 형태, select 는 p.id 로 한정해 집계/정렬 비용을 격리)
    private static final String LIST_AGG = """
            SELECT p.id FROM products p
            LEFT JOIN likes l ON l.product_id = p.id AND l.deleted_at IS NULL
            WHERE p.status = 'ON_SALE' AND p.deleted_at IS NULL
            GROUP BY p.id
            ORDER BY COUNT(l.id) DESC, p.id DESC
            """;
    private static final String LIST_DENORM = """
            SELECT p.id FROM products p
            WHERE p.status = 'ON_SALE' AND p.deleted_at IS NULL
            ORDER BY p.like_count DESC, p.id DESC
            """;
    private static final String DETAIL_AGG =
            "SELECT COUNT(*) FROM likes WHERE product_id = " + POPULAR_PRODUCT_ID + " AND deleted_at IS NULL";
    private static final String DETAIL_DENORM =
            "SELECT like_count FROM products WHERE id = " + POPULAR_PRODUCT_ID;

    @Autowired
    private DataSource dataSource;

    @Test
    void likeCountAggregationVsDenormalized() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(true);
            prepareSchema(c);
            seedProducts(c);

            List<String> list = new ArrayList<>();
            List<String> detail = new ArrayList<>();
            String header = String.format("%-9s | %-12s | %-16s | %-14s", "likes", "집계(ms)", "집계+인덱스(ms)", "비정규화(ms)");
            list.add(header);
            detail.add(header);

            for (int size : LIKE_SIZES) {
                resetLikes(c);
                seedLikes(c, size);
                syncProductLikeCount(c);

                dropLikesIndex(c);
                double listAgg = median(c, LIST_AGG);
                double detailAgg = median(c, DETAIL_AGG);

                addLikesIndex(c);
                double listAggIdx = median(c, LIST_AGG);
                double detailAggIdx = median(c, DETAIL_AGG);
                dropLikesIndex(c);

                double listDenorm = median(c, LIST_DENORM);
                double detailDenorm = median(c, DETAIL_DENORM);

                list.add(String.format("%-9d | %-12.1f | %-16.1f | %-14.1f", size, listAgg, listAggIdx, listDenorm));
                detail.add(String.format("%-9d | %-12.1f | %-16.1f | %-14.1f", size, detailAgg, detailAggIdx, detailDenorm));
            }

            System.out.println("\n================ BENCHMARK: like count ================");
            System.out.println("products=" + PRODUCTS + ", warmup=" + WARMUP + ", iters=" + ITERS + " (median)");
            System.out.println("\n[목록] 판매중 상품 좋아요 수 내림차순 정렬 (전체)");
            list.forEach(System.out::println);
            System.out.println("\n[단건] 가장 인기 상품의 활성 좋아요 수");
            detail.forEach(System.out::println);
            System.out.println("=======================================================\n");
        }
    }

    private double median(Connection c, String sql) throws Exception {
        for (int i = 0; i < WARMUP; i++) runOnce(c, sql);
        List<Long> nanos = new ArrayList<>();
        for (int i = 0; i < ITERS; i++) nanos.add(runOnce(c, sql));
        Collections.sort(nanos);
        return nanos.get(nanos.size() / 2) / 1_000_000.0;
    }

    private long runOnce(Connection c, String sql) throws Exception {
        long start = System.nanoTime();
        try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                rs.getObject(1);
            }
        }
        return System.nanoTime() - start;
    }

    private void prepareSchema(Connection c) throws Exception {
        exec(c, "TRUNCATE TABLE products");
        exec(c, "TRUNCATE TABLE likes");
        // 비정규화 안(1·3안): products 에 like_count 컬럼 + 정렬용 인덱스
        try {
            exec(c, "ALTER TABLE products ADD COLUMN like_count BIGINT NOT NULL DEFAULT 0");
        } catch (Exception ignored) {
            // 이미 존재하면 무시
        }
        try {
            exec(c, "ALTER TABLE products ADD INDEX idx_products_like_count (like_count)");
        } catch (Exception ignored) {
            // 이미 존재하면 무시
        }
    }

    private void seedProducts(Connection c) throws Exception {
        StringBuilder sb = new StringBuilder();
        int inChunk = 0;
        for (int p = 1; p <= PRODUCTS; p++) {
            if (inChunk == 0) {
                sb.setLength(0);
                sb.append("INSERT INTO products (brand_id, name, price, status, created_at, updated_at) VALUES ");
            } else {
                sb.append(',');
            }
            sb.append("(1,'p").append(p).append("',1000,'ON_SALE',NOW(),NOW())");
            if (++inChunk == 1000) {
                exec(c, sb.toString());
                inChunk = 0;
            }
        }
        if (inChunk > 0) exec(c, sb.toString());
    }

    private void resetLikes(Connection c) throws Exception {
        exec(c, "TRUNCATE TABLE likes");
    }

    /**
     * zipf 분포로 좋아요를 분배한다(소수의 인기 상품에 집중). 상품 p 는 user 1..count_p 의 좋아요를 받아
     * UNIQUE(user_id, product_id) 를 위반하지 않는다. 총합은 약 target 건.
     */
    private void seedLikes(Connection c, int target) throws Exception {
        double h = 0;
        for (int p = 1; p <= PRODUCTS; p++) h += 1.0 / p;

        StringBuilder sb = new StringBuilder();
        int inChunk = 0;
        for (int p = 1; p <= PRODUCTS; p++) {
            int count = (int) Math.round(target / (p * h));
            for (int u = 1; u <= count; u++) {
                if (inChunk == 0) {
                    sb.setLength(0);
                    sb.append("INSERT INTO likes (user_id, product_id, created_at, updated_at) VALUES ");
                } else {
                    sb.append(',');
                }
                sb.append('(').append(u).append(',').append(p).append(",NOW(),NOW())");
                if (++inChunk == 1000) {
                    exec(c, sb.toString());
                    inChunk = 0;
                }
            }
        }
        if (inChunk > 0) exec(c, sb.toString());
    }

    /** likes(SSOT)에서 products.like_count 로 재집계 동기화 (3안의 동기화 = 1안의 적재). */
    private void syncProductLikeCount(Connection c) throws Exception {
        exec(c, "UPDATE products SET like_count = 0");
        exec(c, "UPDATE products p "
                + "JOIN (SELECT product_id, COUNT(*) cnt FROM likes WHERE deleted_at IS NULL GROUP BY product_id) c "
                + "  ON c.product_id = p.id "
                + "SET p.like_count = c.cnt");
    }

    private void addLikesIndex(Connection c) throws Exception {
        exec(c, "ALTER TABLE likes ADD INDEX idx_likes_bench (product_id, deleted_at)");
    }

    private void dropLikesIndex(Connection c) {
        try {
            exec(c, "ALTER TABLE likes DROP INDEX idx_likes_bench");
        } catch (Exception ignored) {
            // 인덱스가 없을 때는 무시
        }
    }

    private void exec(Connection c, String sql) throws Exception {
        try (Statement st = c.createStatement()) {
            st.execute(sql);
        }
    }
}
