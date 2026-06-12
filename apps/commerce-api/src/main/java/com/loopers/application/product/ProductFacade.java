package com.loopers.application.product;

import com.loopers.domain.brand.BrandService;
import com.loopers.domain.product.ProductDetailService;
import com.loopers.domain.product.ProductDetailService.ProductDetail;
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
    private final ProductDetailService productDetailService;

    public ProductInfo createProduct(ProductCriteria.Create criteria) {
        brandService.requireExists(criteria.brandId());
        ProductModel product = productService.createProduct(
            criteria.brandId(),
            criteria.name(),
            criteria.description(),
            criteria.price(),
            criteria.stock(),
            criteria.imageUrl()
        );
        return ProductInfo.from(product);
    }

    public ProductInfo getProduct(Long id) {
        ProductModel product = productService.getProduct(id);
        return ProductInfo.from(product);
    }

    /**
     * 상품 상세: Product + Brand + likeCount 를 도메인 서비스에서 조합.
     */
    public ProductDetailInfo getProductDetail(Long id) {
        ProductDetail detail = productDetailService.getDetail(id);
        return ProductDetailInfo.from(detail);
    }

    public List<ProductInfo> listProducts(ProductCriteria.List criteria) {
        List<ProductModel> products = productService.listProducts(criteria.sortType(), criteria.brandId());
        return products.stream()
            .map(ProductInfo::from)
            .toList();
    }
}
