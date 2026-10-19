package q3;

import java.io.*;

public class Main {

    private static No[] nos;

    public static void main(String[] args) throws Exception {
        nos = new No[Config.NUM_NOS];
        for (int i = 0; i < Config.NUM_NOS; i++) {
            nos[i] = new No(i);
            nos[i].iniciar(); //inicialização dos nós p0-p5
        }
        pausa(300);

        modoInterativo(); // run da interação geral

        for (No n : nos) n.parar();
        System.exit(0);
    }

    /** Valida e executa "Px arquivoN.ext". Entrada inválida não gera mensagens no anel. */
    static boolean executarComando(String linha) {
        String[] partes = linha.trim().split("\\s+");
        if (partes.length != 2) return erro();
        // nó de início vai ser de 0 a 5
        int origem = Validador.parseNo(partes[0]);
        if (origem < 0) return erro();
        //valida o formato do arquivo e a faixa 1-60
        if (!Validador.arquivoValido(partes[1])) return erro();
        //se enfim chegar a essa parte tudo está válido e é iniciado a busca
        nos[origem].iniciarBusca(partes[1]);
        return true;
    }

    private static boolean erro() {
        Config.imprimir(Config.MSG_ERRO);
        return false;
    }

    private static void modoInterativo() throws IOException {
        Config.imprimir("\nDigite: <Px> <arquivoN.ext>  (ex.: P0 arquivo35.ext) | 'sair' para encerrar");
        BufferedReader teclado = new BufferedReader(new InputStreamReader(System.in));
        //leitura feita do devido comando de entrada ou o de sair para encerrar execução
        String linha;
        while ((linha = teclado.readLine()) != null) {
            if (linha.trim().equalsIgnoreCase("sair")) break;
            if (linha.trim().isEmpty()) continue;
            executarComando(linha); //aplicação do metodo com a string da entrada que inicia a busca
            pausa(300);
        }
    }

    private static void pausa(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException ignored) { }
    }
}