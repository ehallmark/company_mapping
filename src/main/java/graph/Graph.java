package graph;

import com.google.common.util.concurrent.AtomicDouble;
import controllers.Main;
import database.Database;
import lombok.NonNull;
import models.*;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;
import java.util.stream.Collectors;

public class Graph {
    private static final Lock lock = new ReentrantLock();
    private Thread garbageCollector;
    private Map<Association.Model, Map<Integer, Node>> nodeCache;
    private final Map<Node, AtomicDouble> accessStatistics;
    public Graph() {
        this.nodeCache = new HashMap<>();
        this.accessStatistics = Collections.synchronizedMap(new HashMap<>());
        this.garbageCollector = new Thread(new GarbageCollector());
        this.garbageCollector.start();
    }

    public void addNode(@NonNull Association.Model model, int id, @NonNull Node node) {
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

    public void connectNodes(@NonNull Node node, @NonNull Node target, @NonNull Association association) {
        lock.lock();
        try {
            node.addEdge(new Edge(node, target, association));
        } finally {
            lock.unlock();
        }
    }

    public Node findNode(@NonNull Association.Model model, int id) {
        lock.lock();
        try {
            Node node = nodeCache.getOrDefault(model, Collections.emptyMap()).get(id);
            if(node!=null) {
                synchronized (accessStatistics) {
                    accessStatistics.putIfAbsent(node, new AtomicDouble(0L));
                    accessStatistics.get(node).getAndAdd(1d);
                }
            }
            return node;
        } finally {
            lock.unlock();
        }
    }

    public Model findByName(Association.Model type, String name, boolean createIfNotPresent) {
        Model model = getModelList(type).stream().filter(m->m.getName().equalsIgnoreCase(name)).findAny().orElse(null);
        if(model==null && createIfNotPresent) {
            // create
            System.out.println("Creating "+type+": "+name);
            Map<String,Object> data = new HashMap<>(Collections.singletonMap(Constants.NAME, name));
            switch (type) {
                case Company: {
                    model = new Company(null,data);
                    break;
                }
                case Market: {
                    model = new Market(null,data);
                    break;
                }
                case Product: {
                    model = new Product(null,data);
                    break;
                } default: {
                    throw new RuntimeException("Invalid creation of model by name: "+type);
                }
            }
            model.createInDatabase();
            addNode(type, model.getId(), new Node(model));
        } else {
            System.out.println("Found "+type+" in database: "+name);
        }
        return model;
    }

    public boolean areAlreadyConnected(@NonNull Association.Model model, int id, @NonNull Association.Model otherModel, int otherId) {
        lock.lock();
        try {
            Node node1 = findNode(model, id);
            Node node2 = findNode(otherModel, otherId);
            Set<Node> node1Relatives = new HashSet<>();
            Function<String,Boolean> recursiveIf =  key -> key.contains(model.toString()) || key.contains(otherModel.toString());
            findRelativesHelper(node1, node1Relatives, recursiveIf);
            return node1Relatives.contains(node2);
        } finally {
            lock.unlock();
        }
    }

    // Call this function after updating the underling models and the SQL database
    public void linkNodeWithAssociation(@NonNull Model model, @NonNull Model otherModel, @NonNull Association association) {
        lock.lock();
        Node node1;
        Node node2;
        try {
            node1 = findNode(model.getType(), model.getId());
            node2 = findNode(otherModel.getType(), otherModel.getId());
            connectNodes(node1, node2, association);
            Association reverseAssociation = node2.getModel().findAssociation(association.getReverseAssociationName());
            connectNodes(node2, node1, reverseAssociation);

        } finally {
            lock.unlock();
        }
        // reset associations

        node1.getModel().setAssociations(null);
        node1.getModel().loadAssociations();
        node2.getModel().setAssociations(null);
        node2.getModel().loadAssociations();
    }

    public void unlinkNodeFromAssociation(@NonNull Association.Model model, int id, @NonNull Association association) {
        Node node = nodeCache.getOrDefault(model, new HashMap<>(1)).get(id);
        unlinkNodeFromAssociation(node, association);
    }

    public void unlinkNodeFromAssociation(@NonNull Node node, @NonNull Association association) {
        lock.lock();
        try {
            node.getEdgeMap().getOrDefault(association.getAssociationName(), Collections.emptyList()).forEach(edge -> {
                edge.getTarget().getEdgeMap().getOrDefault(association.getReverseAssociationName(), new ArrayList<>(1))
                        .removeIf(e -> e.getTarget().equals(node));
                edge.getTarget().getModel().loadAssociations();
                Association targetAssoc = edge.getTarget().getModel().findAssociation(association.getReverseAssociationName());
                edge.getTarget().getModel().getAssociations().getOrDefault(targetAssoc, new ArrayList<>(1))
                        .removeIf(e -> new Node(e).equals(node));
            });
            node.getEdgeMap().remove(association.getAssociationName());
        } finally {
            lock.unlock();
        }
    }

    public Node deleteNode(@NonNull Association.Model model, int id) {
        lock.lock();
        try {
            Node node = nodeCache.getOrDefault(model, new HashMap<>(1)).remove(id);
            if(node!=null) {
                accessStatistics.remove(node);
                // remove edges
                node.getEdgeMap().values().forEach(edges->{
                   edges.forEach(edge->{
                        edge.getTarget().getEdgeMap().getOrDefault(edge.getAssociation().getReverseAssociationName(), new ArrayList<>(1))
                        .removeIf(e->e.getTarget().equals(node));
                        edge.getTarget().getModel().loadAssociations();
                        Association targetAssoc = edge.getTarget().getModel().findAssociation(edge.getAssociation().getReverseAssociationName());
                        edge.getTarget().getModel().getAssociations().getOrDefault(targetAssoc, new ArrayList<>(1))
                               .removeIf(e->new Node(e).equals(node));
                   });
                });
            }
            return node;
        } finally {
            lock.unlock();
        }
    }

    public List<Model> getModelList(@NonNull Association.Model model) {
        lock.lock();
        try {
            Map<Integer, Node> idMap = nodeCache.getOrDefault(model, Collections.emptyMap());
            return idMap.values().stream().map(Node::getModel).collect(Collectors.toList());
        } finally {
            lock.unlock();
        }
    }


    public Collection<Node> findMutualRelatives(@NonNull Model model1, @NonNull Model model2) {
        Node node1 = findNode(model1.getType(), model1.getId());
        Node node2 = findNode(model2.getType(), model2.getId());
        Set<Node> mutualRelatives = new HashSet<>();
        Function<String,Boolean> recursiveIf =  key -> (key.contains("Revenue")||key.contains("Market Share")||key.equals("Market")||key.equals("Parent Market"));
        if(node1!=null && node2!=null) {
            mutualRelatives.add(node1);
            mutualRelatives.add(node2);

            Collection<Node> relatives1 = new HashSet<>();
            relatives1.add(node1);
            Collection<Node> relatives2 = new HashSet<>();
            relatives2.add(node2);

            // find relatives
            findRelativesHelper(node1, relatives1, recursiveIf);
            findRelativesHelper(node2, relatives2, recursiveIf);
            for(Node relative1 : relatives1) {
                if(relatives2.contains(relative1)) {
                    mutualRelatives.add(relative1);
                }
            }
        }
        return mutualRelatives;
    }

    private static void findRelativesHelper(@NonNull Node node, @NonNull Collection<Node> relatives, Function<String,Boolean> recurseIfFunction) {
        Map<String, List<Edge>> edgeMap = node.getEdgeMap();
        Set<String> keys = new HashSet<>(edgeMap.keySet());
        boolean seenAll = true;
        for(String key : keys) {
            List<Edge> edges = edgeMap.getOrDefault(key, Collections.emptyList());
            for(Edge edge : edges) {
                seenAll = seenAll && !relatives.add(edge.getTarget());
            }
        }
        if(!seenAll) { // recurse on revenues
            for (String key : keys) {
                List<Edge> edges = edgeMap.getOrDefault(key, Collections.emptyList());
                if (recurseIfFunction.apply(key)) {
                    for (Edge edge : edges) {
                        findRelativesHelper(edge.getTarget(), relatives, recurseIfFunction);
                    }
                }
            }
        }
    }


    private static Graph graph;
    public static Graph load() {
        return load(false);
    }
    public static Graph load(boolean force) {
        if(graph!=null && !force) return graph;
        if(graph!=null) {
            // stop garbage collector
            graph.garbageCollector.interrupt();
        }
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
                        model.getAvailableAttributes().stream().filter(f -> f.endsWith("_id")).collect(Collectors.toList()), null, null);
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


    private class GarbageCollector implements Runnable {
        private final long runEveryMS = 30 * 1000L;
        private final double keepDataPercent = 0.1;
        @Override
        public void run() {
            while(!Thread.interrupted()) {
                System.out.println("Running GC... "+Runtime.getRuntime().freeMemory());
                runGC();
                try {
                    TimeUnit.MILLISECONDS.sleep(runEveryMS);
                } catch (Exception e) {
                    e.printStackTrace();
                    break;
                }
            }
            System.out.println("Interrupted GC.");
        }

        private void runGC() {
            // Delete inner data/associations from nodes that are rarely accessed
            synchronized (accessStatistics) {
                final int limit = Math.round(accessStatistics.size() * (float) keepDataPercent);
                AtomicInteger cnt = new AtomicInteger(0);
                accessStatistics.entrySet().stream().sorted(Comparator.comparingDouble(e -> e.getValue().get()))
                        .filter(e -> cnt.getAndIncrement() < limit || e.getValue().get() <= 1 || !e.getKey().getModel().existsInDatabase())
                        .limit(limit).forEach(e -> {
                    e.getKey().getModel().purgeMemory();
                    accessStatistics.put(e.getKey(), new AtomicDouble(0));
                });
            }
            System.gc();
        }
    }
}
