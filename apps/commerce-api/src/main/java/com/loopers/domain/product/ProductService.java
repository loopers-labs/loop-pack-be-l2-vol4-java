package com.loopers.domain.product;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;
    private final ProductStockService productStockService;

    @Transactional
    public ProductModel createProduct(Long brandId, String name, String description, Long price, Integer stock) {
        return createProduct(brandId, name, description, price, stock, ProductStatus.ON_SALE);
    }

    @Transactional
    public ProductModel createProduct(Long brandId, String name, String description, Long price, Integer stock, ProductStatus status) {
        ProductModel product = ProductModel.of(
                brandId,
                ProductName.of(name),
                ProductDescription.of(description),
                ProductPrice.of(price),
                status
        );
        ProductModel productModel = productRepository.save(product);
        productStockService.createStock(productModel.getId(), stock);
        return productModel;
    }

    @Transactional(readOnly = true)
    public ProductModel getProduct(Long id) {
        return productRepository.find(id)
                .orElseThrow(
                    () -> new CoreException(ErrorType.NOT_FOUND, "상품을 찾을 수 없습니다.")
                );
    }

    @Transactional(readOnly = true)
    public Page<ProductModel> getProducts(Long brandId, ProductStatus status, ProductSortType sort, Pageable pageable) {
        return productRepository.search(brandId, status, sort, pageable);
    }

    @Transactional
    public ProductModel updateProduct(Long id, Long brandId, String name, String description, Long price, Integer stock, ProductStatus status) {
        ProductModel product = getProduct(id);
        product.update(
                brandId,
                ProductName.of(name),
                ProductDescription.of(description),
                ProductPrice.of(price),
                status
        );
        productStockService.changeStock(id, stock);
        return product;
    }

    @Transactional
    public void increaseLikeCount(Long productId) {
        productRepository.increaseLikeCount(productId);
    }

    @Transactional
    public void decreaseLikeCount(Long productId) {
        productRepository.decreaseLikeCount(productId);
    }

    @Transactional
    public void deleteProduct(Long id) {
        ProductModel product = getProduct(id);
        product.delete();
        productRepository.save(product);
        productStockService.deleteStock(id);
    }
}