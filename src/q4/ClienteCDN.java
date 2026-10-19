package q4;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.Scanner;

/**
 * Cliente (gerador de tráfego) da CDN.
 *
 * Para cada arquivo solicitado, executa duas etapas:
 *  1. Consulta o router (UDP), informando o arquivo e a região, para descobrir o Edge.
 *  2. Conecta ao Edge indicado (TCP), envia GET e mede o tempo da resposta.
 *
 * Esse é o componente que simula o "usuário final" da Figura 1 do enunciado:
 * ele não sabe onde o arquivo está, só informa o que quer e a região em que se
 * encontra (localização fictícia). Quem decide o Edge é o router.
 *
 * Tecnologias usadas aqui: socket UDP (consulta ao router) e socket TCP
 * (download no Edge). O tempo medido é o que permite comparar HIT e MISS.
 */
public class ClienteCDN {

    public static void main(String[] args) {
        Scanner sc = new Scanner(System.in);

        // o nome só serve para identificar o cliente nos logs, já que o enunciado
        // permite vários clientes gerando tráfego ao mesmo tempo
        System.out.print("Nome deste cliente (ex.: Cli1): ");
        String nome = sc.nextLine().trim();

        System.out.println("[" + nome + "] Uso: <REGIAO> <arquivoN.ext>   (regiões: NORDESTE, SUDESTE, SUL)");
        System.out.println("[" + nome + "] Outros comandos: 'varios <REGIAO> <de> <ate>' | 'sair'");

        // laço de comandos: uma operação por vez, como pede o roteiro de gravação
        while (true) {
            String linha = sc.nextLine().trim();

            if (linha.equalsIgnoreCase("sair")) {
                break;
            }
            // entrada vazia não derruba o programa, só pede de novo
            if (linha.isEmpty()) {
                System.out.println("Entrada vazia. Tente outra vez!");
                continue;
            }
            // comando extra para encher o cache de uma vez (usado para mostrar o LRU)
            if (linha.toLowerCase().startsWith("varios")) {
                executarVarios(nome, linha);
                continue;
            }

            // formato esperado: exatamente duas palavras (região e arquivo);
            // qualquer outra quantidade é rejeitada sem enviar nada pela rede
            String[] p = linha.split("\\s+");
            if (p.length != 2) {
                System.out.println("[" + nome + "] Erro na entrada de dados. Tente outra vez!");
                continue;
            }
            requisitar(nome, p[0].toUpperCase(), p[1].toLowerCase());
        }
    }

    /**
     * Solicita um arquivo: descobre o Edge pelo router e baixa o arquivo dele.
     *
     * É aqui que acontecem os casos de teste do enunciado: ao pedir o mesmo
     * arquivo duas vezes, a primeira resposta vem como MISS (mais lenta, porque o
     * Edge precisa ir até a origem) e a segunda como HIT (rápida, direto da memória).
     */
    private static void requisitar(String nome, String regiao, String arquivo) {
        // Validação local: o formato é verificado antes de qualquer envio pela rede.
        // Assim, entradas como "arquivoABC.ext" nem chegam a gastar tráfego.
        if (Config.numeroDoArquivo(arquivo) < 0) {
            System.out.println("[" + nome + "] Erro na entrada de dados. Tente outra vez!");
            return;
        }

        try {
            // Etapa 1: descoberta do Edge (passos 1 e 2 da Figura 1).
            String resposta = consultarRouter(arquivo, regiao);
            // se o router respondeu ERRO (ex.: região inexistente), mostramos o motivo e paramos
            if (!resposta.startsWith("OK")) {
                System.out.println("[" + nome + "] Router: " + resposta);
                return;
            }
            // Formato da resposta: OK host:porta.
            // substring(3) descarta o "OK " e o split separa o host da porta
            String[] destino = resposta.substring(3).trim().split(":");
            String host = destino[0];
            int porta = Integer.parseInt(destino[1]);

            // Etapa 2: download no Edge (passos 3 e 4 da Figura 1), com medição do tempo.
            // o cronômetro começa antes de abrir a conexão, então o tempo inclui
            // conexão TCP + GET + resposta, ou seja, o que o usuário "sente"
            long inicio = System.nanoTime();
            Protocolo.Resposta r;
            // try-with-resources garante que o socket é fechado mesmo se der erro
            try (Socket socket = new Socket(host, porta)) {
                socket.setSoTimeout(5000); // se o Edge travar, não ficamos esperando para sempre
                PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
                BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

                // protocolo de aplicação simples baseado em texto: "GET /arquivoN.ext"
                out.println("GET /" + arquivo);
                // a leitura da resposta (status, Cache, Length e conteúdo) fica na classe Protocolo
                r = Protocolo.lerResposta(in);
            }
            // converte nanossegundos para milissegundos
            double ms = (System.nanoTime() - inicio) / 1_000_000.0;

            // mostra só os primeiros 40 caracteres do conteúdo para não poluir o console;
            // o "versao N" que aparece aí prova que o dado foi atualizado após uma invalidação
            String previa = r.conteudo.isEmpty() ? "(sem conteudo)"
                    : (r.conteudo.length() > 40 ? r.conteudo.substring(0, 40) + "..." : r.conteudo);
            // a linha final traz tudo o que precisamos observar na demonstração:
            // qual Edge atendeu, status, HIT/MISS, tamanho e tempo
            System.out.printf("[%s] Edge %d | %s | Cache: %s | Length: %d | %.2f ms | \"%s\"%n",
                    nome, porta, r.status, r.cache, r.conteudo.length(), ms, previa);

            // Tratamento de erros: o programa não encerra, só informa e volta a pedir comandos.
        } catch (SocketTimeoutException e) {
            // cai aqui se o router (2 s) ou o Edge (5 s) não responderem a tempo
            System.out.println("[" + nome + "] Tempo esgotado: o servidor não respondeu. Ele está ligado?");
        } catch (IOException | RuntimeException e) {
            // demais falhas: conexão recusada (Edge desligado), resposta malformada etc.
            System.out.println("[" + nome + "] Falha de comunicação: " + e.getMessage());
        }
    }

    /**
     * Pergunta ao router (UDP) qual Edge atende o arquivo para a região informada.
     *
     * Usei UDP aqui porque a consulta é pequena (uma pergunta e uma resposta),
     * parecida com uma consulta DNS, e não precisa de conexão.
     */
    private static String consultarRouter(String arquivo, String regiao) throws IOException {
        try (DatagramSocket socket = new DatagramSocket()) {
            socket.setSoTimeout(2000); // Evita bloqueio indefinido se o router estiver fora do ar.
            // como o UDP não garante entrega, sem esse timeout o cliente poderia travar

            // a região vai no payload, é assim que o router "sabe" onde o cliente está
            byte[] dados = ("WHERE " + arquivo + " " + regiao).getBytes();
            socket.send(new DatagramPacket(dados, dados.length,
                    InetAddress.getByName(Config.HOST), Config.PORTA_ROUTER));

            // espera a resposta (chamada bloqueante, limitada pelo timeout acima)
            byte[] buf = new byte[256];
            DatagramPacket pacote = new DatagramPacket(buf, buf.length);
            socket.receive(pacote);
            // usa só os bytes realmente recebidos, não o buffer inteiro
            return new String(pacote.getData(), 0, pacote.getLength()).trim();
        }
    }

    /**
     * Solicita em sequência os arquivos de 'de' a 'ate'. Útil para preencher o
     * cache e observar a política de substituição.
     *
     * Exemplo: "varios NORDESTE 1 101" pede 101 arquivos; como o cache comporta 100,
     * o Edge precisa remover o menos recentemente usado (LRU), e isso aparece no log dele.
     */
    private static void executarVarios(String nome, String linha) {
        String[] p = linha.split("\\s+");
        try {
            // precisa ter 4 partes: varios, região, início e fim
            if (p.length != 4) {
                throw new NumberFormatException();
            }
            int de = Integer.parseInt(p[2]);
            int ate = Integer.parseInt(p[3]);
            // valida o intervalo (1 a 1000, que é o total de arquivos da origem)
            if (de < 1 || ate > Config.TOTAL_ARQUIVOS || de > ate) {
                throw new NumberFormatException();
            }
            // reaproveita o mesmo método de uma requisição normal, uma por arquivo
            for (int i = de; i <= ate; i++) {
                requisitar(nome, p[1].toUpperCase(), "arquivo" + i + ".ext");
            }
        } catch (NumberFormatException e) {
            // números inválidos ou fora do intervalo: mostra como usar o comando
            System.out.println("[" + nome + "] Use: varios <REGIAO> <de> <ate>  (1 a " + Config.TOTAL_ARQUIVOS + ")");
        }
    }
}