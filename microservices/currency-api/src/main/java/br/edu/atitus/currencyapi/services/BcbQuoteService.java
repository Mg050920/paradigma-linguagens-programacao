package br.edu.atitus.currencyapi.services;

import br.edu.atitus.currencyapi.bcb.BcbPtaxClient;
import br.edu.atitus.currencyapi.bcb.BcbPtaxResponse;
import br.edu.atitus.currencyapi.bcb.BcbQuoteNotFoundException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryRegistry;
import java.net.URI;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class BcbQuoteService {
    private static final Logger log = LoggerFactory.getLogger(BcbQuoteService.class);
    private static final DateTimeFormatter FORMATO_BCB = DateTimeFormatter.ofPattern("MM-dd-uuuu");

    private final BcbPtaxClient client;
    private final Retry retry;
    private final CircuitBreaker circuitBreaker;
    private final String baseUrl;

    public BcbQuoteService(BcbPtaxClient client,
                           RetryRegistry retryRegistry,
                           CircuitBreakerRegistry circuitBreakerRegistry,
                           @Value("${bcb.ptax.base-url}") String baseUrl) {
        this.client = client;
        this.retry = retryRegistry.retry("bcb-ptax");
        this.circuitBreaker = circuitBreakerRegistry.circuitBreaker("bcb-ptax");
        this.baseUrl = baseUrl.replaceAll("/+$", "");
        this.retry.getEventPublisher().onRetry(event ->
                log.warn("BCB: nova tentativa apos falha: {}", event.getLastThrowable().toString()));
        this.circuitBreaker.getEventPublisher().onStateTransition(event ->
                log.warn("BCB: Circuit Breaker {}", event.getStateTransition()));
    }

    public Double consultar(String moeda, LocalDate data) {
        // Retry por fora do Circuit Breaker: cada tentativa conta como chamada externa.
        Supplier<Double> chamada = CircuitBreaker.decorateSupplier(circuitBreaker,
                () -> consultarUmaVez(moeda, data));
        return Retry.decorateSupplier(retry, chamada).get();
    }

    private Double consultarUmaVez(String moeda, LocalDate data) {
        // Moeda validada no servico principal; evita parametros inesperados na URL.
        if (!moeda.matches("[A-Z]{3}")) {
            throw new IllegalArgumentException("Codigo de moeda invalido: " + moeda);
        }
        String endereco = baseUrl
                + "/CotacaoMoedaDia(moeda=@moeda,dataCotacao=@dataCotacao)"
                + "?@moeda='" + moeda + "'&@dataCotacao='" + data.format(FORMATO_BCB)
                + "'&$format=json";
        log.info("Consultando PTAX do Banco Central: moeda={}, data={}", moeda, data);
        BcbPtaxResponse resposta = client.consultar(URI.create(endereco));
        List<BcbPtaxResponse.Cotacao> cotacoes = resposta == null ? null : resposta.value();
        if (cotacoes == null || cotacoes.isEmpty()) {
            throw new BcbQuoteNotFoundException("BCB nao encontrou cotacao de " + moeda + " em " + data);
        }

        // Preferir o boletim de fechamento, que representa o fechamento da data.
        return cotacoes.stream()
                .filter(c -> c != null && c.cotacaoVenda() != null
                        && Double.isFinite(c.cotacaoVenda()) && c.cotacaoVenda() > 0)
                .filter(c -> c.tipoBoletim() != null
                        && c.tipoBoletim().toLowerCase(Locale.ROOT).contains("fechamento"))
                .map(BcbPtaxResponse.Cotacao::cotacaoVenda)
                .findFirst()
                .orElseGet(() -> cotacoes.stream()
                        .filter(c -> c != null && c.cotacaoVenda() != null
                                && Double.isFinite(c.cotacaoVenda()) && c.cotacaoVenda() > 0)
                        .reduce((anterior, atual) -> atual)
                        .map(BcbPtaxResponse.Cotacao::cotacaoVenda)
                        .orElseThrow(() -> new BcbQuoteNotFoundException(
                                "BCB nao retornou cotacao valida de " + moeda + " em " + data)));
    }
}
