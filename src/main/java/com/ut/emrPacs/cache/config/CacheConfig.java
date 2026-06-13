package com.ut.emrPacs.cache.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Cache;
import com.ut.emrPacs.model.permission.EndpointPermissionRule;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;

import java.time.Duration;
import java.util.Set;

@Configuration
@EnableCaching
public class CacheConfig {

    public static final String DROPDOWN_COUNTRIES = "dropdownCountries";
    public static final String DROPDOWN_HOSPITALS_BY_USER = "dropdownHospitalsByUser";
    public static final String DROPDOWN_MODALITYS_BY_HOSPITAL = "dropdownModalitiesByHospital";
    public static final String DROPDOWN_MODALITY_CATALOG = "dropdownModalityCatalog";
    public static final String DROPDOWN_DICOM_SERVERS_BY_HOSPITAL = "dropdownDicomServersByHospital";
    public static final String DROPDOWN_USERS = "dropdownUsers";
    public static final String DROPDOWN_PATIENTS_BY_HOSPITAL = "dropdownPatientsByHospital";
    public static final String DROPDOWN_USER_GROUPS = "dropdownUserGroups";
    public static final String PACS_RESULT_TEMPLATES_BY_SCOPE = "pacsResultTemplatesByScope";
    public static final String[] DROPDOWN_CACHE_NAMES = {
            DROPDOWN_COUNTRIES,
            DROPDOWN_HOSPITALS_BY_USER,
            DROPDOWN_MODALITYS_BY_HOSPITAL,
            DROPDOWN_MODALITY_CATALOG,
            DROPDOWN_DICOM_SERVERS_BY_HOSPITAL,
            DROPDOWN_USERS,
            DROPDOWN_PATIENTS_BY_HOSPITAL,
            DROPDOWN_USER_GROUPS,
            PACS_RESULT_TEMPLATES_BY_SCOPE
    };

    @Bean("endpointPermissionCache")
    public Cache<String, EndpointPermissionRule> endpointPermissionCache() {
        return Caffeine.newBuilder()
                .maximumSize(10000)
                .expireAfterWrite(Duration.ofMinutes(10))
                .recordStats()
                .build();
    }

    @Bean("permissionCodeCache")
    public Cache<String, Set<String>> permissionCodeCache() {
        return Caffeine.newBuilder()
                .maximumSize(20000)
                .expireAfterWrite(Duration.ofMinutes(10))
                .recordStats()
                .build();
    }

    @Bean
    @ConditionalOnProperty(name = "app.cache.provider", havingValue = "redis")
    public CacheManager redisCacheManager(
            RedisConnectionFactory redisConnectionFactory,
            @Value("${app.cache.ttl-seconds:600}") long ttlSeconds,
            @Value("${app.cache.redis.key-prefix:udaya_pacs}") String keyPrefix
    ) {
        String normalizedPrefix = (keyPrefix == null || keyPrefix.isBlank())
                ? "udaya_pacs"
                : keyPrefix.trim();
        RedisCacheConfiguration configuration = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofSeconds(Math.max(30, ttlSeconds)))
                .disableCachingNullValues()
                .prefixCacheNameWith(normalizedPrefix + "::")
                .serializeValuesWith(
                        RedisSerializationContext.SerializationPair.fromSerializer(
                                new GenericJackson2JsonRedisSerializer()
                        )
                );

        return RedisCacheManager.builder(redisConnectionFactory)
                .cacheDefaults(configuration)
                .transactionAware()
                .withInitialCacheConfigurations(java.util.Arrays.stream(DROPDOWN_CACHE_NAMES)
                        .collect(java.util.stream.Collectors.toMap(name -> name, name -> configuration)))
                .build();
    }

    @Bean
    @ConditionalOnMissingBean(CacheManager.class)
    public CacheManager cacheManager(
            @Value("${app.cache.ttl-seconds:600}") long ttlSeconds,
            @Value("${app.cache.max-entries:5000}") long maxEntries
    ) {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager(
                DROPDOWN_CACHE_NAMES
        );
        cacheManager.setAllowNullValues(false);
        cacheManager.setCaffeine(Caffeine.newBuilder()
                .maximumSize(Math.max(1000, maxEntries))
                .expireAfterWrite(Duration.ofSeconds(Math.max(30, ttlSeconds)))
                .recordStats());
        return cacheManager;
    }
}
