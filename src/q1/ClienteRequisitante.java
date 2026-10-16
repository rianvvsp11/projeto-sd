package q1;

import java.net.*;
import java.util.Scanner;

public class ClienteRequisitante {
    static final int PORTA_SERVIDOR = 4242;

    public static void main(String[] args) throws Exception {
        Scanner sc = new Scanner(System.in);
        System.out.print("Insira o nome do cliente: ");
        String nome = sc.nextLine().trim();

        InetAddress servidor = InetAddress.getByName("localhost");
        DatagramSocket socket = new DatagramSocket(); // porta

        // essa thread irá receber continuamente as mensagens do server
        Thread ouvinte = new Thread(() -> {
            byte[] buf = new byte[1024];
            while (true) {
                try {
                    DatagramPacket p = new DatagramPacket(buf, buf.length);
                    socket.receive(p);
                    String msg = new String(p.getData(), 0, p.getLength());
                    if (msg.startsWith("UPDATE")) { //apenas uma diferenciação de update da resposta a uma consulta

                        System.out.println("[" + nome + "] ATUALIZAÇÃO RECEBIDA: " + msg);
                    } else {
                        System.out.println("[" + nome + "] Resposta: " + msg);
                    }
                } catch (Exception e) {
                    break; // socket fecha, encerra a thread
                }
            }
        });

        // thread de serviço: não impede o encerramento do programa quando a thread principal termina.
        ouvinte.setDaemon(true);
        ouvinte.start();

        //loop da thread principal para leitura da mensagem e envio das consultas
        System.out.println("[" + nome + "] Digite um nome (ex.: servidorX)' ou digite 'sair' para encerrar");
        while (true) {
            String linha = sc.nextLine().trim();
            if (linha.equalsIgnoreCase("sair")) break; //para encerrar
            if (linha.isEmpty()) { //garantir que não seja vazio a entrada
                System.out.println("Nada inserido aqui. Tente outra vez!");
                continue;
            }

            String msg = "RESOLVE " + linha;
            byte[] dados = msg.getBytes();
            // envia o datagrama ao servidor, indicando seu endereço e portass
            socket.send(new DatagramPacket(dados, dados.length, servidor, PORTA_SERVIDOR));
        }
        socket.close();
    }
}