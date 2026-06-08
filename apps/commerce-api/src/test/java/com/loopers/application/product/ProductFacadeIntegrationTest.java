package com.loopers.application.product;

import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.brand.BrandRepository;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class ProductFacadeIntegrationTest {

    @Autowired private ProductFacade productFacade;
    @Autowired private BrandRepository brandRepository;
    @Autowired private DatabaseCleanUp databaseCleanUp;

    private BrandModel brand;

    @BeforeEach
    void setUp() {
        brand = brandRepository.save(new BrandModel("테스트브랜드"));
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private ProductInfo registerDefaultProduct(String name, long price, int quantity) {
        return productFacade.registerProduct(brand.getId(), name, price, quantity);
    }

    @DisplayName("상품 등록 시,")
    @Nested
    class RegisterProduct {

        @DisplayName("상품과 재고가 함께 생성된다.")
        @Test
        void createsProductAndStock_whenInputsAreValid() {
            ProductInfo info = productFacade.registerProduct(brand.getId(), "테스트상품", 10000L, 5);

            assertThat(info.name()).isEqualTo("테스트상품");
            assertThat(info.stocks()).hasSize(1);
            assertThat(info.stocks().get(0).price()).isEqualTo(10000L);
            assertThat(info.stocks().get(0).quantity()).isEqualTo(5);
        }
    }

    @DisplayName("상품 수정 시,")
    @Nested
    class UpdateProduct {

        @DisplayName("상품명만 입력하면, 상품명이 변경된다.")
        @Test
        void updatesName_whenOnlyNameIsProvided() {
            ProductInfo registered = registerDefaultProduct("테스트상품", 10000L, 5);

            ProductInfo updated = productFacade.updateProduct(registered.id(), "수정상품", null, null, null);

            assertThat(updated.name()).isEqualTo("수정상품");
            assertThat(updated.stocks().get(0).price()).isEqualTo(10000L);
            assertThat(updated.stocks().get(0).quantity()).isEqualTo(5);
        }

        @DisplayName("가격만 입력하면, 가격이 변경된다.")
        @Test
        void updatesPrice_whenOnlyPriceIsProvided() {
            ProductInfo registered = registerDefaultProduct("테스트상품", 10000L, 5);
            Long stockId = registered.stocks().get(0).id();

            ProductInfo updated = productFacade.updateProduct(registered.id(), null, stockId, 20000L, null);

            assertThat(updated.stocks().get(0).price()).isEqualTo(20000L);
            assertThat(updated.stocks().get(0).quantity()).isEqualTo(5);
        }

        @DisplayName("양수 재고 증감량 입력하면, 재고가 증가한다.")
        @Test
        void increasesStock_whenStockQuantityIsPositive() {
            ProductInfo registered = registerDefaultProduct("테스트상품", 10000L, 5);
            Long stockId = registered.stocks().get(0).id();

            ProductInfo updated = productFacade.updateProduct(registered.id(), null, stockId, null, 3);

            assertThat(updated.stocks().get(0).quantity()).isEqualTo(8);
        }

        @DisplayName("음수 재고 증감량 입력하면, 재고가 차감된다.")
        @Test
        void decreasesStock_whenStockQuantityIsNegative() {
            ProductInfo registered = registerDefaultProduct("테스트상품", 10000L, 5);
            Long stockId = registered.stocks().get(0).id();

            ProductInfo updated = productFacade.updateProduct(registered.id(), null, stockId, null, -2);

            assertThat(updated.stocks().get(0).quantity()).isEqualTo(3);
        }
    }
}
