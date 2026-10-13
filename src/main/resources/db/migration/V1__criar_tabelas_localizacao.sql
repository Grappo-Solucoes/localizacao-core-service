CREATE SCHEMA IF NOT EXISTS localizacao;

CREATE TABLE IF NOT EXISTS localizacao.ponto_rota (
    id VARCHAR(64) PRIMARY KEY,
    viagem_id VARCHAR(64) NOT NULL,
    nome VARCHAR(255) NOT NULL,
    latitude DOUBLE PRECISION NOT NULL,
    longitude DOUBLE PRECISION NOT NULL,
    tipo VARCHAR(32) NOT NULL,
    visitado BOOLEAN NOT NULL DEFAULT FALSE,
    horario_chegada TIMESTAMP,
    ordem INTEGER NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_ponto_rota_viagem ON localizacao.ponto_rota(viagem_id);

CREATE TABLE IF NOT EXISTS localizacao.ponto_embarque (
    id VARCHAR(64) PRIMARY KEY,
    viagem_id VARCHAR(64) NOT NULL,
    aluno_id VARCHAR(64) NOT NULL,
    nome VARCHAR(255) NOT NULL,
    latitude DOUBLE PRECISION NOT NULL,
    longitude DOUBLE PRECISION NOT NULL,
    tipo VARCHAR(32) NOT NULL,
    realizado BOOLEAN NOT NULL DEFAULT FALSE,
    horario_realizado TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_ponto_embarque_viagem ON localizacao.ponto_embarque(viagem_id);
CREATE INDEX IF NOT EXISTS idx_ponto_embarque_aluno ON localizacao.ponto_embarque(aluno_id);
