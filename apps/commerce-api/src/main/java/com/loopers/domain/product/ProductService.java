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

    @Transactional
    public void decrementLikeCount(Long productId) {
        productRepository.decrementLikeCount(productId);
    }
}
