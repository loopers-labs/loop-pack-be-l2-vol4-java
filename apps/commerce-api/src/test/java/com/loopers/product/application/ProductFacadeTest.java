package com.loopers.product.application;

import com.loopers.brand.application.BrandService;
import com.loopers.brand.domain.Brand;
import com.loopers.like.application.LikeService;
import com.loopers.like.domain.Like;
import com.loopers.product.domain.Product;
import com.loopers.product.domain.ProductSortType;
import com.loopers.support.fake.FakeBrandRepository;
import com.loopers.support.fake.FakeLikeRepository;
import com.loopers.support.fake.FakeProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;

import static org.assertj.core.api.Assertions.assertThat;

class ProductFacadeTest {

    private FakeProductRepository productRepository;
    private FakeBrandRepository brandRepository;
    private FakeLikeRepository likeRepository;
    private ProductFacade productFacade;

    @BeforeEach
    void setUp() {
        productRepository = new FakeProductRepository();
        brandRepository = new FakeBrandRepository();
        likeRepository = new FakeLikeRepository();

        ProductService productService = new ProductService(productRepository);
        BrandService brandService = new BrandService(brandRepository);
        LikeService likeService = new LikeService(likeRepository);

        productFacade = new ProductFacade(productService, brandService, likeService);
    }

    @DisplayName("상품 상세 조회 시 브랜드 정보와 좋아요 수가 함께 제공된다.")
    @Test
    void getProductDetail_combinesBrandAndLikeCount() {
        Brand brand = brandRepository.save(new Brand("나이키", "브랜드"));
        Product product =
            productRepository.save(new Product(brand.getId(), "운동화", "설명", 50_000L, 10));
        likeRepository.save(new Like(1L, product.getId()));
        likeRepository.save(new Like(2L, product.getId()));

        ProductDetailInfo detail = productFacade.getProductDetail(product.getId());

        assertThat(detail.brandName()).isEqualTo("나이키");
        assertThat(detail.likeCount()).isEqualTo(2L);
        assertThat(detail.price()).isEqualTo(50_000L);
    }

    @DisplayName("상품 목록을 likes_desc 로 조회하면 좋아요 수 내림차순으로 정렬된다.")
    @Test
    void getProducts_sortsByLikesDesc() {
        Brand brand = brandRepository.save(new Brand("브랜드", "설명"));
        Product a = productRepository.save(new Product(brand.getId(), "A", "설명", 1_000L, 10));
        Product b = productRepository.save(new Product(brand.getId(), "B", "설명", 1_000L, 10));

        likeRepository.save(new Like(1L, b.getId()));
        likeRepository.save(new Like(2L, b.getId()));
        likeRepository.save(new Like(3L, a.getId()));

        Page<ProductDetailInfo> result =
            productFacade.getProducts(null, ProductSortType.LIKES_DESC, 0, 20);

        assertThat(result.getContent())
            .extracting(ProductDetailInfo::id)
            .containsExactly(b.getId(), a.getId());
    }

    @DisplayName("상품 목록은 page/size 로 페이지네이션된다.")
    @Test
    void getProducts_paginates() {
        Brand brand = brandRepository.save(new Brand("브랜드", "설명"));
        for (int i = 0; i < 5; i++) {
            productRepository.save(new Product(brand.getId(), "P" + i, "설명", 1_000L, 10));
        }

        Page<ProductDetailInfo> page0 =
            productFacade.getProducts(null, ProductSortType.LATEST, 0, 2);
        Page<ProductDetailInfo> page2 =
            productFacade.getProducts(null, ProductSortType.LATEST, 2, 2);

        assertThat(page0.getContent()).hasSize(2);
        assertThat(page0.getTotalElements()).isEqualTo(5);
        assertThat(page0.getTotalPages()).isEqualTo(3);
        assertThat(page0.isFirst()).isTrue();
        assertThat(page0.isLast()).isFalse();

        assertThat(page2.getContent()).hasSize(1);
        assertThat(page2.isFirst()).isFalse();
        assertThat(page2.isLast()).isTrue();
    }

    @DisplayName("관리자 상품 목록은 brandId 로 필터링되고 운영용 상품 정보를 반환한다.")
    @Test
    void getProductsForAdmin_filtersByBrand() {
        Brand brandA = brandRepository.save(new Brand("A", "설명"));
        Brand brandB = brandRepository.save(new Brand("B", "설명"));
        productRepository.save(new Product(brandA.getId(), "a1", "설명", 1_000L, 3));
        productRepository.save(new Product(brandB.getId(), "b1", "설명", 1_000L, 7));

        Page<ProductInfo> result = productFacade.getProductsForAdmin(brandA.getId(), 0, 20);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).brandId()).isEqualTo(brandA.getId());
        assertThat(result.getContent().get(0).stock()).isEqualTo(3);
    }
}
