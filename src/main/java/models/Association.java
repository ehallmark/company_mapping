package models;

import lombok.Getter;
import lombok.NonNull;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;

public class Association implements Serializable {
    private static final long serialVersionUID = 1L;
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
        MarketRevenue,
        CompanyRevenue,
        ProductRevenue,
        MarketShareRevenue,
        Region
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
    @Getter
    private String joinTableName;
    @Getter
    private String associationName;
    @Getter
    private String reverseAssociationName;
    @Getter
    private final List<String> joinAttributes;
    public Association(Model model, String parentTableName, String childTableName, String joinTableName, Type type, String parentIdField, String childIdField, boolean dependent, String reverseAssociationName) {
        this(model.toString(), model, parentTableName, childTableName, joinTableName, type, parentIdField, childIdField, dependent, reverseAssociationName);
    }

    public Association(String associationName, Model model, String parentTableName, String childTableName, String joinTableName, Type type, String parentIdField, String childIdField, boolean dependent, String reverseAssociationName) {
        this(associationName, model, parentTableName, childTableName, joinTableName, type, parentIdField, childIdField, dependent, reverseAssociationName, Collections.emptyList());
    }

    public Association(String associationName, Model model, String parentTableName, String childTableName, String joinTableName, Type type, String parentIdField, String childIdField, boolean dependent, String reverseAssociationName, @NonNull List<String> joinAttributes) {
        this.parentIdField = parentIdField;
        this.joinAttributes = joinAttributes;
        this.reverseAssociationName=reverseAssociationName;
        this.dependent = dependent;
        this.associationName=associationName;
        this.joinTableName = joinTableName;
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

    public boolean shouldNotExpand(boolean isRevenueModel) {
        if(getAssociationName().startsWith("Parent ")||getAssociationName().equals("Sub Company")) {
            return true;
        }
        if(isRevenueModel && !(getAssociationName().equals("Sub Revenue"))) {
            return true;
        }
        return false;
    }

}
