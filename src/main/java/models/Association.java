package models;

import lombok.Getter;

public class Association {
    public enum Type {
        OneToMany,
        ManyToOne,
        OneToOne,
        ManyToMany
    }

    public enum Model {
        Company,
        Market,
        Product,
        Revenue,
        Segment
    }

    @Getter
    private String parentTableName;
    @Getter
    private String childTableName;
    @Getter
    private Type type;
    @Getter
    private String parentIdField;
    @Getter
    private String childIdField;
    @Getter
    private Model model;
    @Getter
    private boolean dependent;
    public Association(Model model, String parentTableName, String childTableName, Type type, String parentIdField, String childIdField, boolean dependent) {
        this.parentIdField = parentIdField;
        this.dependent = dependent;
        this.parentTableName = parentTableName;
        this.childIdField = childIdField;
        this.childTableName = childTableName;
        this.model=model;
        this.type = type;
        if(this.type.equals(Type.OneToMany)) {
            // child id must exist
            if(parentIdField==null) {
                throw new RuntimeException("Parent id must exist for one to many");
            }
        }
        if(this.type.equals(Type.ManyToOne)) {
            // child id must exist
            if(parentIdField==null) {
                throw new RuntimeException("Parent id must exist for many to one");
            }
        }
        if(this.type.equals(Type.OneToOne)) {
            // child id must exist
            if(childIdField==null && parentIdField==null) {
                throw new RuntimeException("Child id or parent id must exist for one to one");
            }
        }
        if(this.type.equals(Type.ManyToMany)) {
            // child id must exist
            if(childIdField==null || parentIdField==null) {
                throw new RuntimeException("Child id and parent id must exist for many to many");
            }
        }
    }


}
