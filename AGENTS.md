# 4Gym

Leia este arquivo e eventuais `AGENTS.md` mais específicos antes de trabalhar no projeto. O app e a documentação de produto usam português do Brasil. Identificadores de código usam inglês.

## Produto

- O 4Gym é um diário de treino móvel com prioridade para Android. O ciclo central é cadastrar exercícios, montar uma rotina, executar e registrar séries, consultar o histórico e reaproveitar os resultados na próxima sessão. Também é possível iniciar um treino livre.
- A primeira entrega usa Kotlin, Jetpack Compose e Room sobre SQLite em um módulo Android, com código organizado por funcionalidade. Valide primeiro no Android. A definição de iOS fica para uma etapa futura.
- O ciclo central funciona em modo avião, sem conta. A interface observa o estado do banco local. Operações de treino ficam fora dos componentes visuais para que telas, notificações e widgets possam acioná-las.
- O MVP cobre biblioteca com busca e filtros, equipamentos cadastráveis, grupos musculares hierárquicos, rotinas ordenadas, séries e descanso configuráveis, treino ativo recuperável e histórico. Use botões ou menus acessíveis para reordenar e agir sobre séries. Gestos, resumo semanal e polimento dos alertas podem evoluir depois.

## Regras de treino e dados

- Reutilize os mesmos modos de rastreamento no editor, na execução e no histórico. Guarde cargas em kg, distância em metros, duração em segundos e frequência cardíaca em BPM.
- Use UUIDs estáveis para entidades. Separe rotina planejada, sessão e resultado de série. Preserve na sessão os dados do exercício necessários ao histórico, incluindo nome, instruções, equipamento, grupo e modo. Identifique cada ocorrência do exercício e cada série planejada mesmo após reordenação. Alterações na biblioteca e na rotina devem preservar os registros de sessões anteriores.
- Mostre as instruções durante o treino. Use o resultado concluído mais recente da mesma série como referência e sugestão editável. Séries puladas e sessões descartadas ficam fora desse cálculo. Preserve a meta planejada no editor. Calcule volume somando carga × repetições das séries concluídas em que essa métrica se aplica.
- Permita uma sessão ativa por vez. Salve as alterações durante a execução e use transações para operações que mudam várias linhas, incluindo início e conclusão. Guarde horários de início e fim da sessão, momento do registro de cada série concluída e intervalos de execução realmente medidos quando disponíveis.
- O descanso usa o padrão do exercício com substituição opcional por série. O cronômetro de execução e a contagem de descanso devem poder ser reconstruídos ao reabrir o app. A entrega do aviso com tela bloqueada deve ser validada em aparelho real. A sessão continua utilizável com notificações desativadas.
- Exporte e versione os esquemas Room. Mudanças de esquema exigem migração que preserve os dados existentes.

## Evolução prevista

- **Conta e sincronização opcional.** Mantenha Room como fonte de leitura da interface. No futuro, use um serviço autenticado para sincronizar alterações nos dois sentidos, preservando o uso offline e permitindo vincular os dados locais a uma conta. Isole os dados por conta e defina o que ocorre ao entrar, sair ou trocar de conta. Use IDs estáveis, revisões do servidor, operações idempotentes, fila local durável, cursor para receber alterações e marcações de exclusão. Resolva conflitos por entidade para preservar sessões concluídas e tornar conflitos de edição de rotinas visíveis. Aplique mudanças remotas ao banco local em transação. Quando houver backend, considere WorkManager com restrição de conectividade e retentativas para esse trabalho.
- **Widgets.** Use Jetpack Glance para retomar treino ou abrir uma rotina favorita. Atualize em eventos do app e derive o conteúdo do estado persistido. Passe ações pelas mesmas operações usadas na interface.
- **Health Connect.** Comece com exportação opcional de sessões concluídas. Solicite apenas a permissão necessária quando a pessoa ativar a integração. Use o UUID da sessão para repetir exportações sem duplicar registros. Acrescente leitura de dados de saúde por finalidade específica e trate ausência do serviço, permissão negada e revogação. Vincular uma conta e conectar o Health Connect são decisões independentes.

## Validação

- Rode `gradlew.bat assembleDebug` e `gradlew.bat testDebugUnitTest` no Windows. Em outro sistema, use `./gradlew`.
- Valide o ciclo principal em modo avião e com retomada após o processo do app ser encerrado. Em Android real, teste bloquear a tela durante o descanso e executar o treino com notificações desativadas. Registre o que foi verificado no ambiente disponível.
