package com.loopers.application.order;

import com.loopers.application.brand.BrandRepository;
import com.loopers.application.coupon.CouponRepository;
import com.loopers.application.product.ProductRepository;
import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.coupon.CouponIssue;
import com.loopers.domain.coupon.CouponTemplate;
import com.loopers.domain.coupon.CouponType;
import com.loopers.domain.order.OrderModel;
import com.loopers.domain.product.ProductModel;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import com.loopers.testcontainers.RedisTestContainersConfig;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.test.context.ContextConfiguration;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

@SpringBootTest
@ContextConfiguration(initializers = RedisTestContainersConfig.class)
public class OrderFacadeIntegrationTest {

    @Autowired
    private OrderFacade orderFacade;

    @Autowired
    private BrandRepository brandRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CouponRepository couponRepository;

    @SpyBean
    private OrderRepository orderRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @Test
    @DisplayName("주문 생성 중 예외 발생 시 재고 차감 및 쿠폰 사용 상태가 롤백된다.")
    void createOrder_RollbackOnFailure() {
        // given
        Long userId = 1L;
        BrandModel brand = brandRepository.save(new BrandModel("Nike"));
        ProductModel product = new ProductModel(brand.getId(), "Air Max", new BigDecimal("100000"));
        product.assignStock(10);
        productRepository.save(product);

        CouponTemplate template = couponRepository.saveTemplate(new CouponTemplate(
                "10% Discount", CouponType.RATE, new BigDecimal("10"), BigDecimal.ZERO, null, LocalDateTime.now().plusDays(1)
        ));
        CouponIssue couponIssue = couponRepository.saveIssue(new CouponIssue(userId, template));

        OrderCreateRequest request = new OrderCreateRequest(
                List.of(new OrderCreateRequest.Item(product.getId(), 2)),
                couponIssue.getId()
        );

        // orderRepository.save 호출 시 예외를 발생시키도록 세팅
        doThrow(new CoreException(ErrorType.INTERNAL_ERROR, "강제 예외 발생"))
                .when(orderRepository).save(any(OrderModel.class));

        // when & then
        assertThatThrownBy(() -> orderFacade.createOrder(userId, request))
                .isInstanceOf(CoreException.class)
                .hasMessage("강제 예외 발생");

        // then - 롤백 확인
        ProductModel updatedProduct = productRepository.findById(product.getId()).orElseThrow();
        assertThat(updatedProduct.getStock().getQuantity()).isEqualTo(10); // 재고 차감이 롤백되어 10개로 유지되어야 함

        CouponIssue updatedCouponIssue = couponRepository.findIssueById(couponIssue.getId()).orElseThrow();
        assertThat(updatedCouponIssue.getStatus()).isEqualTo(com.loopers.domain.coupon.CouponStatus.AVAILABLE); // 쿠폰 사용 상태가 롤백되어야 함
    }
}
