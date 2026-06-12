package com.loopers.application.like;

import com.loopers.domain.brand.BrandEntity;
import com.loopers.domain.brand.BrandRepository;
import com.loopers.domain.like.LikeEntity;
import com.loopers.domain.like.LikeRepository;
import com.loopers.domain.product.ProductEntity;
import com.loopers.domain.product.ProductRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@RequiredArgsConstructor
@Service
public class LikeApplicationService {

    private final LikeRepository likeRepository;
    private final ProductRepository productRepository;
    private final BrandRepository brandRepository;

    @Transactional
    public void addLike(Long userId, Long productId) {
        productRepository.find(productId)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "[id = " + productId + "] 상품을 찾을 수 없습니다."));

        Optional<LikeEntity> existing = likeRepository.findAny(userId, productId);
        if (existing.isPresent()) {
            LikeEntity like = existing.get();
            if (!like.isDeleted()) {
                throw new CoreException(ErrorType.CONFLICT, "이미 좋아요한 상품입니다.");
            }
            like.restore();
            likeRepository.save(like);
        } else {
            likeRepository.save(new LikeEntity(userId, productId));
        }
        productRepository.incrementLikeCount(productId);
    }

    @Transactional
    public void removeLike(Long userId, Long productId) {
        LikeEntity like = likeRepository.findActive(userId, productId)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "좋아요 정보를 찾을 수 없습니다."));
        like.delete();
        likeRepository.save(like);
        productRepository.decrementLikeCount(productId);
    }

    public Page<LikeInfo> getLikedProducts(Long userId, Pageable pageable) {
        return likeRepository.findActiveByUserId(userId, pageable).map(like -> {
            ProductEntity product = productRepository.find(like.getProductId())
                    .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "[id = " + like.getProductId() + "] 상품을 찾을 수 없습니다."));
            BrandEntity brand = brandRepository.findById(product.getBrandId())
                    .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "브랜드를 찾을 수 없습니다."));
            return LikeInfo.from(product, brand);
        });
    }
}
