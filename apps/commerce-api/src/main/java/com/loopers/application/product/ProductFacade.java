package com.loopers.application.product;

import com.loopers.application.event.ProductViewedEvent;
import com.loopers.application.ranking.RankingRepository;
import com.loopers.domain.brand.Brand;
import com.loopers.domain.brand.BrandService;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductBrandProcessService;
import com.loopers.domain.product.ProductService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.time.Clock;
import java.time.LocalDate;

@Slf4j
@Component
public class ProductFacade {
    private final ProductService productService;
    private final BrandService brandService;
    private final ProductBrandProcessService productBrandProcessService;
    private final ProductCacheRepository productCacheRepository;
    private final ProductLikeCountRepository productLikeCountRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final RankingRepository rankingRepository;
    private final Clock clock;

    @Autowired
    public ProductFacade(
        ProductService productService,
        BrandService brandService,
        ProductBrandProcessService productBrandProcessService,
        ProductCacheRepository productCacheRepository,
        ProductLikeCountRepository productLikeCountRepository,
        ApplicationEventPublisher eventPublisher,
        RankingRepository rankingRepository,
        Clock clock
    ) {
        this.productService = productService;
        this.brandService = brandService;
        this.productBrandProcessService = productBrandProcessService;
        this.productCacheRepository = productCacheRepository;
        this.productLikeCountRepository = productLikeCountRepository;
        this.eventPublisher = eventPublisher;
        this.rankingRepository = rankingRepository;
        this.clock = clock;
    }

    public ProductFacade(
        ProductService productService,
        BrandService brandService,
        ProductBrandProcessService productBrandProcessService,
        ProductCacheRepository productCacheRepository,
        ProductLikeCountRepository productLikeCountRepository
    ) {
        this(
            productService,
            brandService,
            productBrandProcessService,
            productCacheRepository,
            productLikeCountRepository,
            event -> {
            },
            null,
            Clock.systemDefaultZone()
        );
    }

    @Transactional
    public ProductInfo createProduct(Long brandId, String name, String description, Long price, Integer stock) {
        Brand brand = brandService.getBrand(brandId);
        Product product = productService.createProduct(brandId, name, description, price, stock);
        ProductInfo productInfo = ProductInfo.from(productBrandProcessService.getProductDetailView(product, brand));
        productCacheRepository.evictProductLists();
        productCacheRepository.cacheProduct(productInfo);
        return productInfo;
    }

    @Transactional
    public ProductInfo getProduct(Long id) {
        ProductInfo productInfo = productCacheRepository.getProduct(id)
            .orElseGet(() -> getProductFromDb(id));
        eventPublisher.publishEvent(ProductViewedEvent.viewed(id, null));
        return productInfo;
    }

    @Transactional
    public ProductDetailInfo getProductDetail(Long id) {
        ProductInfo productInfo = getProduct(id);
        Long rank = null;
        if (rankingRepository != null) {
            try {
                var foundRank = rankingRepository.findRank(LocalDate.now(clock), id);
                rank = foundRank.isPresent() ? foundRank.getAsLong() : null;
            } catch (DataAccessException e) {
                // 랭킹은 유실 가능한 read model이므로 상품 상세 가용성을 우선한다.
                log.warn("상품 상세 랭킹 조회에 실패했습니다. productId={}", id, e);
            }
        }
        return new ProductDetailInfo(productInfo, rank);
    }

    private ProductInfo getProductFromDb(Long id) {
        Product product = productService.getProduct(id);
        Brand brand = brandService.getBrand(product.getBrandId());
        ProductInfo productInfo = applyLatestLikeCount(
            ProductInfo.from(productBrandProcessService.getProductDetailView(product, brand))
        );
        productCacheRepository.cacheProduct(productInfo);
        return productInfo;
    }

    @Transactional(readOnly = true)
    public List<ProductInfo> getAllProducts(Long brandId, String sort, Integer page, Integer size) {
        return productCacheRepository.getProducts(brandId, sort, page, size)
            .orElseGet(() -> getAllProductsFromDb(brandId, sort, page, size));
    }

    private List<ProductInfo> getAllProductsFromDb(Long brandId, String sort, Integer page, Integer size) {
        if (brandId != null) {
            brandService.validateBrandExists(brandId);
        }

        List<Product> products = productService.getAllProducts(brandId, sort, page, size);
        List<Long> brandIds = productBrandProcessService.getBrandIds(products);
        List<Brand> brands = brandService.getBrandsByIds(brandIds);
        List<ProductInfo> productInfos = productBrandProcessService.getProductDetailViews(products, brands).stream()
            .map(ProductInfo::from)
            .map(this::applyLatestLikeCount)
            .toList();
        productCacheRepository.cacheProducts(brandId, sort, page, size, productInfos);
        return productInfos;
    }

    private ProductInfo applyLatestLikeCount(ProductInfo productInfo) {
        return productLikeCountRepository.get(productInfo.id())
            .map(productInfo::withLikeCount)
            .orElse(productInfo);
    }

    @Transactional
    public ProductInfo updateProduct(Long id, String name, String description, Long price, Integer stock) {
        Product product = productService.updateProduct(id, name, description, price, stock);
        Brand brand = brandService.getBrand(product.getBrandId());
        ProductInfo productInfo = applyLatestLikeCount(
            ProductInfo.from(productBrandProcessService.getProductDetailView(product, brand))
        );
        productCacheRepository.evictProduct(id);
        productCacheRepository.evictProductLists();
        productCacheRepository.cacheProduct(productInfo);
        return productInfo;
    }

    @Transactional
    public void deleteProduct(Long id) {
        productService.deleteProduct(id);
        productCacheRepository.evictProduct(id);
        productCacheRepository.evictProductLists();
    }
}
