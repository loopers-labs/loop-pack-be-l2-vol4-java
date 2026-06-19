package com.loopers.domain.product;

import com.loopers.domain.common.PageCriteria;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@RequiredArgsConstructor
@Component
public class ProductReader {

    private final ProductRepository productRepository;

    public Product getProduct(Long id) {
        return getProductById(id);
    }

    public List<Product> findProductsByIds(List<Long> ids) {
        return productRepository.findAllByIds(ids);
    }

    public List<Product> findProductsByIdsForUpdate(List<Long> ids) {
        return productRepository.findAllByIdsForUpdate(ids);
    }

    public List<Product> getAllProducts(Long brandId, String sort, Integer page, Integer size) {
        ProductSort productSort = ProductSort.from(sort);
        PageCriteria pageCriteria = PageCriteria.of(page, size);
        return brandId == null
            ? productRepository.findAll(productSort, pageCriteria.page(), pageCriteria.size())
            : productRepository.findAllByBrandId(brandId, productSort, pageCriteria.page(), pageCriteria.size());
    }

    Product getProductById(Long id) {
        return productRepository.find(id)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "[id = " + id + "] 상품을 찾을 수 없습니다."));
    }
}
