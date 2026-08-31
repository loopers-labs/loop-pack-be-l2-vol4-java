package com.loopers.infrastructure.product;
import com.loopers.application.product.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import java.time.Duration;import java.util.*;
// Hides: Redis key/serialization/TTL details behind the product result contract.
@Component @RequiredArgsConstructor public class RedisProductCache implements ProductCache{private final StringRedisTemplate redis;private String key(long b,int l){return "w4:product:"+b+":"+l;}public Optional<List<ProductQueryService.Result>> get(long b,int l){String raw=redis.opsForValue().get(key(b,l));if(raw==null)return Optional.empty();return Optional.of(Arrays.stream(raw.split(",")).filter(s->!s.isBlank()).map(s->{String[] p=s.split(":");return new ProductQueryService.Result(Long.parseLong(p[0]),Long.parseLong(p[1]));}).toList());}public void put(long b,int l,List<ProductQueryService.Result> v){redis.opsForValue().set(key(b,l),v.stream().map(x->x.id()+":"+x.price()).reduce((a,c)->a+","+c).orElse(""),Duration.ofMinutes(5));}public void evict(long b){Set<String> keys=redis.keys("w4:product:"+b+":*");if(keys!=null&&!keys.isEmpty())redis.delete(keys);}}
