package com.busco.localizacao.infra.migration;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifica que a migração Flyway (`V1__criar_tabelas_localizacao.sql`) aplica
 * em banco de teste (H2 em modo PostgreSQL) e cria o schema/tabelas/colunas
 * esperados pelo mapeamento JPA.
 */
class FlywayMigrationTest {

    private static final String URL =
            "jdbc:h2:mem:localizacao-migration;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1";
    private static final String USER = "sa";
    private static final String PASSWORD = "";

    private MigrateResult migracao;

    @BeforeEach
    void aplicarMigracao() throws Exception {
        try (Connection conn = DriverManager.getConnection(URL, USER, PASSWORD);
             Statement st = conn.createStatement()) {
            st.execute("DROP ALL OBJECTS");
        }

        Flyway flyway = Flyway.configure()
                .dataSource(URL, USER, PASSWORD)
                .locations("classpath:db/migration")
                .schemas("localizacao")
                .load();

        migracao = flyway.migrate();
    }

    @Test
    void migracaoAplicaComSucesso() {
        assertTrue(migracao.success, "a migração deveria aplicar sem erro");
        assertEquals(1, migracao.migrationsExecuted,
                "deveria existir exatamente uma migração (V1)");
    }

    @Test
    void criaTabelasNoSchemaLocalizacao() throws Exception {
        assertTrue(tabelaExiste("ponto_rota"), "tabela localizacao.ponto_rota deveria existir");
        assertTrue(tabelaExiste("ponto_embarque"), "tabela localizacao.ponto_embarque deveria existir");
    }

    @Test
    void pontoRotaTemColunasMapeadasPelaEntidadeJpa() throws Exception {
        List<String> colunas = colunasDe("ponto_rota");

        assertTrue(colunas.containsAll(List.of(
                "id", "viagem_id", "nome", "latitude", "longitude", "tipo",
                "visitado", "horario_previsto", "horario_chegada", "tempo_parada", "ordem"
        )), "colunas de ponto_rota divergem do mapeamento: " + colunas);
    }

    @Test
    void pontoEmbarqueTemColunasMapeadasPelaEntidadeJpa() throws Exception {
        List<String> colunas = colunasDe("ponto_embarque");

        assertTrue(colunas.containsAll(List.of(
                "id", "viagem_id", "aluno_id", "nome", "latitude", "longitude",
                "tipo", "realizado", "horario_realizado"
        )), "colunas de ponto_embarque divergem do mapeamento: " + colunas);
    }

    private boolean tabelaExiste(String tabela) throws Exception {
        String sql = "SELECT COUNT(*) FROM information_schema.tables "
                + "WHERE table_schema = 'localizacao' AND table_name = '" + tabela + "'";
        try (Connection conn = DriverManager.getConnection(URL, USER, PASSWORD);
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            rs.next();
            return rs.getInt(1) == 1;
        }
    }

    private List<String> colunasDe(String tabela) throws Exception {
        String sql = "SELECT column_name FROM information_schema.columns "
                + "WHERE table_schema = 'localizacao' AND table_name = '" + tabela + "'";
        List<String> colunas = new ArrayList<>();
        try (Connection conn = DriverManager.getConnection(URL, USER, PASSWORD);
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                colunas.add(rs.getString(1).toLowerCase());
            }
        }
        return colunas;
    }
}
