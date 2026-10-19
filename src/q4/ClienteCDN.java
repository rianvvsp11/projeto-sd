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
 */
public class ClienteCDN {

    public static void main(String[] args) {
        Scanner sc = new Scanner(System.in);

        System.out.print("Nome deste cliente (ex.: Cli1): ");
        String nome = sc.nextLine().trim();

        System.out.println("[" + nome + "] Uso: <REGIAO> <arquivoN.ext>   (regiões: NORDESTE, SUDESTE, SUL)");
        System.out.println("[" + nome + "] Outros comandos: 'varios <REGIAO> <de> <ate>' | 'sair'");

        while (true) {
            String linha = sc.nextLine().trim();

            if (linha.equalsIgnoreCase("sair")) {
                break;
            }
            if (linha.isEmpty()) {
                System.out.println("Entrada vazia. Tente outra vez!");
                continue;
            }
            if (linha.toLowerCase().startsWith("varios")) {
                executarVarios(nome, linha);
                continue;
            }

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
     */
    private static void requisitar(String nome, String regiao, String arquivo) {
        // Validação local: o formato é verificado antes de qualquer envio pela rede.
        if (Config.numeroDoArquivo(arquivo) < 0) {
            System.out.println("[" + nome + "] Erro na entrada de dados. Tente outra vez!");
            return;
        }

        try {
            // Etapa 1: descoberta do Edge.
            String resposta = consultarRouter(arquivo, regiao);
            if (!resposta.startsWith("OK")) {
                System.out.println("[" + nome + "] Router: " + resposta);
                return;
            }
            // Formato da resposta: OK host:porta.
            String[] destino = resposta.substring(3).trim().split(":");
            String host = destino[0];
            int porta = Integer.parseInt(destino[1]);

            // Etapa 2: download no Edge, com medição do tempo da requisição.
            long inicio = System.nanoTime();
            Protocolo.Resposta r;
            try (Socket socket = new Socket(host, porta)) {
                socket.setSoTimeout(5000);
                PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
                BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

                out.println("GET /" + arquivo);
                r = Protocolo.lerResposta(in);
            }
            double ms = (System.nanoTime() - inicio) / 1_000_000.0;

            String previa = r.conteudo.isEmpty() ? "(sem conteudo)"
                    : (r.conteudo.length() > 40 ? r.conteudo.substring(0, 40) + "..." : r.conteudo);
            System.out.printf("[%s] Edge %d | %s | Cache: %s | Length: %d | %.2f ms | \"%s\"%n",
                    nome, porta, r.status, r.cache, r.conteudo.length(), ms, previa);

        } catch (SocketTimeoutException e) {
            System.out.println("[" + nome + "] Tempo esgotado: o servidor não respondeu. Ele está ligado?");
        } catch (IOException | RuntimeException e) {
            System.out.println("[" + nome + "] Falha de comunicação: " + e.getMessage());
        }
    }

    /** Pergunta ao router (UDP) qual Edge atende o arquivo para a região informada. */
    private static String consultarRouter(String arquivo, String regiao) throws IOException {
        try (DatagramSocket socket = new DatagramSocket()) {
            socket.setSoTimeout(2000); // Evita bloqueio indefinido se o router estiver fora do ar.

            byte[] dados = ("WHERE " + arquivo + " " + regiao).getBytes();
            socket.send(new DatagramPacket(dados, dados.length,
                    InetAddress.getByName(Config.HOST), Config.PORTA_ROUTER));

            byte[] buf = new byte[256];
            DatagramPacket pacote = new DatagramPacket(buf, buf.length);
            socket.receive(pacote);
            return new String(pacote.getData(), 0, pacote.getLength()).trim();
        }
    }

    /**
     * Solicita em sequência os arquivos de 'de' a 'ate'. Útil para preencher o
     * cache e observar a política de substituição.
     */
    private static void executarVarios(String nome, String linha) {
        String[] p = linha.split("\\s+");
        try {
            if (p.length != 4) {
                throw new NumberFormatException();
            }
            int de = Integer.parseInt(p[2]);
            int ate = Integer.parseInt(p[3]);
            if (de < 1 || ate > Config.TOTAL_ARQUIVOS || de > ate) {
                throw new NumberFormatException();
            }
            for (int i = de; i <= ate; i++) {
                requisitar(nome, p[1].toUpperCase(), "arquivo" + i + ".ext");
            }
        } catch (NumberFormatException e) {
            System.out.println("[" + nome + "] Use: varios <REGIAO> <de> <ate>  (1 a " + Config.TOTAL_ARQUIVOS + ")");
        }
    }
}