package br.edu.atitus.currencyapi.services;

import java.time.LocalDate;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

@Service
public class CachedBcbQuoteService {
    private final BcbQuoteService bcbQuoteService;

    public CachedBcbQuoteService(BcbQuoteService bcbQuoteService) {
        this.bcbQuoteService = bcbQuoteService;
    }

    // A chave combina moeda e data. Excecoes e resultados invalidos nao sao armazenados.
    @Cacheable(cacheNames = "cotacoes-bcb", key = "#p0 + ':' + #p1",
               unless = "#result == null || #result <= 0")
    public Double consultar(String moeda, LocalDate data) {
        return bcbQuoteService.consultar(moeda, data);
    }
}
