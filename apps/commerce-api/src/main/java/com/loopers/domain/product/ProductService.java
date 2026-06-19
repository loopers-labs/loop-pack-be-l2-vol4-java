package com.loopers.domain.product;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.List;

@RequiredArgsConstructor
@Component
public class ProductService {

    private final ProductRepository productRepository;
    private final ProductStatsService productStatsService;

    public ProductModel getById(Long id) {
        return productRepository.find(id)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "[id = " + id + "] 상품을 찾을 수 없습니다."));
    }

    public List<ProductModel> findAllByIds(List<Long> ids) {
        return productRepository.findAllByIds(ids);
    }

    public List<ProductModel> findAllByIdsOrThrow(List<Long> ids) {
        List<ProductModel> found = productRepository.findAllByIds(ids);
        if (found.size() != ids.size()) {
            throw new CoreException(ErrorType.NOT_FOUND, "존재하지 않는 상품이 포함되어 있습니다.");
        }
        return found;
    }

    public List<ProductModel> findAllByBrandId(Long brandId) {
        return productRepository.findAllByBrandId(brandId);
    }

    public Page<ProductModel> findAllByBrandId(Long brandId, Pageable pageable) {
        return productRepository.findAllByBrandId(brandId, pageable);
    }

    public Page<ProductModel> findProducts(Long brandId, Pageable pageable) {
        return productRepository.findProducts(brandId, pageable);
    }

    @Transactional
    public ProductModel create(Long brandId, String name, BigDecimal price) {
        ProductModel product = productRepository.save(new ProductModel(brandId, name, price));
        productStatsService.create(product);
        return product;
    }

    public ProductModel update(Long id, String name, BigDecimal price) {
        ProductModel product = getById(id);

        product.update(name, price);

        return productRepository.save(product);
    }

    @Transactional
    public void delete(Long id) {
        ProductModel product = getById(id);

        productStatsService.delete(product);

        product.delete();
        productRepository.save(product);
    }

    public void softDeleteAllByBrandId(Long brandId) {
        productRepository.softDeleteAllByBrandId(brandId, ZonedDateTime.now());
    }

}
