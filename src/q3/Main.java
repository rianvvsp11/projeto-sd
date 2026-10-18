package q3;

import java.io.*;

public class Main {

    private static No[] nos;

    public static void main(String[] args) throws Exception {
        nos = new No[Config.NUM_NOS];
        for (int i = 0; i < Config.NUM_NOS; i++) {
            nos[i] = new No(i);
            nos[i].iniciar();
        }
        pausa(300);

        if (args.length > 0 && args[0].equalsIgnoreCase("teste")) {
            executarTestes();
        } else {
            modoInterativo();
        }
        for (No n : nos) n.parar();
        System.exit(0);
    }

    /** Valida e executa "Px arquivoN.ext". Entrada inválida não gera mensagens no anel. */
    static boolean executarComando(String linha) {
        String[] partes = linha.trim().split("\\s+");
        if (partes.length != 2) return erro();
        int origem = Validador.parseNo(partes[0]);
        if (origem < 0) return erro();
        if (!Validador.arquivoValido(partes[1])) return erro();
        nos[origem].iniciarBusca(partes[1]);
        return true;
    }

    private static boolean erro() {
        Config.imprimir(Config.MSG_ERRO);
        return false;
    }

    private static void executarTestes() {
        Config.imprimir("\n===== CASO 1: busca válida (P0, arquivo35.ext) =====");
        executarComando("P0 arquivo35.ext");
        pausa(800);
        Config.imprimir("\n===== CASO 2a: busca inválida (P1, arquivo99.ext) =====");
        executarComando("P1 arquivo99.ext");
        pausa(300);
        Config.imprimir("\n===== CASO 2b: entrada inválida (P4, arquivoABC.ext) =====");
        executarComando("P4 arquivoABC.ext");
        pausa(300);
        Config.imprimir("\nTestes concluídos.");
    }

    private static void modoInterativo() throws IOException {
        Config.imprimir("\nDigite: <Px> <arquivoN.ext>  (ex.: P0 arquivo35.ext) | 'sair' para encerrar");
        BufferedReader teclado = new BufferedReader(new InputStreamReader(System.in));
        String linha;
        while ((linha = teclado.readLine()) != null) {
            if (linha.trim().equalsIgnoreCase("sair")) break;
            if (linha.trim().isEmpty()) continue;
            executarComando(linha);
            pausa(300);
        }
    }

    private static void pausa(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException ignored) { }
    }
}