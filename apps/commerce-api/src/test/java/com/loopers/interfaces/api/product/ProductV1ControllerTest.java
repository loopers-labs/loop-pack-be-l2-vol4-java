package com.loopers.interfaces.api.product;

import com.loopers.application.product.ProductFacade;
import com.loopers.application.product.ProductInfo;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;

import java.time.ZonedDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.mock;

class ProductV1ControllerTest {

    private ProductFacade productFacade;
    private ProductV1Controller productV1Controller;

    @BeforeEach
    void setUp() {
        productFacade = mock(ProductFacade.class);
        productV1Controller = new ProductV1Controller(productFacade);
    }

    @DisplayName("어드민 상품 단건 조회 시, ")
    @Nested
    class GetProduct {

        @DisplayName("존재하는 상품이면, 재고 수량과 날짜가 포함된 ProductAdminResponse를 반환한다.")
        @Test
        void returnsProductAdminResponse_whenProductExists() {
            // Arrange
            ZonedDateTime now = ZonedDateTime.now();
            ProductInfo info = new ProductInfo(
                1L, 2L, "에어맥스", "편안한 운동화", 100_000L, 5,
                "나이키", true, 10, now, now
            );
            when(productFacade.getProduct(1L)).thenReturn(info);

            // Act
            var result = productV1Controller.getProduct(1L);

            // Assert
            ProductV1Dto.ProductAdminResponse response = result.data();
            assertThat(response.id()).isEqualTo(1L);
            assertThat(response.brandId()).isEqualTo(2L);
            assertThat(response.brandName()).isEqualTo("나이키");
            assertThat(response.stock()).isEqualTo(10);
            assertThat(response.createdAt()).isEqualTo(now);
        }

        @DisplayName("존재하지 않는 상품이면, NOT_FOUND 예외가 전파된다.")
        @Test
        void propagatesNotFound_whenProductDoesNotExist() {
            // Arrange
            when(productFacade.getProduct(999L))
                .thenThrow(new CoreException(ErrorType.NOT_FOUND, "상품을 찾을 수 없습니다."));

            // Act & Assert
            CoreException result = assertThrows(CoreException.class,
                () -> productV1Controller.getProduct(999L)
            );
            assertThat(result.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }
    }

    @DisplayName("어드민 상품 목록 조회 시, ")
    @Nested
    class GetProducts {

        @DisplayName("brandId 없이 요청하면, 전체 상품 목록이 ProductAdminResponse로 반환된다.")
        @Test
        void returnsProductAdminResponsePage_whenBrandIdIsNull() {
            // Arrange
            ZonedDateTime now = ZonedDateTime.now();
            ProductInfo info = new ProductInfo(
                1L, 2L, "에어맥스", "편안한 운동화", 100_000L, 5,
                "나이키", true, 10, now, now
            );
            when(productFacade.getProducts(null, "latest", 0, 20))
                .thenReturn(new PageImpl<>(List.of(info)));

            // Act
            var result = productV1Controller.getProducts(null, 0, 20);

            // Assert
            Page<ProductV1Dto.ProductAdminResponse> page = result.data();
            assertThat(page.getContent()).hasSize(1);
            ProductV1Dto.ProductAdminResponse response = page.getContent().get(0);
            assertThat(response.name()).isEqualTo("에어맥스");
            assertThat(response.brandName()).isEqualTo("나이키");
            assertThat(response.stock()).isEqualTo(10);
            assertThat(response.createdAt()).isEqualTo(now);
        }

        @DisplayName("brandId를 지정하면, 해당 브랜드의 상품 목록만 반환된다.")
        @Test
        void returnsFilteredProducts_whenBrandIdIsProvided() {
            // Arrange
            ZonedDateTime now = ZonedDateTime.now();
            ProductInfo info = new ProductInfo(
                1L, 2L, "에어맥스", "편안한 운동화", 100_000L, 5,
                "나이키", true, 10, now, now
            );
            when(productFacade.getProducts(2L, "latest", 0, 20))
                .thenReturn(new PageImpl<>(List.of(info)));

            // Act
            var result = productV1Controller.getProducts(2L, 0, 20);

            // Assert
            assertThat(result.data().getContent()).hasSize(1);
            assertThat(result.data().getContent().get(0).brandId()).isEqualTo(2L);
        }
    }

    @DisplayName("상품 수정 시, ")
    @Nested
    class UpdateProduct {

        @DisplayName("정상 요청이면, 성공 응답을 반환한다.")
        @Test
        void returnsSuccess_whenInputIsValid() {
            // Arrange
            var request = new ProductV1Dto.UpdateRequest(null, "조던", "농구화", 200_000L);
            doNothing().when(productFacade).updateProduct(1L, "조던", "농구화", 200_000L);

            // Act
            var result = productV1Controller.updateProduct(1L, request);

            // Assert
            assertThat(result.data()).isNull();
        }

        @DisplayName("요청에 brandId가 포함되면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenBrandIdIncluded() {
            // Arrange
            var request = new ProductV1Dto.UpdateRequest(1L, "조던", "농구화", 200_000L);

            // Act & Assert
            CoreException ex = assertThrows(CoreException.class,
                () -> productV1Controller.updateProduct(1L, request));
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("상품 삭제 시, ")
    @Nested
    class DeleteProduct {

        @DisplayName("존재하는 상품이면, 성공 응답을 반환한다.")
        @Test
        void returnsSuccess_whenProductExists() {
            // Arrange
            doNothing().when(productFacade).deleteProduct(1L);

            // Act
            var result = productV1Controller.deleteProduct(1L);

            // Assert
            assertThat(result.data()).isNull();
        }

        @DisplayName("존재하지 않는 상품이면, NOT_FOUND 예외가 전파된다.")
        @Test
        void propagatesNotFound_whenProductDoesNotExist() {
            // Arrange
            doThrow(new CoreException(ErrorType.NOT_FOUND, "상품을 찾을 수 없습니다."))
                .when(productFacade).deleteProduct(999L);

            // Act & Assert
            CoreException ex = assertThrows(CoreException.class,
                () -> productV1Controller.deleteProduct(999L));
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }
    }
}
