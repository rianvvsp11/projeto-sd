package q2;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;

/**
 * Servidor de diretório (service discovery) baseado em TCP multithread.
 *
 * Responsabilidades:
 *  - Registrar os servidores disponíveis e os serviços que oferecem (REGISTER).
 *  - Indicar a um cliente um servidor ativo para um serviço, segundo a técnica
 *    de balanceamento de carga solicitada (LOOKUP).
 *  - Controlar o número de conexões ativas por servidor (LOOKUP incrementa, FIM decrementa).
 *
 * Cada conexão recebida é atendida por uma thread própria. O estado compartilhado
 * é protegido por métodos synchronized.
 */
public class Diretorio {

    /** Porta TCP do diretório. */
    static final int PORTA = 6000;

    /** Tempo máximo, em milissegundos, para verificar se um servidor está ativo. */
    private static final int TIMEOUT_VERIFICACAO_MS = 500;

    /** Técnicas de balanceamento de carga suportadas. */
    enum Estrategia { ROUND_ROBIN, RANDOM, MENOS_CONEXOES }

    /** Serviço -> lista de servidores que o oferecem (na ordem de registro). */
    private static final Map<String, List<InfoServidor>> servicos = new HashMap<>();

    /** Nome do servidor -> ficha do servidor (ordenado por nome, para facilitar a leitura do log). */
    private static final Map<String, InfoServidor> servidores = new TreeMap<>();

    /** Serviço -> posição do próximo servidor a ser indicado (round robin). */
    private static final Map<String, Integer> proximo = new HashMap<>();

    private static final Random random = new Random();

    public static void main(String[] args) throws IOException {
        ServerSocket servidor = new ServerSocket(PORTA);
        log("Ativo na porta " + PORTA + ". Aguardando registros e consultas...");

        // Laço principal: uma thread é criada para cada conexão recebida.
        while (true) {
            Socket cliente = servidor.accept();
            new Thread(() -> atender(cliente)).start();
        }
    }

    /**
     * Atende uma conexão: lê um comando, processa e envia a resposta.
     */
    private static void atender(Socket socket) {
        try (socket;
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true)) {

            String linha = in.readLine();
            if (linha == null) {
                return; // Conexão aberta e fechada sem dados.
            }
            log("Recebi de " + socket.getRemoteSocketAddress() + ": " + linha);

            String resposta = processar(linha);
            out.println(resposta);
            log("Respondi: " + resposta);
        } catch (IOException e) {
            log("Erro ao atender conexão: " + e.getMessage());
        }
    }

    /**
     * Interpreta o comando e retorna a resposta conforme o protocolo.
     */
    private static String processar(String linha) {
        String[] p = linha.trim().split("\\s+");

        switch (p[0].toUpperCase()) {
            case "REGISTER": {
                if (p.length != 5) {
                    return "ERRO formato: REGISTER servico nome host porta";
                }
                try {
                    registrar(p[1].toLowerCase(), p[2], p[3], Integer.parseInt(p[4]));
                    return "OK registrado";
                } catch (NumberFormatException e) {
                    return "ERRO porta invalida";
                }
            }
            case "LOOKUP": {
                if (p.length != 3) {
                    return "ERRO formato: LOOKUP servico estrategia";
                }
                Estrategia estrategia = parseEstrategia(p[2]);
                if (estrategia == null) {
                    return "ERRO estrategia invalida (use rr, random ou min)";
                }
                return buscar(p[1].toLowerCase(), estrategia);
            }
            case "FIM": {
                if (p.length != 2) {
                    return "ERRO formato: FIM servidor";
                }
                liberar(p[1]);
                return "OK liberado";
            }
            default:
                return "ERRO comando invalido";
        }
    }

    /** Converte a abreviação recebida na técnica correspondente; retorna null se inválida. */
    private static Estrategia parseEstrategia(String texto) {
        switch (texto.toLowerCase()) {
            case "rr":     return Estrategia.ROUND_ROBIN;
            case "random": return Estrategia.RANDOM;
            case "min":    return Estrategia.MENOS_CONEXOES;
            default:       return null;
        }
    }

    /**
     * Registra um serviço oferecido por um servidor. Se o servidor já existir,
     * seu endereço é atualizado; caso contrário, uma nova ficha é criada.
     */
    private static synchronized void registrar(String servico, String nome, String host, int porta) {
        InfoServidor servidor = servidores.get(nome);
        if (servidor == null) {
            servidor = new InfoServidor(nome, host, porta);
            servidores.put(nome, servidor);
        } else {
            servidor.host = host;
            servidor.porta = porta;
        }

        List<InfoServidor> lista = servicos.computeIfAbsent(servico, k -> new ArrayList<>());
        if (!lista.contains(servidor)) {
            lista.add(servidor);
        }
        log("Registrado: serviço '" + servico + "' em " + servidor);
    }

    /**
     * Seleciona um servidor ativo para o serviço. Servidores que não respondem
     * à verificação são removidos e a escolha é repetida.
     */
    private static String buscar(String servico, Estrategia estrategia) {
        while (true) {
            InfoServidor escolhido = escolher(servico, estrategia);
            if (escolhido == null) {
                log("Nenhum servidor disponível para '" + servico + "'");
                return "ERRO servico indisponivel";
            }
            if (estaAtivo(escolhido)) {
                log(estrategia + " escolheu " + escolhido.nome + " para '" + servico + "' | " + estado());
                return "OK " + escolhido.nome + " " + escolhido.host + " " + escolhido.porta;
            }
            log("Servidor " + escolhido.nome + " não respondeu e foi removido do diretório");
            remover(escolhido.nome);
        }
    }

    /**
     * Aplica a técnica de balanceamento sobre os servidores do serviço e
     * incrementa o contador de conexões do servidor escolhido.
     *
     * @return servidor escolhido, ou null se não houver servidor para o serviço
     */
    private static synchronized InfoServidor escolher(String servico, Estrategia estrategia) {
        List<InfoServidor> lista = servicos.get(servico);
        if (lista == null || lista.isEmpty()) {
            return null;
        }

        InfoServidor escolhido;
        switch (estrategia) {
            case ROUND_ROBIN: {
                // Revezamento: usa a posição guardada para o serviço e avança uma casa.
                int indice = proximo.getOrDefault(servico, 0) % lista.size();
                escolhido = lista.get(indice);
                proximo.put(servico, indice + 1);
                break;
            }
            case RANDOM:
                escolhido = lista.get(random.nextInt(lista.size()));
                break;
            default: {
                // Menor número de conexões; em caso de empate, prevalece o primeiro da lista.
                escolhido = lista.get(0);
                for (InfoServidor candidato : lista) {
                    if (candidato.conexoesAtivas < escolhido.conexoesAtivas) {
                        escolhido = candidato;
                    }
                }
            }
        }

        escolhido.conexoesAtivas++;
        return escolhido;
    }

    /**
     * Verifica se o servidor aceita conexões, abrindo e fechando um socket com tempo limite.
     */
    private static boolean estaAtivo(InfoServidor servidor) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(servidor.host, servidor.porta), TIMEOUT_VERIFICACAO_MS);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    /** Remove o servidor da tabela e de todas as listas de serviços. */
    private static synchronized void remover(String nome) {
        servidores.remove(nome);
        for (List<InfoServidor> lista : servicos.values()) {
            lista.removeIf(s -> s.nome.equals(nome));
        }
    }

    /** Decrementa o contador de conexões do servidor, sem permitir valor negativo. */
    private static synchronized void liberar(String nome) {
        InfoServidor servidor = servidores.get(nome);
        if (servidor != null && servidor.conexoesAtivas > 0) {
            servidor.conexoesAtivas--;
        }
        log("Conexão liberada em " + nome + " | " + estado());
    }

    /** Resumo textual dos servidores registrados e de suas conexões ativas. */
    private static synchronized String estado() {
        return servidores.values().toString();
    }

    private static void log(String texto) {
        System.out.println("[Diretório] " + texto);
    }
}