package q4;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Constantes compartilhadas pelos componentes da CDN e validação do nome de arquivo.
 *
 * Todos os componentes (Origem, Router, Edges e Cliente) usam esta classe, então
 * qualquer ajuste de porta, tamanho de cache ou TTL padrão é feito em um lugar só.
 */
public final class Config {

    // classe utilitária: o construtor privado impede que alguém crie instâncias dela
    private Config() { }

    // tudo roda na mesma máquina (localhost), cada componente em uma porta diferente
    static final String HOST = "localhost";

    // porta TCP do servidor de origem (o enunciado sugere 8000)
    static final int PORTA_ORIGEM = 8000;

    // porta UDP do router/GeoDNS (o enunciado sugere algo como 5353; usei 5354)
    // as portas dos Edges (8001, 8002 e 8003) são digitadas ao iniciar cada um
    // e ficam mapeadas por região dentro do RouterGeoDNS
    static final int PORTA_ROUTER = 5354;

    // total de arquivos armazenados na origem (arquivo1.ext até arquivo1000.ext),
    // como pede o enunciado
    static final int TOTAL_ARQUIVOS = 1000;

    // capacidade máxima do cache de cada Edge, em número de arquivos (100, conforme o enunciado)
    // ao passar disso, o Edge remove o menos recentemente usado (política LRU)
    static final int TAMANHO_CACHE = 100;

    // tamanho de cada arquivo, em caracteres (aparece como "Length: 1024" na resposta)
    static final int TAMANHO_CONTEUDO = 1024;

    // TTL padrão das entradas do cache, usado quando se aperta só Enter ao iniciar o Edge
    // (10 segundos, o mesmo valor do caso de teste 2, expiração por TTL)
    static final int TTL_PADRAO_SEGUNDOS = 10;

    // delay artificial da origem, pra simular distância e acesso ao disco
    // é ele que deixa o MISS visivelmente mais lento que o HIT no caso de teste 1
    static final long LATENCIA_ORIGEM_MS = 300;

    // pasta (relativa ao diretório de execução) onde a origem grava seus arquivos mestres
    static final String PASTA_ORIGEM = "origem_arquivos";

    // padrão aceito: "arquivo" + número de 1 a 9 dígitos + ".ext" (ex.: arquivo20.ext)
    // o limite de 9 dígitos evita estouro de int no parseInt
    // o ^ e o $ obrigam o nome inteiro a seguir o formato
    private static final Pattern PADRAO_ARQUIVO = Pattern.compile("^arquivo(\\d{1,9})\\.ext$");

    /**
     * Valida o nome do arquivo e extrai o número dele.
     *
     * Retorna -1 se o nome não seguir o formato (ex.: "arquivoABC.ext"); nesse caso
     * quem chamou mostra "Erro na entrada de dados. Tente outra vez!".
     *
     * Atenção: esse método só checa o FORMATO. Se o número está dentro de 1 a 1000
     * é verificado por quem chama (ex.: a origem responde 404 para arquivo1001.ext).
     * Como o padrão só aceita "arquivoN.ext", ele também impede nomes com "../",
     * ou seja, ninguém consegue acessar arquivos fora da pasta da origem.
     */
    static int numeroDoArquivo(String nome) {
        Matcher m = PADRAO_ARQUIVO.matcher(nome); // se tudo certo extrai o número do nome do arquivo
        return m.matches() ? Integer.parseInt(m.group(1)) : -1; // não segue o formato
    }
}