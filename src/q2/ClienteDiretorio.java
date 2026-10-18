package q2;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

/**
 * Utilitário de comunicação com o diretório. Cada chamada abre uma conexão TCP,
 * envia uma linha de comando, lê uma linha de resposta e encerra a conexão.
 */
public class ClienteDiretorio {

    /** Endereço do diretório (todos os processos executam na mesma máquina). */
    static final String HOST = "localhost";

    /** Porta do diretório, definida em uma única constante na classe Diretorio. */
    static final int PORTA = Diretorio.PORTA;

    /**
     * Envia um comando ao diretório e retorna a resposta recebida.
     *
     * @param comando linha de texto no formato do protocolo
     * @return resposta do diretório; mensagem de erro se a conexão for encerrada sem resposta
     * @throws IOException se o diretório estiver indisponível
     */
    static String enviar(String comando) throws IOException {
        try (Socket socket = new Socket(HOST, PORTA);
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {

            out.println(comando);
            String resposta = in.readLine();
            return resposta != null ? resposta : "ERRO sem resposta do diretorio";
        }
    }
}