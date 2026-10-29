package br.edu.atitus.currencyapi.bcb;

import java.net.URI;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;

// Cliente declarativo: o RestClient executa a chamada HTTP definida por HttpExchange.
@HttpExchange
public interface BcbPtaxClient {
    @GetExchange
    BcbPtaxResponse consultar(URI endereco);
}
