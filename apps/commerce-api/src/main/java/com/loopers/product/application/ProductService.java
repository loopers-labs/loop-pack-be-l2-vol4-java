package com.loopers.product.application;

import com.loopers.product.domain.ProductModel;
import com.loopers.product.domain.ProductRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;

@RequiredArgsConstructor
@Service
public class ProductService {

    private final ProductRepository productRepository;

    public ProductModel create(Long brandId, String name, String description, Long price, Integer stock) {
        return productRepository.save(new ProductModel(brandId, name, description, price, stock));
    }

    public void saveAll(List<ProductModel> products) {
        products.forEach(productRepository::save);
    }

    public ProductModel get(Long id) {
        return productRepository.find(id)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "[id = " + id + "] 상품을 찾을 수 없습니다."));
    }

    public List<ProductModel> getAll() {
        return productRepository.findAll();
    }

    public List<ProductModel> getByBrandId(Long brandId) {
        return productRepository.findByBrandId(brandId);
    }

    /** 주문에 필요한 상품들을 조회한다. 일부라도 존재하지 않으면 예외를 던진다. */
    public List<ProductModel> getAllByIds(Collection<Long> ids) {
        List<ProductModel> products = productRepository.findAllByIds(ids);
        if (products.size() != ids.stream().distinct().count()) {
            throw new CoreException(ErrorType.NOT_FOUND, "존재하지 않는 상품이 포함되어 있습니다.");
        }
        return products;
    }

    /** 존재하는 상품만 조회한다. 삭제되었거나 없는 상품은 결과에서 제외된다. */
    public List<ProductModel> getExistingByIds(Collection<Long> ids) {
        return productRepository.findAllByIds(ids);
    }

    public ProductModel update(Long id, String name, String description, Long price, Integer stock) {
        ProductModel product = get(id);
        product.update(name, description, price, stock);
        return productRepository.save(product);
    }

    public void delete(Long id) {
        ProductModel product = get(id);
        product.delete();
        productRepository.save(product);
    }

    /** 브랜드 삭제 시 해당 브랜드의 상품을 함께 논리 삭제한다. */
    public void deleteAllByBrandId(Long brandId) {
        List<ProductModel> products = productRepository.findByBrandId(brandId);
        for (ProductModel product : products) {
            product.delete();
            productRepository.save(product);
        }
    }
}
