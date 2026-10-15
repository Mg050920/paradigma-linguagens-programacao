package br.edu.atitus.productapi.servicesimpl;

import br.edu.atitus.productapi.dtos.CurrencyResponse;
import br.edu.atitus.productapi.entities.ProductEntity;
import br.edu.atitus.productapi.repositories.ProductRepository;
import org.junit.jupiter.api.Test;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProductServiceJpaTest {
    @Test
    void converteEPreservaOsCamposDoProduto() throws Exception {
        var repository = mock(ProductRepository.class);
        var currency = mock(CurrencyClientService.class);
        when(repository.findById(1L)).thenReturn(Optional.of(produto()));
        when(currency.getCurrency("USD", "BRL"))
                .thenReturn(new CurrencyResponse("USD", "BRL", 5.15, "Currency API"));
        var response = new ProductServiceJpa(repository, currency).findById(1L, "BRL");
        assertEquals(515, response.convertedPrice(), 0.001);
        assertEquals("Marca", response.brand());
        assertEquals("Modelo", response.model());
        assertEquals("Descricao", response.description());
    }

    @Test
    void falhaDaConversaoPreservaProdutoComMarcador() throws Exception {
        var repository = mock(ProductRepository.class);
        var currency = mock(CurrencyClientService.class);
        when(repository.findById(1L)).thenReturn(Optional.of(produto()));
        when(currency.getCurrency("USD", "BRL")).thenThrow(new IllegalStateException("indisponivel"));
        var response = new ProductServiceJpa(repository, currency).findById(1L, "BRL");
        assertEquals(-1, response.convertedPrice());
        assertEquals(100, response.price());
        assertEquals(1L, response.id());
        assertTrue(response.environment().contains("ERROR: indisponivel"));
    }

    @Test
    void mesmaMoedaNaoConsultaServicoRemoto() throws Exception {
        var repository = mock(ProductRepository.class);
        var currency = mock(CurrencyClientService.class);
        when(repository.findById(1L)).thenReturn(Optional.of(produto()));
        var response = new ProductServiceJpa(repository, currency).findById(1L, "USD");
        assertEquals(100, response.convertedPrice());
        verifyNoInteractions(currency);
    }

    private ProductEntity produto() {
        return new ProductEntity(1L, "Marca", "Modelo", "Descricao", "USD", 100, "imagem");
    }
}
