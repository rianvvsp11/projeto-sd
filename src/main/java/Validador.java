import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Validação das entradas digitadas pelo usuário. */
public final class Validador {
    private Validador() { }

    private static final Pattern ARQUIVO = Pattern.compile("^arquivo(\\d{1,9})\\.ext$");
    private static final Pattern NO = Pattern.compile("^[Pp]?(\\d{1,9})$");

    /** Retorna o id do nó (0..5) ou -1 se inválido. */
    public static int parseNo(String texto) {
        Matcher m = NO.matcher(texto);
        if (!m.matches()) return -1;
        int id = Integer.parseInt(m.group(1));
        return id < Config.NUM_NOS ? id : -1;
    }

    /** arquivoN.ext com N entre 1 e 60. */
    public static boolean arquivoValido(String nome) {
        Matcher m = ARQUIVO.matcher(nome);
        if (!m.matches()) return false;
        int n = Integer.parseInt(m.group(1));
        return n >= 1 && n <= Config.TOTAL_ARQUIVOS;
    }
}