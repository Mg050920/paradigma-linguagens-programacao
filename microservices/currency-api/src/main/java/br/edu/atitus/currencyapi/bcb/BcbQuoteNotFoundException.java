package br.edu.atitus.currencyapi.bcb;

// Nao ha motivo para repetir uma consulta que retornou sem cotacao.
public class BcbQuoteNotFoundException extends RuntimeException {
    public BcbQuoteNotFoundException(String message) {
        super(message);
    }
}
