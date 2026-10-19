package q4;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Origin Server (servidor de origem) da CDN.
 *
 * Armazena os arquivos mestres em disco e os fornece aos Edges em caso de
 * cache miss. Mantém o registro dos Edges ativos para, quando um arquivo é
 * alterado, enviar a mensagem INVALIDATE a todos eles.
 *
 * É a "fonte da verdade": os Edges só guardam cópias.
 */
public class ServidorOrigem {

    private static final Path PASTA = Paths.get(Config.PASTA_ORIGEM);

    // portas dos Edges registrados; conjunto thread-safe, pois várias threads mexem nele
    private static final Set<Integer> edges = ConcurrentHashMap.newKeySet();

    // versão atual de cada arquivo (permite ver "versao 2" depois de uma alteração)
    private static final Map<Integer, Integer> versoes = new ConcurrentHashMap<>();

    // impede que uma leitura de arquivo ocorra durante a sua gravação
    private static final Object LOCK_ARQUIVOS = new Object();

    public static void main(String[] args) throws IOException {
        Files.createDirectories(PASTA); // prepara o armazenamento: cria a pasta e gera os 1000 arquivos.

        gerarArquivos();

        // abre a porta TCP fixa da origem (8000)
        ServerSocket servidor = new ServerSocket(Config.PORTA_ORIGEM);
        log("Ativo na porta " + Config.PORTA_ORIGEM + " com " + Config.TOTAL_ARQUIVOS
                + " arquivos em '" + PASTA + "'.");
        log("Comandos: alterar arquivoN.ext | invalidar arquivoN.ext | edges");

        // console roda em thread própria, pois a thread principal fica bloqueada em accept().
        Thread console = new Thread(ServidorOrigem::lerComandos);
        console.setDaemon(true);
        console.start();

        // thread por conexão recebida (Edges buscando arquivos ou se registrando).
        while (true) {
            Socket cliente = servidor.accept();
            new Thread(() -> atender(cliente)).start();
        }
    }

    // cria (ou recria) os 1000 arquivos mestres, todos na versão 1
    private static void gerarArquivos() throws IOException {
        for (int i = 1; i <= Config.TOTAL_ARQUIVOS; i++) {
            versoes.put(i, 1);
            gravar(i, 1);
        }
    }

    private static void gravar(int numero, int versao) throws IOException {
        String nome = "arquivo" + numero + ".ext";
        byte[] bytes = gerarConteudo(nome, versao).getBytes(StandardCharsets.US_ASCII);
        Files.write(PASTA.resolve(nome), bytes);
    }

    // gera o conteúdo de um arquivo, preenchido até o tamanho padrão (1024)
    // o texto inclui nome e versão, assim dá para ver no cliente que o dado mudou
    private static String gerarConteudo(String nome, int versao) {
        StringBuilder sb = new StringBuilder(nome + " | versao " + versao + " | ");
        while (sb.length() < Config.TAMANHO_CONTEUDO) {
            sb.append('x');
        }
        sb.setLength(Config.TAMANHO_CONTEUDO);
        return sb.toString();
    }

    // atende uma conexão: lê uma linha de comando e responde (GET ou REGISTER_EDGE)
    private static void atender(Socket socket) {
        try (socket;
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true)) {

            String linha = in.readLine();
            if (linha == null) {
                return;
            }
            log("Recebi de " + socket.getRemoteSocketAddress() + ": " + linha);

            String[] p = linha.trim().split("\\s+");
            if (p[0].equals("GET") && p.length == 2) {
                servirArquivo(p[1], out);
            } else if (p[0].equals("REGISTER_EDGE") && p.length == 2) {
                registrarEdge(p[1], out);
            } else {
                Protocolo.enviarResposta(out, new Protocolo.Resposta("400 Bad Request", null, ""));
            }
        } catch (IOException e) {
            log("Erro ao atender conexão: " + e.getMessage());
        }
    }

    // lê o arquivo do disco e o envia ao solicitante, ou responde 404 se não existir
    private static void servirArquivo(String caminho, PrintWriter out) {
        String nome = caminho.startsWith("/") ? caminho.substring(1) : caminho;

        // A validação pelo padrão arquivoN.ext também impede acesso a caminhos fora da pasta.
        int numero = Config.numeroDoArquivo(nome);
        if (numero < 1 || numero > Config.TOTAL_ARQUIVOS) {
            // erro tratado: formato certo mas fora de 1 a 1000 (ex.: arquivo1001.ext) -> 404
            Protocolo.enviarResposta(out, new Protocolo.Resposta("404 Not Found", null, ""));
            log("Arquivo inexistente: " + nome);
            return;
        }

        try {
            Thread.sleep(Config.LATENCIA_ORIGEM_MS); // Simula a distância até a origem (por isso o MISS é lento).

            String conteudo;
            synchronized (LOCK_ARQUIVOS) {
                conteudo = new String(Files.readAllBytes(PASTA.resolve(nome)), StandardCharsets.US_ASCII);
            }
            Protocolo.enviarResposta(out, new Protocolo.Resposta("200 OK", null, conteudo));
            log("Enviei " + nome + " (" + conteudo.length() + " caracteres)");
        } catch (IOException e) {
            // erro tratado: falha de leitura em disco -> 500
            Protocolo.enviarResposta(out, new Protocolo.Resposta("500 Internal Server Error", null, ""));
            log("Erro ao ler " + nome + ": " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** Registra a porta de um Edge para o envio de invalidações. */
    // é assim que a origem "descobre" os Edges, sem precisar de uma lista fixa no código
    private static void registrarEdge(String textoPorta, PrintWriter out) {
        try {
            int porta = Integer.parseInt(textoPorta);
            edges.add(porta);
            log("Edge registrado na porta " + porta + " | edges=" + edges);
            out.println("OK registrado");
        } catch (NumberFormatException e) {
            out.println("ERRO porta invalida");
        }
    }

    /** Lê e executa os comandos digitados pelo operador. */
    // "alterar" muda o arquivo e invalida; "invalidar" só invalida; "edges" lista os registrados
    private static void lerComandos() {
        Scanner sc = new Scanner(System.in);
        while (sc.hasNextLine()) {
            String linha = sc.nextLine().trim();
            if (linha.isEmpty()) {
                continue;
            }
            String[] p = linha.split("\\s+");
            String comando = p[0].toLowerCase();

            if (comando.equals("edges")) {
                log("Edges registrados: " + edges);
            } else if ((comando.equals("alterar") || comando.equals("invalidar")) && p.length == 2) {
                int numero = Config.numeroDoArquivo(p[1]);
                if (numero < 1 || numero > Config.TOTAL_ARQUIVOS) {
                    // erro tratado: nome inválido ou fora do intervalo
                    System.out.println("Erro na entrada de dados. Tente outra vez!");
                    continue;
                }
                if (comando.equals("alterar")) {
                    alterar(numero);
                }
                notificarEdges(p[1]);
            } else {
                System.out.println("Comandos: alterar arquivoN.ext | invalidar arquivoN.ext | edges");
            }
        }
    }

    /** Grava uma nova versão do arquivo. */
    private static void alterar(int numero) {
        try {
            synchronized (LOCK_ARQUIVOS) {
                int novaVersao = versoes.merge(numero, 1, Integer::sum); // soma 1 à versão atual
                gravar(numero, novaVersao);
                log("arquivo" + numero + ".ext alterado: agora na versão " + novaVersao);
            }
        } catch (IOException e) {
            log("Erro ao gravar o arquivo: " + e.getMessage());
        }
    }

    /**
     * Envia INVALIDATE a todos os Edges registrados. Um Edge inacessível é
     * removido da lista.
     */
    // invalidação ativa (caso de teste 3): a origem avisa todos os Edges, que removem a cópia antiga
    private static void notificarEdges(String nome) {
        if (edges.isEmpty()) {
            log("Nenhum Edge registrado.");
            return;
        }
        for (int porta : edges) {
            try (Socket s = new Socket()) {
                s.connect(new InetSocketAddress(Config.HOST, porta), 1000); // timeout de conexão: 1 s
                s.setSoTimeout(2000);
                PrintWriter out = new PrintWriter(s.getOutputStream(), true);
                BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream()));

                out.println("INVALIDATE /" + nome);
                log("INVALIDATE /" + nome + " enviado ao Edge " + porta + " -> " + in.readLine());
            } catch (IOException e) {
                // erro tratado: Edge desligado; remove da lista para não tentar de novo
                log("Edge " + porta + " inacessível; removido da lista (" + e.getMessage() + ")");
                edges.remove(porta);
            }
        }
    }

    private static void log(String texto) {
        System.out.println("[Origem] " + texto);
    }
}