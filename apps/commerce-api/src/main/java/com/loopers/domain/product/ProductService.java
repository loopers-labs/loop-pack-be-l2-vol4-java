package com.loopers.domain.product;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@RequiredArgsConstructor
@Component
public class ProductService {

    private final ProductRepository productRepository;

    public ProductModel getById(Long id) {
        return productRepository.find(id)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "[id = " + id + "] 상품을 찾을 수 없습니다."));
    }

    public List<ProductModel> findAll(SortType sortType) {
        return productRepository.findAll(sortType);
    }

    public List<ProductModel> findAllByIds(List<Long> ids) {
        return productRepository.findAllByIds(ids);
    }

    public List<ProductModel> findAllByBrandId(Long brandId) {
        return productRepository.findAllByBrandId(brandId);
    }

    public Page<ProductModel> findAllByBrandId(Long brandId, Pageable pageable) {
        return productRepository.findAllByBrandId(brandId, pageable);
    }

    public ProductModel create(Long brandId, String name, BigDecimal price) {
        return productRepository.save(new ProductModel(brandId, name, price));
    }

    public ProductModel update(Long id, String name, BigDecimal price) {
        ProductModel product = getById(id);

        product.update(name, price);

        return productRepository.save(product);
    }

    public void delete(Long id) {
        ProductModel product = getById(id);

        product.delete();

        productRepository.save(product);
    }

    @Transactional
    public void increaseLikeCount(Long id) {
        productRepository.increaseLikeCount(id);
    }

    @Transactional
    public void decreaseLikeCount(Long id) {
        productRepository.decreaseLikeCount(id);
    }
}
