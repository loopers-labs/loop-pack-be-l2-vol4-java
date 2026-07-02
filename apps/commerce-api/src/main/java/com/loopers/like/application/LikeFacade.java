package com.loopers.like.application;

import com.loopers.brand.application.BrandService;
import com.loopers.brand.domain.Brand;
import com.loopers.inventory.application.InventoryService;
import com.loopers.member.application.MemberService;
import com.loopers.product.application.ProductDetailInfo;
import com.loopers.product.application.ProductDisplayService;
import com.loopers.product.application.ProductService;
import com.loopers.product.domain.Product;
import com.loopers.product.domain.ProductSortType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@RequiredArgsConstructor
@Service
@Transactional
public class LikeFacade {

    private final LikeService likeService;
    private final MemberService memberService;
    private final ProductService productService;
    private final BrandService brandService;
    private final InventoryService inventoryService;
    private final ProductDisplayService productDisplayService = new ProductDisplayService();

    public void registerLike(Long memberId, Long productId) {
        memberService.get(memberId);
        productService.get(productId);
        likeService.like(memberId, productId);
    }

    public void cancelLike(Long memberId, Long productId) {
        memberService.get(memberId);
        likeService.unlike(memberId, productId);
    }

    @Transactional(readOnly = true)
    public List<ProductDetailInfo> getMyLikedProducts(Long memberId) {
        memberService.get(memberId);

        List<Long> likedProductIds = likeService.getLikedProductIds(memberId);
        if (likedProductIds.isEmpty()) {
            return List.of();
        }

        List<Product> products = productService.getExistingByIds(likedProductIds);
        List<Long> brandIds = products.stream().map(Product::getBrandId).distinct().toList();
        List<Long> productIds = products.stream().map(Product::getId).toList();

        Map<Long, Brand> brandMap = brandService.getMapByIds(brandIds);
        Map<Long, Long> likeCountMap = likeService.getLikeCounts(productIds);
        Map<Long, Integer> stockMap = inventoryService.getQuantityMap(productIds);

        return productDisplayService
            .assembleList(products, brandMap, likeCountMap, stockMap, ProductSortType.LATEST)
            .stream()
            .map(ProductDetailInfo::from)
            .toList();
    }
}
