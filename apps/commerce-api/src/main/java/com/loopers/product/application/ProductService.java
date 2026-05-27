package com.loopers.product.application;

import com.loopers.brand.domain.BrandRepository;
import com.loopers.product.domain.Product;
import com.loopers.product.domain.ProductRepository;
import com.loopers.product.domain.ProductStock;
import com.loopers.product.domain.ProductStockRepository;
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
    private final ProductStockRepository productStockRepository;
    private final BrandRepository brandRepository;
    private final ProductReader productReader;

    @Transactional
    public Product create(ProductCommand.Create command) {
        if (!brandRepository.existsById(command.brandId())) {
            throw new CoreException(ErrorType.NOT_FOUND, "브랜드를 찾을 수 없습니다.");
        }
        Product product = Product.create(command.brandId(), command.name(), command.description(), command.price());
        Product saved = productRepository.save(product);

        ProductStock stock = ProductStock.create(saved.getId(), command.initialStockQuantity());
        productStockRepository.save(stock);

        return saved;
    }

    @Transactional
    public Product update(ProductCommand.Update command) {
        Product product = productReader.get(command.productId());
        product.update(command.name(), command.description(), command.price());
        return product;
    }

    @Transactional
    public void delete(Long productId) {
        Product product = productReader.get(productId);
        ProductStock stock = productReader.getStock(productId);
        product.delete();
        stock.delete();
    }

    @Transactional(readOnly = true)
    public Product get(Long productId) {
        return productReader.get(productId);
    }

    @Transactional(readOnly = true)
    public List<Product> getAll(ProductSortOption sortOption) {
        return switch (sortOption) {
            case LATEST -> productRepository.findAllOrderByLatest();
            case PRICE_ASC -> productRepository.findAllOrderByPriceAsc();
        };
    }
}
