package q4;

import java.util.regex.Matcher;
import java.util.regex.Pattern;


 //Constantes compartilhadas pelos componentes da CDN e validação do nome de arquivo.

public final class Config {

    private Config() { }
    static final String HOST = "localhost";

    //porta TCP do servidor de origem
    static final int PORTA_ORIGEM = 8000;

    //porta UDP do router
    static final int PORTA_ROUTER = 5354;

    //arquivos armazenados na origem
    static final int TOTAL_ARQUIVOS = 1000;

    //cachê máximo de cada Edge, em número de arquivos
    static final int TAMANHO_CACHE = 100;

    //tamanho de cada arquivo
    static final int TAMANHO_CONTEUDO = 1024;

    //TTL padrão das entradas do cache
    static final int TTL_PADRAO_SEGUNDOS = 10;

    //delay artificial da origem, pra simular distancia e acesso ao disco
    static final long LATENCIA_ORIGEM_MS = 300;

    //Ppsta (relativa ao diretório de execução) onde a origem grava seus arquivos
    static final String PASTA_ORIGEM = "origem_arquivos";

    private static final Pattern PADRAO_ARQUIVO = Pattern.compile("^arquivo(\\d{1,9})\\.ext$");


    static int numeroDoArquivo(String nome) {
        Matcher m = PADRAO_ARQUIVO.matcher(nome); //se tudo certo extrai o número do nome do arquivo
        return m.matches() ? Integer.parseInt(m.group(1)) : -1; //nao segue o formato
    }
}