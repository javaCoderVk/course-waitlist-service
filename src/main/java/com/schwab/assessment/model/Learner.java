package com.schwab.assessment.model;

import java.util.Objects;

/**
 * Immutable representation of a course participant.
 */
public final class Learner {
    private final String id;
    private final String name;
    private final boolean meetsPrerequisite;

    public Learner(String id, String name, boolean meetsPrerequisite) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Learner ID cannot be null or blank");
        }
        this.id = id.trim();
        this.name = (name == null || name.isBlank()) ? "Unknown" : name.trim();
        this.meetsPrerequisite = meetsPrerequisite;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public boolean meetsPrerequisite() {
        return meetsPrerequisite;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Learner learner = (Learner) o;
        return Objects.equals(id, learner.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "Learner{" +
                "id='" + id + '\'' +
                ", name='" + name + '\'' +
                ", meetsPrerequisite=" + meetsPrerequisite +
                '}';
    }
}
