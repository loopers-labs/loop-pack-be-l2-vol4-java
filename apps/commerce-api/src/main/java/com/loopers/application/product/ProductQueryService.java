package com.loopers.application.product;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import java.util.*;
// Hides: deterministic DB ordering, cache-aside fallback, and invalidation scope.
@Component @RequiredArgsConstructor public class ProductQueryService{private final JdbcTemplate db;private final ProductCache cache;
 public List<Result> find(long brandId,int limit){if(brandId<=0||limit<=0)throw new IllegalArgumentException();try{Optional<List<Result>> hit=cache.get(brandId,limit);if(hit.isPresent())return hit.get();}catch(RuntimeException ignored){}List<Result> rows=db.query("select id,price from products where brand_id=? order by price,id limit ?",(rs,n)->new Result(rs.getLong(1),rs.getLong(2)),brandId,limit);try{cache.put(brandId,limit,rows);}catch(RuntimeException ignored){}return rows;}
 public void priceChanged(long brandId){try{cache.evict(brandId);}catch(RuntimeException ignored){}}
 public record Result(long id,long price){}
}
