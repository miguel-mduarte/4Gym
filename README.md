# 4Gym

Diário de treino offline para Android, em Kotlin com Jetpack Compose e Room. O banco é criado no primeiro uso e a biblioteca começa com equipamentos predefinidos.

## Executar

Abra a pasta no Android Studio com o SDK Platform 37 instalado e configure o JDK do Gradle. Com `JAVA_HOME` e o SDK Android configurados, rode os comandos abaixo no PowerShell.

```powershell
.\gradlew.bat assembleDebug testDebugUnitTest lintDebug
.\gradlew.bat installDebug
```

O APK de depuração fica em `app/build/outputs/apk/debug/app-debug.apk`. Para instalar pelo Gradle, conecte um aparelho com depuração USB ou inicie um emulador. O app exige Android 8 (API 26) ou superior.

## Experimentar o fluxo local

1. Em Treinos, abra uma ficha para visualizar o treino ou inicie um treino livre.
2. Cadastre exercícios pela Biblioteca, pelo editor de treino ou durante um treino ativo. Para cardio, escolha só BPM, BPM e tempo ou BPM e distância, conforme o que deseja registrar.
3. Registre pelo menos uma série para abrir a revisão. Antes de salvar, confira as séries, corrija data, hora ou duração e, se quiser, inclua uma anotação e uma autoavaliação. Confira o resultado no Histórico.
4. Inicie novamente o mesmo treino e confira a referência **Anterior** de cada série. Repita em modo avião, saindo e reabrindo o app durante a execução.

## Planos de treino

Em **Treinos → Novo plano**, você pode salvar um plano em branco, criar treinos dentro dele ou vincular fichas de **Meus treinos**. Os treinos criados no editor entram na lista ao salvar o plano. O primeiro plano fica ativo, e você pode alternar pelo botão **Ativar plano**.

- **Dias fixos.** Escolha os dias da semana e atribua um treino a cada dia ou use a sequência dos treinos do plano.
- **Por intervalo.** Escolha a data do primeiro treino e o intervalo em dias. A sequência avança quando uma sessão iniciada pelo plano é concluída.
- **Treino de hoje.** A tela inicial mostra o treino previsto para o plano ativo, um dia de descanso ou a sessão já concluída no dia.

## Próximos passos

- **Lembretes de treino.** Permitir que a pessoa receba um aviso nos dias previstos pelo plano ativo.
- **Histórico e consistência.** Adicionar calendário de sessões, frequência por semana ou mês, treinos realizados em relação aos planejados e sequência de semanas com a meta cumprida. Na contagem de consistência, respeitar os descansos programados.
- **Progressão por exercício.** Abrir a evolução de cada exercício e alternar entre os gráficos dos parâmetros que ele registra: volume de carga, carga, repetições, duração, distância e BPM, conforme o modo de rastreamento. Mostrar os valores por sessão, a variação no período e as repetições junto da carga para facilitar a comparação. Usar as séries concluídas, incluindo correções feitas no Histórico.
- **Perfil e medidas.** Começar com um perfil local no celular, que reúna a evolução de força e resistência do Histórico e permita registrar anotações, peso, composição corporal e medidas como cintura, braço e coxa. Cada registro deve ter data, valor e unidade para permitir comparar as mudanças ao longo do tempo.
- **Redesign.** Revisar hierarquia visual, navegação e espaçamento. Usar ícones reconhecíveis onde deixarem a tela mais limpa, com rótulos acessíveis e texto nas ações que precisarem de contexto.
- **Integrações.** Permitir vincular uma conta para sincronizar treinos, planos, sessões e registros do perfil em duas direções, preservando o uso offline. Evoluir widgets, Health Connect e alertas de descanso com a tela bloqueada. O [AGENTS.md](AGENTS.md) descreve a arquitetura e os critérios de validação.
