package q4;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.util.HashMap;
import java.util.Map;

/**
 * Router / GeoDNS da CDN (UDP).
 *
 * Recebe do cliente a pergunta "onde baixo o arquivo X?", junto com a região
 * fictícia em que ele se encontra, e responde com o endereço do Edge Server
 * associado a essa região.
 *
 * Protocolo:
 *  - Requisição: WHERE arquivoN.ext REGIAO
 *  - Resposta:   OK host:porta | ERRO motivo
 */
public class RouterGeoDNS {

    /** Região fictícia -> porta do Edge responsável. */
    private static final Map<String, Integer> edgePorRegiao = new HashMap<>();

    static {
        edgePorRegiao.put("NORDESTE", 8001);
        edgePorRegiao.put("SUDESTE", 8002);
        edgePorRegiao.put("SUL", 8003);
    }

    public static void main(String[] args) throws Exception {
        DatagramSocket socket = new DatagramSocket(Config.PORTA_ROUTER);
        log("Escutando na porta UDP " + Config.PORTA_ROUTER + " | regiões: " + edgePorRegiao.keySet());

        byte[] buf = new byte[512];
        while (true) {
            DatagramPacket pacote = new DatagramPacket(buf, buf.length);
            socket.receive(pacote); // Chamada bloqueante.

            String msg = new String(pacote.getData(), 0, pacote.getLength()).trim();
            String resposta = processar(msg);

            // A resposta é enviada ao IP e porta de origem do pacote recebido.
            byte[] dados = resposta.getBytes();
            socket.send(new DatagramPacket(dados, dados.length, pacote.getAddress(), pacote.getPort()));
            log("'" + msg + "' -> '" + resposta + "'");
        }
    }

    /** Interpreta a requisição e determina o Edge adequado. */
    static String processar(String msg) {
        String[] p = msg.split("\\s+");
        if (!p[0].equals("WHERE") || p.length != 3) {
            return "ERRO formato: WHERE arquivo regiao";
        }
        if (Config.numeroDoArquivo(p[1]) < 0) {
            return "ERRO arquivo invalido";
        }
        Integer porta = edgePorRegiao.get(p[2].toUpperCase());
        if (porta == null) {
            return "ERRO regiao desconhecida (use NORDESTE, SUDESTE ou SUL)";
        }
        return "OK " + Config.HOST + ":" + porta;
    }

    private static void log(String texto) {
        System.out.println("[Router] " + texto);
    }
}