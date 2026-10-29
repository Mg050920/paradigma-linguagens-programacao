package br.edu.atitus.currencyapi.bcb;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

// A API do BCB devolve as cotacoes dentro do campo "value".
@JsonIgnoreProperties(ignoreUnknown = true)
public record BcbPtaxResponse(List<Cotacao> value) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Cotacao(Double cotacaoVenda, String tipoBoletim) { }
}
