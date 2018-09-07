package graph;

import lombok.Getter;
import lombok.NonNull;
import models.Association;

import java.util.Objects;

public class Edge {
    @Getter
    private final Association association;
    @Getter
    private final Node node;
    @Getter
    private final Node target;
    public Edge(@NonNull Node node, @NonNull Node target, @NonNull Association association) {
        this.association=association;
        this.node=node;
        this.target=target;
    }

    @Override
    public boolean equals(Object other) {
        if(!(other instanceof Edge)) return false;

        return association.getAssociationName().equals(((Edge) other).association.getAssociationName()) &&
                node.equals(((Edge) other).node) &&
                target.equals(((Edge) other).target);
    }

    @Override
    public int hashCode() {
        return Objects.hash(association.getAssociationName(), node, target);
    }
}
