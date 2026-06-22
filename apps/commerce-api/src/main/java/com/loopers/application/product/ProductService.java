package com.loopers.application.product;

import com.loopers.domain.product.ProductFilter;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductRepository;
import com.loopers.domain.product.ProductSort;

import java.util.List;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Component
public class ProductService {

    private final ProductRepository productRepository;

    @Transactional
    public ProductModel create(ProductModel product) {
        return productRepository.save(product);
    }

    @Transactional(readOnly = true)
    public ProductModel getById(Long id) {
        return productRepository.findById(id)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "[id = " + id + "] 상품을 찾을 수 없습니다."));
    }

    @Transactional(readOnly = true)
    public Page<ProductModel> getAll(ProductFilter filter, ProductSort sort, PageRequest pageRequest) {
        return productRepository.findAll(filter, sort, pageRequest);
    }

    @Transactional
    public ProductModel update(Long id, String name, Long price) {
        ProductModel product = getById(id);
        product.update(name, price);
        return product;
    }

    @Transactional
    public void delete(Long id) {
        ProductModel product = getById(id);
        product.delete();
    }

    @Transactional(readOnly = true)
    public List<ProductModel> findAllByBrandId(Long brandId) {
        return productRepository.findAllByBrandId(brandId);
    }
}
