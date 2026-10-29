Paradigma e Linguagens da Programação

Aluno: Maurício Gabriel de Oliveira Becker

Disciplina: Paradigma e Linguagens da Programação

Professor: Luciano Rodrigo Ferretto

Curso: Ciência da Computação — Atitus Educação

Sobre o projeto

Este repositório contém as atividades práticas realizadas na disciplina de Paradigma e Linguagens da Programação.

O projeto foi desenvolvido com base nos exemplos trabalhados em aula, utilizando Java, Spring Boot e uma arquitetura de microserviços.

A aplicação possui dois serviços principais:

product-api: responsável pelo cadastro e consulta dos produtos.
currency-api: responsável pelas taxas de conversão entre moedas.

A comunicação entre os serviços utiliza HttpExchange e RestClient, com o HashiCorp Consul para descoberta de serviços.

Durante as atividades, foram implementados padrões de resiliência para tratar possíveis falhas de comunicação e melhorar o funcionamento da aplicação.

Atividade 1 — Resilience Patterns
Objetivo

O objetivo da primeira atividade foi implementar os padrões de resiliência estudados em aula na comunicação entre o product-api e o currency-api.

A ideia era permitir que o sistema continuasse retornando os dados de um produto mesmo quando ocorresse algum problema durante a consulta da taxa de conversão.

Implementação

Criei a classe CurrencyClientService para centralizar as chamadas ao currency-api.

Também alterei o ProductServiceJpa para utilizar essa classe, mantendo a lógica de comunicação separada do serviço responsável pelos produtos.

Foram implementados os seguintes padrões:

Fallback

Quando ocorre alguma falha na consulta da cotação, o sistema continua retornando os dados do produto.

Nesse caso, o campo convertedPrice recebe o valor -1, e o campo environment apresenta a informação sobre a falha.

Timeout

Foram definidos limites para evitar que a aplicação fique aguardando uma resposta por muito tempo:

Connect timeout: 1 segundo.
Read timeout: 2 segundos.

Retry

Utilizei o Resilience4j para realizar novas tentativas quando ocorre uma falha na comunicação.

As configurações utilizadas foram:

Até 3 tentativas, incluindo a primeira.
Intervalo de 1 segundo entre as tentativas.
Não repetir chamadas rejeitadas pelo Circuit Breaker.

Circuit Breaker

O Circuit Breaker foi configurado para acompanhar as falhas e interromper temporariamente novas chamadas quando necessário.

Configuração	Valor
Tipo de janela	COUNT_BASED
Tamanho da janela	5
Mínimo de chamadas	5
Taxa de falhas	50%
Tempo aberto	100 segundos

Quando o circuito é aberto, as chamadas são bloqueadas temporariamente e o fallback pode ser utilizado.

Cache

Utilizei o Caffeine para armazenar as taxas de conversão em memória, evitando consultas repetidas ao outro serviço.

As configurações foram:

Limite de 1.000 entradas.
Expiração após 10 minutos.
Chave formada pelas moedas de origem e destino.

A ordem dos mecanismos foi configurada como cache (99), retry (100) e circuit breaker (101).

Também foram realizados ajustes nas dependências, nas configurações do Consul e na ordem dos campos do ProductResponse.

Testes da primeira atividade

Foram criados testes automatizados para verificar o comportamento dos padrões implementados.

Os testes incluem situações de conversão, fallback, retry, circuit breaker e cache.

Para executar os testes, entrar na pasta microservices/product-api e utilizar:

mvnw.cmd test

Resultado obtido em 15/10/2026:

Resultado	Quantidade
Testes executados	6
Falhas	0
Erros	0
Ignorados	0

BUILD SUCCESS

Atividade 2 — Integração com API Externa e Resilience Patterns
Objetivo

Na segunda atividade, o objetivo foi modificar o currency-api para consultar as cotações de moedas na API PTAX do Banco Central do Brasil.

Antes dessa alteração, o serviço utilizava apenas as taxas cadastradas no banco de dados local.

Com a integração, a aplicação passou a consultar o Banco Central e utilizar o PostgreSQL como alternativa quando a consulta externa não retorna uma cotação válida.

Também foram aplicados os padrões de resiliência estudados anteriormente, desta vez na comunicação com uma API externa.

Implementação
Integração com o Banco Central

Para realizar as consultas à API PTAX, utilizei HttpExchange e RestClient, seguindo o padrão de comunicação utilizado no projeto.

A consulta utiliza o endpoint CotacaoMoedaDia.

Conforme permitido na atividade, configurei uma data fixa para consultar as cotações:

bcb.ptax.data-cotacao=2026-10-27

Assim, os valores retornados correspondem a uma cotação histórica, não necessariamente à cotação atual.

Fallback

O fallback foi implementado para consultar o PostgreSQL quando não for possível obter a cotação na API do Banco Central.

Isso pode acontecer quando:

A API externa está indisponível.
Ocorre alguma falha na comunicação.
A consulta ultrapassa o tempo limite.
Não existe cotação para a moeda ou data solicitada.
O Circuit Breaker está aberto.

Se a cotação também não estiver disponível no banco local, a aplicação retorna HTTP 404.

Timeout

Foram utilizados os tempos definidos na atividade:

Connect timeout: 2 segundos.
Read timeout: 5 segundos.
Retry

O Retry foi configurado utilizando Resilience4j.

Máximo de 3 tentativas.
Intervalo de 1 segundo.
Não repetir chamadas rejeitadas pelo Circuit Breaker.

Respostas sem cotação também não são repetidas, pois novas tentativas para a mesma moeda e data não resolveriam necessariamente a ausência dos dados.

Circuit Breaker

Foram mantidos os parâmetros solicitados pelo professor:

Configuração	Valor
Tipo de janela	COUNT_BASED
Tamanho da janela	5
Mínimo de chamadas	5
Taxa de falhas	50%
Tempo aberto	100 segundos

Quando o circuito está aberto, a aplicação pode utilizar o fallback local sem continuar realizando chamadas externas.

Cache

Para o armazenamento temporário das cotações, utilizei o Caffeine com as seguintes configurações:

Máximo de 1.000 entradas.
Expiração de 10 minutos.
Chave considerando moeda e data.
Armazenamento somente de cotações válidas.

O objetivo do cache é diminuir a quantidade de consultas repetidas ao Banco Central.

Conversão de moedas

A aplicação também realiza o cálculo das conversões entre diferentes moedas.

Para converter entre duas moedas estrangeiras, são utilizadas as respectivas cotações em reais.

Na conversão de reais para outra moeda, é utilizado o inverso da cotação.

Quando as moedas são iguais, a taxa retornada é 1.

Classes utilizadas

As principais classes adicionadas foram:

Classe	Função
BcbPtaxClient	Realiza a comunicação HTTP com o Banco Central
BcbPtaxResponse	Representa os dados recebidos da API
BcbClientConfiguration	Configura o RestClient e os timeouts
BcbQuoteService	Realiza consultas com Retry e Circuit Breaker
CachedBcbQuoteService	Controla o cache das cotações
CurrencyServiceBcb	Realiza as conversões e o fallback local

A classe CurrencyServiceBcb implementa a interface CurrencyService e utiliza @Primary.

A implementação anterior com JPA foi mantida para preservar a estrutura do projeto.

Testes da segunda atividade

Foram adicionados testes automatizados para verificar as funcionalidades implementadas.

Entre os comportamentos testados estão:

Consulta e conversão de moedas.
Conversão inversa.
Conversão entre moedas estrangeiras.
Utilização do fallback.
Tratamento de cotação inexistente.
Retorno HTTP 404.
Funcionamento do Retry.
Abertura do Circuit Breaker.
Utilização do cache.

Para executar os testes, acessar a pasta microservices/currency-api e utilizar:

mvnw.cmd clean test

Resultado obtido em 29/10/2026:

Resultado	Quantidade
Testes executados	14
Falhas	0
Erros	0
Ignorados	0

BUILD SUCCESS

Os testes automatizados utilizam simulações para verificar diferentes situações de funcionamento.

Testes realizados com os microserviços

Além dos testes automatizados, executei os dois microserviços localmente para verificar se a comunicação continuava funcionando após as alterações.

Utilizei o Docker Desktop para executar o PostgreSQL e o HashiCorp Consul.

Foram utilizados dois bancos de dados:

db_currency
db_product

O currency-api foi iniciado na porta 8100 e o product-api na porta 8000.

Os serviços foram registrados no Consul e apareceram com status saudável.

Teste do currency-api

Realizei uma consulta utilizando o endpoint:

GET http://localhost:8100/currencies?source=USD&target=BRL

Resposta obtida:

{
  "sourceCurrency": "USD",
  "targetCurrency": "BRL",
  "conversionRate": 5.3346,
  "environment": "Currency API running in Port: 8100"
}

O log da aplicação registrou uma consulta à API PTAX do Banco Central utilizando a data de 27/10/2026.

O endpoint retornou a taxa de conversão corretamente. A origem final da cotação ainda pode ser conferida pelos logs para distinguir uma resposta externa do fallback local.

Teste do product-api

Também consultei o produto de ID 1, solicitando a conversão para reais.

GET http://localhost:8000/products/1?targetCurrency=BRL

O produto utilizado no teste foi um Apple iPhone 15, com preço original de USD 699,00.

A aplicação retornou a taxa de 5,3346.

Cálculo:

699 × 5,3346 = 3728,8854

O resultado corresponde ao preço convertido de R$ 3.728,8854.

Com esse teste, foi possível verificar que o product-api conseguiu consultar o serviço de moedas e utilizar a taxa retornada.

Outros cenários de validação

Além das consultas realizadas, a atividade prevê verificar outros comportamentos:

Cenário	Resultado esperado
Cotação disponível no BCB	Retornar a cotação consultada
Cotação indisponível	Utilizar o banco local
Falha de comunicação	Realizar as tentativas configuradas
Resposta demorada	Acionar o timeout
Falhas consecutivas	Abrir o Circuit Breaker
Circuito aberto	Utilizar o fallback
Consultas repetidas	Utilizar o cache
Cotação inexistente nas duas fontes	Retornar HTTP 404

Esses cenários foram considerados nos testes automatizados e no roteiro de validação. A execução manual completa de todos eles ainda está pendente.

Como executar o projeto
Tecnologias utilizadas
Java 21 e Java 25
Spring Boot
Spring Cloud
Spring Data JPA
PostgreSQL
HashiCorp Consul
Resilience4j
Caffeine
Flyway
Maven
Docker
Pré-requisitos

Para executar o projeto, é necessário ter:

JDK 25 instalado.
PostgreSQL disponível.
HashiCorp Consul configurado.
Bancos db_currency e db_product criados.
Configurações de conexão com o banco cadastradas.

Nos testes locais, utilizei o PostgreSQL 16 pelo Docker na porta 5433 e o Consul na porta 8500.

O projeto também mantém o arquivo microservices/start_consul.bat, utilizado para iniciar o Consul e configurar os serviços quando o executável está instalado localmente.

As configurações do banco devem ser ajustadas de acordo com o ambiente de execução.

Executando o currency-api

Abrir um terminal na pasta:

microservices/currency-api

Executar:

mvnw.cmd spring-boot:run

Endpoint de consulta:

http://localhost:8100/currencies?source=USD&target=BRL
Executando o product-api

Abrir outro terminal na pasta:

microservices/product-api

Executar:

mvnw.cmd spring-boot:run

Endpoint de consulta:

http://localhost:8000/products/1?targetCurrency=BRL

Para testar a comunicação entre os serviços, os dois microserviços precisam estar executando.

Organização do projeto
paradigma-linguagens-programacao/
|
|-- README.md
|
|-- microservices/
    |
    |-- product-api/
    |   |-- src/
    |   |-- pom.xml
    |
    |-- currency-api/
    |   |-- src/
    |   |-- pom.xml
    |
    |-- start_consul.bat
    |
    |-- ATIVIDADE_BCB.md

A primeira atividade foi desenvolvida principalmente no product-api, enquanto a segunda concentrou as alterações no currency-api.

A documentação específica da segunda atividade está disponível no arquivo:

ATIVIDADE_BCB.md

Considerações finais

Com essas atividades, pude praticar os padrões de resiliência apresentados durante as aulas e entender melhor como eles podem ser utilizados na comunicação entre serviços.

Na primeira atividade, trabalhei com a comunicação entre dois microserviços. Na segunda, apliquei os mesmos conceitos na integração com uma API externa.

Também consegui praticar o uso do Resilience4j, cache com Caffeine, comunicação HTTP e configuração dos serviços utilizando Consul e PostgreSQL.

Os testes automatizados foram executados com sucesso, e a comunicação entre o product-api e o currency-api foi verificada localmente.

Repositórios

Repositório da atividade:

https://github.com/Mg050920/paradigma-linguagens-programacao

Projeto utilizado como base nas aulas:

https://github.com/luciano-ferretto/ParadLingProg_202602
