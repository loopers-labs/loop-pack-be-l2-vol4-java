package com.loopers.tddstudy.domain.product;

import com.loopers.tddstudy.domain.brand.Brand;
import com.loopers.tddstudy.support.FakeBrandRepository;
import com.loopers.tddstudy.support.FakeProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class ProductDomainServiceTest {

    private FakeProductRepository fakeProductRepository;
    private FakeBrandRepository fakeBrandRepository;
    private ProductDomainService productDomainService;

    @BeforeEach
    void setUp() {
        fakeProductRepository = new FakeProductRepository();
        fakeBrandRepository = new FakeBrandRepository();
        productDomainService = new ProductDomainService(fakeProductRepository, fakeBrandRepository);
    }

    @Test
    @DisplayName("상품 상세 조회 시 브랜드 정보가 포함된다")
    void get_product_detail_includes_brand_info() {
        Brand brand = fakeBrandRepository.save(new Brand("나이키", "스포츠 브랜드", 1L));
        Product product = fakeProductRepository.save(new Product("나이키 운동화", 50000, 10, brand.getId()));

        ProductDetail detail = productDomainService.getDetail(product.getId());

        assertThat(detail.productName()).isEqualTo("나이키 운동화");
        assertThat(detail.brandName()).isEqualTo("나이키");
        assertThat(detail.likeCount()).isEqualTo(0);
    }

    @Test
    @DisplayName("존재하지 않는 상품 조회 시 예외가 발생한다")
    void get_product_detail_not_found_throws_exception() {
        assertThatThrownBy(() -> productDomainService.getDetail(999L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("상품을 찾을 수 없습니다.");
    }

    @Test
    @DisplayName("브랜드가 삭제된 상품 조회 시 예외가 발생한다")
    void get_product_detail_deleted_brand_throws_exception() {
        Brand brand = fakeBrandRepository.save(new Brand("나이키", "스포츠 브랜드", 1L));
        brand.softDelete();
        fakeBrandRepository.save(brand);
        Product product = fakeProductRepository.save(new Product("나이키 운동화", 50000, 10, brand.getId()));

        assertThatThrownBy(() -> productDomainService.getDetail(product.getId()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("브랜드를 찾을 수 없습니다.");
    }
}
