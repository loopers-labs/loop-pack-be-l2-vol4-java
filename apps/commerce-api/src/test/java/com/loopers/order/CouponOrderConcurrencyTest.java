package com.loopers.order;

import com.loopers.brand.domain.Brand;
import com.loopers.brand.infrastructure.BrandJpaRepository;
import com.loopers.coupon.domain.Coupon;
import com.loopers.coupon.domain.CouponState;
import com.loopers.coupon.domain.CouponType;
import com.loopers.coupon.domain.MemberCoupon;
import com.loopers.coupon.infrastructure.CouponJpaRepository;
import com.loopers.coupon.infrastructure.MemberCouponJpaRepository;
import com.loopers.inventory.domain.Inventory;
import com.loopers.inventory.infrastructure.InventoryJpaRepository;
import com.loopers.member.domain.Member;
import com.loopers.member.infrastructure.MemberJpaRepository;
import com.loopers.order.application.OrderFacade;
import com.loopers.order.domain.OrderLine;
import com.loopers.order.infrastructure.OrderJpaRepository;
import com.loopers.product.domain.Product;
import com.loopers.product.infrastructure.ProductJpaRepository;
import com.loopers.support.ConcurrencyTestSupport;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class CouponOrderConcurrencyTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    @Autowired private OrderFacade orderFacade;
    @Autowired private MemberJpaRepository memberJpaRepository;
    @Autowired private BrandJpaRepository brandJpaRepository;
    @Autowired private ProductJpaRepository productJpaRepository;
    @Autowired private InventoryJpaRepository inventoryJpaRepository;
    @Autowired private CouponJpaRepository couponJpaRepository;
    @Autowired private MemberCouponJpaRepository memberCouponJpaRepository;
    @Autowired private OrderJpaRepository orderJpaRepository;
    @Autowired private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("동일한 쿠폰으로 여러 기기에서 동시에 주문해도 쿠폰은 단 한 번만 사용된다.")
    @Test
    void sameCoupon_concurrentOrders_usedOnce() throws InterruptedException {
        Member member = memberJpaRepository.save(new Member("user0001", "pw123456"));
        Brand brand = brandJpaRepository.save(new Brand("브랜드", "설명"));
        Product product = productJpaRepository.save(new Product(brand.getId(), "상품", "설명", 1_000L));
        inventoryJpaRepository.save(new Inventory(product.getId(), 1_000)); // 재고는 충분

        Coupon coupon =
            couponJpaRepository.save(
                new Coupon("정액", CouponType.FIXED, 100L, null, ZonedDateTime.now(SEOUL).plusDays(1)));
        MemberCoupon memberCoupon =
            memberCouponJpaRepository.save(
                new MemberCoupon(
                    member.getId(), coupon.getId(), coupon.getExpiredAt(), ZonedDateTime.now(SEOUL)));
        Long memberCouponId = memberCoupon.getId();

        int threads = 16;
        ConcurrencyTestSupport.Result result =
            ConcurrencyTestSupport.runConcurrently(
                threads,
                idx ->
                    orderFacade.createOrder(
                        member.getId(), List.of(new OrderLine(product.getId(), 1)), memberCouponId));

        assertThat(result.success()).isEqualTo(1);
        assertThat(result.failure()).isEqualTo(threads - 1);
        assertThat(memberCouponJpaRepository.findById(memberCouponId).orElseThrow().getState())
            .isEqualTo(CouponState.USED);
        // 성공한 1건만 커밋되어 주문/재고차감이 반영된다.
        assertThat(orderJpaRepository.count()).isEqualTo(1);
        assertThat(inventoryJpaRepository.findByProductId(product.getId()).orElseThrow().getAvailableQuantity())
            .isEqualTo(999);
    }
}
