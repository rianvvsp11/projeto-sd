package q3;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.*;

/**
 * Nó do anel: servidor TCP multithread (uma thread por conexão recebida) que também
 * atua como cliente ao repassar mensagens ao sucessor (sentido horário).
 */

//cada nó conhece seu sucessor e antecessor, e obviamente o seu conteúdo armazenado
    //buscas são sempre repassadas pelos nós até chegar ao responsável
public class No {
    private final int id;
    private final int porta;
    private final int idSucessor;
    private final int idAntecessor;
    private final Set<String> arquivos = new HashSet<>(); //nomes dos arquivos armazenados pelo nó

    //registro de mensagens recebidas e enviadas; sincronizada por ser acessada por várias threads
    private final List<String> log = Collections.synchronizedList(new ArrayList<>());
    private ServerSocket servidor;

    // veredita se o nó está em execução e garante visibilidade entre as threads
    private volatile boolean ativo = true;

    public No(int id) {
        this.id = id;
        this.porta = Config.PORTA_BASE + id;
        this.idSucessor = (id + 1) % Config.NUM_NOS;
        this.idAntecessor = (id + Config.NUM_NOS - 1) % Config.NUM_NOS;
        //módulo garante que os nós extremos, primeiro e último, sejam, respectivamente, sucessor e antecessor do outro

        for (int i = id * Config.ARQ_POR_NO + 1; i <= (id + 1) * Config.ARQ_POR_NO; i++) {
            arquivos.add("arquivo" + i + ".ext"); //loop para os arquivos pois são em sequencia obedecendo a faixa de 1-60
        } //criação do nó
    }

    public int getId() { return id; }

    public List<String> getLog() { return log; } //registra um evento no log do nó e mostra na tela com hora e seu id

    private void registrar(String texto) {
        String linha = "[" + Config.hora() + "] [P" + id + "] " + texto;
        log.add(linha);
        Config.imprimir(linha);
    }

    /** Inicia o servidor TCP do nó em uma thread própria. */
    public void iniciar() throws IOException {
        servidor = new ServerSocket(porta);

        //thread para aceitar conexões
        Thread t = new Thread(this::aceitarConexoes, "P" + id + "-servidor");
        t.setDaemon(true);
        t.start();
        registrar("Iniciado na porta " + porta
                + " | Faixa de arquivos de " + (id * Config.ARQ_POR_NO + 1)
                + " até " + ((id + 1) * Config.ARQ_POR_NO));
    }
//encerra o nó fechando o socket
    public void parar() {
        ativo = false;
        try { if (servidor != null) servidor.close(); } catch (IOException ignored) { }
    }

    //loop de aceitação. cada conexão recebida é tratada por uma
    // nova thread, e o nó pode receber mensagens simultaneas
    private void aceitarConexoes() {
        while (ativo) {
            try {
                Socket s = servidor.accept();
                new Thread(() -> tratarConexao(s), "P" + id + "-conn").start();
            } catch (IOException e) {
                if (ativo) registrar("Erro ao aceitar conexão: " + e.getMessage());
            }
        }
    }

    //leitura da linha enviada pelo remetente, processando ela
    // em caso de falhas o nó não é afetado
    private void tratarConexao(Socket s) {
        try (Socket socket = s;
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {
            String linha = in.readLine();
            if (linha == null) return;
            try {
                processar(Mensagem.parse(linha));
            } catch (IllegalArgumentException e) {
                registrar("q3.Mensagem ignorada: " + e.getMessage());
            }
        } catch (IOException e) {
            registrar("Erro ao tratar conexão: " + e.getMessage());
        }
    }

    // o processamento é feito a principio a depender do tipo de mensagem
    // se for busca analisa a msg, a resposta somente é registrada
    private void processar(Mensagem m) {
        if (m.tipo == Mensagem.Tipo.BUSCA) {
            registrar("RECEBIDO  BUSCA '" + m.arquivo + "' de P" + m.origem
                    + " (via P" + idAntecessor + ", saltos=" + m.saltos + ")");
            tratarBusca(m);
        } else {
            registrar("RECEBIDO  RESPOSTA: '" + m.arquivo + "' encontrado em P" + m.responsavel
                    + " (saltos=" + m.saltos + ")");
        }
    }

    // o tratamento da busca pode ter 3 resultados: 1. o devido arquivo está no nó
    // 2. nenhum nó contém o arquivo (em caso de a msg percorrer toda a faixa sem localizar
    //3. repasse da busca pro nó vizinho (x+1) quando o nó x não contém o arquivo, e incrementa o salt0
    private void tratarBusca(Mensagem m) {
        if (arquivos.contains(m.arquivo)) {
            registrar("Arquivo '" + m.arquivo + "' PERTENCE a este nó. Respondendo a P" + m.origem);
            if (m.origem == id) {
                registrar("RECEBIDO  RESPOSTA (local): '" + m.arquivo + "' encontrado em P" + id);
            } else {
                enviar(Config.PORTA_BASE + m.origem,
                        Mensagem.resposta(m.origem, m.arquivo, id, m.saltos),
                        "RESPOSTA '" + m.arquivo + "' para P" + m.origem);
            }
            return;
        }
        if (m.saltos >= Config.NUM_NOS) { // deu a volta completa sem achar
            registrar("Busca de '" + m.arquivo + "' deu a volta no anel sem resultado. Descartada.");
            return;
        }
        enviar(Config.PORTA_BASE + idSucessor, m.comSaltoExtra(),
                "BUSCA '" + m.arquivo + "' para P" + idSucessor);
    }

    /** Atua como cliente: abre conexão TCP e envia uma linha. */
    private void enviar(int portaDestino, Mensagem m, String descricao) {
        try (Socket s = new Socket(Config.HOST, portaDestino);
             PrintWriter out = new PrintWriter(s.getOutputStream(), true)) {
            out.println(m.serializar());
            registrar("ENVIADO   " + descricao);
        } catch (IOException e) {
            registrar("Falha ao enviar " + descricao + ": " + e.getMessage());
        }
    }

    /** Inicia uma busca originada por este nó. */
    public void iniciarBusca(String arquivo) {
        registrar("Iniciando busca por '" + arquivo + "'");
        tratarBusca(Mensagem.busca(id, arquivo, 0));
    }
}