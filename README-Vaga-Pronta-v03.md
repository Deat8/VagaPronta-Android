# Vaga Pronta 0.3 — controlador de tela do Bookker

**Mudança solicitada:** intervalo de busca variável de **1 a 30 minutos**, configurável por barra e botões de ±1 minuto; contagem regressiva em painel visível e botão **PARAR**.

## Instalação / compilação

O código-fonte é um projeto Android nativo (Java) com serviço de acessibilidade. A pasta `app/`, os arquivos `build.gradle`, `settings.gradle`, `gradle.properties` e `.github/workflows/build-vagapronta.yml` precisam ser copiados para a **raiz do repositório GitHub `VagaPronta-Android`** (substituindo o workflow anterior). Faça commit + push. O GitHub Actions compilará o APK com Android Gradle Plugin 8.7.3, Gradle 8.9, JDK 17 e SDK 35.

Em GitHub: Actions > Compilar Vaga Pronta - Acessibilidade > artefato `VagaPronta-Acessibilidade-03-APK` > extrair `app-debug.apk`.

**ATENÇÃO:** versão 0.3 usa o pacote `de.deat.vagapronta` (como a antiga 0.2); ela deve ser desinstalada se assinada com outra chave. A versão de diagnóstico 1.3 usa outro pacote, portanto pode aparecer como segundo app com nome semelhante; desinstale o diagnóstico para não confundir.

## Uso

1. Faça o login **no Bookker original** normalmente; não precisa cadastrar o Vaga Pronta no Entra.
2. Abra Vaga Pronta, habilite `Vaga Pronta — Bookker` em Acessibilidade; em Android/Samsung que bloqueiam apps instalados manualmente, talvez seja necessário habilitar **Permitir configurações restritas** nos detalhes do aplicativo.
3. Escolha de **1 a 30 minutos** (padrão 15), ajustando pela barra ou pelos botões ±1.
4. No Bookker, selecione veículo, prédio, data e turno na tela de estacionamento.
5. Volte ao Vaga Pronta e toque em **Iniciar busca automática**. Ele abre o Bookker e tenta ler os controles exibidos.
6. Se o Bookker responder explicitamente **sem vagas**, o Vaga Pronta procura outras opções e agenda uma nova rodada após o intervalo. A contagem aparece na tarja superior, com **PARAR**.
7. Quando encontra uma confirmação textual de reserva, interrompe. Se a confirmação for incerta, para para impedir duplicidade.

## Limitações verificáveis

- **Não há integração com API e não há login próprio Microsoft**; opera apenas a interface existente do Bookker via `AccessibilityService`, pacote alvo `com.bookkercorporate.b2b`.
- **Precisa do Bookker visível e do celular desbloqueado**. Não garantimos operação com tela bloqueada, fora do Bookker ou se o Android suspender/matar o serviço. O contador pode continuar enquanto a interface estiver ativa, mas não há garantia de execução em background.
- A árvore real de acessibilidade e todos os textos de fluxo de reserva no Bookker **não puderam ser testados em dispositivo autenticado neste ambiente**. Por isso, os seletores são heurísticos e podem precisar de ajustes após o primeiro teste. Não afirmamos que já reservou vagas reais.
- Somente toca controles identificáveis por texto acessível; **não clica em coordenadas cegamente**, não lê nós com `isPassword()`, não pede acesso à internet e não captura sessão/tokens.
- Não persiste agendamento ativo após reinício do serviço; exige início explícito para prevenir duplicidades.
- Não testa mais de 16 ações por rodada; se a resposta for incerta, interrompe.

## Testes executáveis

```bash
javac -d build-test app/src/main/java/de/deat/vagapronta/RetryPolicy.java scripts/TestRetryPolicy.java
java -cp build-test TestRetryPolicy
```

Os testes verificam valores extremos (1, 30 e fora do intervalo), 17 minutos, cálculo dos deadlines, arredondamento e formato MM:SS. Compilação Android e instalação devem ser confirmadas no GitHub Actions e em celular real.
