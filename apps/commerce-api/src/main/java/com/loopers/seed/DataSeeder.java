package com.loopers.seed;

import com.loopers.domain.brand.Brand;
import com.loopers.domain.like.Like;
import com.loopers.domain.product.Product;
import com.loopers.domain.shared.Money;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * 5주차 읽기 최적화 학습용 시드 데이터 생성기.
 *
 * - Brand 100개
 * - Product 100,000개 (brandId / price / stock 무작위)
 * - Like 약 80만 (멱급수 편향: 상위 1% 인기상품 쏠림)
 *
 * 동작 조건:
 * - {@code spring.profiles.active=local} 일 때만 실행
 * - 이미 PRODUCT_COUNT 이상 있으면 스킵 (멱등)
 * - Random seed 를 고정해 EXPLAIN 결과 재현 가능
 */
@Slf4j
@Component
@Profile("local")
@Order(100)
public class DataSeeder implements CommandLineRunner {

    private static final long RANDOM_SEED = 42L;
    private static final int BRAND_COUNT = 100;
    private static final int PRODUCT_COUNT = 100_000;
    private static final int USER_POOL_SIZE = 10_000;
    private static final int BATCH_SIZE = 1_000;

    @PersistenceContext
    private EntityManager em;

    @Override
    @Transactional
    public void run(String... args) {
        long startMs = System.currentTimeMillis();

        Long currentProducts = em.createQuery("SELECT COUNT(p) FROM Product p", Long.class).getSingleResult();
        if (currentProducts < PRODUCT_COUNT) {
            Random random = new Random(RANDOM_SEED);
            log.info("=== [Seed] 시작 — Brand {}, Product {}, Like ~80만 ===", BRAND_COUNT, PRODUCT_COUNT);
            List<Long> brandIds = seedBrands();
            List<Long> productIds = seedProducts(random, brandIds);
            seedLikes(random, productIds);
        } else {
            log.info("[Seed] Product 이미 {}건 존재. 시드 스킵.", currentProducts);
        }

        // ProductLikeStat 백필 (한 번 INSERT...SELECT 로)
        seedProductLikeStat();

        long elapsedSec = (System.currentTimeMillis() - startMs) / 1000;
        log.info("=== [Seed] 완료 — 소요 {}초 ===", elapsedSec);
    }

    /**
     * 기존 product_like 의 좋아요 수를 집계해 product_like_stat 에 한 번 채운다.
     * native INSERT...SELECT 한 방으로 처리 — JPA persist 반복보다 훨씬 빠르다.
     */
    private void seedProductLikeStat() {
        Long currentStats = em.createQuery("SELECT COUNT(s) FROM ProductLikeStat s", Long.class).getSingleResult();
        if (currentStats >= PRODUCT_COUNT) {
            log.info("[Seed] ProductLikeStat 이미 {}건 존재. 백필 스킵.", currentStats);
            return;
        }

        int inserted = em.createNativeQuery("""
            INSERT INTO product_like_stat (product_id, brand_id, like_count, version, updated_at)
            SELECT p.id, p.brand_id, COALESCE(l.cnt, 0), 0, NOW()
            FROM product p
            LEFT JOIN (
                SELECT product_id, COUNT(*) AS cnt FROM product_like GROUP BY product_id
            ) l ON l.product_id = p.id
            """).executeUpdate();
        log.info("[Seed] ProductLikeStat {}건 백필 완료", inserted);
    }

    private List<Long> seedBrands() {
        for (int i = 1; i <= BRAND_COUNT; i++) {
            em.persist(Brand.create("브랜드-" + i, "브랜드 " + i + " 설명"));
        }
        flushAndClear();
        List<Long> ids = em.createQuery("SELECT b.id FROM Brand b ORDER BY b.id", Long.class).getResultList();
        log.info("[Seed] Brand {}건 생성", ids.size());
        return ids;
    }

    private List<Long> seedProducts(Random random, List<Long> brandIds) {
        for (int i = 1; i <= PRODUCT_COUNT; i++) {
            long brandId = brandIds.get(random.nextInt(brandIds.size()));
            long price = 1_000L + random.nextInt(999_001);   // 1,000 ~ 1,000,000원
            int stock = random.nextInt(1_000);
            em.persist(Product.create(
                "상품-" + i,
                "상품 " + i + " 설명",
                Money.of(price),
                stock,
                brandId
            ));
            if (i % BATCH_SIZE == 0) {
                flushAndClear();
                if (i % 20_000 == 0) {
                    log.info("[Seed] Product 진행 {}/{}", i, PRODUCT_COUNT);
                }
            }
        }
        flushAndClear();
        List<Long> ids = em.createQuery("SELECT p.id FROM Product p ORDER BY p.id", Long.class).getResultList();
        log.info("[Seed] Product {}건 생성", ids.size());
        return ids;
    }

    /**
     * 멱급수 편향 분포 (총 ~80만):
     *  - 상위 1% (1,000개): 좋아요 50~150 → ~100,000
     *  - 상위 1~10% (9,000개): 좋아요 30~80 → ~500,000
     *  - 상위 10~50% (40,000개): 좋아요 3~10 → ~260,000
     *  - 하위 50% (50,000개): 좋아요 0 (skip)
     */
    private void seedLikes(Random random, List<Long> productIds) {
        int total = 0;
        int topOnePct = (int) (productIds.size() * 0.01);
        int topTenPct = (int) (productIds.size() * 0.10);
        int topFiftyPct = (int) (productIds.size() * 0.50);

        for (int i = 0; i < topOnePct; i++) {
            total = persistLikesForProduct(random, productIds.get(i), 50, 150, total);
        }
        log.info("[Seed] Like 상위 1% 그룹 완료 — 누적 {}건", total);

        for (int i = topOnePct; i < topTenPct; i++) {
            total = persistLikesForProduct(random, productIds.get(i), 30, 80, total);
        }
        log.info("[Seed] Like 상위 10% 그룹 완료 — 누적 {}건", total);

        for (int i = topTenPct; i < topFiftyPct; i++) {
            total = persistLikesForProduct(random, productIds.get(i), 3, 10, total);
        }
        flushAndClear();
        log.info("[Seed] Like {}건 생성", total);
    }

    private int persistLikesForProduct(Random random, Long productId, int min, int max, int currentTotal) {
        int likes = min + random.nextInt(max - min + 1);
        // UNIQUE (user_id, product_id) 위반 방지를 위해 중복 userId 회피
        Set<Long> userIds = new HashSet<>(likes);
        while (userIds.size() < likes) {
            userIds.add((long) (random.nextInt(USER_POOL_SIZE) + 1));
        }
        int running = currentTotal;
        for (Long userId : userIds) {
            em.persist(Like.of(userId, productId));
            running++;
            if (running % BATCH_SIZE == 0) {
                flushAndClear();
            }
        }
        return running;
    }

    private void flushAndClear() {
        em.flush();
        em.clear();
    }
}
