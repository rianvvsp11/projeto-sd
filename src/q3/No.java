package q3;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.*;

/**
 * Nó do anel: servidor TCP multithread (uma thread por conexão recebida) que também
 * atua como cliente ao repassar mensagens ao sucessor (sentido horário).
 */
public class No {
    private final int id;
    private final int porta;
    private final int idSucessor;
    private final int idAntecessor;
    private final Set<String> arquivos = new HashSet<>();
    private final List<String> log = Collections.synchronizedList(new ArrayList<>());
    private ServerSocket servidor;
    private volatile boolean ativo = true;

    public No(int id) {
        this.id = id;
        this.porta = Config.PORTA_BASE + id;
        this.idSucessor = (id + 1) % Config.NUM_NOS;
        this.idAntecessor = (id + Config.NUM_NOS - 1) % Config.NUM_NOS;
        for (int i = id * Config.ARQ_POR_NO + 1; i <= (id + 1) * Config.ARQ_POR_NO; i++) {
            arquivos.add("arquivo" + i + ".ext");
        }
    }

    public int getId() { return id; }

    public List<String> getLog() { return log; }

    private void registrar(String texto) {
        String linha = "[" + Config.hora() + "] [P" + id + "] " + texto;
        log.add(linha);
        Config.imprimir(linha);
    }

    /** Inicia o servidor TCP do nó em uma thread própria. */
    public void iniciar() throws IOException {
        servidor = new ServerSocket(porta);
        Thread t = new Thread(this::aceitarConexoes, "P" + id + "-servidor");
        t.setDaemon(true);
        t.start();
        registrar("Iniciado na porta " + porta + " | antecessor=P" + idAntecessor
                + " sucessor=P" + idSucessor + " | arquivos "
                + (id * Config.ARQ_POR_NO + 1) + " a " + ((id + 1) * Config.ARQ_POR_NO));
    }

    public void parar() {
        ativo = false;
        try { if (servidor != null) servidor.close(); } catch (IOException ignored) { }
    }

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