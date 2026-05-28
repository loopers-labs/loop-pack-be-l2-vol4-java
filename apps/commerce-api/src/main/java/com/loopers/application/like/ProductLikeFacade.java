package com.loopers.application.like;

import com.loopers.application.product.ProductInfo;
import com.loopers.domain.brand.Brand;
import com.loopers.domain.brand.BrandService;
import com.loopers.domain.like.ProductLikeService;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductBrandProcessService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@RequiredArgsConstructor
@Component
public class ProductLikeFacade {

    private final ProductLikeService productLikeService;
    private final BrandService brandService;
    private final ProductBrandProcessService productBrandProcessService;

    @Transactional
    public void likeProduct(String userLoginId, Long productId) {
        productLikeService.likeProduct(userLoginId, productId);
    }

    @Transactional
    public void unlikeProduct(String userLoginId, Long productId) {
        productLikeService.unlikeProduct(userLoginId, productId);
    }

    @Transactional(readOnly = true)
    public List<ProductInfo> getLikedProducts(String userLoginId) {
        List<Product> products = productLikeService.getLikedProducts(userLoginId);
        List<Long> brandIds = productBrandProcessService.getBrandIds(products);
        List<Brand> brands = brandService.getBrandsByIds(brandIds);
        return productBrandProcessService.getProductDetailViews(products, brands).stream()
            .map(ProductInfo::from)
            .toList();
    }
}
