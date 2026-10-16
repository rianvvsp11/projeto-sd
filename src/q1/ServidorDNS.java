package q1;

import java.net.*;
import java.util.*;
import java.util.concurrent.*;

public class ServidorDNS {
    static final int PORTA = 4242; //porta que o server recebe requisições
    static final Map<String, String> tabela = new ConcurrentHashMap<>(); // a tabela vai associar nome e endereço devidamente

    //associa o nome ao cliente que consultou
    static final Map<String, Set<InetSocketAddress>> interessados = new ConcurrentHashMap<>();

    // inicialização da tabela dos ip's
    public static void main(String[] args) throws Exception {
        for (int i = 1; i <= 10; i++) {
            tabela.put("servidor" + i, "192.168.0." + (i * 10));
        }

        // criação do socket UDP vinculado à porta do serviço
        DatagramSocket socket = new DatagramSocket(PORTA);
        System.out.println("[DNS] Escutando na porta " + PORTA);
        byte[] buf = new byte[1024];

        // loop que vai fazer o server atender as requisições
        while (true) {
            DatagramPacket pacote = new DatagramPacket(buf, buf.length);
            socket.receive(pacote);

            // converte apenas os bytes efetivamente recebidos em texto
            String msg = new String(pacote.getData(), 0, pacote.getLength()).trim();

            // endereçodo remetente usado para enviar a resposta
            InetSocketAddress origem = new InetSocketAddress(pacote.getAddress(), pacote.getPort());
            System.out.println("[DNS] Recebi de " + origem + ": " + msg);
            processar(socket, msg, origem);
        }
    }

    static void processar(DatagramSocket socket, String msg, InetSocketAddress origem) throws Exception {
        String[] p = msg.split("\\s+");

        // divide a mensagem em campos separados por espaço
        if (p[0].equals("RESOLVE") && p.length == 2) {
            String ip = tabela.get(p[1]); // será feita a busca do endereço associado ao nome
            if (ip != null) {
                interessados.computeIfAbsent(p[1], k -> ConcurrentHashMap.newKeySet()).add(origem);
                enviar(socket, "OK " + ip, origem); //cliente passa a ser um interessado no nome consultado
            } else { //para caso o nome nao exista ainda
                enviar(socket, "ERRO nome nao encontrado", origem);
            }

        } else if (p[0].equals("REGISTER") && p.length == 3) {
            tabela.put(p[1], p[2]); // associaçao do nome ao novo ip
            enviar(socket, "OK registrado", origem); //confirmaçao enviada ao registrador

            // binding dinâmico: notifica os clientes que consultaram o nome
            Set<InetSocketAddress> lista = interessados.get(p[1]);
            if (lista != null) { // caso em que nenhum cliente consultou o nome
                for (InetSocketAddress interessado : lista) {
                    enviar(socket, "UPDATE " + p[1] + " " + p[2], interessado);
                }
            }

        } else {
            enviar(socket, "ERRO comando invalido", origem);
        }
    }
//envia uma mensagem de texto a um destino por meio de um datagrama UDP
    // os parametros referem-se respectivamente ao socket udp usando pro envio, o devido conteudo da mensagem e o endereço ip
    static void enviar(DatagramSocket socket, String texto, InetSocketAddress destino) throws Exception {
        byte[] dados = texto.getBytes();
        socket.send(new DatagramPacket(dados, dados.length, destino.getAddress(), destino.getPort()));
        System.out.println("[DNS] Enviei para " + destino + ": " + texto);
    }
}