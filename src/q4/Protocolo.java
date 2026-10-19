package q4;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;

//metodos de leitura e escrita das respostas do protocolo de aplicação
final class Protocolo {

    private Protocolo() { }

    /** Resposta a uma requisição GET. */
    static final class Resposta {
        final String status;    //linha de status "200 OK"
        final String cache;     // miss / hit, ou null quando não se aplica.
        final String conteudo;  // empty quando não há conteúdo.

        Resposta(String status, String cache, String conteudo) {
            this.status = status;
            this.cache = cache;
            this.conteudo = conteudo;
        }

        boolean ok() {
            return status.startsWith("200");
        } // se bem sucedida a resposta, status é iniciado
    }

//Escreve a resposta no fluxo, no formato do protocolo, e descarrega o buffer
static void enviarResposta(PrintWriter out, Resposta r) {
        out.print(r.status + "\n");
        if (r.cache != null) {
            out.print("Cache: " + r.cache + "\n");
        }
        out.print("Length: " + r.conteudo.length() + "\n");
        out.print("\n");
        out.print(r.conteudo);
        out.flush(); //garante o envio de dados
    }

    //leitura de uma resposta completa do fluxo. O cabeçalho é lido linha a linha
    static Resposta lerResposta(BufferedReader in) throws IOException {
        String status = in.readLine();
        if (status == null) {
            throw new IOException("Conexão encerrada sem resposta");
        }

        String cache = null;
        int tamanho = 0;
        String linha;
        while ((linha = in.readLine()) != null && !linha.isEmpty()) {
            try {
                if (linha.startsWith("Cache:")) {
                    cache = linha.substring(6).trim();
                } else if (linha.startsWith("Length:")) {
                    tamanho = Integer.parseInt(linha.substring(7).trim());
                }
            } catch (NumberFormatException e) {
                throw new IOException("Cabeçalho malformado: " + linha);
            }
        }

        char[] buffer = new char[tamanho];
        int lidos = 0;
        while (lidos < tamanho) {
            int n = in.read(buffer, lidos, tamanho - lidos);
            if (n == -1) {
                throw new IOException("Conteúdo incompleto (" + lidos + " de " + tamanho + ")");
            }
            lidos += n;
        }
        return new Resposta(status, cache, new String(buffer));
    }
}