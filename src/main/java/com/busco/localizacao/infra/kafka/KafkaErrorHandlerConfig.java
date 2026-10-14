package com.busco.localizacao.infra.kafka;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

/**
 * Tratamento de erro do consumo: retentativa curta e, se persistir, publicacao no topico de dead
 * letter em vez de travar a particao.
 *
 * <p>O destino e o padrao do {@link DeadLetterPublishingRecoverer}: {@code <topico>.DLT} na mesma
 * particao. O registro e republicado com o valor ja desserializado, por isso o produtor deste
 * servico usa serializador JSON (ver {@code application.properties}).</p>
 */
@Configuration
public class KafkaErrorHandlerConfig {

    @Bean
    public DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<Object, Object> kafkaTemplate,
                                                 @Value("${tracking.tentativas:2}") long tentativas) {
        // Intervalo maior que zero: a retentativa acontece depois da espera, e so quando ela se
        // esgota o registro vai para o dead letter. Com intervalo zero o handler apenas faz o seek
        // e a reentrega nao chega ao recoverer (verificado com broker real no
        // KafkaDltIntegrationTest).
        DefaultErrorHandler handler = new DefaultErrorHandler(new DeadLetterPublishingRecoverer(kafkaTemplate),
                new FixedBackOff(500L, tentativas));
        // Depois de publicar no dead letter o offset avanca (exige ack manual, que e o modo do
        // container): sem isso um restart reprocessaria e republicaria o registro que sempre falha.
        handler.setCommitRecovered(true);
        return handler;
    }
}
