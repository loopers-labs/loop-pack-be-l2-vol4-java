package com.loopers.application.product;

import com.loopers.domain.brand.Brand;
import com.loopers.domain.brand.BrandRepository;
import com.loopers.domain.common.PageResult;
import com.loopers.domain.inventory.Inventory;
import com.loopers.domain.inventory.InventoryRepository;
import com.loopers.domain.like.LikeRepository;
import com.loopers.domain.product.Money;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductCommand;
import com.loopers.domain.product.ProductDetail;
import com.loopers.domain.product.ProductRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Component
public class ProductApplicationService {

    private final ProductRepository productRepository;
    private final InventoryRepository inventoryRepository;
    private final BrandRepository brandRepository;
    private final LikeRepository likeRepository;

    @Transactional
    public ProductInfo.Created register(ProductCriteria.Register command) {
        if (brandRepository.find(command.brandId()).isEmpty()) {
            throw new CoreException(ErrorType.NOT_FOUND, "브랜드를 찾을 수 없습니다.");
        }
        Product product = Product.create(
                command.brandId(),
                command.name(),
                Money.of(command.price())
        );
        Product saved = productRepository.save(product);
        // 상품과 재고는 별도 애그리거트지만, 1:1 쌍 생성이라 같은 트랜잭션에서 함께 만든다.
        inventoryRepository.save(Inventory.create(saved.getId(), command.stock()));
        return ProductInfo.Created.from(saved, command.stock());
    }

    @Transactional(readOnly = true)
    public ProductInfo.Detail getProduct(Long id) {
        Product product = productRepository.find(id)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "상품을 찾을 수 없습니다."));
        Brand brand = brandRepository.find(product.getBrandId())
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "브랜드를 찾을 수 없습니다."));
        Inventory inventory = inventoryRepository.find(id)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "재고 정보를 찾을 수 없습니다."));
        long likeCount = likeRepository.countByProductId(id);
        return ProductInfo.Detail.from(new ProductDetail(
                product, brand.getId(), brand.getName(), likeCount,
                inventory.getQuantity(), inventory.isSoldOut()));
    }

    @Transactional(readOnly = true)
    public PageResult<ProductInfo.ListItem> getAllProducts(ProductCriteria.GetAll command) {
        ProductCommand.Search search = new ProductCommand.Search(
                command.page(),
                command.size(),
                command.brandId(),
                command.sortType()
        );
        PageResult<Product> products = productRepository.findAll(search);

        Set<Long> brandIds = products.content().stream()
                .map(Product::getBrandId)
                .collect(Collectors.toSet());
        Map<Long, Brand> brandById = brandRepository.findAllByIds(brandIds).stream()
                .collect(Collectors.toMap(Brand::getId, Function.identity()));

        Set<Long> productIds = products.content().stream()
                .map(Product::getId)
                .collect(Collectors.toSet());
        Map<Long, Long> likeCountByProductId = likeRepository.countByProductIds(productIds);
        Map<Long, Inventory> inventoryByProductId = inventoryRepository.findAllByProductIds(productIds).stream()
                .collect(Collectors.toMap(Inventory::getProductId, Function.identity()));

        return products.map(product -> {
            Brand brand = brandById.get(product.getBrandId());
            if (brand == null) {
                throw new CoreException(ErrorType.NOT_FOUND, "브랜드를 찾을 수 없습니다.");
            }
            Inventory inventory = inventoryByProductId.get(product.getId());
            if (inventory == null) {
                throw new CoreException(ErrorType.NOT_FOUND, "재고 정보를 찾을 수 없습니다.");
            }
            long likeCount = likeCountByProductId.getOrDefault(product.getId(), 0L);
            return ProductInfo.ListItem.from(new ProductDetail(
                    product, brand.getId(), brand.getName(), likeCount,
                    inventory.getQuantity(), inventory.isSoldOut()));
        });
    }

    @Transactional
    public void modify(ProductCriteria.Modify command) {
        Product product = findOrThrow(command.id());
        product.modify(command.name(), Money.of(command.price()));
        productRepository.update(product);

        Inventory inventory = inventoryRepository.find(command.id())
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "재고 정보를 찾을 수 없습니다."));
        inventory.adjust(command.stock());
        inventoryRepository.update(inventory);
    }

    @Transactional
    public void delete(Long id) {
        Product product = findOrThrow(id);
        product.delete();
        productRepository.update(product);
        // 재고도 함께 소프트 삭제 — 주문과 같은 inventory 행 락 규약을 공유해 "삭제된 상품 주문" 경쟁을 차단한다.
        // (삭제의 UPDATE 가 행 락을 잡아 주문의 FOR UPDATE 와 직렬화되고, 주문의 락 조회는 deleted_at IS NULL 로 필터한다.)
        inventoryRepository.find(id).ifPresent(inventory -> {
            inventory.delete();
            inventoryRepository.update(inventory);
        });
    }

    private Product findOrThrow(Long id) {
        return productRepository.find(id)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "상품을 찾을 수 없습니다."));
    }
}
