package q3;

/**
 * q3.Mensagem trocada entre os nós (tipo composto), serializada em uma linha de texto:
 *   BUSCA|origem|arquivo|saltos
 *   RESPOSTA|origem|arquivo|responsavel|saltos
 */
public class Mensagem {

    public enum Tipo { BUSCA, RESPOSTA }

    public final Tipo tipo;
    public final int origem;        // nó que iniciou a busca
    public final String arquivo;
    public final int responsavel;   // nó que possui o arquivo (-1 em BUSCA)
    public final int saltos;

    private Mensagem(Tipo tipo, int origem, String arquivo, int responsavel, int saltos) {
        this.tipo = tipo;
        this.origem = origem;
        this.arquivo = arquivo;
        this.responsavel = responsavel;
        this.saltos = saltos;
    }

    public static Mensagem busca(int origem, String arquivo, int saltos) {
        return new Mensagem(Tipo.BUSCA, origem, arquivo, -1, saltos);
    }

    public static Mensagem resposta(int origem, String arquivo, int responsavel, int saltos) {
        return new Mensagem(Tipo.RESPOSTA, origem, arquivo, responsavel, saltos);
    }

    /** Cópia da busca com um salto a mais (ao repassar ao sucessor). */
    public Mensagem comSaltoExtra() {
        return busca(origem, arquivo, saltos + 1);
    }

    public String serializar() {
        return tipo == Tipo.BUSCA
                ? "BUSCA|" + origem + "|" + arquivo + "|" + saltos
                : "RESPOSTA|" + origem + "|" + arquivo + "|" + responsavel + "|" + saltos;
    }

    /** Converte a linha recebida em q3.Mensagem; lança IllegalArgumentException se malformada. */
    public static Mensagem parse(String linha) {
        try {
            String[] p = linha.split("\\|");
            switch (p[0]) {
                case "BUSCA":
                    return busca(Integer.parseInt(p[1]), p[2], Integer.parseInt(p[3]));
                case "RESPOSTA":
                    return resposta(Integer.parseInt(p[1]), p[2], Integer.parseInt(p[3]), Integer.parseInt(p[4]));
                default:
                    throw new IllegalArgumentException("Tipo desconhecido: " + p[0]);
            }
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("q3.Mensagem malformada: " + linha, e);
        }
    }
}