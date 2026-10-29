package br.edu.atitus.currencyapi.services;

import br.edu.atitus.currencyapi.bcb.BcbPtaxClient;
import br.edu.atitus.currencyapi.bcb.BcbPtaxResponse;
import br.edu.atitus.currencyapi.bcb.BcbQuoteNotFoundException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import java.net.URI;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class BcbQuoteServiceTest {
    private BcbPtaxClient client;
    private BcbQuoteService service;
    private final LocalDate data = LocalDate.of(2026, 10, 27);

    @BeforeEach
    void iniciar() {
        client = mock(BcbPtaxClient.class);
        RetryConfig retryConfig = RetryConfig.custom()
                .maxAttempts(3).waitDuration(Duration.ofMillis(1))
                .ignoreExceptions(CallNotPermittedException.class, BcbQuoteNotFoundException.class)
                .build();
        CircuitBreakerConfig circuitConfig = CircuitBreakerConfig.custom()
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(5).minimumNumberOfCalls(5)
                .failureRateThreshold(50).waitDurationInOpenState(Duration.ofSeconds(100))
                .ignoreExceptions(BcbQuoteNotFoundException.class)
                .build();
        service = new BcbQuoteService(client, RetryRegistry.of(retryConfig),
                CircuitBreakerRegistry.of(circuitConfig), "https://olinda.bcb.gov.br/olinda/servico/PTAX/versao/v1/odata");
    }

    @Test
    void usaBoletimDeFechamentoEDataFormatoBcb() {
        when(client.consultar(any(URI.class))).thenReturn(new BcbPtaxResponse(List.of(
                new BcbPtaxResponse.Cotacao(5.10, "Abertura"),
                new BcbPtaxResponse.Cotacao(5.20, "Fechamento PTAX"))));
        assertEquals(5.20, service.consultar("USD", data), 0.000001);
        var captor = org.mockito.ArgumentCaptor.forClass(URI.class);
        verify(client).consultar(captor.capture());
        String url = captor.getValue().toString();
        assertTrue(url.contains("@moeda='USD'"));
        assertTrue(url.contains("@dataCotacao='10-27-2026'"));
    }

    @Test
    void semCotacaoExternaNaoFazRetryDesnecessario() {
        when(client.consultar(any(URI.class))).thenReturn(new BcbPtaxResponse(List.of()));
        assertThrows(BcbQuoteNotFoundException.class, () -> service.consultar("USD", data));
        verify(client, times(1)).consultar(any(URI.class));
    }

    @Test
    void fazTresTentativasQuandoFalhaComunicao() {
        when(client.consultar(any(URI.class))).thenThrow(new RuntimeException("rede indisponivel"));
        assertThrows(RuntimeException.class, () -> service.consultar("USD", data));
        verify(client, times(3)).consultar(any(URI.class));
    }

    @Test
    void circuitBreakerInterrompeChamadasDepoisDeCincoFalhas() {
        when(client.consultar(any(URI.class))).thenThrow(new RuntimeException("rede indisponivel"));
        assertThrows(RuntimeException.class, () -> service.consultar("USD", data));
        assertThrows(CallNotPermittedException.class, () -> service.consultar("USD", data));
        assertThrows(CallNotPermittedException.class, () -> service.consultar("USD", data));
        // Cinco tentativas chegaram ao HTTP. Ao abrir, o Circuit Breaker bloqueia novas chamadas.
        verify(client, times(5)).consultar(any(URI.class));
    }

    @Test
    void aceitaCotacaoValidaMesmoSemBoletimDeFechamento() {
        when(client.consultar(any(URI.class))).thenReturn(new BcbPtaxResponse(List.of(
                new BcbPtaxResponse.Cotacao(5.10, "Intermediario"))));
        assertEquals(5.10, service.consultar("USD", data), 0.000001);
    }
}
