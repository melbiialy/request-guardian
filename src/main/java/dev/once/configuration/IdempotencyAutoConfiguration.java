package dev.once.configuration;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.once.filter.IdempotencyFilter;
import dev.once.filter.WrapperFilter;
import dev.once.storage.IdempotencyStore;
import dev.once.storage.InMemoryIdempotencyStore;
import dev.once.storage.RedisIdempotencyStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;

@AutoConfiguration
@EnableConfigurationProperties(IdempotencyProperties.class)
public class IdempotencyAutoConfiguration {
    @Bean
    @ConditionalOnProperty(prefix = "idempotency",
            name = "store",
            havingValue = "in-memory")
    @ConditionalOnMissingBean({IdempotencyStore.class, RedisConnectionFactory.class})
    public IdempotencyStore inMemoryIdempotencyStore(IdempotencyProperties properties) {
        return new InMemoryIdempotencyStore(properties.getTtl().getSeconds());
    }

    @Bean("idempotencyRedisTemplate")
    @ConditionalOnProperty(prefix = "idempotency",
            name = "store",
            havingValue = "redis")
    @ConditionalOnBean(RedisConnectionFactory.class)
    public RedisTemplate<String,String > redisIdempotencyStore(RedisConnectionFactory redisConnectionFactory) {
        RedisTemplate<String, String> template = new RedisTemplate<>();
        template.setConnectionFactory(redisConnectionFactory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new StringRedisSerializer());
        template.afterPropertiesSet();
        return template;
    }
    @Bean
    @ConditionalOnBean(RedisConnectionFactory.class)
    public IdempotencyStore redisIdempotencyStore(
            @Qualifier("idempotencyRedisTemplate") RedisTemplate<String, String> template,
            ObjectMapper objectMapper,
            IdempotencyProperties properties) {
        return new RedisIdempotencyStore(template, objectMapper, properties.getTtl().toSeconds());
    }
    @Bean
    public IdempotencyFilter idempotencyFilter(IdempotencyStore idempotencyStore) {
        return new IdempotencyFilter(idempotencyStore);
    }
    @Bean
    public WrapperFilter wrapperFilter(IdempotencyStore idempotencyStore) {
        return new WrapperFilter(idempotencyStore);
    }


}
