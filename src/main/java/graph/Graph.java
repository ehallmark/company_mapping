package graph;

import controllers.Main;
import database.Database;
import lombok.Getter;
import models.Association;
import models.Model;

import java.util.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

public class Graph {
    private static final Lock lock = new ReentrantLock();

    private Map<Association.Model, Map<Integer, Node>> nodeCache;

    public Graph() {
        this.nodeCache = new HashMap<>();
    }

    public void addNode(Association.Model model, int id, Node node) {
        lock.lock();
        try {
            node.getModel().setNodeCache(this);
            nodeCache.putIfAbsent(model, new HashMap<>());
            Map<Integer, Node> resourceMap = nodeCache.get(model);
            resourceMap.putIfAbsent(id, node);
        } finally {
            lock.unlock();
        }
    }

    public void connectNodes(Node node, Node target, Association association) {
        lock.lock();
        try {
            node.addEdge(new Edge(node, target, association));
        } finally {
            lock.unlock();
        }
    }

    public Node findNode(Association.Model model, int id) {
        lock.lock();
        try {
            return nodeCache.getOrDefault(model, Collections.emptyMap()).get(id);
        } finally {
            lock.unlock();
        }
    }

    public Node deleteNode(Association.Model model, int id) {
        lock.lock();
        try {
            return nodeCache.getOrDefault(model, new HashMap<>(1)).remove(id);
        } finally {
            lock.unlock();
        }
    }

    public List<Model> getModelList(Association.Model model) {
        lock.lock();
        try {
            Map<Integer, Node> idMap = nodeCache.getOrDefault(model, Collections.emptyMap());
            return idMap.values().stream().map(Node::getModel)
                    .sorted((n1, n2) -> Integer.compare(n1.getId(), n2.getId())).collect(Collectors.toList());
        } finally {
            lock.unlock();
        }
    }

    private static Graph graph;
    public static Graph load() throws Exception {
        return load(false);
    }
    public static Graph load(boolean force) throws Exception {
        if(graph!=null && !force) return graph;
        System.out.println("BUILDING GRAPH!!!");
        lock.lock();
        try {
            // test graph
            graph = new Graph();

            Association.Model[] modelTypes = Association.Model.values();
            List<Model> allModels = new ArrayList<>();
            for (Association.Model modelType : modelTypes) {
                System.out.println("Loading model type: " + modelType);
                Model model = Main.getModelByType(modelType);
                List<Model> models = Database.selectAll(model.isRevenueModel(), modelType, model.getTableName(),
                        model.getAvailableAttributes().stream().filter(f -> f.endsWith("_id")).collect(Collectors.toList()));
                allModels.addAll(models);
                for (Model instance : models) {
                    graph.addNode(modelType, instance.getId(), new Node(instance));
                }
            }

            System.out.println("Adding connections...");
            // need to add edges
            for (Model model : allModels) {
                Node node = graph.findNode(Association.Model.valueOf(model.getClass().getSimpleName()), model.getId());
                for (Association association : model.getAssociationsMeta()) {
                    switch (association.getType()) {
                        case ManyToOne: {
                            // model has the parent id
                            Integer assocId = (Integer) model.getData().get(association.getParentIdField());
                            if (assocId != null) {
                                // try find parent
                                Node assoc = graph.findNode(association.getModel(), assocId);
                                graph.connectNodes(node, assoc, association);
                                Association reverseAssociation = assoc.getModel().getAssociationsMeta()
                                        .stream().filter(a -> a.getAssociationName().equals(association.getReverseAssociationName()))
                                        .findAny().orElse(null);

                                if (reverseAssociation != null) {
                                    graph.connectNodes(assoc, node, reverseAssociation);
                                }
                            }
                            break;
                        }
                        case OneToMany: {
                            // other model has the parent id

                            break;
                        }
                        default: {
                            throw new RuntimeException("Unsupport join type: " + association.getType());
                        }
                    }
                }
            }
        } catch(Exception e) {
            e.printStackTrace();
            throw new RuntimeException("Error creating graph: "+e.getMessage());
        } finally {
            lock.unlock();
            System.out.println("FINISHED GRAPH!!!");
        }
        return graph;
    }


    public static void main(String[] args) throws Exception {
        load();
    }

}
