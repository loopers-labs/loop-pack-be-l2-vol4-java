package com.loopers.brand.application;

import com.loopers.brand.domain.Brand;
import com.loopers.brand.domain.BrandRepository;
import com.loopers.product.domain.Product;
import com.loopers.product.domain.ProductRepository;
import com.loopers.product.domain.ProductStock;
import com.loopers.product.domain.ProductStockRepository;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest
class BrandServiceCascadeIntegrationTest {

    private final BrandAdminService brandAdminService;
    private final BrandRepository brandRepository;
    private final ProductRepository productRepository;
    private final ProductStockRepository productStockRepository;
    private final DatabaseCleanUp databaseCleanUp;

    @Autowired
    public BrandServiceCascadeIntegrationTest(
            BrandAdminService brandAdminService,
            BrandRepository brandRepository,
            ProductRepository productRepository,
            ProductStockRepository productStockRepository,
            DatabaseCleanUp databaseCleanUp
    ) {
        this.brandAdminService = brandAdminService;
        this.brandRepository = brandRepository;
        this.productRepository = productRepository;
        this.productStockRepository = productStockRepository;
        this.databaseCleanUp = databaseCleanUp;
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @Test
    @DisplayName("brand 삭제 시 해당 brand 의 product 와 stock 도 모두 soft delete 된다")
    void givenBrandWithProductsAndStocks_whenDelete_thenAllAreSoftDeleted() {
        Brand brand = brandRepository.save(Brand.create("루퍼스", "설명", null));
        Product p1 = productRepository.save(Product.create(brand.getId(), "셔츠", "설명", 10_000L, null));
        Product p2 = productRepository.save(Product.create(brand.getId(), "바지", "설명", 20_000L, null));
        productStockRepository.save(ProductStock.create(p1.getId(), 50));
        productStockRepository.save(ProductStock.create(p2.getId(), 30));

        brandAdminService.delete(brand.getId());

        assertAll(
                () -> assertThat(brandRepository.findById(brand.getId())).isEmpty(),
                () -> assertThat(productRepository.findById(p1.getId())).isEmpty(),
                () -> assertThat(productRepository.findById(p2.getId())).isEmpty(),
                () -> assertThat(productStockRepository.findByProductId(p1.getId())).isEmpty(),
                () -> assertThat(productStockRepository.findByProductId(p2.getId())).isEmpty()
        );
    }

    @Test
    @DisplayName("다른 brand 의 product 는 cascade 영향을 받지 않는다")
    void givenMultipleBrands_whenDeleteOne_thenOnlyThatBrandsProductsAreSoftDeleted() {
        Brand targetBrand = brandRepository.save(Brand.create("타겟", "설명", null));
        Brand otherBrand = brandRepository.save(Brand.create("다른브랜드", "설명", null));
        Product targetProduct = productRepository.save(Product.create(targetBrand.getId(), "셔츠", "설명", 10_000L, null));
        Product otherProduct = productRepository.save(Product.create(otherBrand.getId(), "바지", "설명", 20_000L, null));
        productStockRepository.save(ProductStock.create(targetProduct.getId(), 50));
        productStockRepository.save(ProductStock.create(otherProduct.getId(), 30));

        brandAdminService.delete(targetBrand.getId());

        assertAll(
                () -> assertThat(brandRepository.findById(otherBrand.getId())).isPresent(),
                () -> assertThat(productRepository.findById(otherProduct.getId())).isPresent(),
                () -> assertThat(productStockRepository.findByProductId(otherProduct.getId())).isPresent()
        );
    }
}
