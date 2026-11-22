package com.hemz.distributedcache.modules.productservice.service;

import com.hemz.distributedcache.modules.cacheclient.CacheAdmin;
import com.hemz.distributedcache.modules.cacheclient.CacheOperations;
import com.hemz.distributedcache.modules.cacheclient.CacheNodeStatus;
import com.hemz.distributedcache.modules.cacheclient.aside.CacheAsideTemplate;
import com.hemz.distributedcache.modules.cacheclient.aside.CacheMetrics;
import com.hemz.distributedcache.modules.productservice.cache.CacheSwitch;
import com.hemz.distributedcache.modules.productservice.cache.ProductCacheProperties;
import com.hemz.distributedcache.modules.productservice.cache.ProductCodec;
import com.hemz.distributedcache.modules.productservice.dto.ProductResponse;
import com.hemz.distributedcache.modules.productservice.repo.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** The real cache-aside logic, with the cache as a map and the database (ProductReader) mocked. */
class ProductServiceTest {

    /** Metrics are not checked here. */
    private static final CacheMetrics NO_METRICS = new CacheMetrics() {
        @Override public void hit(long nanos) { }
        @Override public void miss(long nanos) { }
        @Override public void error() { }
    };

    private static final ProductResponse PRODUCT = new ProductResponse(
            42L, "SKU-42", "Lamp", "A desk lamp", new BigDecimal("19.99"), 3L, true);

    private final ProductReader reader = mock(ProductReader.class);
    private final MapCache cache = new MapCache();
    private CacheSwitch cacheSwitch;
    private ProductService service;

    @BeforeEach
    void setUp() {
        when(reader.loadById(42L)).thenReturn(PRODUCT);
        cacheSwitch = new CacheSwitch(props(true), cache);
        CacheAsideTemplate template = new CacheAsideTemplate(cache, NO_METRICS);
        service = new ProductService(mock(ProductRepository.class), reader, template,
                new ProductCodec(JsonMapper.builder().build()), cacheSwitch);
    }

    /** The cache settings. Only "enabled" matters here: the cache is a map, so the node settings are never used. */
    private static ProductCacheProperties props(boolean enabled) {
        ProductCacheProperties.Remote remote = new ProductCacheProperties.Remote(
                List.of("127.0.0.1:7001"),
                150,    // virtualNodes
                10,     // connectTimeoutMs
                20,     // readTimeoutMs
                8,      // poolSize
                20,     // borrowTimeoutMs
                3,      // breakerThreshold
                5000);  // breakerCooldownMs
        return new ProductCacheProperties(enabled, remote);
    }

    @Test
    void cacheOnSecondReadSkipsTheDatabase() {
        assertEquals(PRODUCT, service.getProductById(42L));
        assertEquals(PRODUCT, service.getProductById(42L));

        verify(reader, times(1)).loadById(42L);
        assertEquals(1, cache.map.size());
    }

    @Test
    void cacheOffEveryReadGoesToTheDatabase() {
        cacheSwitch.set(false);

        service.getProductById(42L);
        service.getProductById(42L);

        verify(reader, times(2)).loadById(42L);
        assertEquals(0, cache.map.size());
    }

    @Test
    void unknownIdIsNotFound() {
        assertThrows(ProductNotFoundException.class, () -> service.getProductById(999L));
    }

    @Test
    void switchingEmptiesTheCache() {
        service.getProductById(42L);

        cacheSwitch.set(false);
        cacheSwitch.set(true);
        service.getProductById(42L);

        assertEquals(1, cache.map.size());
        verify(reader, times(2)).loadById(42L);
    }

    @Test
    void deadCacheStillAnswersFromTheDatabase() {
        cache.down = true;

        assertEquals(PRODUCT, service.getProductById(42L));
        assertEquals(PRODUCT, service.getProductById(42L));

        verify(reader, times(2)).loadById(42L);
    }

    @Test
    void switchStillFlipsWhenTheCacheIsDown() {
        cache.down = true;

        cacheSwitch.set(false);

        assertFalse(cacheSwitch.isEnabled());
    }

    /** Stands in for Cache System. down = every call fails, like a stopped server. */
    private static final class MapCache implements CacheOperations, CacheAdmin {
        final Map<String, byte[]> map = new HashMap<>();
        boolean down;

        private void check() {
            if (down) throw new IllegalStateException("cache down");
        }

        @Override public byte[] get(String key) { check(); return map.get(key); }
        @Override public void set(String key, byte[] value) { check(); map.put(key, value); }
        @Override public void delete(String key) { check(); map.remove(key); }
        @Override public void flushAll() { check(); map.clear(); }
        @Override public String address() { return "test"; }
        @Override public List<CacheNodeStatus> nodes() { return List.of(); }
        @Override public void shutdownNode(String name) { throw new UnsupportedOperationException(); }
    }
}
