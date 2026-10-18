package q2;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.Scanner;

/**
 * Cliente da calculadora distribuída.
 *
 * Para cada operação, executa três etapas:
 *  1. Descoberta: consulta o diretório (LOOKUP), informando a técnica de balanceamento.
 *  2. Uso: conecta ao servidor indicado e envia a operação.
 *  3. Liberação: informa ao diretório o término do uso (FIM), para que o
 *     contador de conexões do servidor seja atualizado.
 */
public class ClienteCalculadora {

    /** Técnica de balanceamento em uso: rr (round robin), random ou min (menos conexões). */
    private static String estrategia = "rr";

    public static void main(String[] args) {
        Scanner sc = new Scanner(System.in);

        System.out.print("Nome deste cliente (ex.: Cli1): ");
        String nome = sc.nextLine().trim();

        System.out.println("[" + nome + "] Operação: <operacao> <a> <b>  (soma, subtracao, multiplicacao, divisao)");
        System.out.println("[" + nome + "] Comandos: 'estrategia rr|random|min' | 'teste' | 'sair'");

        while (true) {
            String linha = sc.nextLine().trim();

            if (linha.equalsIgnoreCase("sair")) {
                break;
            }
            if (linha.isEmpty()) {
                System.out.println("Entrada vazia. Tente outra vez!");
                continue;
            }
            if (linha.equalsIgnoreCase("teste")) {
                executarTeste(nome);
                continue;
            }
            if (linha.toLowerCase().startsWith("estrategia")) {
                trocarEstrategia(nome, linha);
                continue;
            }
            executarOperacao(nome, linha);
        }
    }

    /** Altera a técnica de balanceamento após validar o valor informado. */
    private static void trocarEstrategia(String nome, String linha) {
        String[] p = linha.split("\\s+");
        if (p.length == 2 && (p[1].equalsIgnoreCase("rr")
                || p[1].equalsIgnoreCase("random")
                || p[1].equalsIgnoreCase("min"))) {
            estrategia = p[1].toLowerCase();
            System.out.println("[" + nome + "] Técnica de balanceamento: " + estrategia);
        } else {
            System.out.println("[" + nome + "] Use: estrategia rr | random | min");
        }
    }

    /**
     * Executa uma operação completa: descoberta, cálculo e liberação.
     *
     * @param linha operação no formato {@code <operacao> <a> <b>}
     */
    private static void executarOperacao(String nome, String linha) {
        String[] p = linha.split("\\s+");
        if (p.length != 3) {
            System.out.println("[" + nome + "] Erro na entrada. Use: <operacao> <a> <b>");
            return;
        }
        String servico = p[0].toLowerCase();

        try {
            // Etapa 1: descoberta do servidor pelo diretório.
            String resposta = ClienteDiretorio.enviar("LOOKUP " + servico + " " + estrategia);
            if (!resposta.startsWith("OK")) {
                System.out.println("[" + nome + "] Diretório: " + resposta);
                return;
            }

            // Formato da resposta: OK <nome> <host> <porta>.
            String[] d = resposta.split("\\s+");
            String nomeServidor = d[1];
            String host = d[2];
            int porta = Integer.parseInt(d[3]);
            System.out.println("[" + nome + "] Diretório (" + estrategia + ") indicou " + nomeServidor
                    + " (" + host + ":" + porta + ") para '" + servico + "'");

            // Etapa 2: conexão direta com o servidor indicado.
            try (Socket socket = new Socket(host, porta);
                 PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
                 BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {

                out.println(linha);
                String resultado = in.readLine();
                System.out.println("[" + nome + "] " + nomeServidor + " respondeu: "
                        + (resultado != null ? resultado : "(sem resposta)"));
            } finally {
                // Etapa 3: o contador de conexões é liberado mesmo se houver falha no uso.
                ClienteDiretorio.enviar("FIM " + nomeServidor);
            }
        } catch (IOException e) {
            System.out.println("[" + nome + "] Falha de comunicação: " + e.getMessage());
        }
    }

    /**
     * Sequência automática de operações para demonstrar a descoberta de dois
     * serviços (soma e divisão), um erro de cálculo e um serviço inexistente.
     */
    private static void executarTeste(String nome) {
        String[] operacoes = {
                "soma 4 5", "soma 10 20", "soma 1 1",
                "divisao 8 2", "divisao 9 3", "divisao 7 0",
                "potencia 2 3"
        };
        System.out.println("[" + nome + "] --- Teste automático (técnica: " + estrategia + ") ---");
        for (String operacao : operacoes) {
            System.out.println("[" + nome + "] >> " + operacao);
            executarOperacao(nome, operacao);
        }
        System.out.println("[" + nome + "] --- Fim do teste ---");
    }
}