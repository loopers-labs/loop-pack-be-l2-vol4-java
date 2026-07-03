package com.loopers.order.application.event;

import com.loopers.brand.application.BrandAdminService;
import com.loopers.brand.application.BrandCommand;
import com.loopers.order.application.DataPlatformSender;
import com.loopers.order.application.OrderCommand;
import com.loopers.order.application.OrderResult;
import com.loopers.order.application.PlaceOrderFacade;
import com.loopers.product.application.ProductAdminService;
import com.loopers.product.application.ProductCommand;
import com.loopers.user.application.UserAccountService;
import com.loopers.user.application.UserCommand;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

@SpringBootTest
class OrderEventHandlerIntegrationTest {

    @Autowired private PlaceOrderFacade placeOrderFacade;
    @Autowired private UserAccountService userAccountService;
    @Autowired private BrandAdminService brandAdminService;
    @Autowired private ProductAdminService productAdminService;
    @Autowired private DatabaseCleanUp databaseCleanUp;

    // 실제 전송 대신 mock 으로 교체해 "호출되었는지"만 검증한다. (Spring Boot 3.4+ @MockitoBean)
    @MockitoBean private DataPlatformSender dataPlatformSender;

    private Long userId;
    private Long productId;

    @BeforeEach
    void setUp() {
        userId = userAccountService.signUp(new UserCommand.SignUp(
                "loopers01", "Passw0rd!", "김루퍼", LocalDate.of(1995, 3, 21), "looper@example.com"
        )).id();
        Long brandId = brandAdminService.create(new BrandCommand.Create("루퍼스", "설명", null)).id();
        productId = productAdminService.create(
                new ProductCommand.Create(brandId, "상품", "설명", 29_000L, null, 10)
        ).id();
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @Test
    @DisplayName("주문 생성이 커밋되면 AFTER_COMMIT 리스너가 비동기로 데이터 플랫폼에 주문 정보를 전송한다")
    void whenOrderPlaced_thenDataPlatformReceivesOrder() {
        OrderResult.Detail order = placeOrderFacade.place(orderCommand());

        // timeout() 검증 모드 = 비동기 호출을 최대 3초 기다린다.
        ArgumentCaptor<OrderCreatedEvent> captor = ArgumentCaptor.forClass(OrderCreatedEvent.class);
        verify(dataPlatformSender, timeout(3000)).sendOrder(captor.capture());

        OrderCreatedEvent sent = captor.getValue();
        assertThat(sent.orderId()).isEqualTo(order.orderId());
        assertThat(sent.userId()).isEqualTo(userId);
        assertThat(sent.orderNumber()).isEqualTo(order.orderNumber());
        assertThat(sent.finalAmount()).isEqualTo(order.finalAmount());
    }

    private OrderCommand.Create orderCommand() {
        return new OrderCommand.Create(
                userId, List.of(new OrderCommand.Line(productId, 1)),
                "김루퍼", "010-1234-5678", "12345", "서울시 강남구", "101동",
                null
        );
    }
}
