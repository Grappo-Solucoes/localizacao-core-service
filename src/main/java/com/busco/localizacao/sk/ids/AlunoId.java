package com.busco.localizacao.sk.ids;

import com.busco.localizacao.sk.ddd.ValueObject;

import java.util.Objects;

/**
 * Identificador tipado de Aluno.
 */
public final class AlunoId implements ValueObject {

    private final String value;

    public AlunoId(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("AlunoId não pode ser nulo ou vazio");
        }
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AlunoId that = (AlunoId) o;
        return Objects.equals(value, that.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
