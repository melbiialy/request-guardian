package dev.once.configuration;

import dev.once.filter.IdempotencyFilter;
import dev.once.filter.WrapperFilter;
import dev.once.storage.IdempotencyStore;
import dev.once.storage.InMemoryIdempotencyStore;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@AutoConfiguration
@EnableConfigurationProperties(IdempotencyProperties.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@Import( RedisIdempotencyConfiguration.class)
public class IdempotencyAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean({IdempotencyStore.class, RedisConnectionFactory.class})
    public IdempotencyStore inMemoryIdempotencyStore(IdempotencyProperties properties) {
        return new InMemoryIdempotencyStore(properties.getTtl().getSeconds());
    }

    @Bean
    public IdempotencyFilter idempotencyFilter(IdempotencyStore idempotencyStore) {
        return new IdempotencyFilter(idempotencyStore);
    }
    @Bean
    public WrapperFilter wrapperFilter(IdempotencyStore idempotencyStore) {
        return new WrapperFilter(idempotencyStore);
    }
    @Bean
    public WebMvcConfigurer idempotencyWebMvcConfigurer(IdempotencyFilter idempotencyFilter) {
        return new WebMvcConfigurer() {
            @Override
            public void addInterceptors(InterceptorRegistry registry) {
                registry.addInterceptor(idempotencyFilter);
            }
        };
    }


}
