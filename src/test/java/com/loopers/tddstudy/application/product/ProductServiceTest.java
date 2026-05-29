package com.loopers.tddstudy.application.product;

import com.loopers.tddstudy.domain.brand.Brand;
import com.loopers.tddstudy.domain.product.Product;
import com.loopers.tddstudy.support.FakeBrandRepository;
import com.loopers.tddstudy.support.FakeProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

class ProductServiceTest {

    private FakeProductRepository fakeProductRepository;
    private FakeBrandRepository fakeBrandRepository;
    private ProductService productService;

    @BeforeEach
    void setUp() {
        fakeProductRepository = new FakeProductRepository();
        fakeBrandRepository = new FakeBrandRepository();
        productService = new ProductService(fakeProductRepository, fakeBrandRepository);
    }

    @Test
    @DisplayName("상품을 생성하면 DRAFT 상태로 저장된다")
    void create_product_success() {
        Brand brand = fakeBrandRepository.save(new Brand("나이키", "스포츠", 1L));

        Product product = productService.create("나이키 운동화", 50000, 10, brand.getId());

        assertThat(product.getName()).isEqualTo("나이키 운동화");
        assertThat(product.getStatus()).isEqualTo("DRAFT");
    }

    @Test
    @DisplayName("존재하지 않는 브랜드로 상품 생성 시 예외가 발생한다")
    void create_product_with_nonexistent_brand_throws_exception() {
        assertThatThrownBy(() -> productService.create("나이키 운동화", 50000, 10, 999L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("브랜드를 찾을 수 없습니다.");
    }

    @Test
    @DisplayName("삭제된 브랜드로 상품 생성 시 예외가 발생한다")
    void create_product_with_deleted_brand_throws_exception() {
        Brand brand = fakeBrandRepository.save(new Brand("나이키", "스포츠", 1L));
        brand.softDelete();
        fakeBrandRepository.save(brand);

        assertThatThrownBy(() -> productService.create("나이키 운동화", 50000, 10, brand.getId()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("삭제된 브랜드에는 상품을 등록할 수 없습니다.");
    }

    @Test
    @DisplayName("ACTIVE 상품은 일반 유저도 조회할 수 있다")
    void get_active_product_by_normal_user_success() {
        Brand brand = fakeBrandRepository.save(new Brand("나이키", "스포츠", 1L));
        Product saved = productService.create("나이키 운동화", 50000, 10, brand.getId());
        productService.publish(saved.getId());

        Product found = productService.getById(saved.getId(), 2L, "USER");

        assertThat(found.getName()).isEqualTo("나이키 운동화");
    }

    @Test
    @DisplayName("DRAFT 상품은 브랜드 소유자가 조회할 수 있다")
    void get_draft_product_by_brand_owner_success() {
        Brand brand = fakeBrandRepository.save(new Brand("나이키", "스포츠", 1L));
        Product saved = productService.create("나이키 운동화", 50000, 10, brand.getId());

        Product found = productService.getById(saved.getId(), 1L, "USER");

        assertThat(found.getName()).isEqualTo("나이키 운동화");
    }

    @Test
    @DisplayName("DRAFT 상품은 타인이 조회하면 예외가 발생한다")
    void get_draft_product_by_other_user_throws_exception() {
        Brand brand = fakeBrandRepository.save(new Brand("나이키", "스포츠", 1L));
        Product saved = productService.create("나이키 운동화", 50000, 10, brand.getId());

        assertThatThrownBy(() -> productService.getById(saved.getId(), 2L, "USER"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("존재하지 않는 상품 조회 시 예외가 발생한다")
    void get_nonexistent_product_throws_exception() {
        assertThatThrownBy(() -> productService.getById(999L, 1L, "USER"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("brandId 로 상품 목록을 필터링할 수 있다")
    void get_all_products_filtered_by_brand_id() {
        Brand brand1 = fakeBrandRepository.save(new Brand("나이키", "스포츠", 1L));
        Brand brand2 = fakeBrandRepository.save(new Brand("아디다스", "독일", 2L));

        Product p1 = productService.create("나이키 운동화", 50000, 10, brand1.getId());
        Product p2 = productService.create("아디다스 운동화", 60000, 5, brand2.getId());
        productService.publish(p1.getId());
        productService.publish(p2.getId());

        List<Product> result = productService.getAll(3L, "USER", brand1.getId(), "latest");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getName()).isEqualTo("나이키 운동화");
    }

    @Test
    @DisplayName("상품 정보를 수정할 수 있다")
    void update_product_success() {
        Brand brand = fakeBrandRepository.save(new Brand("나이키", "스포츠", 1L));
        Product saved = productService.create("나이키 운동화", 50000, 10, brand.getId());

        productService.update(saved.getId(), "나이키 슬리퍼", 30000, 20);

        Product updated = productService.getById(saved.getId(), 1L, "USER");
        assertThat(updated.getName()).isEqualTo("나이키 슬리퍼");
        assertThat(updated.getPrice()).isEqualTo(30000);
    }

    @Test
    @DisplayName("상품을 삭제하면 일반 유저는 조회할 수 없다")
    void delete_product_then_user_cannot_access() {
        Brand brand = fakeBrandRepository.save(new Brand("나이키", "스포츠", 1L));
        Product saved = productService.create("나이키 운동화", 50000, 10, brand.getId());
        productService.publish(saved.getId());

        productService.delete(saved.getId());

        assertThatThrownBy(() -> productService.getById(saved.getId(), 2L, "USER"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("브랜드 삭제 시 소속 상품도 함께 삭제된다")
    void delete_all_products_by_brand_id() {
        Brand brand = fakeBrandRepository.save(new Brand("나이키", "스포츠", 1L));
        productService.create("나이키 운동화", 50000, 10, brand.getId());
        productService.create("나이키 티셔츠", 30000, 20, brand.getId());

        productService.deleteAllByBrandId(brand.getId());

        List<Product> result = productService.getAll(99L, "OPERATOR", null, "latest");
        assertThat(result.stream().filter(Product::isDeleted)).hasSize(2);
    }

    @Test
    @DisplayName("최신순 정렬 시 마지막에 생성된 상품이 먼저 반환된다")
    void get_all_sorted_by_latest() {
        Brand brand = fakeBrandRepository.save(new Brand("나이키", "스포츠", 1L));
        Product p1 = productService.create("첫번째", 10000, 5, brand.getId());
        Product p2 = productService.create("두번째", 20000, 5, brand.getId());
        Product p3 = productService.create("세번째", 30000, 5, brand.getId());
        productService.publish(p1.getId());
        productService.publish(p2.getId());
        productService.publish(p3.getId());

        List<Product> result = productService.getAll(1L, "USER", null, "latest");

        assertThat(result.get(0).getName()).isEqualTo("세번째");
        assertThat(result.get(2).getName()).isEqualTo("첫번째");
    }

    @Test
    @DisplayName("가격 오름차순 정렬 시 가장 저렴한 상품이 먼저 반환된다")
    void get_all_sorted_by_price_asc() {
        Brand brand = fakeBrandRepository.save(new Brand("나이키", "스포츠", 1L));
        Product p1 = productService.create("비싼거", 50000, 5, brand.getId());
        Product p2 = productService.create("저렴한거", 10000, 5, brand.getId());
        Product p3 = productService.create("중간", 30000, 5, brand.getId());
        productService.publish(p1.getId());
        productService.publish(p2.getId());
        productService.publish(p3.getId());

        List<Product> result = productService.getAll(1L, "USER", null, "price_asc");

        assertThat(result.get(0).getName()).isEqualTo("저렴한거");
        assertThat(result.get(2).getName()).isEqualTo("비싼거");
    }

    @Test
    @DisplayName("좋아요 내림차순 정렬 시 좋아요가 많은 상품이 먼저 반환된다")
    void get_all_sorted_by_likes_desc() {
        Brand brand = fakeBrandRepository.save(new Brand("나이키", "스포츠", 1L));
        Product p1 = productService.create("좋아요 없음", 10000, 5, brand.getId());
        Product p2 = productService.create("좋아요 많음", 20000, 5, brand.getId());
        Product p3 = productService.create("좋아요 중간", 30000, 5, brand.getId());
        productService.publish(p1.getId());
        productService.publish(p2.getId());
        productService.publish(p3.getId());
        for (int i = 0; i < 5; i++) p2.increaseLikeCount();
        for (int i = 0; i < 3; i++) p3.increaseLikeCount();
        fakeProductRepository.save(p2);
        fakeProductRepository.save(p3);

        List<Product> result = productService.getAll(1L, "USER", null, "likes_desc");

        assertThat(result.get(0).getName()).isEqualTo("좋아요 많음");
        assertThat(result.get(2).getName()).isEqualTo("좋아요 없음");
    }
}
