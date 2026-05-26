package com.loopers.application.product;

import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.brand.BrandService;
import com.loopers.domain.product.ProductCatalogService;
import com.loopers.domain.product.ProductDetail;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductService;
import com.loopers.domain.product.ProductSort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@RequiredArgsConstructor
@Component
public class ProductFacade {
    private final ProductService productService;
    private final BrandService brandService;
    private final ProductCatalogService productCatalogService;

    public ProductInfo createProduct(Long brandId, String name, String description, Long price, Integer stock) {
        BrandModel brand = brandService.getBrand(brandId);
        ProductModel product = productService.createProduct(brandId, name, description, price, stock);
        return ProductInfo.from(new ProductDetail(product, brand));
    }

    public ProductInfo getProduct(Long id) {
        ProductDetail productDetail = productCatalogService.getProductDetail(id);
        return ProductInfo.from(productDetail);
    }

    public List<ProductInfo> getAllProducts() {
        return getAllProducts(null);
    }

    public List<ProductInfo> getAllProducts(String sort) {
        List<ProductDetail> productDetails = productCatalogService.getProductDetails(ProductSort.from(sort));
        return productDetails.stream()
            .map(ProductInfo::from)
            .toList();
    }

    public ProductInfo updateProduct(Long id, String name, String description, Long price, Integer stock) {
        ProductModel product = productService.updateProduct(id, name, description, price, stock);
        BrandModel brand = brandService.getBrand(product.getBrandId());
        return ProductInfo.from(new ProductDetail(product, brand));
    }

    public void deleteProduct(Long id) {
        productService.deleteProduct(id);
    }
}
