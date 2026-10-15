package br.edu.atitus.currencyapi.servicesimpl;

import br.edu.atitus.currencyapi.dtos.CurrencyResponse;
import br.edu.atitus.currencyapi.repositories.CurrencyRepository;
import br.edu.atitus.currencyapi.services.CurrencyService;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class CurrencyServiceJpa implements CurrencyService {

    private final CurrencyRepository repository;

    @Value("${server.port:8080}")
    private String serverPort;

    public CurrencyServiceJpa(CurrencyRepository repository) {
        this.repository = repository;
    }

    @Override
    public CurrencyResponse findBySourceCurrencyAndTargetCurrency(String sourceCurrency, String targetCurrency) throws Exception {
        var currency =  repository.findBySourceCurrencyAndTargetCurrency(sourceCurrency.toUpperCase(), targetCurrency.toUpperCase())
                .orElseThrow(() -> new EntityNotFoundException("Currency não encontrado"));
        String environment = "Currency API running in port " + serverPort;
        return new CurrencyResponse(currency.getSourceCurrency(), currency.getTargetCurrency(), currency.getConversionRate(), environment);
    }
}
