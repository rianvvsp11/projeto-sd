package q3;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Validação das entradas digitadas pelo usuário. */
public final class Validador {
    private Validador() { }

    private static final Pattern ARQUIVO = Pattern.compile("^arquivo(\\d{1,9})\\.ext$");
    private static final Pattern NO = Pattern.compile("^[Pp]?(\\d{1,9})$");
//expressoes regulares para as entradas

    /** Retorna o id do nó (0..5) ou -1 se inválido. */
    public static int parseNo(String texto) {
        Matcher m = NO.matcher(texto);
        if (!m.matches()) return -1;
        int id = Integer.parseInt(m.group(1));
        return id < Config.NUM_NOS ? id : -1;
    }

    /** arquivoN.ext com N entre 1 e 60. */
    public static boolean arquivoValido(String nome) {
        Matcher m = ARQUIVO.matcher(nome); //analisando a entrada através da expressão regular definida
        if (!m.matches()) return false;      // entrada deve conter: "arquivo" + valor + ".ext"
        int n = Integer.parseInt(m.group(1));
        return n >= 1 && n <= Config.TOTAL_ARQUIVOS; // checa se o valor está na faixa
    } // para os casos de teste em situação de inválido, os dois exemplos são formulados usando um valor fora da faixa (1)
}     // e letras (2) após "arquivo", o que respectivamente torna FALSE por ser maior que 60 e por não seguir a expressão regular
        // m.group em (1) = 99. 99 > 60 entao retorna false
