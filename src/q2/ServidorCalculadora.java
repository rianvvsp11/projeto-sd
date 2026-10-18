package q2;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Scanner;

/**
 * Servidor de calculadora básica baseado em TCP multithread.
 *
 * Ao iniciar, registra no diretório os quatro serviços que oferece (soma,
 * subtracao, multiplicacao e divisao). Cada conexão de cliente é atendida por
 * uma thread própria e pode enviar várias operações.
 *
 * Protocolo com o cliente:
 *  - Requisição: {@code <operacao> <a> <b>}  (ex.: soma 4 5)
 *  - Resposta:   {@code OK <resultado>} ou {@code ERRO <motivo>}
 */
public class ServidorCalculadora {

    /** Serviços oferecidos, que correspondem às operações aceitas. */
    private static final String[] SERVICOS = {"soma", "subtracao", "multiplicacao", "divisao"};

    /** Endereço simulado do servidor. */
    private static final String HOST = "localhost";

    private static String nome;

    /** Atraso artificial por operação, usado para simular processamento e observar o balanceamento por conexões. */
    private static int atrasoMs;

    public static void main(String[] args) throws IOException {
        Scanner sc = new Scanner(System.in);

        System.out.print("Nome do servidor (ex.: calc1): ");
        nome = sc.nextLine().trim();
        if (nome.isEmpty() || nome.contains(" ")) {
            System.out.println("Nome inválido: não pode ser vazio nem conter espaços.");
            return;
        }

        int porta;
        try {
            System.out.print("Porta (ex.: 9001): ");
            porta = Integer.parseInt(sc.nextLine().trim());
            System.out.print("Atraso simulado por operação, em ms (Enter = 0): ");
            String entrada = sc.nextLine().trim();
            atrasoMs = entrada.isEmpty() ? 0 : Integer.parseInt(entrada);
        } catch (NumberFormatException e) {
            System.out.println("Valor numérico inválido.");
            return;
        }

        // A porta é aberta antes do registro, para que o diretório consiga verificar o servidor.
        ServerSocket servidor;
        try {
            servidor = new ServerSocket(porta);
        } catch (IOException e) {
            System.out.println("Não foi possível abrir a porta " + porta + ": " + e.getMessage());
            return;
        }

        // Registro de cada serviço no diretório.
        for (String servico : SERVICOS) {
            try {
                String resposta = ClienteDiretorio.enviar(
                        "REGISTER " + servico + " " + nome + " " + HOST + " " + porta);
                System.out.println("[" + nome + "] Registro de '" + servico + "': " + resposta);
            } catch (IOException e) {
                System.out.println("[" + nome + "] Diretório indisponível. Inicie o Diretorio e tente novamente.");
                return;
            }
        }

        System.out.println("[" + nome + "] Pronto na porta " + porta + ". Aguardando clientes...");

        // Laço principal: uma thread por conexão de cliente.
        while (true) {
            Socket cliente = servidor.accept();
            new Thread(() -> atender(cliente)).start();
        }
    }

    /**
     * Atende uma conexão, processando requisições até o cliente encerrá-la.
     */
    private static void atender(Socket socket) {
        try (socket;
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true)) {

            String linha;
            while ((linha = in.readLine()) != null) {
                System.out.println("[" + nome + "] Recebi: " + linha);

                if (atrasoMs > 0) {
                    Thread.sleep(atrasoMs);
                }

                String resposta = calcular(linha);
                out.println(resposta);
                System.out.println("[" + nome + "] Respondi: " + resposta);
            }
        } catch (IOException e) {
            System.out.println("[" + nome + "] Conexão encerrada com erro: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Valida a requisição e executa a operação.
     *
     * @param linha requisição no formato {@code <operacao> <a> <b>}
     * @return resposta no formato do protocolo
     */
    static String calcular(String linha) {
        String[] p = linha.trim().split("\\s+");
        if (p.length != 3) {
            return "ERRO formato: <operacao> <a> <b>";
        }

        double a, b;
        try {
            a = Double.parseDouble(p[1]);
            b = Double.parseDouble(p[2]);
        } catch (NumberFormatException e) {
            return "ERRO operandos invalidos";
        }

        double resultado;
        switch (p[0].toLowerCase()) {
            case "soma":
                resultado = a + b;
                break;
            case "subtracao":
                resultado = a - b;
                break;
            case "multiplicacao":
                resultado = a * b;
                break;
            case "divisao":
                if (b == 0) {
                    return "ERRO divisao por zero";
                }
                resultado = a / b;
                break;
            default:
                return "ERRO operacao desconhecida";
        }
        return "OK " + formatar(resultado);
    }

    /** Exibe valores inteiros sem casa decimal (9 em vez de 9.0). */
    private static String formatar(double valor) {
        return valor == Math.rint(valor) ? String.valueOf((long) valor) : String.valueOf(valor);
    }
}