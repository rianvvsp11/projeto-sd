package q2;

public class InfoServidor {

    final String nome;

    String host;
    int porta;
    int conexoesAtivas;

    InfoServidor(String nome, String host, int porta) {
        this.nome = nome;
        this.host = host;
        this.porta = porta;
    }

    @Override
    public String toString() {
        return nome + "(" + host + ":" + porta + ", conexoes=" + conexoesAtivas + ")";
    }
}