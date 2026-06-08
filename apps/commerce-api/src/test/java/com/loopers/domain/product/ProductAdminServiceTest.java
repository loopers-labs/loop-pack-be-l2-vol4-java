package com.loopers.domain.product;

import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.brand.BrandRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class ProductAdminServiceTest {

    @InjectMocks
    private ProductAdminService productAdminService;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private BrandRepository brandRepository;

    @Test
    @DisplayName("상품 정보를 입력하면 상품과 재고가 정상적으로 등록된다.")
    void registerProduct_ShouldSaveProductAndStock() {
        // given
        Long brandId = 1L;
        String name = "Air Jordan";
        BigDecimal price = new BigDecimal("200000");
        int initialStock = 100;
        
        given(brandRepository.findById(brandId)).willReturn(Optional.of(new BrandModel("Nike")));
        
        ProductModel product = new ProductModel(brandId, name, price);
        ReflectionTestUtils.setField(product, "id", 1L);
        given(productRepository.save(any(ProductModel.class))).willReturn(product);

        // when
        Long productId = productAdminService.registerProduct(brandId, name, price, initialStock);

        // then
        assertThat(productId).isEqualTo(1L);
    }

    @Test
    @DisplayName("존재하는 상품의 정보를 수정하면 업데이트된다.")
    void updateProduct_ShouldUpdateInfo() {
        // given
        Long productId = 1L;
        String newName = "Air Jordan 2";
        BigDecimal newPrice = new BigDecimal("250000");
        ProductModel product = new ProductModel(10L, "Air Jordan", new BigDecimal("200000"));
        ReflectionTestUtils.setField(product, "id", productId);
        
        given(productRepository.findById(productId)).willReturn(Optional.of(product));

        // when
        productAdminService.updateProduct(productId, newName, newPrice);

        // then
        assertThat(product.getName()).isEqualTo(newName);
        assertThat(product.getPrice()).isEqualTo(newPrice);
    }

    @Test
    @DisplayName("상품을 삭제하면 논리 삭제(isDeleted=true) 처리된다.")
    void deleteProduct_ShouldMarkAsDeleted() {
        // given
        Long productId = 1L;
        ProductModel product = new ProductModel(10L, "Air Jordan", new BigDecimal("200000"));
        ReflectionTestUtils.setField(product, "id", productId);
        
        given(productRepository.findById(productId)).willReturn(Optional.of(product));

        // when
        productAdminService.deleteProduct(productId);

        // then
        assertThat(product.isDeleted()).isTrue();
    }

    @Test
    @DisplayName("존재하지 않는 브랜드 ID로 상품을 등록하려 하면 예외가 발생한다.")
    void registerProduct_NonExistentBrand_ShouldThrowException() {
        // given
        Long brandId = 999L;
        given(brandRepository.findById(brandId)).willReturn(Optional.empty());

        // when & then
        CoreException exception = assertThrows(CoreException.class, () -> 
                productAdminService.registerProduct(brandId, "Air Jordan", new BigDecimal("200000"), 100)
        );
        assertThat(exception.getErrorType()).isEqualTo(ErrorType.BRAND_NOT_FOUND);
    }
}
