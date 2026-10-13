package com.busco.localizacao.sk.ddd;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import jakarta.persistence.Embeddable;
import jakarta.persistence.MappedSuperclass;
import org.springframework.lang.NonNull;
import org.springframework.data.annotation.PersistenceConstructor;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

import static java.util.Objects.requireNonNull;

/**
 * Base class for value objects that are used as identifiers for {@link IdentifiableDomainObject}s. These are
 * essentially UUID-wrappers.
 */
@Embeddable
@MappedSuperclass
public abstract class DomainObjectId implements ValueObject, Serializable {

    private static final long serialVersionUID = 5308817818523849761L;

    @JsonValue

    private final String uuid;

    @JsonCreator
    @PersistenceConstructor
    protected DomainObjectId(@NonNull String uuid) {
        this.uuid = requireNonNull(uuid, "uuid must not be null");
    }

    /**
     * Creates a new, random instance of the given {@code idClass}.
     */
    @NonNull
    public static <ID extends DomainObjectId> ID randomId(@NonNull Class<ID> idClass) {
        requireNonNull(idClass, "idClass must not be null");
        try {
            return idClass.getConstructor(String.class).newInstance(UUID.randomUUID().toString());
        } catch (Exception ex) {
            throw new RuntimeException("Could not create new instance of " + idClass, ex);
        }
    }

    public static <ID extends DomainObjectId> ID fromString(@NonNull String uuid, @NonNull Class<ID> idClass) {
        requireNonNull(uuid, "uuid must not be null");
        requireNonNull(idClass, "idClass must not be null");
        try {
            return idClass.getConstructor(String.class).newInstance(uuid);
        } catch (Exception ex) {
            throw new RuntimeException("Could not create instance of " + idClass, ex);
        }
    }

    /**
     * Returns the ID as a UUID string.
     */
    @NonNull
    public String toUUID() {
        return uuid;
    }

    @Override
    public boolean equals(Object obj) {

        if (this == obj)
            return true;

        if (obj == null || !getClass().equals(obj.getClass()))
            return false;

        return Objects.equals(uuid, ((DomainObjectId) obj).uuid);
    }

    @Override
    public int hashCode() {
        return Objects.hash(uuid);
    }
}
