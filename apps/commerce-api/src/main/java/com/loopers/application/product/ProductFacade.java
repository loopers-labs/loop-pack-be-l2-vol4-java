package com.loopers.application.product;

import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.brand.BrandService;
import com.loopers.domain.product.ProductPage;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@RequiredArgsConstructor
@Component
public class ProductFacade {
    private final ProductService productService;
    private final BrandService brandService;

    public ProductInfo createProduct(Long brandId, String name, String description, Long price, Integer stock) {
        ProductModel product = productService.createProduct(brandId, name, description, price, stock);
        return ProductInfo.from(product);
    }

    public ProductInfo getProduct(Long id) {
        ProductModel product = productService.getProduct(id);
        return ProductInfo.from(product);
    }

    public ProductDisplayInfo getProductDisplay(Long id) {
        ProductModel product = productService.getProduct(id);
        BrandModel brand = brandService.getBrand(product.getBrandId());
        return ProductDisplayInfo.of(product, brand);
    }

    public ProductDetailInfo getProductDetail(Long productId) {
        ProductModel product = productService.getProduct(productId);
        BrandModel brand = brandService.getBrand(product.getBrandId());
        return ProductDetailInfo.of(product, brand);
    }

    public List<ProductInfo> getAllProducts() {
        List<ProductModel> products = productService.getAllProducts();
        return products.stream()
            .map(ProductInfo::from)
            .toList();
    }

    public ProductPageInfo searchProducts(Long brandId, String sort, String direction, Integer page, Integer size) {
        ProductPage productPage = productService.searchProducts(brandId, sort, direction, page, size);
        List<ProductDisplayInfo> content = productPage.products().stream()
            .map(product -> {
                BrandModel brand = brandService.getBrand(product.getBrandId());
                return ProductDisplayInfo.of(product, brand);
            })
            .toList();
            
        return new ProductPageInfo(
            content,
            productPage.page(),
            productPage.size(),
            productPage.totalElements(),
            productPage.totalPages()
        );
    }

    public ProductInfo updateProduct(Long id, Long brandId, String name, String description, Long price, Integer stock) {
        ProductModel product = productService.updateProduct(id, brandId, name, description, price, stock);
        return ProductInfo.from(product);
    }

    public void deleteProduct(Long id) {
        productService.deleteProduct(id);
    }
}
