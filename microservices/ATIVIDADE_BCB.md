# Atividade Prática — Integração com API Externa e Resilience Patterns

**Aluno:** Maurício Gabriel de Oliveira Becker

**Disciplina:** Paradigmas e Linguagens da Programação

**Professor:** Luciano Rodrigo Ferretto

## 1. Objetivo

Nesta atividade, evoluí o `currency-api` para buscar cotações na API PTAX do Banco Central do Brasil.

O objetivo foi permitir que o sistema consultasse uma fonte externa de cotações, utilizando os padrões de resiliência estudados em aula para lidar com possíveis falhas de comunicação.

Quando o Banco Central não retorna uma cotação válida, o sistema tenta utilizar o valor cadastrado no banco de dados PostgreSQL.

Mantive o endpoint `/currencies?source=USD&target=BRL` e a integração existente entre o `product-api` e o `currency-api`, utilizando o HashiCorp Consul.

## 2. O que foi feito

* **API externa:** implementei a comunicação HTTP utilizando `@HttpExchange` e `RestClient`, consultando o endpoint `CotacaoMoedaDia` do Banco Central.

* **Data de referência:** utilizei a data fixa de 27/10/2026, configurada em `bcb.ptax.data-cotacao=2026-10-27`. Dessa forma, a aplicação consulta uma cotação histórica, e não necessariamente a cotação atual.

* **Fallback:** quando ocorre uma falha HTTP, timeout, circuito aberto ou ausência de cotação, a aplicação consulta o banco de dados local. Caso também não exista uma cotação cadastrada, retorna HTTP 404.

* **Timeout:** configurei 2 segundos para estabelecer a conexão e 5 segundos para aguardar a leitura da resposta.

* **Retry:** configurei até 3 tentativas no total, com intervalo de 1 segundo entre elas. Chamadas rejeitadas pelo Circuit Breaker e respostas sem cotação não são repetidas.

* **Circuit Breaker:** configurei uma janela baseada em contagem de 5 chamadas, com mínimo de 5 chamadas para avaliação, taxa de falhas de 50% e tempo de 100 segundos no estado aberto.

* **Cache:** utilizei Caffeine para armazenar até 1.000 cotações em memória, com expiração de 10 minutos após a gravação. A chave considera a moeda e a data consultada, armazenando somente resultados válidos.

Para realizar conversões entre moedas estrangeiras, a aplicação consulta a cotação em reais de cada moeda e divide a cotação de origem pela cotação de destino.

Na conversão de BRL para uma moeda estrangeira, utiliza o inverso da cotação. Quando as moedas de origem e destino são iguais, a taxa de conversão é 1.

## 3. Organização das classes

As principais classes adicionadas foram:

* `bcb/BcbPtaxClient`: define a comunicação HTTP com a API do Banco Central.

* `bcb/BcbPtaxResponse`: representa os dados recebidos na resposta JSON.

* `bcb/BcbClientConfiguration`: configura o RestClient, os timeouts e a habilitação do cache.

* `services/BcbQuoteService`: realiza as consultas externas utilizando Retry e Circuit Breaker com Resilience4j.

* `services/CachedBcbQuoteService`: gerencia o cache das cotações obtidas da API externa.

* `services/CurrencyServiceBcb`: realiza os cálculos de conversão e utiliza o banco de dados local como fallback.

A classe `CurrencyServiceBcb` implementa a interface `CurrencyService`, já utilizada pelo controller, e recebe a anotação `@Primary`.

A implementação JPA anterior foi mantida, preservando a estrutura existente do projeto.

## 4. Tecnologias utilizadas

* Java 25
* Spring Boot
* Spring Cloud
* Spring Data JPA
* HttpExchange e RestClient
* PostgreSQL 16
* HashiCorp Consul
* Resilience4j
* Caffeine Cache
* Flyway
* Maven
* Docker

## 5. Como executar o projeto

Para executar a aplicação, é necessário ter o JDK 25, o Docker Desktop e os serviços PostgreSQL e Consul disponíveis.

### 5.1. Banco de dados

Foram utilizados dois bancos PostgreSQL:

* `db_currency`: armazenamento das cotações locais.
* `db_product`: armazenamento dos produtos.

Durante os testes locais, utilizei o PostgreSQL 16 executado em um container Docker, disponibilizado na porta 5433 do computador.

### 5.2. Discovery Service

Utilizei o HashiCorp Consul para manter a descoberta de serviços entre os microserviços.

Nos testes locais, o Consul foi executado pelo Docker, com a interface disponível em:

`http://localhost:8500`

As configurações dos microserviços foram cadastradas no armazenamento Key/Value do Consul.

Também foi preservado o arquivo `start_consul.bat` do projeto original, que pode ser utilizado em ambientes com o executável do Consul instalado e configurado.

### 5.3. Currency API

Na pasta `microservices/currency-api`, executar:

```cmd
mvnw.cmd clean test
```

Para iniciar a aplicação:

```cmd
mvnw.cmd spring-boot:run
```

A aplicação utiliza a porta 8100.

Endpoint para consulta:

`http://localhost:8100/currencies?source=USD&target=BRL`

### 5.4. Product API

Na pasta `microservices/product-api`, executar:

```cmd
mvnw.cmd test
```

Para iniciar a aplicação:

```cmd
mvnw.cmd spring-boot:run
```

A aplicação utiliza a porta 8000.

Endpoint para consulta:

`http://localhost:8000/products/1?targetCurrency=BRL`

Os dois microserviços devem permanecer em execução para validar a comunicação entre eles.

## 6. Testes e validação

Foram considerados os cenários solicitados na atividade, envolvendo consultas externas, falhas de comunicação e utilização dos mecanismos de resiliência.

### 6.1. Cenários de teste

| Cenário                             | Procedimento                                              | Resultado esperado                                   |
| ----------------------------------- | --------------------------------------------------------- | ---------------------------------------------------- |
| Cotação disponível no BCB           | Consultar USD para BRL com a API disponível               | Retornar a cotação histórica da PTAX                 |
| Cotação indisponível                | Consultar uma data sem cotação no BCB                     | Utilizar o banco local como fallback                 |
| Falha de comunicação                | Configurar temporariamente um endereço externo inválido   | Realizar até 3 tentativas e consultar o banco local  |
| Timeout                             | Simular demora superior ao limite configurado             | Interromper a espera e utilizar o fallback           |
| Circuit Breaker                     | Simular falhas consecutivas na API externa                | Abrir o circuito ao atingir o limite configurado     |
| Fallback com circuito aberto        | Consultar enquanto o circuito estiver aberto              | Utilizar a cotação local sem nova requisição externa |
| Cache                               | Consultar repetidamente a mesma moeda e data              | Reutilizar o resultado armazenado em memória         |
| Ausência de cotação nas duas fontes | Consultar uma cotação inexistente no BCB e no banco local | Retornar HTTP 404                                    |

Os cenários acima descrevem os comportamentos que devem ser verificados. Os testes automatizados utilizam simulações para validar diferentes situações de funcionamento.

### 6.2. Testes automatizados

Foram implementados testes utilizando JUnit e mocks para verificar o comportamento das classes responsáveis pela integração.

Os testes abrangem:

* Consulta de cotações.
* Cálculo das taxas de conversão.
* Conversão inversa e entre moedas estrangeiras.
* Utilização do fallback local.
* Tratamento de cotação não encontrada.
* Resposta HTTP 404.
* Conversão entre moedas iguais.
* Seleção dos dados de cotação.
* Funcionamento do Retry.
* Abertura do Circuit Breaker.
* Funcionamento do cache.
* Tratamento de falhas externas.

Para executar os testes:

```cmd
mvnw.cmd clean test
```

**Resultado obtido na execução local:**

* Testes executados: 14
* Falhas: 0
* Erros: 0
* Testes ignorados: 0
* Resultado: BUILD SUCCESS

### 6.3. Testes de integração realizados

Além dos testes automatizados, executei os microserviços localmente utilizando PostgreSQL e Consul.

O `currency-api` iniciou corretamente e realizou uma consulta à API PTAX do Banco Central para a data de 27/10/2026.

Na consulta de USD para BRL, a aplicação retornou:

```json
{
  "sourceCurrency": "USD",
  "targetCurrency": "BRL",
  "conversionRate": 5.3346,
  "environment": "Currency API running in Port: 8100"
}
```

Também executei o `product-api` e realizei uma consulta ao produto de ID 1, solicitando a conversão do preço para reais.

O produto utilizado no teste foi um iPhone 15, com preço original de USD 699,00.

A aplicação retornou:

* Preço original: USD 699,00
* Taxa de conversão: 5,3346
* Preço convertido: BRL 3.728,8854

O cálculo foi realizado da seguinte forma:

`699 × 5,3346 = 3728,8854`

Esse teste confirmou a comunicação entre o `product-api` e o `currency-api`, além da utilização da taxa de conversão na resposta do produto.

Os dois serviços também foram registrados no Consul e apresentaram status saudável.

## 7. Considerações finais

Com esta atividade, consegui aplicar os padrões de resiliência estudados em aula em uma integração com uma API externa.

A principal mudança foi permitir que o `currency-api` consultasse as cotações do Banco Central, mantendo o PostgreSQL como alternativa quando a fonte externa não disponibiliza os dados necessários.

Os padrões Retry, Circuit Breaker e Timeout ajudam a controlar falhas na comunicação, enquanto o cache reduz a necessidade de realizar consultas repetidas.

Os testes automatizados foram concluídos com sucesso, e a comunicação entre os microserviços foi validada localmente.

A atividade também permitiu praticar conceitos de integração HTTP, tratamento de exceções, persistência de dados e arquitetura de microserviços.

## 8. Entrega

O código-fonte da atividade está organizado no mesmo projeto utilizado durante a disciplina, mantendo os microserviços existentes.

A entrega deverá ser realizada pelo repositório GitHub, contendo os arquivos Java, as configurações, os testes automatizados e esta documentação.

**Repositório:**

https://github.com/Mg050920/paradigma-linguagens-programacao

Não é necessário enviar arquivos compactados, pois a correção será realizada diretamente pelo código-fonte disponível no GitHub.
