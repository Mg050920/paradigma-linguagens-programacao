package br.edu.atitus.currencyapi.services;

import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CachedBcbQuoteServiceTest {
    @Configuration
    @EnableCaching
    static class ConfiguracaoCache {
        @Bean
        CacheManager cacheManager() {
            CaffeineCacheManager cacheManager = new CaffeineCacheManager("cotacoes-bcb");
            cacheManager.setCaffeine(Caffeine.newBuilder()
                    .maximumSize(1000).expireAfterWrite(Duration.ofMinutes(10)));
            return cacheManager;
        }
        @Bean
        BcbQuoteService bcbQuoteService() {
            return mock(BcbQuoteService.class);
        }
        @Bean
        CachedBcbQuoteService cachedBcbQuoteService(BcbQuoteService externo) {
            return new CachedBcbQuoteService(externo);
        }
    }

    @Test
    void reutilizaCotacaoPorMoedaEData() {
        try (var contexto = new AnnotationConfigApplicationContext(ConfiguracaoCache.class)) {
            var cache = contexto.getBean(CachedBcbQuoteService.class);
            var externo = contexto.getBean(BcbQuoteService.class);
            var d1 = LocalDate.of(2026, 10, 27);
            var d2 = LocalDate.of(2026, 10, 26);
            when(externo.consultar("USD", d1)).thenReturn(5.2);
            when(externo.consultar("USD", d2)).thenReturn(5.1);
            when(externo.consultar("EUR", d1)).thenReturn(6.0);
            assertEquals(5.2, cache.consultar("USD", d1));
            assertEquals(5.2, cache.consultar("USD", d1));
            assertEquals(5.1, cache.consultar("USD", d2));
            assertEquals(6.0, cache.consultar("EUR", d1));
            verify(externo, times(1)).consultar("USD", d1);
            verify(externo, times(1)).consultar("USD", d2);
            verify(externo, times(1)).consultar("EUR", d1);
        }
    }

    @Test
    void naoArmazenaFalhaNoCache() {
        try (var contexto = new AnnotationConfigApplicationContext(ConfiguracaoCache.class)) {
            var cache = contexto.getBean(CachedBcbQuoteService.class);
            var externo = contexto.getBean(BcbQuoteService.class);
            var data = LocalDate.of(2026, 10, 27);
            when(externo.consultar("USD", data))
                    .thenThrow(new RuntimeException("falha"))
                    .thenReturn(5.2);
            assertThrows(RuntimeException.class, () -> cache.consultar("USD", data));
            assertEquals(5.2, cache.consultar("USD", data));
            verify(externo, times(2)).consultar("USD", data);
        }
    }
}
