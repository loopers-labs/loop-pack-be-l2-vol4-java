package com.loopers.application.stock;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.application.brand.BrandFacade;
import com.loopers.application.product.ProductFacade;
import com.loopers.domain.outbox.OutboxEvent;
import com.loopers.infrastructure.outbox.OutboxJpaRepository;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class StockOutboxIntegrationTest {

    private final ProductFacade productFacade;
    private final BrandFacade brandFacade;
    private final OutboxJpaRepository outboxJpaRepository;
    private final ObjectMapper objectMapper;
    private final DatabaseCleanUp databaseCleanUp;

    private Long productId;

    @Autowired
    StockOutboxIntegrationTest(
        ProductFacade productFacade,
        BrandFacade brandFacade,
        OutboxJpaRepository outboxJpaRepository,
        ObjectMapper objectMapper,
        DatabaseCleanUp databaseCleanUp
    ) {
        this.productFacade = productFacade;
        this.brandFacade = brandFacade;
        this.outboxJpaRepository = outboxJpaRepository;
        this.objectMapper = objectMapper;
        this.databaseCleanUp = databaseCleanUp;
    }

    @BeforeEach
    void setUp() {
        Long brandId = brandFacade.create("나이키", "Just Do It").id();
        productId = productFacade.createProduct("에어맥스 270", "데일리 러닝화", 159_000L, 50, brandId).id();
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("관리자가 재고를 변경하면, 같은 트랜잭션에서 STOCK_CHANGED outbox 이벤트(절대 수량+version)가 1건 쌓인다.")
    @Test
    void appendsStockChangedOutbox_whenAdminUpdatesStock() throws Exception {
        // when
        productFacade.updateProduct(productId, "에어맥스 270", "재고 조정", 159_000L, 30);

        // then
        List<OutboxEvent> rows = outboxJpaRepository.findAll();
        assertThat(rows).hasSize(1);
        OutboxEvent row = rows.get(0);
        JsonNode payload = objectMapper.readTree(row.getPayload());
        assertAll(
            () -> assertThat(row.getAggregateType()).isEqualTo("Product"),
            () -> assertThat(row.getAggregateId()).isEqualTo(productId),
            () -> assertThat(row.getEventType()).isEqualTo("STOCK_CHANGED"),
            () -> assertThat(row.isPublished()).isFalse(),
            () -> assertThat(payload.get("quantity").asLong()).isEqualTo(30L),
            () -> assertThat(payload.has("version")).isTrue()
        );
    }

    @DisplayName("연속된 재고 변경은 version 이 단조 증가한다 — 소비자의 최신성 가드가 순서를 판별할 수 있도록.")
    @Test
    void stockEventVersionIncreases_acrossSuccessiveUpdates() throws Exception {
        // when : 같은 상품 재고를 두 번 변경
        productFacade.updateProduct(productId, "에어맥스 270", "1차 조정", 159_000L, 40);
        productFacade.updateProduct(productId, "에어맥스 270", "2차 조정", 159_000L, 30);

        // then : 뒤에 발행된 이벤트의 version 이 더 크다
        List<OutboxEvent> rows = outboxJpaRepository.findByPublishedIsFalseOrderByIdAsc(org.springframework.data.domain.PageRequest.of(0, 10));
        assertThat(rows).hasSize(2);
        long firstVersion = objectMapper.readTree(rows.get(0).getPayload()).get("version").asLong();
        long secondVersion = objectMapper.readTree(rows.get(1).getPayload()).get("version").asLong();
        assertThat(secondVersion).isGreaterThan(firstVersion);
    }
}
