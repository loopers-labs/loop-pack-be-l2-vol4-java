package com.loopers.benchmark;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * 재고 차감 락(FOR UPDATE)의 "경합 범위"를 보여주는 결정적 실험.
 *
 * <p>가설: 재고를 어디에 두느냐에 따라 주문이 잡는 락이 상품 정보 수정을 막을 수도, 안 막을 수도 있다.
 * <ul>
 *   <li>A) 재고 = products 컬럼      → 주문이 products 행을 잠가, 같은 상품의 정보(price) 수정이 대기한다.</li>
 *   <li>B) 재고 = 별도 product_stocks → 주문은 재고 행만 잠가, 상품 정보 수정이 막히지 않는다.</li>
 * </ul>
 *
 * <p>측정 방법: "주문이 재고 락을 {@link #HOLD_MS}ms 보유 중"인 상황을 한 커넥션에서 재현하고,
 * 그동안 다른 커넥션에서 같은 상품의 정보를 UPDATE 하며 대기시간을 잰다.
 * (락 경합은 서로 다른 커넥션 사이에서만 생기므로 커넥션 2개가 필요하다.)
 *
 * <p>프로덕션 스키마/엔티티는 건드리지 않고, 테스트 안에서 raw SQL/DDL 로만 두 변형을 구성한다.
 * 수동 실행: {@code ./gradlew :apps:commerce-api:test --tests "*StockLockContentionTest"}
 */
@SpringBootTest
class StockLockContentionTest {

    private static final long PRODUCT_ID = 1L;
    private static final long HOLD_MS = 2000;

    // A) 재고가 products 컬럼이면 주문은 products 행 자체를 잠근다.
    private static final String LOCK_STOCK_AS_PRODUCT_COLUMN =
            "SELECT stock_quantity FROM products WHERE id = " + PRODUCT_ID + " FOR UPDATE";
    // B) 재고가 별도 테이블이면 주문은 product_stocks 행만 잠근다.
    private static final String LOCK_STOCK_AS_SEPARATE_TABLE =
            "SELECT quantity FROM product_stocks WHERE product_id = " + PRODUCT_ID + " FOR UPDATE";
    // 같은 상품의 기본 정보(price) 수정.
    private static final String UPDATE_PRODUCT_INFO =
            "UPDATE products SET price = price + 1 WHERE id = " + PRODUCT_ID;

    @Autowired
    private DataSource dataSource;

    @Test
    void stockLockBlocksProductUpdateOnlyWhenStockIsAColumn() throws Exception {
        setup();

        long waitWhenColumn = measureProductUpdateWaitWhileStockLocked(LOCK_STOCK_AS_PRODUCT_COLUMN);
        long waitWhenSeparate = measureProductUpdateWaitWhileStockLocked(LOCK_STOCK_AS_SEPARATE_TABLE);

        System.out.println("\n========= STOCK LOCK CONTENTION (hold=" + HOLD_MS + "ms) =========");
        System.out.printf("A) 재고 = products 컬럼   : 상품정보 UPDATE 대기 = %d ms%n", waitWhenColumn);
        System.out.printf("B) 재고 = 별도 테이블      : 상품정보 UPDATE 대기 = %d ms%n", waitWhenSeparate);
        System.out.println("=> A 는 재고 락이 products 행을 잠가 상품 UPDATE 가 락 보유시간만큼 대기,");
        System.out.println("   B 는 재고 락이 product_stocks 행만 잠가 상품 UPDATE 가 막히지 않는다.");
        System.out.println("=============================================================\n");
    }

    /**
     * 한 커넥션이 {@code stockLockSql} 로 재고 행을 {@link #HOLD_MS}ms 잠그고 있는 동안,
     * 다른 커넥션의 상품 정보 UPDATE 가 얼마나 대기하는지(ms) 측정한다.
     */
    private long measureProductUpdateWaitWhileStockLocked(String stockLockSql) throws Exception {
        ExecutorService holder = Executors.newSingleThreadExecutor();
        CountDownLatch lockAcquired = new CountDownLatch(1);
        try {
            // [holder] 주문이 재고 락을 보유 중인 상황을 재현한다.
            Future<?> holding = holder.submit(() -> {
                try (Connection conn = dataSource.getConnection()) {
                    conn.setAutoCommit(false);
                    runQuery(conn, stockLockSql);   // 1) 재고 행을 FOR UPDATE 로 잠근다
                    lockAcquired.countDown();        // 2) 락을 잡았음을 알린다
                    Thread.sleep(HOLD_MS);           // 3) 락을 보유한 채 트랜잭션 유지
                    conn.commit();                   // 4) 커밋으로 락 해제
                }
                return null;
            });

            // [main] 재고 락이 잡힌 뒤, 같은 상품의 정보를 수정하며 대기시간을 잰다.
            lockAcquired.await();
            long waitMs;
            try (Connection conn = dataSource.getConnection()) {
                conn.setAutoCommit(true);
                long start = System.nanoTime();
                try (Statement st = conn.createStatement()) {
                    st.executeUpdate(UPDATE_PRODUCT_INFO);
                }
                waitMs = (System.nanoTime() - start) / 1_000_000;
            }

            holding.get();   // holder 트랜잭션이 정리될 때까지 대기
            return waitMs;
        } finally {
            holder.shutdownNow();
        }
    }

    /** 상품 1개와 그 재고 1행을 준비한다. A 변형을 위해 products 에 stock_quantity 컬럼도 추가한다. */
    private void setup() throws Exception {
        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(true);
            exec(conn, "TRUNCATE TABLE products");
            exec(conn, "TRUNCATE TABLE product_stocks");
            try {
                exec(conn, "ALTER TABLE products ADD COLUMN stock_quantity INT NOT NULL DEFAULT 0");
            } catch (Exception alreadyExists) {
                // 컬럼이 이미 있으면 무시
            }
            exec(conn, "INSERT INTO products (id, brand_id, name, price, status, stock_quantity, created_at, updated_at) "
                    + "VALUES (" + PRODUCT_ID + ", 1, 'p1', 1000, 'ON_SALE', 100, NOW(), NOW())");
            exec(conn, "INSERT INTO product_stocks (product_id, quantity, created_at, updated_at) "
                    + "VALUES (" + PRODUCT_ID + ", 100, NOW(), NOW())");
        }
    }

    private void runQuery(Connection conn, String sql) throws Exception {
        try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                rs.getObject(1);
            }
        }
    }

    private void exec(Connection conn, String sql) throws Exception {
        try (Statement st = conn.createStatement()) {
            st.execute(sql);
        }
    }
}
