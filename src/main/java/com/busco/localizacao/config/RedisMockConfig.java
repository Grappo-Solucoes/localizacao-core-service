//package com.busco.localizacao.config;
//
//import com.busco.localizacao.domain.entity.PontoEmbarque;
//import com.busco.localizacao.domain.entity.PontoRota;
//import com.busco.localizacao.infra.redis.AlunoEmbarqueRedisRepository;
//import com.busco.localizacao.infra.redis.RotaRedisRepository;
//import org.springframework.context.annotation.Bean;
//import org.springframework.context.annotation.Configuration;
//import org.springframework.context.annotation.Primary;
//import org.springframework.context.annotation.Profile;
//import reactor.core.publisher.Flux;
//import reactor.core.publisher.Mono;
//
//import java.util.ArrayList;
//import java.util.HashMap;
//import java.util.List;
//import java.util.Map;
//import java.util.concurrent.ConcurrentHashMap;
//
//@Configuration
//@Profile("test")
//public class RedisMockConfig {
//
//    @Bean
//    @Primary
//    public RotaRedisRepository rotaRedisRepositoryMock() {
//        // Create a mock implementation that doesn't call super()
//        return new RotaRedisRepository() {
//            private final Map<String, List<PontoRota>> rotas = new ConcurrentHashMap<>();
//
//            @Override
//            public Mono<List<PontoRota>> buscarPontosRota(String viagemId) {
//                List<PontoRota> pontos = rotas.getOrDefault(viagemId, criarPontosRotaMock(viagemId));
//                return Mono.justOrEmpty(pontos);
//            }
//
//            @Override
//            public Mono<Boolean> salvarPontosRota(String viagemId, List<PontoRota> pontos) {
//                rotas.put(viagemId, pontos);
//                return Mono.just(true);
//            }
//
//            @Override
//            public Mono<Boolean> atualizarPonto(String pontoId, PontoRota pontoAtualizado) {
//                // Find and update the ponto in the map
//                for (List<PontoRota> pontos : rotas.values()) {
//                    for (int i = 0; i < pontos.size(); i++) {
//                        if (pontos.get(i).getId().equals(pontoId)) {
//                            pontos.set(i, pontoAtualizado);
//                            return Mono.just(true);
//                        }
//                    }
//                }
//                return Mono.just(false);
//            }
//
//            private List<PontoRota> criarPontosRotaMock(String viagemId) {
//                List<PontoRota> pontos = new ArrayList<>();
//
//                // Terminal
//                PontoRota terminal = new PontoRota(
//                        "term1",
//                        "Terminal Central",
//                        -25.3870808,
//                        -49.1621886,
//                        PontoRota.TipoPonto.TERMINAL
//                );
//                terminal.setOrdem(1);
//                terminal.setTempoParada(300); // 5 minutos
//                pontos.add(terminal);
//
//                // Escola 1
//                PontoRota escola1 = new PontoRota(
//                        "esc1",
//                        "Escola Estadual Maria Augusta",
//                        -25.3880808,
//                        -49.1631886,
//                        PontoRota.TipoPonto.ESCOLA
//                );
//                escola1.setOrdem(2);
//                escola1.setTempoParada(180); // 3 minutos
//                pontos.add(escola1);
//
//                // Escola 2
//                PontoRota escola2 = new PontoRota(
//                        "esc2",
//                        "Colégio Municipal São José",
//                        -25.3890808,
//                        -49.1641886,
//                        PontoRota.TipoPonto.ESCOLA
//                );
//                escola2.setOrdem(3);
//                escola2.setTempoParada(180); // 3 minutos
//                pontos.add(escola2);
//
//                return pontos;
//            }
//        };
//    }
//
//    @Bean
//    @Primary
//    public AlunoEmbarqueRedisRepository alunoEmbarqueRedisRepositoryMock() {
//        // Create a mock implementation that doesn't call super()
//        return new AlunoEmbarqueRedisRepository() {
//            private final Map<String, Map<String, PontoEmbarque>> embarques = new ConcurrentHashMap<>();
//
//            @Override
//            public Mono<PontoEmbarque> buscarPontoEmbarque(String viagemId, String alunoId) {
//                Map<String, PontoEmbarque> mapa = embarques.computeIfAbsent(viagemId,
//                        k -> criarPontosEmbarqueMock(viagemId));
//                return Mono.justOrEmpty(mapa.get(alunoId));
//            }
//
//            @Override
//            public Mono<Map<String, PontoEmbarque>> buscarMapaEmbarques(String viagemId) {
//                Map<String, PontoEmbarque> mapa = embarques.computeIfAbsent(viagemId,
//                        k -> criarPontosEmbarqueMock(viagemId));
//                return Mono.just(mapa);
//            }
//
//            @Override
//            public Flux<PontoEmbarque> buscarTodosPontosEmbarque(String viagemId) {
//                Map<String, PontoEmbarque> mapa = embarques.computeIfAbsent(viagemId,
//                        k -> criarPontosEmbarqueMock(viagemId));
//                return Flux.fromIterable(mapa.values());
//            }
//
//            private Map<String, PontoEmbarque> criarPontosEmbarqueMock(String viagemId) {
//                Map<String, PontoEmbarque> mapa = new HashMap<>();
//
//                // Aluno 1 - Embarque na Escola 1
//                PontoEmbarque ponto1 = new PontoEmbarque(
//                        "0ea11bc3-5ebe-4b5b-b094-8dbea78629fa",
//                        "esc1",
//                        -25.3881808,
//                        -49.1632886,
//                        PontoEmbarque.TipoEmbarque.EMBARQUE
//                );
//                ponto1.setHorarioPrevisto(System.currentTimeMillis() + 3600000); // +1 hora
//                mapa.put("0ea11bc3-5ebe-4b5b-b094-8dbea78629fa", ponto1);
//
//                // Aluno 2 - Desembarque na Escola 2
//                PontoEmbarque ponto2 = new PontoEmbarque(
//                        "aluno456",
//                        "esc2",
//                        -25.3891808,
//                        -49.1642886,
//                        PontoEmbarque.TipoEmbarque.DESEMBARQUE
//                );
//                ponto2.setHorarioPrevisto(System.currentTimeMillis() + 7200000); // +2 horas
//                mapa.put("aluno456", ponto2);
//
//                return mapa;
//            }
//        };
//    }
//}