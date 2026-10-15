package br.edu.atitus.productapi.servicesimpl;

import br.edu.atitus.productapi.clients.CurrencyClient;
import br.edu.atitus.productapi.dtos.CurrencyResponse;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

@Service
public class CurrencyClientService {
    private static final Logger log = LoggerFactory.getLogger(CurrencyClientService.class);
    private final CurrencyClient currencyClient;

    public CurrencyClientService(CurrencyClient currencyClient) {
        this.currencyClient = currencyClient;
    }

    // Ordem configurada: Cache(99) -> Retry(100) -> CircuitBreaker(101).
    @Cacheable(value = "currency-api", key = "#source + '-' + #target")
    @Retry(name = "currency-api")
    @CircuitBreaker(name = "currency-api")
    public CurrencyResponse getCurrency(String source, String target) {
        log.info("Consultando currency-api: {} -> {}", source, target);
        return currencyClient.getCurrency(source, target);
    }
}
