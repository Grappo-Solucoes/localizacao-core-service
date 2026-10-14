package com.busco.localizacao.infra.rest.advice;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

/**
 * Traduz erros do domínio e do framework para status HTTP adequados, evitando que
 * exceções vazem para o cliente (spec do change
 * {@code substituicao-gradual-localizacao-core-service}, cenário
 * "Tratamento de erro centralizado").
 */
@RestControllerAdvice
public class ExceptionAdvice {

    private static final Logger log = LoggerFactory.getLogger(ExceptionAdvice.class);

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleIllegalArgument(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body(erro(ex.getMessage(), ex));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, String>> handleIllegalState(IllegalStateException ex) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(erro(ex.getMessage(), ex));
    }

    /**
     * Exceções do próprio framework (rota inexistente, método ou media type não
     * suportados, corpo inválido, falha de validação) já carregam o status adequado em
     * {@link ErrorResponse}. Sem este tratamento o catch-all devolveria 500 para todas
     * elas, mascarando 404/405/415/400.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleGeneric(Exception ex) {
        if (ex instanceof ErrorResponse errorResponse) {
            return ResponseEntity.status(errorResponse.getStatusCode())
                    .body(erro(detalhe(errorResponse, ex), ex));
        }
        log.error("Erro interno nao mapeado", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(erro("Erro interno: " + ex.getClass().getSimpleName(), ex));
    }

    private String detalhe(ErrorResponse errorResponse, Exception ex) {
        String detail = errorResponse.getBody().getDetail();
        return detail != null ? detail : ex.getMessage();
    }

    /** Map.of nao aceita valor nulo; mensagem ausente nao pode virar outro erro. */
    private Map<String, String> erro(String mensagem, Exception ex) {
        String texto = mensagem != null ? mensagem : ex.getClass().getSimpleName();
        return Map.of("error", texto);
    }
}
