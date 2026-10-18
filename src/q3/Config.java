package q3;

import java.text.SimpleDateFormat;

/** Constantes compartilhadas pela aplicação. */
public final class Config {
    private Config() { }

    public static final int NUM_NOS = 6;
    public static final int ARQ_POR_NO = 10;
    public static final int TOTAL_ARQUIVOS = NUM_NOS * ARQ_POR_NO;
    public static final int PORTA_BASE = 5000;
    public static final String HOST = "localhost";
    public static final String MSG_ERRO = "Erro na entrada de dados. Tente outra vez!";

    /** Garante que linhas de log de threads diferentes não se misturem no console. */
    public static final Object LOCK_CONSOLE = new Object();

    public static String hora() {
        // SimpleDateFormat não é thread-safe: cria uma instância por chamada.
        return new SimpleDateFormat("HH:mm:ss.SSS").format(new java.util.Date());
    }

    public static void imprimir(String texto) {
        synchronized (LOCK_CONSOLE) {
            System.out.println(texto);
        }
    }
}