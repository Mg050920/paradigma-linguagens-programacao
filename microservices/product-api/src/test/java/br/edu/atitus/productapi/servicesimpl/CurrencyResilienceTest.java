package br.edu.atitus.productapi.servicesimpl;

import br.edu.atitus.productapi.clients.CurrencyClient;
import br.edu.atitus.productapi.dtos.CurrencyResponse;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest(classes = CurrencyResilienceTest.App.class, properties = {
    "spring.cloud.consul.enabled=false",
    "spring.cloud.consul.config.enabled=false",
    "spring.cloud.discovery.enabled=false",
    "spring.autoconfigure.exclude=org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration,org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration",
    "spring.cache.type=caffeine",
    "spring.cache.cache-names=currency-api",
    "spring.cache.caffeine.spec=maximumSize=1000,expireAfterWrite=10m",
    "resilience4j.retry.retry-aspect-order=100",
    "resilience4j.retry.instances.currency-api.max-attempts=3",
    "resilience4j.retry.instances.currency-api.wait-duration=1ms",
    "resilience4j.retry.instances.currency-api.ignore-exceptions[0]=io.github.resilience4j.circuitbreaker.CallNotPermittedException",
    "resilience4j.circuitbreaker.circuit-breaker-aspect-order=101",
    "resilience4j.circuitbreaker.instances.currency-api.sliding-window-type=COUNT_BASED",
    "resilience4j.circuitbreaker.instances.currency-api.sliding-window-size=5",
    "resilience4j.circuitbreaker.instances.currency-api.minimum-number-of-calls=5",
    "resilience4j.circuitbreaker.instances.currency-api.failure-rate-threshold=50",
    "resilience4j.circuitbreaker.instances.currency-api.wait-duration-in-open-state=100s"
})
class CurrencyResilienceTest {
    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    @EnableCaching(order = 99)
    @Import(CurrencyClientService.class)
    static class App {
        @Bean CurrencyClient currencyClient() { return mock(CurrencyClient.class); }
    }
    @Autowired CurrencyClientService service;
    @Autowired CurrencyClient client;
    @Autowired CacheManager caches;
    @Autowired CircuitBreakerRegistry circuits;

    @BeforeEach
    void resetState() {
        reset(client);
        caches.getCache("currency-api").clear();
        circuits.circuitBreaker("currency-api").reset();
    }

    @Test
    void repeteFalhasTransitoriasEArmazenaSucesso() {
        var expected = new CurrencyResponse("USD", "BRL", 5.15, "teste");
        when(client.getCurrency("USD", "BRL"))
            .thenThrow(new IllegalStateException("temporario"))
            .thenThrow(new IllegalStateException("temporario"))
            .thenReturn(expected);
        assertEquals(expected, service.getCurrency("USD", "BRL"));
        assertEquals(expected, service.getCurrency("USD", "BRL"));
        verify(client, times(3)).getCurrency("USD", "BRL");
    }

    @Test
    void abreAposCincoTentativasENaoArmazenaFalhas() {
        when(client.getCurrency("USD", "BRL")).thenThrow(new IllegalStateException("fora"));
        assertThrows(Exception.class, () -> service.getCurrency("USD", "BRL"));
        assertThrows(Exception.class, () -> service.getCurrency("USD", "BRL"));
        assertEquals(io.github.resilience4j.circuitbreaker.CircuitBreaker.State.OPEN,
            circuits.circuitBreaker("currency-api").getState());
        assertThrows(io.github.resilience4j.circuitbreaker.CallNotPermittedException.class,
            () -> service.getCurrency("USD", "BRL"));
        verify(client, times(5)).getCurrency("USD", "BRL");
        assertNull(caches.getCache("currency-api").get("USD-BRL"));
    }

    @Test
    void cacheRespondeMesmoComCircuitoAberto() {
        var expected = new CurrencyResponse("USD", "BRL", 5.15, "teste");
        when(client.getCurrency("USD", "BRL")).thenReturn(expected);
        service.getCurrency("USD", "BRL");
        circuits.circuitBreaker("currency-api").transitionToOpenState();
        assertEquals(expected, service.getCurrency("USD", "BRL"));
        verify(client, times(1)).getCurrency("USD", "BRL");
    }
}
