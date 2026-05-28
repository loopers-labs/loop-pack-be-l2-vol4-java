package com.loopers.like.application;

import com.loopers.brand.application.BrandService;
import com.loopers.brand.domain.BrandModel;
import com.loopers.member.application.MemberService;
import com.loopers.product.application.ProductDetailInfo;
import com.loopers.product.application.ProductDisplayService;
import com.loopers.product.application.ProductService;
import com.loopers.product.domain.ProductModel;
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

        List<ProductModel> products = productService.getExistingByIds(likedProductIds);
        List<Long> brandIds = products.stream().map(ProductModel::getBrandId).distinct().toList();
        List<Long> productIds = products.stream().map(ProductModel::getId).toList();

        Map<Long, BrandModel> brandMap = brandService.getMapByIds(brandIds);
        Map<Long, Long> likeCountMap = likeService.getLikeCounts(productIds);

        return productDisplayService
            .assembleList(products, brandMap, likeCountMap, ProductSortType.LATEST)
            .stream()
            .map(ProductDetailInfo::from)
            .toList();
    }
}
