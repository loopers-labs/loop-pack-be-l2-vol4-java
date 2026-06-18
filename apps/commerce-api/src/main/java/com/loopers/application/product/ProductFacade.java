package com.loopers.application.product;

import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.brand.BrandService;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductService;
import com.loopers.domain.product.ProductSortType;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@RequiredArgsConstructor
@Component
public class ProductFacade {

    private final ProductService productService;
    private final BrandService brandService;

    public ProductInfo createProduct(Long brandId, String name, String description, Long price, Integer stock, String imageUrl) {
        brandService.getBrand(brandId);
        ProductModel product = productService.createProduct(brandId, name, description, price, stock, imageUrl);
        return ProductInfo.from(product);
    }

    public ProductInfo getProduct(Long id) {
        ProductModel product = productService.getProduct(id);
        return ProductInfo.from(product);
    }

    public List<ProductInfo> getAllProducts() {
        List<ProductModel> products = productService.getAllProducts();
        return products.stream()
            .map(ProductInfo::from)
            .toList();
    }

    public ProductInfo updateProduct(Long id, String name, String description, Long price, Integer stock, String imageUrl) {
        ProductModel product = productService.updateProduct(id, name, description, price, stock, imageUrl);
        return ProductInfo.from(product);
    }

    public void deleteProduct(Long id) {
        productService.deleteProduct(id);
    }

    public List<ProductDetailInfo> getProductsWithDetail(Long brandId, ProductSortType sort) {
        return productService.getActiveProducts(brandId).stream()
            .map(this::toDetailInfo)
            .filter(Objects::nonNull)
            .sorted(comparatorOf(sort))
            .toList();
    }

    public ProductDetailInfo getProductWithDetail(Long productId) {
        ProductModel product = productService.getProduct(productId);
        BrandModel brand = brandService.findBrand(product.getBrandId())
            .filter(b -> !b.isSuspended())
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "계약 중지되었거나 삭제된 브랜드의 상품은 조회할 수 없습니다."));
        return ProductDetailInfo.from(product, brand, product.getLikeCount());
    }

    private ProductDetailInfo toDetailInfo(ProductModel product) {
        Optional<BrandModel> brandOpt = brandService.findBrand(product.getBrandId());
        if (brandOpt.isEmpty() || brandOpt.get().isSuspended()) {
            return null;
        }
        return ProductDetailInfo.from(product, brandOpt.get(), product.getLikeCount());
    }

    private Comparator<ProductDetailInfo> comparatorOf(ProductSortType sort) {
        return switch (sort) {
            case PRICE_ASC -> Comparator.comparingLong(ProductDetailInfo::price);
            case LIKES_DESC -> Comparator.comparingLong(ProductDetailInfo::likeCount).reversed();
            case LATEST -> Comparator.comparing(ProductDetailInfo::createdAt).reversed();
        };
    }
}
