package com.loopers.application.wishlist;

import com.loopers.application.product.ProductInfo;
import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.brand.BrandRepository;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductService;
import com.loopers.domain.wishlist.WishlistModel;
import com.loopers.domain.wishlist.WishlistRepository;
import com.loopers.domain.wishlist.WishlistService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Component
public class WishlistFacade {

    private final WishlistService wishlistService;
    private final ProductService productService;
    private final BrandRepository brandRepository;
    private final WishlistRepository wishlistRepository;

    public void addLike(Long userId, Long productId) {
        wishlistService.add(userId, productId);
    }

    public void removeLike(Long userId, Long productId) {
        wishlistService.remove(userId, productId);
    }

    public List<ProductInfo> getLikedProducts(Long userId) {
        List<Long> productIds = wishlistService.getList(userId).stream()
                .map(WishlistModel::getProductId)
                .toList();
        List<ProductModel> products = productService.getListByIds(productIds);
        Map<Long, BrandModel> brandMap = brandRepository.findAllByIds(
                products.stream().map(ProductModel::getBrandId).distinct().toList()
        ).stream().collect(Collectors.toMap(BrandModel::getId, b -> b));
        Map<Long, Long> likeCounts = wishlistRepository.countsByProductIds(productIds);
        return products.stream()
                .map(p -> ProductInfo.from(p, brandMap.get(p.getBrandId()), likeCounts.getOrDefault(p.getId(), 0L)))
                .toList();
    }
}
