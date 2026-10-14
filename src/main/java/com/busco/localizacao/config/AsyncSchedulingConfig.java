package com.busco.localizacao.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Habilita o agendamento e a execução assíncrona usados pela Visão ao Vivo.
 *
 * <p>Sem {@code @EnableScheduling} o ciclo de 5s do {@code VisaoGeralScheduler}
 * nunca é disparado; sem {@code @EnableAsync} o {@code @Async} do
 * {@code VisaoGeralSnapshotPublisher} é ignorado.</p>
 */
@Configuration
@EnableScheduling
@EnableAsync
public class AsyncSchedulingConfig {
}
