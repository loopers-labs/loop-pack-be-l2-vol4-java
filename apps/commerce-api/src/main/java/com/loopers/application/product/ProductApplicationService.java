package com.loopers.application.product;

import com.loopers.domain.brand.Brand;
import com.loopers.domain.brand.BrandRepository;
import com.loopers.domain.common.PageResult;
import com.loopers.domain.inventory.Inventory;
import com.loopers.domain.inventory.InventoryRepository;
import com.loopers.domain.product.Money;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductCommand;
import com.loopers.domain.product.ProductDetail;
import com.loopers.domain.product.ProductRepository;
import com.loopers.domain.product.event.ProductViewedEvent;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Component
public class ProductApplicationService {

    private final ProductRepository productRepository;
    private final InventoryRepository inventoryRepository;
    private final BrandRepository brandRepository;
    private final ProductCacheRepository productCacheRepository;
    private final ApplicationEventPublisher eventPublisher;

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

    // 목록과 같은 이유로 자체 트랜잭션을 만들지 않는다(SUPPORTS) — 캐시 히트가 커넥션을 선점하지 않게 한다.
    @Transactional(propagation = Propagation.SUPPORTS, readOnly = true)
    public ProductInfo.Detail getProduct(Long id) {
        Optional<ProductInfo.Detail> cached = productCacheRepository.findDetail(id);
        if (cached.isPresent()) {
            // 캐시 히트도 유효한 조회다 → 행동 로깅 대상. (조회는 비로그인이 가능해 userId 는 null.)
            // 이 경로는 트랜잭션이 없을 수 있어, 듣는 리스너는 fallbackExecution=true 로 즉시 실행돼야 한다.
            eventPublisher.publishEvent(new ProductViewedEvent(id, null, ZonedDateTime.now()));
            return cached.get();
        }
        // 캐시 미스 → 실제 DB 재계산. PK 단건 키라 likeCount/재고 변동은 evict 없이 짧은 TTL 로 흡수한다.
        Product product = productRepository.find(id)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "상품을 찾을 수 없습니다."));
        Brand brand = brandRepository.find(product.getBrandId())
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "브랜드를 찾을 수 없습니다."));
        Inventory inventory = inventoryRepository.find(id)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "재고 정보를 찾을 수 없습니다."));
        // 좋아요 수는 비정규화 카운터(product.likeCount)에서 읽는다 — COUNT 집계를 매 조회마다 돌리지 않는다.
        ProductInfo.Detail detail = ProductInfo.Detail.from(new ProductDetail(
                product, brand.getId(), brand.getName(), product.getLikeCount(),
                inventory.getQuantity(), inventory.isSoldOut()));
        productCacheRepository.putDetail(detail);
        eventPublisher.publishEvent(new ProductViewedEvent(id, null, ZonedDateTime.now()));
        return detail;
    }

    // 캐시 히트가 대부분인 경로 — 자체 트랜잭션을 만들지 않는다(SUPPORTS: 필요 없지만 상위 tx 가 있으면 합류).
    // @Transactional(readOnly) 이면 히트여도 커넥션을 선점해, 고트래픽에서 히트들이 풀을 두고 경쟁→붕괴한다(실측).
    // 미스 시 재계산의 repo 호출은 Spring Data 의 클래스레벨 readOnly 트랜잭션으로 각자 짧게 커넥션을 빌린다.
    @Transactional(propagation = Propagation.SUPPORTS, readOnly = true)
    public PageResult<ProductInfo.ListItem> getAllProducts(ProductCriteria.GetAll command) {
        ProductCommand.Search search = new ProductCommand.Search(
                command.page(),
                command.size(),
                command.brandId(),
                command.sortType()
        );
        Optional<PageResult<ProductInfo.ListItem>> cached = productCacheRepository.findList(search);
        if (cached.isPresent()) {
            return cached.get();
        }
        // 캐시 미스 → 실제 DB 재계산.
        PageResult<Product> products = productRepository.findAll(search);

        Set<Long> brandIds = products.content().stream()
                .map(Product::getBrandId)
                .collect(Collectors.toSet());
        Map<Long, Brand> brandById = brandRepository.findAllByIds(brandIds).stream()
                .collect(Collectors.toMap(Brand::getId, Function.identity()));

        Set<Long> productIds = products.content().stream()
                .map(Product::getId)
                .collect(Collectors.toSet());
        Map<Long, Inventory> inventoryByProductId = inventoryRepository.findAllByProductIds(productIds).stream()
                .collect(Collectors.toMap(Inventory::getProductId, Function.identity()));

        PageResult<ProductInfo.ListItem> result = products.map(product -> {
            Brand brand = brandById.get(product.getBrandId());
            if (brand == null) {
                throw new CoreException(ErrorType.NOT_FOUND, "브랜드를 찾을 수 없습니다.");
            }
            Inventory inventory = inventoryByProductId.get(product.getId());
            if (inventory == null) {
                throw new CoreException(ErrorType.NOT_FOUND, "재고 정보를 찾을 수 없습니다.");
            }
            // 좋아요 수는 비정규화 카운터(product.likeCount)에서 읽는다 — 목록당 COUNT GROUP BY 를 제거.
            return ProductInfo.ListItem.from(new ProductDetail(
                    product, brand.getId(), brand.getName(), product.getLikeCount(),
                    inventory.getQuantity(), inventory.isSoldOut()));
        });
        // 목록은 짧은 TTL 로만 신선도를 관리한다(정밀 무효화 없음) — 좋아요/등록/삭제로 순서·구성이 바뀌어도 TTL 이 상한.
        productCacheRepository.putList(search, result);
        return result;
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
        // 상세 캐시는 PK 단건 키라 정확히 evict 한다(목록은 키 fan-out 이라 evict 불가 → TTL 로만 관리).
        productCacheRepository.evictDetail(command.id());
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
        // 삭제된 상품이 상세 캐시로 노출되지 않도록 evict 한다(목록은 TTL 동안 stale 허용).
        productCacheRepository.evictDetail(id);
    }

    private Product findOrThrow(Long id) {
        return productRepository.find(id)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "상품을 찾을 수 없습니다."));
    }
}
