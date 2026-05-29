package com.loopers.domain.product;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@RequiredArgsConstructor
@Component
public class ProductService {

    private final ProductRepository productRepository;

    @Transactional
    public ProductModel createProduct(Long brandId, String name, String description, Long price) {
        return productRepository.save(new ProductModel(brandId, name, description, price));
    }

    @Transactional(readOnly = true)
    public ProductModel getProduct(Long id) {
        return productRepository.find(id)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND,
                "[id = " + id + "] 상품을 찾을 수 없습니다."));
    }

    @Transactional(readOnly = true)
    public List<ProductModel> getAllProducts(SortType sortType) {
        return productRepository.findAll(sortType);
    }

    @Transactional
    public ProductModel updateProduct(Long id, String name, String description, Long price) {
        ProductModel product = getProduct(id);
        product.update(name, description, price);
        return productRepository.save(product);
    }

    @Transactional
    public void deleteProduct(Long id) {
        getProduct(id);
        productRepository.delete(id);
    }

    /** @Modifying 으로 race condition 없이 원자적으로 증감 */
    @Transactional
    public void increaseLikeCount(Long productId) {
        productRepository.increaseLikeCount(productId);
    }

    @Transactional
    public void decreaseLikeCount(Long productId) {
        productRepository.decreaseLikeCount(productId);
    }

    @Transactional
    public void deleteAllByBrandId(Long brandId) {
        productRepository.deleteAllByBrandId(brandId);
    }
}
