package com.loopers.application.product;

import com.loopers.domain.product.ProductAdminService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ProductAdminFacadeTest {

    @InjectMocks
    private ProductAdminFacade productAdminFacade;

    @Mock
    private ProductAdminService productAdminService;

    @Test
    @DisplayName("상품 등록 요청 시 어드민 서비스의 등록 로직이 호출된다.")
    void registerProduct_ShouldCallService() {
        // given
        Long brandId = 1L;
        String name = "Air Jordan";
        BigDecimal price = new BigDecimal("200000");
        int initialStock = 100;
        given(productAdminService.registerProduct(brandId, name, price, initialStock)).willReturn(10L);

        // when
        Long productId = productAdminFacade.registerProduct(brandId, name, price, initialStock);

        // then
        assertThat(productId).isEqualTo(10L);
        verify(productAdminService).registerProduct(brandId, name, price, initialStock);
    }
}
