# \# Atividade prática — Resilience Patterns

# 

# \- \*\*Aluno:\*\* Maurício Gabriel de Oliveira Becker

# \- \*\*Disciplina:\*\* Paradigma e Linguagens da Programação

# \- \*\*Professor:\*\* Luciano Rodrigo Ferretto

# 

# \## Objetivo

# 

# Aplicar os padrões de resiliência na comunicação entre o `product-api` e o `currency-api`, usando como base o projeto desenvolvido em aula. A ideia é permitir que os dados do produto continuem disponíveis quando houver algum problema na consulta da taxa de conversão.

# 

# \## Alterações realizadas

# 

# Criei a classe `CurrencyClientService` para concentrar a chamada ao `currency-api`. O `ProductServiceJpa` passou a usar essa classe, em vez de chamar o cliente diretamente.

# 

# Foram implementados os cinco padrões pedidos na atividade:

# 

# \- \*\*Fallback:\*\* se a conversão falhar, o produto continua sendo retornado, mas com `convertedPrice` igual a `-1` e a indicação do erro em `environment`.

# \- \*\*Timeout:\*\* foram configurados limites de 1 segundo para conexão e 2 segundos para leitura da resposta.

# \- \*\*Retry:\*\* são feitas até 3 tentativas, contando a primeira, com intervalo de 1 segundo. Quando o circuito está aberto, a exceção `CallNotPermittedException` não provoca novas tentativas.

# \- \*\*Circuit Breaker:\*\* considera as últimas 5 tentativas e começa a avaliar depois de 5 chamadas. Se a taxa de falhas atingir ou ultrapassar 50%, o circuito abre por 100 segundos. Depois desse período, uma nova chamada permite iniciar a verificação da recuperação.

# \- \*\*Cache:\*\* o Caffeine armazena as conversões por 10 minutos após a gravação, com limite de 1.000 entradas. A chave é formada pelas moedas de origem e destino.

# 

# A ordem foi configurada como cache (99), retry (100) e circuit breaker (101). Assim, quando a taxa já está no cache, não é necessário consultar o outro serviço. O fallback fica no serviço de produtos para não salvar uma falha como taxa válida.

# 

# Também atualizei o script do Consul e adicionei as dependências necessárias. Durante a revisão, corrigi a ordem dos campos marca, modelo e descrição no `ProductResponse`, que estavam sendo preenchidos em posições diferentes das declaradas.

# 

# \## Como executar

# 

# Pré-requisitos:

# 

# \- JDK 25.

# \- PostgreSQL.

# \- Consul disponível no PATH.

# 

# As versões do Java dos módulos foram mantidas como no projeto do professor: Java 21 no `product-api` e Java 25 no `currency-api`.

# 

# 1\. Criar os bancos `db\_product` e `db\_currency` no PostgreSQL local, na porta 5432.

# 2\. Conferir o usuário e a senha no arquivo `microservices/start\_consul.bat`. O exemplo utiliza `postgres` nos dois campos.

# 3\. Executar `start\_consul.bat` e conferir o Consul em http://localhost:8500.

# 4\. Abrir um terminal na pasta `microservices/currency-api` e executar:

# 

# &#x20;  ```bat

# &#x20;  mvnw.cmd spring-boot:run

# &#x20;  ```

# 

# 5\. Em outro terminal, na pasta `microservices/product-api`, executar:

# 

# &#x20;  ```bat

# &#x20;  mvnw.cmd spring-boot:run

# &#x20;  ```

# 

# 6\. Consultar http://localhost:8000/products/1?targetCurrency=BRL.

# 

# O Flyway cria as tabelas e insere os dados iniciais. As taxas de conversão são exemplos da atividade, não valores atualizados do mercado. As configurações de monitoramento e as credenciais de exemplo são para uso local.

# 

# \## Testes automatizados

# 

# Foram incluídos testes para conversão, fallback, mesma moeda, retry, abertura do circuito e uso do cache.

# 

# Para executar, na pasta `microservices/product-api`:

# 

# ```bat

# mvnw.cmd test

# ```

# 

# Os 6 testes automatizados do `product-api` foram executados em 15/10/2026 com Java 25, sem falhas ou erros, obtendo \*\*BUILD SUCCESS\*\*.

# 

# | Resultado | Quantidade |

# | --- | --- |

# | Testes executados | 6 |

# | Falhas | 0 |

# | Erros | 0 |

# | Testes ignorados | 0 |

# 

# Esses testes utilizam simulações das dependências. Os testes manuais com os dois microserviços em execução ainda estão pendentes.

# 

# \## Roteiro para testar os serviços

# 

# Reiniciar o `product-api` antes de cada cenário limpa o cache em memória e o estado do circuito. Para facilitar a observação, utilizar uma instância de cada microserviço.

# 

# Os resultados desta tabela são esperados e ainda precisam ser confirmados na execução manual.

# 

# | Padrão | Procedimento | Resultado esperado |

# | --- | --- | --- |

# | Cache | Consultar duas vezes a conversão USD para BRL | A segunda consulta usa o cache; o log de consulta remota aparece só na primeira |

# | Fallback | Com cache vazio, desligar o currency-api e consultar o produto | Dados do produto com `convertedPrice=-1` e erro em `environment` |

# | Retry | Executar o teste automatizado de duas falhas seguidas de sucesso | O resultado é obtido na terceira tentativa |

# | Circuit Breaker | Manter o currency-api indisponível e repetir consultas sem cache | Após cinco tentativas consecutivas com falha, partindo do circuito reiniciado, o circuito abre e impede novas chamadas remotas |

# | Timeout | Durante o teste, atrasar a resposta do currency-api por mais de 2 segundos | A tentativa falha por timeout de leitura |

# 

# Para provocar o atraso, pode-se usar um breakpoint dentro de `CurrencyController.getCurrency`, suspendendo apenas a thread da requisição. Depois do teste, liberar a execução.

# 

# Com os dados iniciais, o produto 1 custa USD 699 e a conversão para BRL usa a taxa 5,15, resultando em aproximadamente 3599,85. Esse valor serve como referência para o teste.

# 

# O log `Consultando currency-api` ajuda a contar as tentativas. Os endpoints abaixo ajudam a acompanhar o comportamento do `product-api`:

# 

# \- http://localhost:8000/actuator/health

# \- http://localhost:8000/actuator/caches

# \- http://localhost:8000/actuator/metrics

# \- http://localhost:8000/actuator/circuitbreakers

# 

# Após executar os cenários, registrar as respostas e os logs observados.

# 

# Para verificar a recuperação do circuito, ligar novamente o `currency-api`, aguardar pelo menos 100 segundos e fazer consultas sem cache para completar as chamadas de teste do estado `HALF\_OPEN`.

# 

# \## Observações

# 

# O circuit breaker conta cada tentativa, incluindo as repetições feitas pelo retry.

# 

# O timeout de leitura não limita toda a requisição a 2 segundos, porque podem ocorrer novas tentativas e intervalos de espera.

# 

# O valor `-1` é apenas o marcador solicitado no exercício, não um preço real.

# 

# \## Repositório

# 

# Código-fonte da atividade:

# 

# https://github.com/Mg050920/paradigma-linguagens-programacao

# 

# Projeto do professor utilizado como base:

# 

# https://github.com/luciano-ferretto/ParadLingProg\_202602

