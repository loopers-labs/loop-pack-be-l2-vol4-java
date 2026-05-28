package com.loopers.application.product;

import com.loopers.application.brand.BrandService;
import com.loopers.application.product.ProductService;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductSort;
import com.loopers.domain.product.ProductStock;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Component
public class ProductFacade {

    private final ProductService productService;
    private final BrandService brandService;

    public ProductInfo getProduct(Long id) {
        Product product = productService.getProduct(id);
        return ProductInfo.from(product);
    }

    public ProductInfo getProductWithStock(Long id) {
        Product product = productService.getProduct(id);
        ProductStock stock = productService.getProductStock(id);
        return ProductInfo.from(product, stock);
    }


    public Page<ProductInfo> getProducts(Long brandId, ProductSort sort, Pageable pageable) {
        if (brandId != null) {
            brandService.getBrand(brandId);
        }
        return productService.getProducts(brandId, sort, pageable).map(ProductInfo::from);
    }

    @Transactional
    public ProductInfo createProduct(ProductCommand.Create command) {
        brandService.getBrand(command.brandId());
        Product product = productService.createProduct(
            command.brandId(), command.name(), command.price(), command.stock());
        ProductStock stock = productService.getProductStock(product.getId());
        return ProductInfo.from(product, stock);
    }

    @Transactional
    public ProductInfo updateProduct(Long id, ProductCommand.Update command) {
        brandService.getBrand(command.brandId());
        Product product = productService.updateProduct(
            id, command.brandId(), command.name(), command.price(), command.stock());
        ProductStock stock = productService.getProductStock(product.getId());
        return ProductInfo.from(product, stock);
    }

    @Transactional
    public void deleteProduct(Long id) {
        productService.deleteProduct(id);
    }

}
