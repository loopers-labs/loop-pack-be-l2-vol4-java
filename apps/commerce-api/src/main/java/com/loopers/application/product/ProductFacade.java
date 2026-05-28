package com.loopers.application.product;

import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.brand.BrandService;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductService;
import com.loopers.domain.stock.StockModel;
import com.loopers.domain.stock.StockService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.util.UUID;

@RequiredArgsConstructor
@Component
public class ProductFacade {

    private final BrandService brandService;
    private final ProductService productService;
    private final StockService stockService;

    /** 상품 등록 — 브랜드 검증 + 상품 저장 + 재고 초기화 */
    public ProductInfo create(UUID brandId, String name, String description, Long price, int initialQuantity) {
        BrandModel brand = brandService.getActive(brandId);
        ProductModel product = productService.create(brand, name, description, price);
        StockModel stock = stockService.create(product.getId(), initialQuantity);
        return ProductInfo.from(product, stock);
    }

    /** 어드민용 — 삭제된 상품 포함 */
    public ProductInfo get(UUID id) {
        ProductModel product = productService.get(id);
        StockModel stock = stockService.getByProductId(id);
        return ProductInfo.from(product, stock);
    }

    /** 고객용 — 활성 상품만 */
    public ProductInfo getActive(UUID id) {
        ProductModel product = productService.getActive(id);
        StockModel stock = stockService.getByProductId(id);
        return ProductInfo.from(product, stock);
    }

    /** 어드민 목록 — 삭제된 상품 포함 */
    public Page<ProductInfo> getList(Pageable pageable) {
        return productService.getList(pageable).map(product -> {
            StockModel stock = stockService.getByProductId(product.getId());
            return ProductInfo.from(product, stock);
        });
    }

    /** 고객 목록 — 활성 상품만 */
    public Page<ProductInfo> getActiveList(Pageable pageable) {
        return productService.getActiveList(pageable).map(product -> {
            StockModel stock = stockService.getByProductId(product.getId());
            return ProductInfo.from(product, stock);
        });
    }

    public ProductInfo update(UUID id, String name, String description, Long price) {
        ProductModel product = productService.update(id, name, description, price);
        StockModel stock = stockService.getByProductId(id);
        return ProductInfo.from(product, stock);
    }

    public void delete(UUID id) {
        productService.delete(id);
    }
}
