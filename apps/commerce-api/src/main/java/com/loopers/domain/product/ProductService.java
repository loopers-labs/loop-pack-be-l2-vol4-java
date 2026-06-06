package com.loopers.domain.product;

import com.loopers.domain.product.enums.ProductSortType;
import com.loopers.domain.product.vo.Price;
import com.loopers.domain.product.vo.ProductName;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@RequiredArgsConstructor
@Component
public class ProductService {

    private final ProductRepository productRepository;
    private final ProductStockRepository productStockRepository;

    @Transactional
    public ProductModel create(Long brandId, ProductName name) {
        return productRepository.save(new ProductModel(brandId, name));
    }

    @Transactional(readOnly = true)
    public ProductModel get(Long id) {
        return productRepository.find(id)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "[id = " + id + "] 상품을 찾을 수 없습니다."));
    }

    @Transactional(readOnly = true)
    public List<ProductModel> getListByIds(List<Long> ids) {
        return productRepository.findAllByIds(ids);
    }

    @Transactional(readOnly = true)
    public Page<ProductModel> getAdminList(Long brandId, Pageable pageable) {
        return productRepository.findAllForAdmin(brandId, pageable);
    }

    @Transactional(readOnly = true)
    public Page<ProductModel> getList(Long brandId, ProductSortType sort, Pageable pageable) {
        return productRepository.findAll(brandId, sort, pageable);
    }

    @Transactional
    public ProductModel update(Long id, ProductName name) {
        ProductModel product = get(id);
        product.update(name);
        return product;
    }

    @Transactional
    public void delete(Long id) {
        get(id).delete();
    }

    @Transactional
    public void suspendAllByBrandId(Long brandId) {
        productRepository.suspendAllByBrandId(brandId);
    }

    @Transactional
    public ProductStockModel addStock(Long productId, Price price, Integer quantity) {
        ProductModel product = get(productId);
        return productStockRepository.save(new ProductStockModel(product, price, quantity));
    }
}
