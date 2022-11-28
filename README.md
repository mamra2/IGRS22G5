# IGRS22G5

## Serviço Alerta3.0
Com este trabalho pretende-se especificar, criar e implementar um serviço suplementar de telecomunicações.
- Verificação dos utilizadores e do seu domínio;
- Permitir o registo e deregisto dos utilizadores;
- Possivel alterar o estado;
- Possivel alterar a localização (From e contact);
- Utilizar o serviço através de uma chamada ou mensagem para sip:alerta@acme.pt;
- Os colaboradores só interagem através de uma conferência criada pelo gestor;
- O gestor recebe chamadas e mensagens diretas dos utilizadores comuns, com destino a sip:alerta@acme.pt e reencaminha em proxy para para o contacto do gestor;
- Enviar uma mensagem direta para todos os utilizadores do domínio `@acme.pt` que estejam registados;
- Gerir lista de colaboradores: Mensagem com ADD adiciona um à lista; Mensagem com REMOVE remove colaborador da lista;
- Criar uma conferência enviando uma mensagem escrita com o texto CONF. Esta é estabelecida entre o gestor e os colaboradores. o Gestor termina a conferencia para todos.
### É ainda feita feita a monitorização dos key performance indicators, tais como:
- Chamadas atendidas;
- Mensagens Recebidas;
- Conferências Realizadas.
