package com.loopers.application.product;

import com.loopers.domain.brand.BrandService;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductDetail;
import com.loopers.domain.product.ProductDisplayService;
import com.loopers.domain.product.ProductService;
import com.loopers.domain.product.ProductSortType;
import com.loopers.domain.shared.Money;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@RequiredArgsConstructor
@Component
public class ProductFacade {
    private final ProductService productService;
    private final ProductDisplayService productDisplayService;
    private final BrandService brandService;

    public ProductInfo createProduct(String name, String description, Long price, Integer stock, Long brandId) {
        if (!brandService.existsBrand(brandId)) {
            throw new CoreException(ErrorType.NOT_FOUND, "[brandId = " + brandId + "] 등록된 브랜드가 아닙니다.");
        }
        Product product = productService.createProduct(name, description, Money.of(price), stock, brandId);
        return ProductInfo.from(product);
    }

    public ProductDetailInfo getProductDetail(Long id) {
        ProductDetail detail = productDisplayService.getProductDetail(id);
        return ProductDetailInfo.from(detail);
    }

    public List<ProductDetailInfo> getProducts(Long brandId, String sort, int page, int size) {
        ProductSortType sortType = ProductSortType.from(sort);
        // 정렬(LIKES_DESC 포함)은 모두 Repository 가 책임진다.
        List<Product> products = productService.getProducts(brandId, sortType, page, size);
        return productDisplayService.getProductDetails(products).stream()
            .map(ProductDetailInfo::from)
            .toList();
    }

    public ProductInfo updateProduct(Long id, String name, String description, Long price, Integer stock) {
        Product product = productService.updateProduct(id, name, description, Money.of(price), stock);
        return ProductInfo.from(product);
    }

    public void deleteProduct(Long id) {
        productService.deleteProduct(id);
    }
}
