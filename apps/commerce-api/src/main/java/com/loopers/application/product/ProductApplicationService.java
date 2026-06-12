package com.loopers.application.product;

import com.loopers.domain.brand.BrandEntity;
import com.loopers.domain.brand.BrandRepository;
import com.loopers.domain.inventory.InventoryEntity;
import com.loopers.domain.inventory.InventoryRepository;
import com.loopers.domain.like.LikeRepository;
import com.loopers.domain.product.ProductEntity;
import com.loopers.domain.product.ProductRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Service
public class ProductApplicationService {

    private final ProductRepository productRepository;
    private final BrandRepository brandRepository;
    private final InventoryRepository inventoryRepository;
    private final LikeRepository likeRepository;

    @Transactional
    public ProductInfo createProduct(Long brandId, String name, String description, Long price, Integer quantity) {
        BrandEntity brand = brandRepository.findById(brandId)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "브랜드를 찾을 수 없습니다."));
        ProductEntity product = productRepository.save(new ProductEntity(brandId, name, description, price));
        InventoryEntity inventory = inventoryRepository.save(new InventoryEntity(product.getId(), quantity));
        return ProductInfo.from(product, brand, inventory);
    }

    public ProductInfo getProduct(Long id) {
        return assembleProductInfo(findProductOrThrow(id));
    }

    public Page<ProductInfo> getAllProducts(Long brandId, Pageable pageable) {
        return productRepository.findAll(brandId, pageable).map(this::assembleProductInfo);
    }

    @Transactional
    public void updateProduct(Long id, String name, String description, Long price, Integer quantity) {
        ProductEntity product = findProductOrThrow(id);
        product.update(name, description, price);
        productRepository.save(product);

        InventoryEntity inventory = inventoryRepository.findByProductId(id)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "[productId = " + id + "] 재고를 찾을 수 없습니다."));
        inventory.updateQuantity(quantity);
        inventoryRepository.save(inventory);
    }

    @Transactional
    public void deleteProduct(Long id) {
        ProductEntity product = findProductOrThrow(id);
        product.delete();
        productRepository.save(product);
        inventoryRepository.deleteByProductId(id);
        likeRepository.deleteAllByProductId(id);
    }

    private ProductInfo assembleProductInfo(ProductEntity product) {
        BrandEntity brand = brandRepository.findById(product.getBrandId())
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "브랜드를 찾을 수 없습니다."));
        InventoryEntity inventory = inventoryRepository.findByProductId(product.getId())
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "[productId = " + product.getId() + "] 재고를 찾을 수 없습니다."));
        return ProductInfo.from(product, brand, inventory);
    }

    private ProductEntity findProductOrThrow(Long id) {
        return productRepository.find(id)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "[id = " + id + "] 상품을 찾을 수 없습니다."));
    }
}
