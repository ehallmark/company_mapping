package graph;

import lombok.Getter;
import lombok.NonNull;
import models.Association;
import models.Model;

import java.util.*;

public class Node {
    @Getter
    private final Model model;
    @Getter
    private Map<String,List<Edge>> edgeMap;
    public Node(@NonNull Model model) {
        this.model=model;
        this.edgeMap = new HashMap<>();
    }

    public void addEdge(Edge edge) {
        edgeMap.putIfAbsent(edge.getAssociation().getAssociationName(), new ArrayList<>());
        List<Edge> edges = edgeMap.get(edge.getAssociation().getAssociationName());
        if(!edges.contains(edge)) {
            edges.add(edge);
        }
    }


    @Override
    public boolean equals(Object other) {
        if(!(other instanceof Node)) return false;

        return (model.getClass().getSimpleName()+model.getId()).equals(
                ((Node)other).model.getClass().getSimpleName()+((Node)other).model.getId()
        );
    }

    @Override
    public int hashCode() {
        return Objects.hash(model.getClass().getSimpleName(), model.getId());
    }
}
