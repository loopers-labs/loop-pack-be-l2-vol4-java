package com.loopers.application.order;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.application.brand.BrandFacade;
import com.loopers.application.product.ProductFacade;
import com.loopers.application.user.UserCommand;
import com.loopers.application.user.UserFacade;
import com.loopers.domain.outbox.OutboxEvent;
import com.loopers.infrastructure.outbox.OutboxJpaRepository;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class OrderOutboxIntegrationTest {

    private final OrderFacade orderFacade;
    private final BrandFacade brandFacade;
    private final ProductFacade productFacade;
    private final UserFacade userFacade;
    private final OutboxJpaRepository outboxJpaRepository;
    private final ObjectMapper objectMapper;
    private final DatabaseCleanUp databaseCleanUp;

    private Long userId;
    private Long productAId;
    private Long productBId;

    @Autowired
    OrderOutboxIntegrationTest(
        OrderFacade orderFacade,
        BrandFacade brandFacade,
        ProductFacade productFacade,
        UserFacade userFacade,
        OutboxJpaRepository outboxJpaRepository,
        ObjectMapper objectMapper,
        DatabaseCleanUp databaseCleanUp
    ) {
        this.orderFacade = orderFacade;
        this.brandFacade = brandFacade;
        this.productFacade = productFacade;
        this.userFacade = userFacade;
        this.outboxJpaRepository = outboxJpaRepository;
        this.objectMapper = objectMapper;
        this.databaseCleanUp = databaseCleanUp;
    }

    @BeforeEach
    void setUp() {
        Long brandId = brandFacade.create("나이키", "Just Do It").id();
        productAId = productFacade.createProduct("에어맥스 270", "데일리 러닝화", 100_000L, 50, brandId).id();
        productBId = productFacade.createProduct("에어포스 1", "클래식", 150_000L, 50, brandId).id();
        userId = userFacade.signUp(new UserCommand.SignUp(
            "user01", "Abcd1234!", "김철수", LocalDate.of(1999, 3, 22), "user@example.com")).id();
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("주문이 생성되면, 같은 트랜잭션에서 ORDER_PLACED outbox 이벤트(상품별 수량 라인)가 1건 쌓인다.")
    @Test
    void appendsOrderPlacedOutbox_whenOrderIsPlaced() throws Exception {
        // when : 상품 A 2개, 상품 B 3개 주문
        OrderInfo order = orderFacade.placeOrder(userId, new OrderCommand.Place(List.of(
            new OrderCommand.Line(productAId, 2),
            new OrderCommand.Line(productBId, 3)
        )));

        // then
        List<OutboxEvent> rows = outboxJpaRepository.findAll();
        assertThat(rows).hasSize(1);
        OutboxEvent row = rows.get(0);
        JsonNode payload = objectMapper.readTree(row.getPayload());
        assertAll(
            () -> assertThat(row.getAggregateType()).isEqualTo("Order"),
            () -> assertThat(row.getAggregateId()).isEqualTo(order.id()),
            () -> assertThat(row.getEventType()).isEqualTo("ORDER_PLACED"),
            () -> assertThat(row.isPublished()).isFalse(),
            () -> assertThat(payload.get("lines")).hasSize(2),
            () -> assertThat(quantityOf(payload, productAId)).isEqualTo(2),
            () -> assertThat(quantityOf(payload, productBId)).isEqualTo(3)
        );
    }

    private int quantityOf(JsonNode payload, Long productId) {
        for (JsonNode line : payload.get("lines")) {
            if (line.get("productId").asLong() == productId) {
                return line.get("quantity").asInt();
            }
        }
        throw new IllegalStateException("productId " + productId + " 라인이 payload 에 없습니다.");
    }
}
