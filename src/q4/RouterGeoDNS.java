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
 *
 * Na Figura 1 do enunciado, ele faz os passos 1 e 2 (buscar IP e retornar o IP
 * do Edge). Funciona como um DNS: o cliente não precisa saber onde estão os
 * Edges, só pergunta ao router, e isso é nomeação (tema da prática) na prática.
 * Usei UDP porque é uma consulta curta, de uma pergunta e uma resposta, sem
 * necessidade de conexão.
 */
public class RouterGeoDNS {

    /** Região fictícia -> porta do Edge responsável. */
    // é aqui que fica a "inteligência" geográfica: cada região é atendida por um Edge fixo
    // (NORDESTE -> 8001, SUDESTE -> 8002, SUL -> 8003). Para adicionar uma região, é só
    // colocar mais uma linha no bloco static abaixo.
    private static final Map<String, Integer> edgePorRegiao = new HashMap<>();

    // bloco executado uma vez, quando a classe é carregada: preenche o mapa
    static {
        edgePorRegiao.put("NORDESTE", 8001);
        edgePorRegiao.put("SUDESTE", 8002);
        edgePorRegiao.put("SUL", 8003);
    }

    public static void main(String[] args) throws Exception {
        // abre o socket UDP na porta fixa do router (a mesma que o cliente usa, vinda do Config)
        DatagramSocket socket = new DatagramSocket(Config.PORTA_ROUTER);
        log("Escutando na porta UDP " + Config.PORTA_ROUTER + " | regiões: " + edgePorRegiao.keySet());

        byte[] buf = new byte[512];
        // laço infinito: o router fica sempre no ar atendendo consultas, uma após a outra
        // (como cada consulta é muito rápida, não precisei de thread aqui, diferente dos servidores TCP)
        while (true) {
            DatagramPacket pacote = new DatagramPacket(buf, buf.length);
            socket.receive(pacote); // Chamada bloqueante: espera até chegar um pacote.

            // usa só os bytes realmente recebidos (getLength), não o buffer inteiro
            String msg = new String(pacote.getData(), 0, pacote.getLength()).trim();
            String resposta = processar(msg);

            // A resposta é enviada ao IP e porta de origem do pacote recebido.
            // como o UDP não tem conexão, é o próprio pacote que diz para onde devolver
            byte[] dados = resposta.getBytes();
            socket.send(new DatagramPacket(dados, dados.length, pacote.getAddress(), pacote.getPort()));
            // o log mostra pergunta e resposta, bom para apontar na gravação
            log("'" + msg + "' -> '" + resposta + "'");
        }
    }

    /** Interpreta a requisição e determina o Edge adequado. */
    // método separado do main para deixar a lógica clara e fácil de testar;
    // ele nunca lança exceção: qualquer problema vira uma resposta "ERRO ...",
    // então uma mensagem estranha nunca derruba o router
    static String processar(String msg) {
        String[] p = msg.split("\\s+");
        // formato: exatamente 3 partes (WHERE, arquivo, região)
        if (!p[0].equals("WHERE") || p.length != 3) {
            return "ERRO formato: WHERE arquivo regiao";
        }
        // valida o nome do arquivo com o mesmo regex do resto do sistema (Config)
        if (Config.numeroDoArquivo(p[1]) < 0) {
            return "ERRO arquivo invalido";
        }
        // toUpperCase deixa a região insensível a maiúsculas/minúsculas ("sul" = "SUL")
        Integer porta = edgePorRegiao.get(p[2].toUpperCase());
        // região fora do mapa (ex.: "NORTE"): informa as regiões válidas
        if (porta == null) {
            return "ERRO regiao desconhecida (use NORDESTE, SUDESTE ou SUL)";
        }
        // sucesso: devolve host:porta, que o cliente usa para abrir a conexão TCP com o Edge
        return "OK " + Config.HOST + ":" + porta;
    }

    private static void log(String texto) {
        System.out.println("[Router] " + texto);
    }
}