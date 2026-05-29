package com.loopers.application.product;

import com.loopers.application.brand.BrandService;
import com.loopers.application.stock.StockService;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductSort;
import com.loopers.domain.stock.StockModel;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Component
public class ProductFacade {

    private final ProductService productService;
    private final StockService stockService;
    private final BrandService brandService;

    @Transactional
    public ProductInfo createProduct(String name, Long price, Long brandId, int stockQuantity) {
        brandService.getById(brandId);
        ProductModel product = productService.create(new ProductModel(name, price, brandId));
        stockService.create(new StockModel(product.getId(), stockQuantity));
        return ProductInfo.from(product);
    }

    public ProductInfo getProduct(Long id) {
        return ProductInfo.from(productService.getById(id));
    }

    public Page<ProductInfo> getProducts(Long brandId, ProductSort sort, int page, int size) {
        return productService.getAll(brandId, sort, PageRequest.of(page, size))
            .map(ProductInfo::from);
    }

    @Transactional
    public ProductInfo updateProduct(Long id, String name, Long price) {
        return ProductInfo.from(productService.update(id, name, price));
    }

    @Transactional
    public void deleteProduct(Long id) {
        productService.delete(id);
    }

    @Transactional
    public void updateStock(Long productId, int quantity) {
        productService.getById(productId);
        stockService.update(productId, quantity);
    }
}
