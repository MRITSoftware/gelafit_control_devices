# GelaFit Control

App Android controlador para manter apps selecionados abertos e receber comandos remotos pelo Supabase.

## Como usar

1. Crie a tabela no Supabase executando `supabase-control.sql`.
2. Compile e instale o APK no tablet.
3. Abra o app, informe o e-mail da unidade, a `Supabase URL` e a `anon key`.
4. Selecione os apps instalados que devem ser monitorados.
5. Marque qual app e o app principal do kiosk.
6. Toque em `Salvar e iniciar controle`.
7. Toque em `Liberar bateria 24/7` e permita ignorar otimizacoes de bateria.

O tablet cria ou atualiza uma linha em `public.gelafit_control_devices` usando o `device_id` exibido na tela. O campo `unit_email` serve para identificar a unidade no painel/Supabase.

## App principal e app de suporte

Use `selected_apps` para listar os apps monitorados, por exemplo o servidor local e o app principal.
Use `active_package` para definir qual app fica aberto na tela para o cliente mexer no kiosk.

O app de suporte e aberto periodicamente para ajudar a manter o backend local vivo. Depois disso, o app principal e trazido para frente.

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
