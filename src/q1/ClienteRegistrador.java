package q1;

import java.net.*;
import java.util.Scanner;

public class ClienteRegistrador {
    static final int PORTA_SERVIDOR = 4242;
//alteração de ip do servidor
    public static void main(String[] args) throws Exception {
        InetAddress servidor = InetAddress.getByName("localhost");
        DatagramSocket socket = new DatagramSocket();
        socket.setSoTimeout(2000); // espera no máximo 2 segundos pela resposta

        Scanner sc = new Scanner(System.in);
        System.out.println("[Registrador] Digite o nome + novoIP ou 'sair' para encerrar");

        while (true) {
            String linha = sc.nextLine().trim();
            if (linha.equalsIgnoreCase("sair")) break;

            String[] partes = linha.split("\\s+");
            if (partes.length != 2) {
                System.out.println("Erro na entrada. Deve ser digitado: <nome> + <novoIP>");
                continue; // a entrada deve conter nome + ip de update somente nessa ordem
            }

            //monta a mensagem
            String msg = "REGISTER " + partes[0].toLowerCase() + " " + partes[1];
            byte[] dados = msg.getBytes();
            socket.send(new DatagramPacket(dados, dados.length, servidor, PORTA_SERVIDOR));

            try { // aguarda a confirmação do servidor
                byte[] buf = new byte[1024];
                DatagramPacket resposta = new DatagramPacket(buf, buf.length);
                socket.receive(resposta);
                System.out.println("[Registrador] Servidor respondeu: "
                        + new String(resposta.getData(), 0, resposta.getLength()));
            } catch (SocketTimeoutException e) {
                System.out.println("[Registrador] Sem resposta do servidor"); // após o tempo estourar sem resposta
            }
        }
        socket.close();
    }
}