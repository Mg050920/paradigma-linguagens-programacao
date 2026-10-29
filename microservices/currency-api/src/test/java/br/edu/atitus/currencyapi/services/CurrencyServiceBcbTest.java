package br.edu.atitus.currencyapi.services;

import br.edu.atitus.currencyapi.entities.CurrencyEntity;
import br.edu.atitus.currencyapi.repositories.CurrencyRepository;
import jakarta.persistence.EntityNotFoundException;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CurrencyServiceBcbTest {
    private CachedBcbQuoteService externo;
    private CurrencyRepository repository;
    private CurrencyServiceBcb service;
    private final LocalDate data = LocalDate.of(2026, 10, 27);

    @BeforeEach
    void iniciar() {
        externo = mock(CachedBcbQuoteService.class);
        repository = mock(CurrencyRepository.class);
        service = new CurrencyServiceBcb(externo, repository, 8100, "2026-10-27");
    }

    @Test
    void retornaCotacaoDoBcbParaUsdBrl() {
        when(externo.consultar("USD", data)).thenReturn(5.25);
        var resultado = service.findBySourceCurrencyAndTargetCurrency("usd", "brl");
        assertEquals(5.25, resultado.conversionRate(), 0.000001);
        assertEquals("USD", resultado.sourceCurrency());
        assertEquals("BRL", resultado.targetCurrency());
        assertEquals("Currency API running in Port: 8100", resultado.environment());
        verifyNoInteractions(repository);
    }

    @Test
    void calculaConversaoInversaSemConsultarBrl() {
        when(externo.consultar("USD", data)).thenReturn(5.0);
        assertEquals(0.20,
                service.findBySourceCurrencyAndTargetCurrency("BRL", "USD").conversionRate(), 0.000001);
        verify(externo, times(1)).consultar("USD", data);
        verifyNoMoreInteractions(externo);
    }

    @Test
    void calculaTaxaCruzadaEntreMoedasEstrangeiras() {
        when(externo.consultar("USD", data)).thenReturn(5.0);
        when(externo.consultar("EUR", data)).thenReturn(6.0);
        assertEquals(5.0 / 6.0,
                service.findBySourceCurrencyAndTargetCurrency("USD", "EUR").conversionRate(), 0.000001);
    }

    @Test
    void usaBancoLocalQuandoApiExternaFalha() {
        when(externo.consultar("USD", data)).thenThrow(new RuntimeException("BCB fora do ar"));
        CurrencyEntity local = new CurrencyEntity();
        local.setSourceCurrency("USD");
        local.setTargetCurrency("BRL");
        local.setConversionRate(5.10);
        when(repository.findBySourceCurrencyAndTargetCurrency("USD", "BRL"))
                .thenReturn(Optional.of(local));
        assertEquals(5.10,
                service.findBySourceCurrencyAndTargetCurrency("USD", "BRL").conversionRate(), 0.000001);
    }

    @Test
    void retornaNotFoundQuandoBcbEBancoLocalNaoTemCotacao() {
        when(externo.consultar("USD", data)).thenThrow(new RuntimeException("sem dados"));
        when(repository.findBySourceCurrencyAndTargetCurrency("USD", "BRL"))
                .thenReturn(Optional.empty());
        assertThrows(EntityNotFoundException.class,
                () -> service.findBySourceCurrencyAndTargetCurrency("USD", "BRL"));
        // CurrencyController existente trata EntityNotFoundException como HTTP 404.
    }

    @Test
    void mesmaMoedaNaoPrecisaDeCotacao() {
        assertEquals(1.0,
                service.findBySourceCurrencyAndTargetCurrency("BRL", "BRL").conversionRate(), 0.000001);
        verifyNoInteractions(externo, repository);
    }
}
