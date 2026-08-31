package com.loopers.application.product;
import java.util.*;
public interface ProductCache{Optional<List<ProductQueryService.Result>> get(long brandId,int limit);void put(long brandId,int limit,List<ProductQueryService.Result> value);void evict(long brandId);}
