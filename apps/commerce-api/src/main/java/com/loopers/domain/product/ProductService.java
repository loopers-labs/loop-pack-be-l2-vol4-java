package com.loopers.domain.product;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Map;

@RequiredArgsConstructor
@Component
public class ProductService {

    private final ProductRepository productRepository;

    @Transactional(readOnly = true)
    public ProductModel getById(Long id) {
        return productRepository.findById(id)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "[id = " + id + "] 상품을 찾을 수 없습니다."));
    }

    /** 존재 검증 전용 — 엔티티 적재 없이 SELECT 1. 존재하지 않으면 NOT_FOUND. */
    @Transactional(readOnly = true)
    public void requireExists(Long id) {
        if (!productRepository.existsById(id)) {
            throw new CoreException(ErrorType.NOT_FOUND, "[id = " + id + "] 상품을 찾을 수 없습니다.");
        }
    }

    @Transactional(readOnly = true)
    public List<ProductModel> getAllByIds(Collection<Long> ids) {
        return productRepository.findAllByIds(ids);
    }

    @Transactional(readOnly = true)
    public Page<ProductModel> search(Long brandId, SortOption sort, Pageable pageable) {
        return productRepository.search(brandId, sort, pageable);
    }

    @Transactional(readOnly = true)
    public long countByBrandId(Long brandId) {
        return productRepository.countByBrandId(brandId);
    }

    @Transactional(readOnly = true)
    public Map<Long, Long> countByBrandIds(Collection<Long> brandIds) {
        return productRepository.countByBrandIds(brandIds);
    }

    @Transactional
    public void incrementLikeCount(Long productId) {
        int updated = productRepository.incrementLikeCount(productId);
        if (updated == 0) {
            throw new CoreException(ErrorType.NOT_FOUND, "[id = " + productId + "] 상품을 찾을 수 없습니다.");
        }
    }

    /**
     * likeCount > 0 가드를 SQL에 위임 — 0건이어도 멱등 처리(이미 0이거나 동시성 보정).
     * <p>호출 컨텍스트: {@link com.loopers.application.like.LikeFacade#unlike(Long, Long)}의 successful path.
     * like row가 막 삭제됐다는 사실이 곧 상품 존재의 강한 증거이므로 여기서 별도 검증을 하지 않는다.
     * 상품 부재로 인한 NOT_FOUND는 Facade의 반대 분기({@code likeService.unlike == 0})에서 {@link #requireExists}가 담당.
     */
    @Transactional
    public void decrementLikeCount(Long productId) {
        productRepository.decrementLikeCount(productId);
    }
}
