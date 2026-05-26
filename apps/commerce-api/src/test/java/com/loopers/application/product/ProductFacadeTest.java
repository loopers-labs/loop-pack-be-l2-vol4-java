package com.loopers.application.product;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

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
}
