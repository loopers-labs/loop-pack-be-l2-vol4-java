package com.loopers.infrastructure.product;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import com.loopers.utils.DatabaseCleanUp;

@SpringBootTest
class ProductRepositoryIntegrationTest {

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ProductJpaRepository productJpaRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private ProductModel createProduct(String name) {
        return ProductModel.builder()
            .brandId(1L)
            .rawName(name)
            .rawDescription("포근한 감성 가디건")
            .rawPrice(39_000)
            .rawStock(50)
            .build();
    }

    @DisplayName("상품을 저장할 때,")
    @Nested
    class Save {

        @DisplayName("저장하면 식별자가 부여되고 모든 필드가 보존된 채 조회된다.")
        @Test
        void assignsId_andPreservesFields() {
            // arrange & act
            ProductModel savedProduct = productRepository.save(createProduct("감성 가디건"));

            // assert
            ProductModel reloadedProduct = productJpaRepository.findById(savedProduct.getId()).orElseThrow();
            assertAll(
                () -> assertThat(savedProduct.getId()).isNotNull(),
                () -> assertThat(reloadedProduct.getBrandId()).isEqualTo(1L),
                () -> assertThat(reloadedProduct.getName().value()).isEqualTo("감성 가디건"),
                () -> assertThat(reloadedProduct.getDescription()).isEqualTo("포근한 감성 가디건"),
                () -> assertThat(reloadedProduct.getPrice().value()).isEqualTo(39_000),
                () -> assertThat(reloadedProduct.getStock().value()).isEqualTo(50)
            );
        }
    }

    @DisplayName("활성 상품을 식별자로 조회할 때,")
    @Nested
    class GetActiveById {

        @DisplayName("삭제되지 않은 상품이면 해당 상품을 반환한다.")
        @Test
        void returnsActiveProduct() {
            // arrange
            ProductModel savedProduct = productRepository.save(createProduct("감성 가디건"));

            // act
            ProductModel foundProduct = productRepository.getActiveById(savedProduct.getId());

            // assert
            assertThat(foundProduct.getId()).isEqualTo(savedProduct.getId());
        }

        @DisplayName("이미 삭제된 상품이면 NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenProductIsDeleted() {
            // arrange
            ProductModel savedProduct = productRepository.save(createProduct("감성 가디건"));
            savedProduct.delete();
            productJpaRepository.saveAndFlush(savedProduct);

            // act & assert
            assertThatThrownBy(() -> productRepository.getActiveById(savedProduct.getId()))
                .isInstanceOf(CoreException.class)
                .extracting("errorType")
                .isEqualTo(ErrorType.NOT_FOUND);
        }

        @DisplayName("존재하지 않는 식별자면 NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenProductIsAbsent() {
            // act & assert
            assertThatThrownBy(() -> productRepository.getActiveById(99999L))
                .isInstanceOf(CoreException.class)
                .extracting("errorType")
                .isEqualTo(ErrorType.NOT_FOUND);
        }
    }

    @DisplayName("활성 상품을 식별자로 탐색할 때,")
    @Nested
    class FindActiveById {

        @DisplayName("삭제되지 않은 상품이면 해당 상품을 담은 Optional을 반환한다.")
        @Test
        void returnsPresent_whenProductIsActive() {
            // arrange
            ProductModel savedProduct = productRepository.save(createProduct("감성 가디건"));

            // act & assert
            assertThat(productRepository.findActiveById(savedProduct.getId())).isPresent();
        }

        @DisplayName("이미 삭제된 상품이면 빈 Optional을 반환한다.")
        @Test
        void returnsEmpty_whenProductIsDeleted() {
            // arrange
            ProductModel savedProduct = productRepository.save(createProduct("감성 가디건"));
            savedProduct.delete();
            productJpaRepository.saveAndFlush(savedProduct);

            // act & assert
            assertThat(productRepository.findActiveById(savedProduct.getId())).isEmpty();
        }

        @DisplayName("존재하지 않는 식별자면 빈 Optional을 반환한다.")
        @Test
        void returnsEmpty_whenProductIsAbsent() {
            // act & assert
            assertThat(productRepository.findActiveById(99999L)).isEmpty();
        }
    }
}
