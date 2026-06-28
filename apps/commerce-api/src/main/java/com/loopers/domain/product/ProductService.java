package com.loopers.domain.product;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Component
public class ProductService {

    private final ProductRepository productRepository;

    @Transactional
    public ProductModel createProduct(String name, String description, Long price, Integer stock, Long brandId) {
        return productRepository.save(new ProductModel(name, description, price, stock, brandId));
    }

    @Transactional(readOnly = true)
    public ProductModel getProduct(Long id) {
        return productRepository.findById(id)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "[id = " + id + "] 상품을 찾을 수 없습니다."));
    }

    @Transactional(readOnly = true)
    public Page<ProductModel> getProducts(Long brandId, ProductSortType sort, Pageable pageable) {
        return productRepository.findAll(brandId, sort, pageable);
    }

    @Transactional
    public ProductModel updateProduct(Long id, String name, String description, Long price, Integer stock) {
        ProductModel product = getProduct(id);
        product.update(name, description, price, stock);
        return productRepository.save(product);
    }

    @Transactional
    public void deleteProduct(Long id) {
        getProduct(id);
        productRepository.delete(id);
    }

    @Transactional
    public List<ProductSnapshot> deductStocks(List<DeductStockCommand> commands) {
        List<Long> ids = commands.stream().map(DeductStockCommand::productId).toList();
        List<ProductModel> products = productRepository.findAllByIdsWithLock(ids);

        if (products.size() != ids.size()) {
            throw new CoreException(ErrorType.NOT_FOUND, "존재하지 않는 상품이 포함되어 있습니다.");
        }

        Map<Long, Integer> quantityMap = commands.stream()
            .collect(Collectors.toMap(DeductStockCommand::productId, DeductStockCommand::quantity));

        List<ProductSnapshot> snapshots = products.stream().map(product -> {
            int quantity = quantityMap.get(product.getId());
            product.decreaseStock(quantity);
            return new ProductSnapshot(product.getId(), product.getName(), product.getPrice(), quantity);
        }).toList();

        productRepository.saveAll(products);
        return snapshots;
    }

    @Transactional(readOnly = true)
    public List<Long> getProductIdsByBrandId(Long brandId) {
        return productRepository.findIdsByBrandId(brandId);
    }

    @Transactional
    public void bulkSoftDeleteByBrandId(Long brandId) {
        productRepository.bulkSoftDelete(brandId);
    }

    @Transactional
    public void incrementLikeCount(Long productId) {
        ProductModel product = getProduct(productId);
        product.incrementLikeCount();
        productRepository.save(product);
    }

    @Transactional
    public void decrementLikeCount(Long productId) {
        ProductModel product = getProduct(productId);
        product.decrementLikeCount();
        productRepository.save(product);
    }

    public record DeductStockCommand(Long productId, int quantity) {}
}
