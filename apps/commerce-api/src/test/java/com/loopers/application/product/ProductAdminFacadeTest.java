package com.loopers.application.product;

import com.loopers.application.brand.BrandRepository;
import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.product.ProductModel;
import com.loopers.support.error.CoreException;
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
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ProductAdminFacadeTest {

    @InjectMocks
    private ProductAdminFacade productAdminFacade;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private BrandRepository brandRepository;

    @Mock
    private org.springframework.data.redis.core.RedisTemplate<String, String> defaultRedisTemplate;

    @Test
    @DisplayName("상품 등록 요청 시 브랜드가 존재하면 상품과 재고가 생성 및 저장된다.")
    void registerProduct_ShouldSaveProduct() {
        // given
        Long brandId = 1L;
        String name = "Air Jordan";
        BigDecimal price = new BigDecimal("200000");
        int initialStock = 100;

        BrandModel brand = new BrandModel("Nike");
        ReflectionTestUtils.setField(brand, "id", brandId);
        given(brandRepository.findById(brandId)).willReturn(Optional.of(brand));

        ProductModel product = new ProductModel(brandId, name, price);
        ReflectionTestUtils.setField(product, "id", 10L);
        given(productRepository.save(any(ProductModel.class))).willReturn(product);

        // when
        Long productId = productAdminFacade.registerProduct(brandId, name, price, initialStock);

        // then
        assertThat(productId).isEqualTo(10L);
        verify(brandRepository).findById(brandId);
        verify(productRepository).save(any(ProductModel.class));
    }

    @Test
    @DisplayName("존재하는 상품의 정보를 수정하면 정보가 업데이트된다.")
    void updateProduct_ShouldUpdateInfo() {
        // given
        Long productId = 10L;
        String newName = "Air Jordan 2";
        BigDecimal newPrice = new BigDecimal("220000");
        ProductModel product = new ProductModel(1L, "Air Jordan", new BigDecimal("200000"));
        ReflectionTestUtils.setField(product, "id", productId);

        given(productRepository.findById(productId)).willReturn(Optional.of(product));

        // when
        productAdminFacade.updateProduct(productId, newName, newPrice);

        // then
        assertThat(product.getName()).isEqualTo(newName);
        assertThat(product.getPrice()).isEqualTo(newPrice);
        verify(productRepository).save(product);
    }

    @Test
    @DisplayName("상품을 삭제하면 논리 삭제 처리된다.")
    void deleteProduct_ShouldMarkAsDeleted() {
        // given
        Long productId = 10L;
        ProductModel product = new ProductModel(1L, "Air Jordan", new BigDecimal("200000"));
        ReflectionTestUtils.setField(product, "id", productId);

        given(productRepository.findById(productId)).willReturn(Optional.of(product));

        // when
        productAdminFacade.deleteProduct(productId);

        // then
        assertThat(product.isDeleted()).isTrue();
        verify(productRepository).save(product);
    }
}
