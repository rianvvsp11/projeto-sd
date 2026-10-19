package q4;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Scanner;

/**
 * Edge Server (nó de borda) da CDN.
 *
 * Atua como servidor TCP para os clientes e para a origem, e como cliente TCP
 * da origem em caso de cache miss.
 *
 * Cache:
 *  - Tabela em memória com capacidade de 100 arquivos e política LRU
 *    (ao exceder a capacidade, remove-se o menos recentemente utilizado).
 *  - Cada entrada possui TTL: ao expirar, é descartada e o próximo acesso é miss.
 *  - Entradas podem ser removidas por invalidação ativa (INVALIDATE da origem).
 *
 * Mensagens tratadas:
 *  - GET /arquivoN.ext        (do cliente)
 *  - INVALIDATE /arquivoN.ext (da origem)
 *
 * É o componente central da demonstração: os 3 casos de teste (HIT/MISS, TTL e
 * invalidação) acontecem aqui.
 */
public class ServidorEdge {

    /** Entrada do cache: conteúdo e instante de inserção (para o cálculo do TTL). */
    private static final class EntradaCache {
        final String conteudo;
        final long inseridoEmNs;

        EntradaCache(String conteudo, long inseridoEmNs) {
            this.conteudo = conteudo;
            this.inseridoEmNs = inseridoEmNs;
        }
    }

    /**
     * Cache LRU: o terceiro argumento (true) ordena as entradas por acesso, e
     * removeEldestEntry descarta a menos recentemente usada ao exceder a capacidade.
     * Todo acesso a esta estrutura é feito dentro de blocos synchronized.
     */
    // várias threads usam o cache ao mesmo tempo, por isso o synchronized em todo acesso
    private static final Map<String, EntradaCache> cache =
            new LinkedHashMap<String, EntradaCache>(16, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, EntradaCache> maisAntiga) {
                    boolean remover = size() > Config.TAMANHO_CACHE;
                    if (remover) {
                        log("Cache cheio: removendo '" + maisAntiga.getKey() + "' (menos usado recentemente)");
                    }
                    return remover;
                }
            };

    private static int porta;
    private static long ttlMs;

    public static void main(String[] args) throws IOException {
        Scanner sc = new Scanner(System.in);
        // a porta e o TTL são digitados, assim dá para subir 3 Edges com configurações diferentes
        try {
            System.out.print("Porta do Edge (ex.: 8001): ");
            porta = Integer.parseInt(sc.nextLine().trim());

            System.out.print("TTL do cache em segundos (Enter = " + Config.TTL_PADRAO_SEGUNDOS + "): ");
            String entrada = sc.nextLine().trim();
            int ttlSegundos = entrada.isEmpty() ? Config.TTL_PADRAO_SEGUNDOS : Integer.parseInt(entrada);
            if (ttlSegundos <= 0) {
                System.out.println("O TTL deve ser maior que zero.");
                return;
            }
            ttlMs = ttlSegundos * 1000L; // guarda em milissegundos para comparar com a idade da entrada
        } catch (NumberFormatException e) {
            // erro tratado: digitar letras em vez de número não derruba o programa com exceção
            System.out.println("Valor numérico inválido.");
            return;
        }

        // A porta é aberta antes do registro, para que a origem já possa enviar invalidações.
        ServerSocket servidor;
        try {
            servidor = new ServerSocket(porta);
        } catch (IOException e) {
            // erro tratado: porta já em uso (ex.: Edge duplicado)
            System.out.println("Não foi possível abrir a porta " + porta + ": " + e.getMessage());
            return;
        }

        if (!registrarNaOrigem()) {
            return;
        }
        log("Pronto na porta " + porta + " | TTL=" + (ttlMs / 1000) + "s | cache máximo="
                + Config.TAMANHO_CACHE + " arquivos");

        // Uma thread por conexão recebida (clientes e origem).
        // assim um cliente lento (ou um MISS de 300 ms) não bloqueia os outros
        while (true) {
            Socket conexao = servidor.accept();
            new Thread(() -> atender(conexao)).start();
        }
    }

    /** Informa à origem a porta deste Edge, para que receba as invalidações. */
    private static boolean registrarNaOrigem() {
        try (Socket s = new Socket(Config.HOST, Config.PORTA_ORIGEM)) {
            s.setSoTimeout(3000);
            PrintWriter out = new PrintWriter(s.getOutputStream(), true);
            BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream()));

            out.println("REGISTER_EDGE " + porta);
            log("Registro na origem: " + in.readLine());
            return true;
        } catch (IOException e) {
            // erro tratado: origem desligada; avisa e encerra em vez de travar
            System.out.println("[Edge " + porta + "] Origem indisponível. Inicie o ServidorOrigem e tente novamente.");
            return false;
        }
    }

    /** Atende uma conexão: lê um comando e o encaminha ao tratamento adequado. */
    private static void atender(Socket socket) {
        try (socket;
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true)) {

            socket.setSoTimeout(5000);
            String linha = in.readLine();
            if (linha == null) {
                return;
            }

            // o mesmo servidor atende o cliente (GET) e a origem (INVALIDATE)
            String[] p = linha.trim().split("\\s+");
            if (p[0].equals("GET") && p.length == 2) {
                tratarGet(p[1], out);
            } else if (p[0].equals("INVALIDATE") && p.length == 2) {
                tratarInvalidate(p[1], out);
            } else {
                Protocolo.enviarResposta(out, new Protocolo.Resposta("400 Bad Request", null, ""));
            }
        } catch (IOException e) {
            log("Erro ao atender conexão: " + e.getMessage());
        }
    }

    /**
     * Trata a requisição de um arquivo: consulta o cache (hit) ou, caso o
     * arquivo não esteja presente ou tenha expirado, busca na origem (miss).
     */
    private static void tratarGet(String caminho, PrintWriter out) {
        long inicio = System.nanoTime();
        String nome = removerBarra(caminho);

        if (Config.numeroDoArquivo(nome) < 0) {
            Protocolo.enviarResposta(out, new Protocolo.Resposta("400 Bad Request", null, ""));
            log("Requisição inválida: " + caminho);
            return;
        }

        Protocolo.Resposta resposta;
        String doCache = consultarCache(nome);

        if (doCache != null) {
            // Cache hit: resposta imediata, sem contato com a origem.
            resposta = new Protocolo.Resposta("200 OK", "HIT", doCache);
        } else {
            // Cache miss: obtém o arquivo da origem e, se existir, o armazena.
            try {
                Protocolo.Resposta daOrigem = buscarNaOrigem(nome);
                if (daOrigem.ok()) { // só guarda sucesso, nunca erro (ex.: 404)
                    guardarNoCache(nome, daOrigem.conteudo);
                }
                resposta = new Protocolo.Resposta(daOrigem.status, "MISS", daOrigem.conteudo);
            } catch (IOException e) {
                // erro tratado: origem fora do ar -> 502 Bad Gateway
                log("Falha ao contatar a origem: " + e.getMessage());
                resposta = new Protocolo.Resposta("502 Bad Gateway", "MISS", "");
            }
        }

        Protocolo.enviarResposta(out, resposta);
        // o tempo no log do Edge mostra a diferença entre HIT (rápido) e MISS (~300 ms)
        double ms = (System.nanoTime() - inicio) / 1_000_000.0;
        log(String.format("GET /%s -> %s | Cache: %s | %.2f ms", nome, resposta.status, resposta.cache, ms));
    }

    /**
     * Consulta o cache aplicando o TTL.
     *
     * @return o conteúdo, se presente e válido; null se ausente ou expirado
     */
    private static String consultarCache(String nome) {
        synchronized (cache) {
            EntradaCache entrada = cache.get(nome); // o get também marca o arquivo como "usado agora" (LRU)
            if (entrada == null) {
                return null;
            }
            // TTL verificado no acesso: compara a idade da entrada com o limite
            long idadeMs = (System.nanoTime() - entrada.inseridoEmNs) / 1_000_000;
            if (idadeMs > ttlMs) {
                cache.remove(nome);
                log("TTL expirado para '" + nome + "' (" + idadeMs + " ms): removido do cache");
                return null; // vira miss e busca a versão atualizada na origem
            }
            return entrada.conteudo;
        }
    }

    private static void guardarNoCache(String nome, String conteudo) {
        synchronized (cache) {
            cache.put(nome, new EntradaCache(conteudo, System.nanoTime()));
            log("'" + nome + "' armazenado no cache (" + cache.size() + "/" + Config.TAMANHO_CACHE + ")");
        }
    }

    /** Abre uma conexão TCP com a origem e solicita o arquivo. */
    // aqui o Edge age como cliente TCP (passos 5 e 6 da Figura 1)
    private static Protocolo.Resposta buscarNaOrigem(String nome) throws IOException {
        try (Socket s = new Socket(Config.HOST, Config.PORTA_ORIGEM)) {
            s.setSoTimeout(5000);
            PrintWriter out = new PrintWriter(s.getOutputStream(), true);
            BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream()));

            out.println("GET /" + nome);
            return Protocolo.lerResposta(in);
        }
    }

    /** Remove o arquivo do cache, atendendo à invalidação enviada pela origem. */
    // invalidação ativa (caso de teste 3): a próxima requisição vira MISS e traz a versão nova
    private static void tratarInvalidate(String caminho, PrintWriter out) {
        String nome = removerBarra(caminho);
        boolean removido;
        synchronized (cache) {
            removido = cache.remove(nome) != null;
        }
        log("INVALIDATE recebido para '" + nome + "': "
                + (removido ? "removido do cache" : "não estava em cache"));
        out.println("OK invalidado"); // confirma para a origem
    }

    private static String removerBarra(String caminho) {
        return caminho.startsWith("/") ? caminho.substring(1) : caminho;
    }

    private static void log(String texto) {
        System.out.println("[Edge " + porta + "] " + texto);
    }
}