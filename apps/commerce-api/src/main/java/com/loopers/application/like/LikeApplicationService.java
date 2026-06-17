package com.loopers.application.like;

import com.loopers.domain.like.service.LikeDomainService;
import com.loopers.domain.product.repository.ProductRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Service
public class LikeApplicationService {

    private final LikeDomainService likeDomainService;
    private final ProductRepository productRepository;

    @Transactional
    public void addLike(Long memberId, Long productId) {
        if (!productRepository.findById(productId).isPresent()) {
            throw new CoreException(ErrorType.NOT_FOUND, "상품을 찾을 수 없습니다.");
        }

        boolean added = likeDomainService.addLike(memberId, productId);
        if (added) {
            productRepository.incrementLikeCount(productId);
        }
    }

    @Transactional
    public void removeLike(Long memberId, Long productId) {
        if (!productRepository.findById(productId).isPresent()) {
            throw new CoreException(ErrorType.NOT_FOUND, "상품을 찾을 수 없습니다.");
        }

        boolean removed = likeDomainService.removeLike(memberId, productId);
        if (removed) {
            productRepository.decrementLikeCount(productId);
        }
    }
}
