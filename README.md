# GelaFit Control

App Android controlador para manter apps selecionados abertos e receber comandos remotos pelo Supabase.

## Como usar

1. Crie a tabela no Supabase executando `supabase-control.sql`.
2. Compile e instale o APK no tablet.
3. Abra o app e libere as permissoes solicitadas.
4. Informe o e-mail da unidade.
5. Pesquise e selecione o MRIT Server.
6. Depois selecione o app kiosk, normalmente o GelaFit GO.
7. Toque em `Salvar e iniciar controle`.
8. O app de suporte abre primeiro; cerca de 20 segundos depois o kiosk abre por cima.

O tablet cria ou atualiza uma linha em `public.gelafit_control_devices` usando o `device_id` exibido na tela. O campo `unit_email` serve para identificar a unidade no painel/Supabase.

## App principal e app de suporte

Use `selected_apps` para listar os apps monitorados, por exemplo o servidor local e o app principal.
Use `active_package` para definir qual app fica aberto na tela para o cliente mexer no kiosk.

O app de suporte e aberto periodicamente para ajudar a manter o backend local vivo. Depois disso, o app principal e trazido para frente.

## Supabase Realtime

O app mantem o kiosk localmente sem consultar o banco a cada ciclo.
O Supabase REST e usado no cadastro inicial, para status periodico e para marcar comando como executado.
Comandos remotos chegam por WebSocket usando Supabase Realtime.
O campo `kiosk_enabled` tambem e ouvido por Realtime: quando `false`, o app para de trazer o kiosk para frente; quando `true`, volta a manter o kiosk ativo.

Para habilitar a tabela no Realtime sem apagar outras tabelas da publication:

```sql
alter publication supabase_realtime
add table public.gelafit_control_devices;
```

Desativar temporariamente o kiosk:

```sql
update public.gelafit_control_devices
set kiosk_enabled = false,
    command_nonce = command_nonce + 1
where unit_email = 'unidade@exemplo.com';
```

Reativar:

```sql
update public.gelafit_control_devices
set kiosk_enabled = true,
    command = 'open_selected',
    target_package = null,
    command_nonce = command_nonce + 1
where unit_email = 'unidade@exemplo.com';
```

## Comandos suportados

- `open`: abre o pacote em `target_package`.
- `restart`: volta para a home e abre novamente o pacote em `target_package`.
- `open_selected`: abre todos os apps selecionados.
- `restart_selected`: volta para a home e abre novamente todos os apps selecionados.

Para disparar um comando, atualize `command` e incremente `command_nonce`.

## Observacao importante

Android comum nao permite que um app mate outro app instalado usando `force-stop`.
Este MVP faz um restart suave: manda o tablet para a home e abre o app novamente.
Para controle total 24/7, o caminho profissional e configurar este app como Device Owner/Kiosk ou usar um dispositivo com root/MDM.
