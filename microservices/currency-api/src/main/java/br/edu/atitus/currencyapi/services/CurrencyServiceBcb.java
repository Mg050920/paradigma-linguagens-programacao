package br.edu.atitus.currencyapi.services;

import br.edu.atitus.currencyapi.dtos.CurrencyResponse;
import br.edu.atitus.currencyapi.entities.CurrencyEntity;
import br.edu.atitus.currencyapi.repositories.CurrencyRepository;
import jakarta.persistence.EntityNotFoundException;
import java.time.LocalDate;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

// Mantive a CurrencyServiceJpa original e seu contrato, utilizado pelo product-api.
@Service
@Primary
public class CurrencyServiceBcb implements CurrencyService {
    private static final Logger log = LoggerFactory.getLogger(CurrencyServiceBcb.class);
    private final CachedBcbQuoteService cotacoesExternas;
    private final CurrencyRepository repository;
    private final int port;
    private final LocalDate dataCotacao;

    public CurrencyServiceBcb(CachedBcbQuoteService cotacoesExternas,
                              CurrencyRepository repository,
                              @Value("${server.port:8100}") int port,
                              @Value("${bcb.ptax.data-cotacao}") String dataCotacao) {
        this.cotacoesExternas = cotacoesExternas;
        this.repository = repository;
        this.port = port;
        this.dataCotacao = LocalDate.parse(dataCotacao);
    }

    @Override
    public CurrencyResponse findBySourceCurrencyAndTargetCurrency(
            String sourceCurrency, String targetCurrency) {
        String source = normalizar(sourceCurrency);
        String target = normalizar(targetCurrency);
        String ambiente = "Currency API running in Port: " + port;

        if (source.equals(target)) {
            return new CurrencyResponse(source, target, 1.0, ambiente);
        }
        try {
            // A PTAX fornece cotacoes contra o real. Para duas moedas estrangeiras,
            // a taxa cruzada e BRL/origem dividido por BRL/destino.
            double origemEmBrl = source.equals("BRL") ? 1.0
                    : cotacoesExternas.consultar(source, dataCotacao);
            double destinoEmBrl = target.equals("BRL") ? 1.0
                    : cotacoesExternas.consultar(target, dataCotacao);
            double taxa = origemEmBrl / destinoEmBrl;
            if (!Double.isFinite(taxa) || taxa <= 0) {
                throw new IllegalStateException("Cotacao externa invalida");
            }
            return new CurrencyResponse(source, target, taxa, ambiente);
        } catch (RuntimeException ex) {
            // Inclui erro HTTP, timeout, circuito aberto e retorno sem cotacao.
            log.warn("PTAX indisponivel para {} -> {}: {}. Consultando banco local.",
                    source, target, ex.toString());
            CurrencyEntity local = repository.findBySourceCurrencyAndTargetCurrency(source, target)
                    .filter(e -> e.getConversionRate() != null
                            && Double.isFinite(e.getConversionRate())
                            && e.getConversionRate() > 0)
                    .orElseThrow(() -> new EntityNotFoundException(
                            "Cotacao nao encontrada no BCB nem no banco local: " + source + " -> " + target));
            return new CurrencyResponse(source, target, local.getConversionRate(), ambiente);
        }
    }

    private String normalizar(String moeda) {
        if (moeda == null || !moeda.trim().matches("(?i)[A-Z]{3}")) {
            throw new IllegalArgumentException("Codigo de moeda invalido");
        }
        return moeda.trim().toUpperCase(Locale.ROOT);
    }
}
