package models;

import database.Database;
import lombok.Getter;
import lombok.NonNull;

import java.util.*;

public abstract class Model {
    @Getter
    private final String tableName;
    @Getter
    private Integer id;
    @Getter
    protected Map<String,Object> data;
    @Getter
    protected final Set<String> availableAttributes;
    @Getter
    protected List<Association> associationsMeta;
    @Getter
    protected Map<Association,List<Model>> associations;
    protected Model(@NonNull List<Association> associationsMeta, @NonNull Set<String> availableAttributes, @NonNull String tableName, Integer id, Map<String,Object> data) {
        this.tableName = tableName;
        this.data = data;
        this.associationsMeta = associationsMeta;
        this.id = id;
        this.availableAttributes=availableAttributes;
        if(id != null && data == null) {
            // pull data from database
            loadAttributesFromDatabase();
        }
    }

    public boolean existsInDatabase() {
        return id != null;
    }


    public void updateAttribute(String attr, Object val) {
        this.data.put(attr, val);
    }

    public void removeAttribute(String attr) {
        this.data.remove(attr);
    }

    public void loadAssociations() {
        if(!existsInDatabase()) {
            throw new RuntimeException("Cannot load associations if the model does not yet exist in the database.");
        }
        if(data==null) {
            loadAttributesFromDatabase();
        }
        this.associations = new HashMap<>();
        for(Association association : associationsMeta) {
            try {
                if (association.getType().equals(Association.Type.OneToMany)) {
                    List<Model> children = Database.loadOneToManyAssociation(association.getModel(), this, association.getChildTableName(), association.getParentIdField());
                    data.put(association.getModel().toString(), children);
                    associations.put(association, children);
                } else if (association.getType().equals(Association.Type.ManyToOne)) {
                    Model parent = Database.loadManyToOneAssociation(association.getModel(), this, association.getChildTableName(), association.getParentIdField());
                    if(parent!=null) {
                        data.put(association.getModel().toString(), parent);
                        associations.put(association, Collections.singletonList(parent));
                    }
                } else if (association.getType().equals(Association.Type.OneToOne)) {
                    if(association.getChildIdField()==null) {

                    } else {

                    }
                    throw new RuntimeException("One to one associations are not yet implemented.");
                } else if (association.getType().equals(Association.Type.ManyToMany)) {
                    List<Model> children = Database.loadManyToManyAssociation(association.getModel(), this, association.getChildTableName(), association.getParentIdField(), association.getChildIdField());
                    data.put(association.getModel().toString(), children);
                    associations.put(association, children);
                }
            } catch(Exception e) {
                e.printStackTrace();
            }
        }
    }

    public void loadAttributesFromDatabase() {
        if(!existsInDatabase()) {
            throw new RuntimeException("Trying to select a record that does not exist in the database...");
        }
        try {
            this.data = Database.select(tableName, id, availableAttributes);
        } catch(Exception e) {
            e.printStackTrace();
            throw new RuntimeException("Error loading attributes from database: " + e.getMessage());
        }
    }


    public void updateInDatabase() {
        // update database
        if(!existsInDatabase()) {
            throw new RuntimeException("Trying to update a record that does not exist in the database...");
        }
        try {
            Database.update(tableName, id, data);
        } catch(Exception e) {
            e.printStackTrace();
            throw new RuntimeException("Error inserting record into database: " + e.getMessage());
        }
    }

    // save to database for the first time
    public void createInDatabase() {
        if(existsInDatabase()) {
            throw new RuntimeException("Trying to create a record that already exists in the database...");
        }
        try {
            id = Database.insert(tableName, data);
        } catch(Exception e) {
            e.printStackTrace();
            throw new RuntimeException("Error inserting record into database: "+e.getMessage());
        }
    }

    // upsert to the database
    public void upsertInDatabase() {
        if(existsInDatabase()) {
            updateInDatabase();
        } else {
            createInDatabase();
        }
    }

    // delete record from the database
    public void deleteFromDatabase(boolean cascade) {
        if(!existsInDatabase()) {
            throw new RuntimeException("Trying to delete a record that does not exist in the database...");
        }
        if(cascade) {
            loadAssociations();
            for(Map.Entry<Association,List<Model>> entry : associations.entrySet()) {
                if(entry.getKey().isDependent()) {
                    for(Model association : entry.getValue()) {
                        association.deleteFromDatabase(cascade);
                    }
                }
            }
        }
        try {
            Database.delete(tableName, id);
        } catch(Exception e) {
            e.printStackTrace();
            throw new RuntimeException("Error deleting record from database: "+e.getMessage());
        }
    }
}
