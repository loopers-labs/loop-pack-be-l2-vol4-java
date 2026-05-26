package com.loopers.application.product;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.loopers.domain.brand.BrandRepository;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

@ExtendWith(MockitoExtension.class)
class ProductFacadeTest {

    @Mock
    private BrandRepository brandRepository;

    @Mock
    private ProductRepository productRepository;

    @InjectMocks
    private ProductFacade productFacade;

    @DisplayName("상품을 생성할 때,")
    @Nested
    class CreateProduct {

        private final Long brandId = 1L;
        private final String name = "감성 가디건";
        private final String description = "포근한 감성 가디건";
        private final Integer price = 39_000;
        private final Integer stock = 50;

        @DisplayName("브랜드가 활성 상태로 존재하면 상품을 저장하고 생성 정보를 반환한다.")
        @Test
        void returnsCreateInfo_whenBrandIsActive() {
            // arrange
            given(brandRepository.existsActiveById(brandId)).willReturn(true);
            given(productRepository.save(any(ProductModel.class))).willAnswer(invocation -> invocation.getArgument(0));

            // act
            ProductCreateInfo createInfo = productFacade.createProduct(brandId, name, description, price, stock);

            // assert
            assertAll(
                () -> assertThat(createInfo).isNotNull(),
                () -> then(brandRepository).should().existsActiveById(brandId),
                () -> then(productRepository).should().save(any(ProductModel.class))
            );
        }

        @DisplayName("브랜드가 활성 상태로 존재하지 않으면 NOT_FOUND 예외가 발생하고 저장하지 않는다.")
        @Test
        void throwsNotFound_whenBrandIsAbsent() {
            // arrange
            given(brandRepository.existsActiveById(brandId)).willReturn(false);

            // act & assert
            assertAll(
                () -> assertThatThrownBy(() -> productFacade.createProduct(brandId, name, description, price, stock))
                    .isInstanceOf(CoreException.class)
                    .extracting("errorType")
                    .isEqualTo(ErrorType.NOT_FOUND),
                () -> then(productRepository).should(never()).save(any(ProductModel.class))
            );
        }
    }

    @DisplayName("상품을 수정할 때,")
    @Nested
    class UpdateProduct {

        private final Long productId = 1L;
        private final String name = "리뉴얼 가디건";
        private final String description = "새 설명";
        private final Integer price = 42_000;
        private final Integer stock = 30;

        @DisplayName("대상 상품이 활성 상태로 존재하면 값을 갱신하고 수정 정보를 반환한다.")
        @Test
        void returnsUpdateInfo_whenProductIsActive() {
            // arrange
            ProductModel product = ProductModel.builder()
                .brandId(1L)
                .rawName("감성 가디건")
                .rawDescription("포근한 감성 가디건")
                .rawPrice(39_000)
                .rawStock(50)
                .build();
            given(productRepository.getActiveById(productId)).willReturn(product);

            // act
            ProductUpdateInfo updateInfo = productFacade.updateProduct(productId, name, description, price, stock);

            // assert
            assertAll(
                () -> assertThat(updateInfo).isNotNull(),
                () -> assertThat(product.getName().value()).isEqualTo(name),
                () -> assertThat(product.getPrice().value()).isEqualTo(price),
                () -> assertThat(product.getStock().value()).isEqualTo(stock),
                () -> then(productRepository).should().getActiveById(productId)
            );
        }

        @DisplayName("대상 상품이 없거나 삭제되어 조회에 실패하면 NOT_FOUND 예외가 전파된다.")
        @Test
        void throwsNotFound_whenProductIsAbsent() {
            // arrange
            given(productRepository.getActiveById(productId))
                .willThrow(new CoreException(ErrorType.NOT_FOUND, "상품이 존재하지 않습니다."));

            // act & assert
            assertThatThrownBy(() -> productFacade.updateProduct(productId, name, description, price, stock))
                .isInstanceOf(CoreException.class)
                .extracting("errorType")
                .isEqualTo(ErrorType.NOT_FOUND);
        }
    }

    @DisplayName("상품을 삭제할 때,")
    @Nested
    class DeleteProduct {

        private final Long productId = 1L;

        @DisplayName("대상 상품이 활성 상태로 존재하면 삭제 처리한다.")
        @Test
        void deletesProduct_whenProductIsActive() {
            // arrange
            ProductModel product = ProductModel.builder()
                .brandId(1L)
                .rawName("감성 가디건")
                .rawDescription("포근한 감성 가디건")
                .rawPrice(39_000)
                .rawStock(50)
                .build();
            given(productRepository.findActiveById(productId)).willReturn(Optional.of(product));

            // act
            productFacade.deleteProduct(productId);

            // assert
            assertThat(product.getDeletedAt()).isNotNull();
        }

        @DisplayName("대상 상품이 없거나 이미 삭제되었으면 예외 없이 아무 동작도 하지 않는다(멱등).")
        @Test
        void doesNothing_whenProductIsAbsent() {
            // arrange
            given(productRepository.findActiveById(productId)).willReturn(Optional.empty());

            // act
            productFacade.deleteProduct(productId);

            // assert
            then(productRepository).should().findActiveById(productId);
        }
    }
}
