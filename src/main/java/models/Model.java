package models;

import database.Database;
import j2html.tags.ContainerTag;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;

import java.io.Serializable;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.*;
import java.util.stream.Collectors;

import static j2html.TagCreator.*;

public abstract class Model implements Serializable {
    private static final long serialVersionUID = 1L;

    @Getter
    private final String tableName;
    @Getter
    private Integer id;
    @Getter @Setter
    protected Map<String,Object> data;
    @Getter
    protected transient final List<String> availableAttributes;
    @Getter
    protected transient List<Association> associationsMeta;
    @Getter
    protected Map<Association,List<Model>> associations;
    protected String template;
    protected Model(@NonNull List<Association> associationsMeta, @NonNull List<String> availableAttributes, @NonNull String tableName, Integer id, Map<String,Object> data) {
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

    public ContainerTag getLink(@NonNull String associationName, @NonNull String associationModel, @NonNull Integer associationId) {
        if(data==null) {
            loadAttributesFromDatabase();
        }
        return div().withId("node-"+this.getClass().getSimpleName()+"-"+id).with(
                getSimpleLink(),
                span("X").attr("data-association", associationModel)
                        .attr("data-association-name", associationName)
                        .attr("data-association-id", associationId.toString()).attr("style","cursor: pointer;").withClass("delete-node").attr("data-resource", this.getClass().getSimpleName()).attr("data-id", id)
        );
    }

    public ContainerTag getSimpleLink() {
        return a((String)data.get(Constants.NAME)).attr("data-id", getId().toString()).attr("data-resource", this.getClass().getSimpleName()).attr("href", "#").withClass("resource-show-link");
    }


    public void associateWith(Model otherModel, String associationName) {
        // find association
        for(Association association : associationsMeta) {
            if(association.getAssociationName().equals(associationName)) {
                    if(association.getModel().toString().equals(this.getClass().getSimpleName())) {
                        // same model, make sure there are no self references
                        // check all parents and children
                        if(associations==null) {
                            loadAssociations();
                        }
                        Set<Integer> allIds = new HashSet<>();
                        List<Map<Association, List<Model>>> maps = Collections.singletonList(associations);
                        while(maps.size()>0) {
                            maps = maps.stream().flatMap(map->map.getOrDefault(association, Collections.emptyList()).stream())
                                    .map(m->{
                                        m.loadAssociations();
                                        allIds.add(m.getId());
                                        return m.getAssociations();
                                    }).collect(Collectors.toList());
                        }
                        for(Association reverseAssociation : associationsMeta) {
                            if(reverseAssociation.getReverseAssociationName().equals(association.getAssociationName())) {
                                // check this association too
                                maps = Collections.singletonList(associations);
                                while(maps.size()>0) {
                                    maps = maps.stream().flatMap(map->map.getOrDefault(reverseAssociation, Collections.emptyList()).stream())
                                            .map(m->{
                                                m.loadAssociations();
                                                allIds.add(m.getId());
                                                return m.getAssociations();
                                            }).collect(Collectors.toList());
                                }
                                break;
                            }
                        }
                        if(allIds.contains(otherModel.getId())) {
                            throw new RuntimeException("Unable to assign association. Cycle detected.");
                        }
                    }
                    switch (association.getType()) {
                        case OneToMany: {
                            // need to set parent id of association
                            otherModel.updateAttribute(association.getParentIdField(), id);
                            otherModel.updateInDatabase();
                            break;
                        }
                        case ManyToOne: {
                            // need to set parent id of current model
                            updateAttribute(association.getParentIdField(), otherModel.getId());
                            updateInDatabase();
                            break;
                        }
                        case ManyToMany: {
                            try {
                                // need to add to join table
                                Connection conn = Database.getConn();
                                PreparedStatement ps = null;
                                ps = conn.prepareStatement("insert into "+association.getJoinTableName() + " ("+association.getParentIdField()+","+association.getChildIdField()+") values (?,?) on conflict do nothing");
                                ps.setInt(1, id);
                                ps.setInt(2, otherModel.getId());
                                ps.executeUpdate();
                                ps.close();
                            } catch(Exception e) {
                                e.printStackTrace();
                            }
                            break;
                        }
                        case OneToOne: {
                            // NOT IMPLEMENTED
                            break;
                        }
                    }
                break;
            }
        }
    }

    private static String capitalize(String in) {
        return in.substring(0, 1).toUpperCase() + in.substring(1);
    }

    public void loadShowTemplate(boolean back) {
        ContainerTag button;
        if(back) {
            String previousTarget = "#"+tableName+"_index_btn";
            button = button("Back to "+capitalize(tableName)).attr("data-target", previousTarget)
                    .withClass("btn btn-outline-secondary btn-sm back-button");
        } else {
            button = span();
        }
        ContainerTag html = div().withClass("row").with(
                div().withClass("col-12").with(
                        button,
                        h4(this.getClass().getSimpleName()+" Information")
                ).with(
                    availableAttributes.stream().filter(attr->!Constants.isHiddenAttr(attr)).map(attr->{
                        Object val = data.get(attr);
                        String orginalAttr = attr;
                        boolean editable = !Arrays.asList(Constants.CREATED_AT, Constants.UPDATED_AT).contains(attr);
                        attr = Constants.humanAttrFor(attr);
                        if(val==null || val.toString().trim().length()==0) val = "(empty)";
                        return div().with(
                                div().attr("data-attr", orginalAttr)
                                        .attr("data-attrname", attr)
                                        .attr("data-val", val.toString())
                                        .attr("data-id", id.toString())
                                        .attr("data-resource", this.getClass().getSimpleName())
                                        .attr("data-field-type", Constants.fieldTypeForAttr(orginalAttr))
                                        .withClass("resource-data-field" + (editable ? " editable" : ""))
                                        .withText(attr+": "+val.toString())
                        );
                    }).collect(Collectors.toList())
                )
        ).with(
                div().withClass("col-12").with(
                        button("Diagram this "+this.getClass().getSimpleName())
                                .attr("data-id", id.toString())
                                .attr("data-resource", this.getClass().getSimpleName())
                                .withClass("btn btn-outline-secondary btn-sm diagram-button")
                ),
                div().withClass("col-12").with(
                        h5("Associations"),
                        div().with(
                                ul().withClass("nav nav-tabs").attr("role", "tablist").with(
                                        associationsMeta.stream().map(association-> {
                                            String assocId = "tab-link-"+association.getAssociationName().toLowerCase().replace(" ","-");
                                            return li().withClass("nav-item").with(
                                                    a(association.getAssociationName()).withClass("nav-link").attr("data-toggle", "tab").withHref("#" + assocId).attr("role", "tab")
                                            );
                                        }).collect(Collectors.toList())
                                )
                        ),
                        div().withClass("row tab-content").withId("main-container").with(
                                associationsMeta.stream().map(association->{
                                    String assocId = "tab-link-"+association.getAssociationName().toLowerCase().replace(" ","-");
                                    List<Model> models = associations.get(association);
                                    if(models==null) {
                                        models = Collections.emptyList();
                                    }
                                    String listRef = "association-"+association.getAssociationName().toLowerCase().replace(" ","-");
                                    Association.Type type = association.getType();
                                    String prepend = "false";
                                    String createText = "(New)";
                                    switch(type) {
                                        case OneToMany: {
                                            prepend = "prepend";
                                            break;
                                        }
                                        case ManyToOne: {
                                            prepend = "false";
                                            createText = "(Update)";
                                            break;
                                        }
                                        case ManyToMany: {
                                            prepend = "prepend";
                                            break;
                                        }
                                        case OneToOne: {
                                            // not implemented
                                            break;
                                        }
                                    }
                                    ContainerTag panel = div().with(a(createText).withHref("#").withClass("resource-new-link"),div().attr("style", "display: none;").with(
                                            form().attr("data-prepend",prepend).attr("data-list-ref","#"+listRef).attr("data-association", association.getModel().toString())
                                                    .attr("data-resource", this.getClass().getSimpleName())
                                                    .attr("data-id", id.toString()).withClass("association").with(
                                                    input().withType("hidden").withName("_association_name").withValue(association.getAssociationName()),
                                                    label("Name:").with(
                                                            input().withType("text").withClass("form-control").withName(Constants.NAME)
                                                    ), br(), button("Create").withClass("btn btn-outline-secondary btn-sm").withType("submit")
                                            ),form().attr("data-association-name-reverse", association.getReverseAssociationName()).attr("data-prepend",prepend).attr("data-list-ref","#"+listRef).attr("data-id", id.toString()).withClass("update-association").attr("data-association", association.getModel().toString())
                                                    .attr("data-resource", this.getClass().getSimpleName())
                                                    .with(
                                                            input().withType("hidden").withName("_association_name").withValue(association.getAssociationName()),
                                                            label(association.getAssociationName()+" Name:").with(
                                                                    select().attr("style","width: 100%").withClass("form-control multiselect-ajax").withName("id")
                                                                    .attr("data-url", "/ajax/resources/"+association.getModel()+"/"+this.getClass().getSimpleName()+"/"+id)
                                                            ), br(), button("Assign").withClass("btn btn-outline-secondary btn-sm").withType("submit")

                                                    )
                                    ));
                                    return div().attr("role", "tabpanel").withId(assocId).withClass("col-12 tab-pane fade").with(
                                            panel, br(),
                                            div().withId(listRef).with(models.stream().map(model->{
                                                return model.getLink(association.getReverseAssociationName(), this.getClass().getSimpleName(), id);
                                            }).collect(Collectors.toList()))
                                    );
                                }).collect(Collectors.toList())
                        )
                )
        );
        template = html.render();
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
                    List<Model> children = Database.loadManyToManyAssociation(association.getModel(), this, association.getJoinTableName(), association.getParentIdField(), association.getChildIdField());
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
            Map<String,Object> dataCopy = new HashMap<>(data);
            List<String> keys = new ArrayList<>(availableAttributes);
            keys.remove(Constants.UPDATED_AT);
            keys.remove(Constants.CREATED_AT);
            Database.update(tableName, id, dataCopy, keys);
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
        loadAssociations();
        for(Map.Entry<Association,List<Model>> entry : associations.entrySet()) {
            for(Model association : entry.getValue()) {
                if(cascade && entry.getKey().isDependent()) {
                    association.deleteFromDatabase(true);
                }
                cleanUpParentIds(entry.getKey(), association.getId());
            }
        }
        try {
            Database.delete(tableName, id);
        } catch(Exception e) {
            e.printStackTrace();
            throw new RuntimeException("Error deleting record from database: "+e.getMessage());
        }
    }

    public void cleanUpParentIds(@NonNull Association association, int assocId) {
        // clean up join table if necessary
        if (association.getType().equals(Association.Type.ManyToMany)) {
            try {
                Database.deleteByFieldName(association.getJoinTableName(), association.getParentIdField(), id);
            } catch (Exception e) {
                e.printStackTrace();
                throw new RuntimeException("Error deleting record from database: " + e.getMessage());
            }
        } else if(Arrays.asList(Association.Type.OneToMany, Association.Type.ManyToOne).contains(association.getType())) {
            // child table has the key
            try {
                Integer idToUse;
                if(association.getType().equals(Association.Type.ManyToOne)) {
                    idToUse = assocId;
                } else {
                    idToUse = id;
                }
                Database.nullifyByFieldName(association.getChildTableName(), association.getParentIdField(), idToUse);
            } catch (Exception e) {
                e.printStackTrace();
                throw new RuntimeException("Error deleting record from database: " + e.getMessage());
            }
        }
    }
}
