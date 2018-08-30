package models;

import database.Database;
import j2html.tags.ContainerTag;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;

import java.io.Serializable;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
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
    private Set<String> allReferences;
    @Getter
    private final boolean isRevenueModel;
    protected Model(@NonNull List<Association> associationsMeta, @NonNull List<String> availableAttributes, @NonNull String tableName, Integer id, Map<String,Object> data, boolean isRevenueModel) {
        this.tableName = tableName;
        this.data = data;
        this.associationsMeta = associationsMeta;
        this.id = id;
        this.isRevenueModel = isRevenueModel;
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
        return div().withId("node-"+this.getClass().getSimpleName()+"-"+id).withClass("stop-delete-prop").with(
                getSimpleLink(),
                span("X").attr("data-association", associationModel)
                        .attr("data-association-name", associationName)
                        .attr("data-association-id", associationId.toString()).attr("style","cursor: pointer;").withClass("delete-node").attr("data-resource", this.getClass().getSimpleName()).attr("data-id", id)
        );
    }

    public ContainerTag getSimpleLink(@NonNull String... additionalClasses) {
        String name;
        if(isRevenueModel) {
            name = "(View)";
        } else {
            name = (String)data.get(Constants.NAME);
        }
        return a(name).attr("data-id", getId().toString()).attr("data-resource", this.getClass().getSimpleName()).attr("href", "#").withClass("resource-show-link "+String.join(" ", additionalClasses));
    }

    public ContainerTag getCreateNewForm() {
        if(this.getClass().getSimpleName().endsWith("Revenue")) {
            String applicableField;
            String associationResource = this.getClass().getSimpleName().replace("Revenue","");
            if(this.getClass().getSimpleName().startsWith("Market")) {
                applicableField = Constants.MARKET_ID;
            } else if(this.getClass().getSimpleName().startsWith("Product")) {
                applicableField = Constants.PRODUCT_ID;

            } else if(this.getClass().getSimpleName().startsWith("Company")) {
                applicableField = Constants.COMPANY_ID;
            } else {
                throw new RuntimeException("Unknown revenue type exception.");
            }
            return form().with(
                    label(associationResource).with(
                            br(),
                            select().withClass("multiselect-ajax")
                                    .withName(applicableField)
                                    .attr("data-url", "/ajax/resources/"+associationResource+"/"+this.getClass().getSimpleName()+"/-1")
                    ), br(),
                    label(Constants.humanAttrFor(Constants.YEAR)).with(
                            br(),
                            input().attr("value", String.valueOf(LocalDate.now().getYear())).withClass("form-control").withName(Constants.YEAR).withType("number")
                    ), br(),
                    label(Constants.humanAttrFor(Constants.VALUE)).with(
                            br(),
                            input().withClass("form-control").withName(Constants.VALUE).withType("number")
                    ), br(),
                    label(Constants.humanAttrFor(Constants.SOURCE)).with(
                            br(),
                            input().withClass("form-control").withName(Constants.SOURCE).withType("text")
                    ), br(),
                    label(Constants.humanAttrFor(Constants.IS_ESTIMATE)).with(
                            br(),
                            input().withName(Constants.IS_ESTIMATE).withType("checkbox").attr("value", "true")
                    ), br(),
                    label(Constants.humanAttrFor(Constants.IS_PERCENTAGE)).with(
                            br(),
                            input().withName(Constants.IS_PERCENTAGE).withType("checkbox").attr("value", "true")
                    ), br(),
                    label(Constants.humanAttrFor(Constants.ESTIMATE_TYPE)).with(
                            br(),
                            select().withClass("multiselect").withName(Constants.ESTIMATE_TYPE).with(
                                    option().attr("selected", "selected"),
                                    option("Low").withValue("0"),
                                    option("Medium").withValue("1"),
                                    option("High").withValue("2")
                            )
                    ), br(),
                    label(Constants.humanAttrFor(Constants.NOTES)).with(
                            br(),
                            textarea().withClass("form-control").withName(Constants.NOTES)
                    ), br(),
                    button("Create").withClass("btn btn-outline-secondary btn-sm").withType("submit")
            );
        } else {
            return form().with(
                    label(Constants.humanAttrFor(Constants.NAME)).with(
                            br(),
                            input().withClass("form-control").withName(Constants.NAME).withType("text")
                    ), br(), button("Create").withClass("btn btn-outline-secondary btn-sm").withType("submit")
            );
        }
    }

    public ContainerTag loadNestedAssociations() {
        if(data==null) {
            loadAttributesFromDatabase();
        }
        ContainerTag inner = ul();
        String revenueClass = "resource-revenue-"+this.getClass().getSimpleName()+id;
        ContainerTag tag = ul().attr("style", "float: left !important; text-align: left !important;").with(
                li().withClass("stop-delete-prop").with(h5(getSimpleLink()).attr("style", "display: inline;"),getRevenueAsSpan(revenueClass)).attr("style", "list-style: none;").with(
                        br(),inner
                )
        );
        this.allReferences = new HashSet<>(Collections.singleton(this.getClass().getSimpleName()+id));
        loadNestedAssociationHelper(inner, allReferences, new AtomicInteger(0), this);
        return tag;
    };

    private ContainerTag getRevenueAsSpan(String updateClass) {
        /*Object revenueStr = getData().getOrDefault(Constants.REVENUE, "");
        if(revenueStr==null) revenueStr = "";
        revenueStr = "Revenue: "+revenueStr;
        String fullId = "resource-revenue-"+getClass().getSimpleName()+getId();
        return span(revenueStr.toString())
                .attr("data-field-type", Constants.NUMBER_FIELD_TYPE)
                .attr("data-resource", this.getClass().getSimpleName())
                .attr("data-val", this.getData().get(Constants.REVENUE))
                .attr("data-attr", Constants.REVENUE)
                .attr("data-attrname", Constants.humanAttrFor(Constants.REVENUE))
                .attr("data-id", id.toString())
                .attr("data-update-class", updateClass)
                .withClass("resource-data-field editable "+fullId).attr("style","margin-left: 10px; display: inline;");
                */
        return span("Revenue: ");
    }

    private void loadNestedAssociationHelper(ContainerTag container, Set<String> alreadySeen, AtomicInteger cnt, Model original) {
        if(associations==null) {
            loadAssociations();
        }
        String originalId = original.getClass().getSimpleName()+original.getId();
        Map<Association,List<Model>> modelMap = new HashMap<>();
        double totalRevenueOfLevel = 0;
        for(Association association : associationsMeta) {
            List<Model> assocModels = associations.getOrDefault(association, Collections.emptyList());
            modelMap.put(association, assocModels);
        }
        final double _totalRevenue = totalRevenueOfLevel;
        if(modelMap.size()>0) {
            // recurse
            modelMap.forEach((association, models) -> {
                boolean pluralize = Arrays.asList(Association.Type.OneToMany, Association.Type.ManyToMany).contains(association.getType());
                ContainerTag tag =  ul().with(
                        li().attr("style", "list-style: none;").with(
                                h6(pluralize?Constants.pluralizeAssociationName(association.getAssociationName()):association.getAssociationName()).attr("style", "cursor: pointer; display: inline;")
                                .attr("onclick", "$(this).parent().nextAll().slideToggle();"),
                                span("Revenue: "+_totalRevenue).withClass("association-revenue-totals").attr("style", "margin-left: 10px; display: inline;")
                        )
                );
                for(Model model : models) {
                    String _id = model.getClass().getSimpleName() + model.getId();
                    boolean sameModel = _id.equals(originalId);
                    ContainerTag inner = ul();
                    String revenueClass = "resource-revenue-"+_id;
                    tag.with(li().attr("style", "display: none;").with(model.getLink(association.getReverseAssociationName(), this.getClass().getSimpleName(), id), model.getRevenueAsSpan(revenueClass), inner));
                    if(!sameModel && !alreadySeen.contains(_id)) {
                        alreadySeen.add(_id);
                        model.loadNestedAssociationHelper(inner, new HashSet<>(alreadySeen), cnt, original);
                    }
                    alreadySeen.add(_id);
                }
                String listRef = "association-"+association.getAssociationName().toLowerCase().replace(" ","-")+cnt.getAndIncrement();
                container.with(tag.with(li().attr("style", "display: none;").with(getAddAssociationPanel(association, listRef, original))));
            });
        }
    }

    public void removeManyToOneAssociations(String associationName) {
        for(Association association : associationsMeta) {
            if(association.getAssociationName().equals(associationName)) {
                switch (association.getType()) {
                    case ManyToOne: {
                        // need to set parent id of current model
                        updateAttribute(association.getParentIdField(), null);
                        updateInDatabase();
                        break;
                    }
                }
                break;
            }
        }
    }

    public void associateWith(Model otherModel, String associationName) {
        // find association
        for(Association association : associationsMeta) {
            if(association.getAssociationName().equals(associationName)) {
                // make sure we haven't introduced in cycles
                if(association.getModel().toString().equals(this.getClass().getSimpleName())) {
                    System.out.println("Checking for cycles...");
                    loadNestedAssociations(); // hack to access allReferences
                    String otherRef = otherModel.getClass().getSimpleName() + otherModel.getId();
                    if (this.allReferences.contains(otherRef)) {
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

    private ContainerTag getAddAssociationPanel(Association association, String listRef, Model diagramModel) {
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
                if(getData().get(association.getParentIdField())==null) {
                    createText = "(Set)";
                }
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
                        .attr("data-refresh",diagramModel!=null ? "refresh" : "f")
                        .attr("data-original-id",diagramModel!=null ? diagramModel.id.toString() : "f")
                        .attr("data-original-resource",diagramModel!=null ? diagramModel.getClass().getSimpleName() : "f")
                        .attr("data-id", id.toString()).withClass("association").with(
                        input().withType("hidden").withName("_association_name").withValue(association.getAssociationName()),
                        label("Name:").with(
                                input().withType("text").withClass("form-control").withName(Constants.NAME)
                        ), br(), button("Create").withClass("btn btn-outline-secondary btn-sm").withType("submit")
                ),form().attr("data-association-name-reverse", association.getReverseAssociationName()).attr("data-prepend",prepend).attr("data-list-ref","#"+listRef).attr("data-id", id.toString()).withClass("update-association").attr("data-association", association.getModel().toString())
                        .attr("data-resource", this.getClass().getSimpleName())
                        .attr("data-refresh",diagramModel!=null ? "refresh" : "f")
                        .attr("data-original-id",diagramModel!=null ? diagramModel.id.toString() : "f")
                        .attr("data-original-resource",diagramModel!=null ? diagramModel.getClass().getSimpleName() : "f")
                        .with(
                                input().withType("hidden").withName("_association_name").withValue(association.getAssociationName()),
                                label(association.getAssociationName()+" Name:").with(
                                        select().attr("style","width: 100%").withClass("form-control multiselect-ajax").withName("id")
                                                .attr("data-url", "/ajax/resources/"+association.getModel()+"/"+this.getClass().getSimpleName()+"/"+id)
                                ), br(), button("Assign").withClass("btn btn-outline-secondary btn-sm").withType("submit")

                        )
        ));
        return panel;
    }

    public void loadShowTemplate(boolean back) {
        ContainerTag button;
        if(back) {
            String previousTarget = "#"+tableName+"_index_btn";
            button = button("Back to "+Constants.pluralizeAssociationName(Constants.humanAttrFor(this.getClass().getSimpleName()))).attr("data-target", previousTarget)
                    .withClass("btn btn-outline-secondary btn-sm back-button");
        } else {
            button = span();
        }
        ContainerTag html = div().withClass("col-12").with(
                div().withClass("col-12").with(
                        button,
                        h4(Constants.humanAttrFor(this.getClass().getSimpleName())+" Information"),
                        button("Delete this "+Constants.humanAttrFor(this.getClass().getSimpleName())).withClass("btn btn-outline-danger btn-sm delete-button")
                        .attr("data-id", id.toString())
                        .attr("data-resource", this.getClass().getSimpleName())
                        .attr("data-resource-name", Constants.humanAttrFor(this.getClass().getSimpleName()))
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
                                        .attr("data-field-type", orginalAttr.equals(Constants.ESTIMATE_TYPE)?Constants.ESTIMATE_TYPE:Constants.fieldTypeForAttr(orginalAttr))
                                        .withClass("resource-data-field" + (editable ? " editable" : ""))
                                        .withText(attr+": "+val.toString())
                        );
                    }).collect(Collectors.toList())
                )
        ).with(
                div().withClass("col-12").with(
                        button("Diagram this "+Constants.humanAttrFor(this.getClass().getSimpleName()))
                                .attr("data-id", id.toString())
                                .attr("data-resource", this.getClass().getSimpleName())
                                .withClass("btn btn-outline-secondary btn-sm diagram-button")
                ),
                div().withClass("col-12").with(
                        h5("Associations"),
                        div().with(
                                ul().withClass("nav nav-tabs").attr("role", "tablist").with(
                                        associationsMeta.stream().map(association-> {
                                            boolean pluralize = Arrays.asList(Association.Type.OneToMany, Association.Type.ManyToMany).contains(association.getType());
                                            String assocId = "tab-link-"+association.getAssociationName().toLowerCase().replace(" ","-");
                                            return li().withClass("nav-item").with(
                                                    a(pluralize?Constants.pluralizeAssociationName(association.getAssociationName()):association.getAssociationName()).withClass("nav-link").attr("data-toggle", "tab").withHref("#" + assocId).attr("role", "tab")
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
                                    ContainerTag panel = getAddAssociationPanel(association, listRef, null);
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
