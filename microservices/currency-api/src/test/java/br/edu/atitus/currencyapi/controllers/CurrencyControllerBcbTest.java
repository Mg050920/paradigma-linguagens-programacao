package br.edu.atitus.currencyapi.controllers;

import br.edu.atitus.currencyapi.services.CurrencyService;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CurrencyControllerBcbTest {

    @Test
    void erroDeCotacaoAusenteViraHttp404() {
        CurrencyService service = (source, target) -> {
            throw new EntityNotFoundException("Cotacao nao encontrada no BCB nem no banco local");
        };
        CurrencyController controller = new CurrencyController(service);
        var excecao = assertThrows(EntityNotFoundException.class,
                () -> controller.getCurrency("USD", "BRL"));
        var resposta = controller.entityNotFoundExceptionHandler(excecao);
        assertEquals(404, resposta.getStatusCode().value());
    }
}
