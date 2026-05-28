package com.loopers.application.product;

import com.loopers.domain.brand.model.Brand;
import com.loopers.domain.brand.repository.BrandRepository;
import com.loopers.domain.brand.service.BrandDomainService;
import com.loopers.domain.product.model.Product;
import com.loopers.domain.product.repository.ProductRepository;
import com.loopers.domain.product.service.ProductDomainService;
import com.loopers.domain.stock.model.Stock;
import com.loopers.domain.stock.repository.StockRepository;
import com.loopers.domain.stock.service.StockDomainService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Service
public class ProductApplicationService {

    private final BrandDomainService brandDomainService;
    private final ProductDomainService productDomainService;
    private final StockDomainService stockDomainService;
    private final ProductRepository productRepository;
    private final BrandRepository brandRepository;
    private final StockRepository stockRepository;

    @Transactional(readOnly = true)
    public ProductInfo getProduct(Long productId) {
        Product product = productDomainService.getProduct(productId);
        Brand brand = brandDomainService.getBrand(product.getBrandId());
        Stock stock = stockDomainService.getStock(productId);
        return ProductInfo.of(product, brand.getName(), stock.getQuantity());
    }

    @Transactional(readOnly = true)
    public Page<ProductInfo> getProducts(Long brandId, ProductSort sort, int page, int size) {
        Page<Product> products = productRepository.findAll(brandId, PageRequest.of(page, size, sort.toSort()));

        List<Long> productIds = products.getContent().stream().map(Product::getId).toList();
        List<Long> brandIds = products.getContent().stream().map(Product::getBrandId).distinct().toList();

        Map<Long, String> brandNameMap = brandRepository.findAllByIdIn(brandIds).stream()
            .collect(Collectors.toMap(Brand::getId, Brand::getName));
        Map<Long, Integer> stockQuantityMap = stockRepository.findAllByProductIdIn(productIds).stream()
            .collect(Collectors.toMap(Stock::getProductId, Stock::getQuantity));

        return products.map(product -> ProductInfo.of(
            product,
            brandNameMap.getOrDefault(product.getBrandId(), ""),
            stockQuantityMap.getOrDefault(product.getId(), 0)
        ));
    }

    @Transactional
    public Product createProduct(Long brandId, String name, String description, Long price, int initialQuantity) {
        brandDomainService.getBrand(brandId);
        Product product = productDomainService.createProduct(brandId, name, description, price);
        stockDomainService.createStock(product.getId(), initialQuantity);
        return product;
    }
}
