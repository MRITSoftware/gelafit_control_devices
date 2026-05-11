# Continuidade - GelaFit Control

## Estado atual

O app Android esta como MVP de controle remoto via Supabase.

Ele permite configurar:

- E-mail da unidade (`unit_email`) para identificar o dispositivo de forma humana.
- Supabase URL e anon key ja ficam pre-configurados no app.
- Dois apps instalados que devem ser monitorados (`selected_apps`).
- App principal do kiosk (`active_package`), obrigatoriamente um dos dois selecionados.
- Kiosk ativo/inativo via `kiosk_enabled`, recebido por Realtime.

O `device_id` continua existindo como identificador tecnico unico do tablet.

## Fluxo pensado para o uso real

O tablet tera normalmente dois apps selecionados:

- App servidor local/backend.
- App principal usado pelo cliente no kiosk.

O servidor local fica na lista de apps monitorados, mas nao deve ficar na frente da tela.
O app principal deve ser marcado como `App principal (kiosk)`.

O servico Android:

- Roda em foreground com notificacao fixa.
- Faz sincronizacao com o Supabase a cada 15 segundos.
- Atualiza `status`, `last_seen_at`, `selected_apps`, `active_package` e `last_error`.
- Mantem o kiosk somente quando `kiosk_enabled = true`.
- Abre o app de suporte primeiro para ajudar a manter o backend local vivo.
- Depois de cerca de 20 segundos, traz o app principal para frente.

## Supabase

Arquivo principal:

- `supabase-control.sql`

Campos atuais da tabela `public.gelafit_control_devices`:

- `device_id`
- `unit_email`
- `status`
- `selected_apps`
- `active_package`
- `kiosk_enabled`
- `command`
- `target_package`
- `command_nonce`
- `last_command_nonce`
- `last_seen_at`
- `last_error`
- `created_at`
- `updated_at`

O SQL tambem tem:

- `create table if not exists`
- `alter table add column if not exists` para `unit_email` e `active_package`
- RLS habilitado
- policies abertas para `anon` em select/insert/update

Nao estamos usando trigger/function por enquanto.

## Comandos remotos suportados

Para disparar comando, alterar `command` e incrementar `command_nonce`.

Comandos:

- `open`: abre o pacote em `target_package`.
- `restart`: manda para home e abre novamente o pacote em `target_package`.
- `open_selected`: abre todos os apps selecionados.
- `restart_selected`: manda para home e abre novamente todos os apps selecionados.

Observacao: Android comum nao permite `force-stop` real em outro app. O restart atual e suave.

## Arquivos importantes

- `app/src/main/java/com/gelafit/control/MainActivity.java`
  - UI de configuracao.
  - Campo de e-mail da unidade.
  - Lista de apps instalados.
  - Selecao do app principal do kiosk.

- `app/src/main/java/com/gelafit/control/ControlService.java`
  - Loop de controle.
  - Sincronizacao com Supabase.
  - Mantem app principal na frente.
  - Reabre apps de suporte periodicamente.

- `app/src/main/java/com/gelafit/control/SupabaseClient.java`
  - GET/POST/PATCH na REST API do Supabase.
  - Cria device se nao existir.
  - Atualiza status do device.

- `app/src/main/java/com/gelafit/control/AppConfig.java`
  - SharedPreferences.
  - Guarda `device_id`, Supabase config, `unit_email`, `selected_apps`, `active_package` e nonce local.

- `app/src/main/AndroidManifest.xml`
  - Permissoes.
  - Foreground service.
  - Boot receiver.

## Validacao feita

Build testado com:

```powershell
gradle assembleDebug --no-daemon
```

Resultado:

- Build passou.
- Gerou APK debug.
- Apareceram apenas warnings de compatibilidade Gradle/Java/compileSdk, sem bloquear.

## Proximos passos sugeridos

1. Instalar o APK em um tablet real.
2. Rodar `supabase-control.sql` no Supabase.
3. Configurar no app:
   - E-mail da unidade.
   - Liberar permissoes solicitadas.
   - Pesquisar e selecionar servidor local + app principal.
   - Marcar o app principal como kiosk.
4. Conferir se a linha aparece no Supabase com `unit_email`.
5. Testar se `active_package` fica sendo trazido para frente.
6. Testar comando remoto incrementando `command_nonce`.
7. Avaliar se o intervalo de 15 segundos esta bom ou se precisa ajustar.
8. Pensar no painel web/admin para controlar unidades por `unit_email`.

## Pontos de atencao

- Policies do Supabase estao abertas para `anon`; bom para MVP, mas precisa endurecer depois.
- Sem Device Owner/Kiosk real, o Android ainda pode limitar controle total 24/7.
- O app servidor local precisa ser testado no tablet para confirmar se reabrir periodicamente e suficiente para manter o backend vivo.
- Se a tabela `public.gelafit_control_devices` ja existir no Supabase antigo, rodar o SQL atual deve adicionar `unit_email` e `active_package`.
