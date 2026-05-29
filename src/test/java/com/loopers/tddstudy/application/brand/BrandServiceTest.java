package com.loopers.tddstudy.application.brand;

import com.loopers.tddstudy.application.product.ProductService;
import com.loopers.tddstudy.domain.brand.Brand;
import com.loopers.tddstudy.domain.product.Product;
import com.loopers.tddstudy.support.FakeBrandRepository;
import com.loopers.tddstudy.support.FakeProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;


import java.util.List;

import static org.assertj.core.api.Assertions.*;



class BrandServiceTest {

    private FakeBrandRepository fakeBrandRepository;
    private FakeProductRepository fakeProductRepository;
    private ProductService productService;
    private BrandService brandService;

    @BeforeEach
    void setUp() {
        fakeBrandRepository = new FakeBrandRepository();
        fakeProductRepository = new FakeProductRepository();
        productService = new ProductService(fakeProductRepository, fakeBrandRepository);
        brandService = new BrandService(fakeBrandRepository, productService);
    }

    @Test
    @DisplayName("브랜드를 생성하면 DRAFT 상태로 저장된다")
    void brand_creat_success() {
        Brand brand = brandService.create("나이키", "스포츠 브랜드", 1L);

        assertThat(brand.getName()).isEqualTo("나이키");
        assertThat(brand.getStatus()).isEqualTo("DRAFT");
        assertThat(brand.getOwnerId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("ACTIVE 브랜드는 일반 유저도 조회할 수 있다")
    void brand_serch_active() {
        Brand saved = brandService.create("나이키", "스포츠 브랜드", 1L);
        brandService.publish(saved.getId());

        Brand found = brandService.getById(saved.getId(), 2L, "USER");

        assertThat(found.getName()).isEqualTo("나이키");
    }

    @Test
    @DisplayName("DRAFT 브랜드는 소유자가 조회할 수 있다")
    void brand_serch_DRAFT_user_success() {
        Brand saved = brandService.create("나이키", "스포츠 브랜드", 1L);

        Brand found = brandService.getById(saved.getId(), 1L, "USER");

        assertThat(found.getName()).isEqualTo("나이키");
    }

    @Test
    @DisplayName("DRAFT 브랜드는 타인이 조회하면 예외가 발생한다")
    void brand_serch_DRAFT_athere_exception() {
        Brand saved = brandService.create("나이키", "스포츠 브랜드", 1L);

        assertThatThrownBy(() -> brandService.getById(saved.getId(), 2L, "USER"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("존재하지 않는 브랜드 조회 시 예외가 발생한다")
    void brand_serch_nullBrand_exception() {
        assertThatThrownBy(() -> brandService.getById(999L, 1L, "USER"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("OPERATOR 는 모든 브랜드 목록을 조회할 수 있다")
    void brand_list_OPERATOR_all_serch() {
        brandService.create("나이키", "스포츠", 1L);
        Brand b2 = brandService.create("아디다스", "독일", 2L);
        brandService.publish(b2.getId());

        List<Brand> brands = brandService.getAll(99L, "OPERATOR");

        assertThat(brands).hasSize(2);
    }

    @Test
    @DisplayName("일반 유저는 ACTIVE 브랜드만 조회된다")
    void brand_list_user_ACTIVE만_serch() {
        brandService.create("나이키", "스포츠", 1L);
        Brand b2 = brandService.create("아디다스", "독일", 2L);
        brandService.publish(b2.getId());

        List<Brand> brands = brandService.getAll(3L, "USER");

        assertThat(brands).hasSize(1);
        assertThat(brands.get(0).getName()).isEqualTo("아디다스");
    }

    @Test
    @DisplayName("브랜드를 수정할 수 있다")
    void brand_modify_success() {
        Brand saved = brandService.create("나이키", "스포츠 브랜드", 1L);

        brandService.update(saved.getId(), "아디다스", "독일 스포츠 브랜드");

        Brand updated = brandService.getById(saved.getId(), 1L, "USER");
        assertThat(updated.getName()).isEqualTo("아디다스");
    }

    @Test
    @DisplayName("브랜드를 삭제하면 OPERATOR 만 조회할 수 있다")
    void brnad_delete_success() {
        Brand saved = brandService.create("나이키", "스포츠 브랜드", 1L);
        brandService.publish(saved.getId());

        brandService.delete(saved.getId());

        assertThatThrownBy(() -> brandService.getById(saved.getId(), 1L, "USER"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("브랜드 삭제 시 소속 상품도 함께 삭제된다")
    void delete_brand_also_deletes_products() {
        Brand brand = brandService.create("나이키", "스포츠", 1L);
        productService.create("나이키 운동화", 50000, 10, brand.getId());
        productService.create("나이키 티셔츠", 30000, 20, brand.getId());

        brandService.delete(brand.getId());

        List<Product> products = productService.getAll(99L, "OPERATOR", null, "latest");
        assertThat(products).allMatch(Product::isDeleted);
    }
}
