package database;

import lombok.Getter;
import lombok.NonNull;
import models.*;

import java.sql.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public class Database {
    private static final String dbUrl = "jdbc:postgresql://localhost/companydb?user=postgres&password=password&tcpKeepAlive=true";

    @Getter
    private static Connection conn;
    static {
        resetConn();
    }

    public static synchronized void resetConn() {
        try {
            conn = DriverManager.getConnection(dbUrl);
            conn.setAutoCommit(true);
        } catch(Exception e) {
            e.printStackTrace();
        }
    }

    public static synchronized Integer insert(@NonNull String tableName, Map<String,Object> data) throws SQLException {
        data = new HashMap<>(data);
        List<String> keys = new ArrayList<>(data.keySet());
        List<String> qs = new ArrayList<>(keys.size());
        for(int i = 0; i < keys.size(); i++) {
            if(data.get(keys.get(i)) != null && data.get(keys.get(i)) instanceof LocalDate) {
                qs.add("?::date");
                data.put(keys.get(i), ((LocalDate) data.get(keys.get(i))).format(DateTimeFormatter.ISO_DATE));
            } else {
                if(keys.get(i).equals(Constants.VALUE) || keys.get(i).equals(Constants.CAGR)) {
                    qs.add("?::double precision");

                } else if (Constants.fieldTypeForAttr(keys.get(i)).equals(Constants.NUMBER_FIELD_TYPE)) {
                    qs.add("?::integer");

                } else if (Constants.fieldTypeForAttr(keys.get(i)).equals(Constants.BOOL_FIELD_TYPE)) {
                    qs.add("?::boolean");

                } else {
                    qs.add("?");
                }
            }
        }
        PreparedStatement ps = conn.prepareStatement("insert into "+tableName+" ("+String.join(",",keys)+") values ("+ String.join(",",qs) + ") returning id");
        for(int i = 0; i < keys.size(); i++) {
            ps.setObject(i+1, data.get(keys.get(i)));
        }
        ps.execute();
        ResultSet rs = ps.getResultSet();
        Integer id = null;
        if(rs.next()) {
            id = rs.getInt(1);
        }
        rs.close();
        ps.close();
        return id;
    }


    public static synchronized List<Model> loadOneToManyAssociation(@NonNull Association.Model associationType, @NonNull Model baseModel, @NonNull String associationTableName, @NonNull String parentIdField) throws SQLException {
        if(!baseModel.existsInDatabase()) {
            System.out.println("Trying to load association of model that does not exist in the database.");
        }
        PreparedStatement ps = conn.prepareStatement("select id from "+associationTableName+" where "+parentIdField+"=?");
        ps.setObject(1, baseModel.getId());
        ResultSet rs = ps.executeQuery();
        List<Model> models = new ArrayList<>();
        while(rs.next()) {
            final int id = rs.getInt(1);
            switch(associationType) {
                case Company: {
                    models.add(new Company(id, null));
                    break;
                }
                case Market: {
                    models.add(new Market(id, null));
                    break;
                }
                case Product: {
                    models.add(new Product(id, null));
                    break;
                }
                case ProductRevenue: {
                    models.add(new ProductRevenue(id, null));
                    break;
                }
                case CompanyRevenue: {
                    models.add(new CompanyRevenue(id, null));
                    break;
                }
                case MarketRevenue: {
                    models.add(new MarketRevenue(id, null));
                    break;
                }
                case MarketShareRevenue: {
                    models.add(new MarketShareRevenue(id, null));
                    break;
                }
                case Region: {
                    models.add(new Region(id, null));
                    break;
                }
            }
        }
        rs.close();
        ps.close();
        return models;
    }

    public static synchronized Map<Model, Map<String,Object>> loadManyToManyAssociation(@NonNull Association.Model associationType, @NonNull Model baseModel, @NonNull String joinTableName, @NonNull String parentIdField, @NonNull String childIdField, List<String> joinAttributes) throws SQLException {
        if(!baseModel.existsInDatabase()) {
            System.out.println("Trying to load association of model that does not exist in the database.");
        }
        String select = joinAttributes==null||joinAttributes.isEmpty() ? childIdField : (childIdField+","+String.join(",",joinAttributes));
        PreparedStatement ps = conn.prepareStatement("select "+select+" from "+joinTableName+" where "+parentIdField+"=?");
        ps.setObject(1, baseModel.getId());
        ResultSet rs = ps.executeQuery();
        Map<Model, Map<String,Object>> models = new HashMap<>();
        while(rs.next()) {
            final int id = rs.getInt(1);
            Map<String,Object> joinData = new HashMap<>();
            if(joinAttributes!=null) {
                for(int i = 0; i < joinAttributes.size(); i++) {
                    String attr = joinAttributes.get(i);
                    joinData.put(attr, rs.getObject(2+i));
                }
            }
            switch(associationType) {
                case Company: {
                    models.put(new Company(id, null), joinData);
                    break;
                }
                case Market: {
                    models.put(new Market(id, null), joinData);
                    break;
                }
                case Product: {
                    models.put(new Product(id, null), joinData);
                    break;
                }
                case CompanyRevenue: {
                    models.put(new CompanyRevenue(id, null), joinData);
                    break;
                }
                case MarketRevenue: {
                    models.put(new MarketRevenue(id, null), joinData);
                    break;
                }
                case ProductRevenue: {
                    models.put(new ProductRevenue(id, null), joinData);
                    break;
                }
                case MarketShareRevenue: {
                    models.put(new MarketShareRevenue(id, null), joinData);
                    break;
                }
                case Region: {
                    models.put(new Region(id, null), joinData);
                    break;
                }
            }
        }
        rs.close();
        ps.close();
        return models;
    }

    public static synchronized Model loadManyToOneAssociation(@NonNull Association.Model associationType, @NonNull Model baseModel, @NonNull String associationTableName, @NonNull String parentIdField) throws SQLException {
        if(!baseModel.existsInDatabase()) {
            System.out.println("Trying to load association of model that does not exist in the database.");
        }
        Integer parentId = (Integer)baseModel.getData().get(parentIdField);
        if(parentId==null) {
            return null;
        }
        Model model = null;
        switch(associationType) {
            case Company: {
                model = new Company(parentId, null);
                break;
            }
            case Market: {
                model = new Market(parentId, null);
                break;
            }
            case Product: {
                model = new Product(parentId, null);
                break;
            }
            case CompanyRevenue: {
                model = new CompanyRevenue(parentId, null);
                break;
            }
            case MarketRevenue: {
                model = new MarketRevenue(parentId, null);
                break;
            }
            case ProductRevenue: {
                model = new ProductRevenue(parentId, null);
                break;
            }
            case MarketShareRevenue: {
                model = new MarketShareRevenue(parentId, null);
                break;
            }
            case Region: {
                model = new Region(parentId, null);
                break;
            }
        }
        return model;
    }

    public static synchronized void update(@NonNull String tableName, int id, Map<String,Object> data, @NonNull List<String> keys) throws SQLException {
        data = new HashMap<>(data);
        List<String> qs = new ArrayList<>(keys.size());
        for(int i = 0; i < keys.size(); i++) {
            if(data.get(keys.get(i)) != null && data.get(keys.get(i)) instanceof LocalDate) {
                qs.add("?::date");
                data.put(keys.get(i), ((LocalDate) data.get(keys.get(i))).format(DateTimeFormatter.ISO_DATE));
            } else {
                qs.add("?");
            }
        }
        PreparedStatement ps = conn.prepareStatement("update "+tableName+" set ("+String.join(",",keys)+",updated_at) = ("+ String.join(",",qs) + ",now()) where id=?");
        for(int i = 0; i < keys.size(); i++) {
            ps.setObject(i+1, data.get(keys.get(i)));
        }
        ps.setObject(keys.size()+1, id);
        ps.executeUpdate();
        ps.close();
    }

    public static synchronized void delete(@NonNull String tableName, int id) throws SQLException {
        deleteByFieldName(tableName, "id", id);
    }

    public static synchronized void deleteByFieldName(@NonNull String tableName, @NonNull String fieldName, int id) throws SQLException {
        PreparedStatement ps = conn.prepareStatement("delete from "+tableName+" where "+fieldName+"=?");
        ps.setObject(1, id);
        ps.executeUpdate();
        ps.close();
    }

    public static synchronized void nullifyFieldName(@NonNull String tableName, @NonNull String fieldName, int id) throws SQLException {
        PreparedStatement ps = conn.prepareStatement("update "+tableName+" set "+fieldName+"=null where id = ?");
        ps.setObject(1, id);
        ps.executeUpdate();
        ps.close();
    }

    public static synchronized Map<String,Object> select(@NonNull String tableName, int id, @NonNull Collection<String> attributes) throws SQLException {
        List<String> attrList = new ArrayList<>(new HashSet<>(attributes));
        PreparedStatement ps = conn.prepareStatement("select "+String.join(",", attrList)+" from "+tableName+" where id=?");
        ps.setObject(1, id);
        ResultSet rs = ps.executeQuery();
        Map<String,Object> data = new HashMap<>(attributes.size());
        if(rs.next()) {
            for(int i = 0; i < attrList.size(); i++) {
                data.put(attrList.get(i), rs.getObject(i+1));
            }
        }
        rs.close();
        ps.close();
        return data;
    }

    public static synchronized Integer findIdByName(@NonNull String tableName, @NonNull String name) throws SQLException {
        PreparedStatement ps = conn.prepareStatement("select id from "+tableName+" where name=? limit 1");
        ps.setObject(1, name);
        ResultSet rs = ps.executeQuery();
        Integer id = null;
        if(rs.next()) {
            id = rs.getInt(1);
        }
        rs.close();
        ps.close();
        return id;
    }

    public static synchronized List<Model> selectAll(boolean isRevenueModel, @NonNull Association.Model model, @NonNull String tableName, @NonNull Collection<String> attributes) throws SQLException {
        return selectAll(isRevenueModel, model, tableName, attributes, null, (Association)null);
    }

    public static synchronized List<Model> selectAll(boolean isRevenueModel, @NonNull Association.Model model, @NonNull String tableName, @NonNull Collection<String> attributes, Association parentAssociation) throws SQLException {
        return selectAll(isRevenueModel, model, tableName, attributes, null, parentAssociation);
    }

    public static synchronized List<Model> selectAll(boolean isRevenueModel, @NonNull Association.Model model, @NonNull String tableName, @NonNull Collection<String> attributes, String searchName, Association parentAssociation) throws SQLException {
        List<String> attrList = new ArrayList<>(new HashSet<>(attributes));
        if(isRevenueModel) {
            attrList.remove(Constants.NAME);
        }
        PreparedStatement ps;
        if(isRevenueModel) {
            if(model.equals(Association.Model.MarketShareRevenue)) {
                String attrStr = String.join(",", attrList.stream().map(a -> "r." + a).collect(Collectors.toList()));
                if(attrList.size()>0) {
                    attrStr = ","+attrStr;
                }
                String sqlStr;
                if(parentAssociation==null) {
                    sqlStr = "select r.id as id,c.name||'''s share of ' || m.name||' market ('||r.year::text||')' as name" + attrStr + " from " + tableName + " as r left join companies as c on (c.id=r.company_id) left join markets as m on (r.market_id=m.id) " + (searchName == null ? "" : (" where (lower(m.name) || lower(c.name)) like '%'||?||'%' ")) + " order by c.name";
                } else {
                    sqlStr = "select r.id as id,c.name||'''s share of ' || m.name||' market ('||r.year::text||')' as name" + attrStr + ",p.id as parent_id, \'Parent Revenue\' as parent_name from " + tableName + " as r left join companies as c on (c.id=r.company_id) left join markets as m on (r.market_id=m.id) left join "+parentAssociation.getParentTableName()+" as p on (r."+parentAssociation.getParentIdField()+"=p.id) " + (searchName == null ? "" : (" where (lower(m.name) || lower(c.name)) like '%'||?||'%' ")) + " order by c.name";
                }
                System.out.println("Market share sql str: "+sqlStr);
                ps = conn.prepareStatement(sqlStr);

            } else {
                String parentTableName;
                String parentIdField;
                parentTableName = tableName.replace("_revenue", "");
                if (parentTableName.equals("companys")) {
                    parentTableName = "companies"; // handle weird english language
                }
                parentIdField = model.toString().replace("Revenue", "").toLowerCase() + "_id";
                String attrStr = String.join(",", attrList.stream().map(a -> "r." + a).collect(Collectors.toList()));
                if(attrList.size()>0) {
                    attrStr = ","+attrStr;
                }
                String sqlStr;
                if(parentAssociation==null) {
                    sqlStr ="select r.id as id,j.name||' Revenue ('||r.year::text||')' as name" + attrStr + " from " + tableName + " as r left join " + parentTableName + " as j on (r." + parentIdField + "=j.id) " + (searchName == null ? "" : (" where lower(j.name) like '%'||?||'%' ")) + " order by lower(j.name)";
                } else {
                    sqlStr ="select r.id as id,j.name||' Revenue ('||r.year::text||')' as name" + attrStr + ",p.id as parent_id, \'Parent Revenue\' as parent_name from " + tableName + " as r left join " + parentTableName + " as j on (r." + parentIdField + "=j.id) left join "+parentAssociation.getParentTableName()+" as p on (r."+parentAssociation.getParentIdField()+"=p.id) " + (searchName == null ? "" : (" where lower(j.name) like '%'||?||'%' ")) + " order by lower(j.name)";
                }
                ps = conn.prepareStatement(sqlStr);
            }

        } else {
            if (parentAssociation != null) {
                ps = conn.prepareStatement("select c.id as id," + String.join(",",  attrList.stream().map(a -> "c." + a).collect(Collectors.toList())) + ",p.id as parent_id, p.name as parent_name from " + tableName + " as c left join "+parentAssociation.getParentTableName()+" as p on (c."+parentAssociation.getParentIdField()+"=p.id) " + (searchName == null ? "" : (" where lower(c.name) like '%'||?||'%' ")) + " order by lower(c.name)");

            } else {
                ps = conn.prepareStatement("select id," + String.join(",", attrList) + " from " + tableName + "" + (searchName == null ? "" : (" where lower(name) like '%'||?||'%' ")) + " order by lower(name)");
            }
        }
        if(searchName!=null) {
            ps.setString(1, searchName);
        }
        ResultSet rs = ps.executeQuery();
        List<Model> models = new ArrayList<>();
        if(isRevenueModel) {
            attrList.add(0, Constants.NAME);
        }
        while(rs.next()) {
            Map<String, Object> data = new HashMap<>();
            int id = rs.getInt(1);
            for (int i = 0; i < attrList.size(); i++) {
                data.put(attrList.get(i), rs.getObject(i + 2));
            }
            Model m = buildModelFromDataAndType(id, data, model);
            Map<Association, List<Model>> associationsMap = new HashMap<>();
            if(parentAssociation!=null) {
                // add association
                Association modelsAssociation = m.getAssociationsMeta().stream().filter(a->a.getAssociationName().equals(parentAssociation.getAssociationName())).findAny().orElse(null);
                if(modelsAssociation!=null) {
                    List<Model> parentList = new ArrayList<>();
                    Integer parentId = (Integer)rs.getObject(attrList.size()+2);
                    if(parentId!=null) {
                        String parentName = (String) rs.getObject(attrList.size() + 3);
                        Map<String, Object> parentData = new HashMap<>();
                        parentData.put(Constants.NAME, parentName);
                        Model parent = buildModelFromDataAndType(parentId, parentData, model);
                        if(parent!=null) {
                            parentList.add(parent);
                        }
                    }
                    associationsMap.put(modelsAssociation, parentList);
                }
                m.setAssociations(associationsMap);
            }
            models.add(m);

        }
        rs.close();
        ps.close();
        return models;
    }

    public static synchronized List<Model> selectAll(boolean isRevenueModel, @NonNull Association.Model model, @NonNull String tableName, @NonNull Collection<String> attributes, List<Association> associations, Integer findId) throws SQLException {
        List<String> attrList = new ArrayList<>(new HashSet<>(attributes));
        if(isRevenueModel) {
            attrList.remove(Constants.NAME);
        }
        PreparedStatement ps;
        List<String> joinAttrStrs = new ArrayList<>();
        List<String> allJoins = new ArrayList<>();
        Map<String, Association> prefixToAssocMap = new HashMap<>();
        Set<String> groups = new HashSet<>();
        boolean useGroups = false;
        if(associations!=null) {
            // add other attr strs
            for(int i = 0; i < associations.size(); i++) {
                Association association = associations.get(i);
                Model m = buildModelFromDataAndType(null, null, association.getModel());
                if (m != null) {
                    String j = "j"+i;
                    String joinStr;
                    List<String> assocAttrList = new ArrayList<>(m.getAvailableAttributes());
                    if(m.isRevenueModel()) {
                        assocAttrList.remove(Constants.NAME);
                    }
                    if(j.equals("j0")) {
                        System.out.println("Attr list: "+assocAttrList);
                    }
                    if(association.getType().equals(Association.Type.ManyToOne)) {
                        allJoins.add("left join "+association.getParentTableName()+ " as "+j+" on ("+j+".id=r."+association.getParentIdField()+")");
                        joinStr = j+".id,"+String.join(",", assocAttrList.stream().map(a -> j + "." + a).collect(Collectors.toList()));
                        groups.add(j+".id");
                    } else if (association.getType().equals(Association.Type.OneToMany)) {
                        allJoins.add("left join "+association.getChildTableName()+ " as "+j+" on ("+j+"."+association.getParentIdField()+"=r.id)");
                        joinStr = "array_agg("+j+".id),"+String.join(",", assocAttrList.stream().map(a -> "array_agg("+ j + "." + a+")").collect(Collectors.toList()));
                        groups.add("r.id");
                        useGroups = true;
                    } else {
                        throw new RuntimeException("Unsupported join type: "+association.getType());
                    }
                    joinAttrStrs.add(joinStr);
                    prefixToAssocMap.put(j, association);
                }
            }
        }
        String groupBy = "";
        if(useGroups && groups.size() > 0) {
           groupBy = " group by "+String.join(",", groups);
        }
        String where = "";
        if(findId!=null) {
            where = " where r.id = ? ";
        }
        String attrStr = String.join(",", attrList.stream().map(a -> "r." + a).collect(Collectors.toList()));
        if(attrList.size()>0) {
            attrStr = ","+attrStr;
        }
        if(isRevenueModel) {
            if(model.equals(Association.Model.MarketShareRevenue)) {
                String sqlStr;
                if (associations != null && allJoins.size()>0 && joinAttrStrs.size()>0) {
                    sqlStr = "select r.id as id,'Market Share' as name " + attrStr + ","+String.join(",", joinAttrStrs)+" from " + tableName + " as r " + String.join(" ", allJoins) + where + " " + groupBy;
                } else {
                    sqlStr = "select id,'Market Share' as name " + attrStr + " from " + tableName + where;
                }
                ps = conn.prepareStatement(sqlStr);

            } else {
                String sqlStr;
                if (associations != null && allJoins.size()>0 && joinAttrStrs.size()>0) {
                    sqlStr ="select r.id as id,'Revenue' as name " + attrStr + ","+String.join(",", joinAttrStrs)+" from " + tableName + " as r " + String.join(" ", allJoins) + where + " " + groupBy;
                } else {
                    sqlStr ="select id,'Revenue' as name " + attrStr + " from " + tableName + where;
                }
                ps = conn.prepareStatement(sqlStr);
            }

        } else {
            if (associations != null && allJoins.size()>0 && joinAttrStrs.size()>0) {
                ps = conn.prepareStatement("select r.id as id" + attrStr + "," + String.join(",",joinAttrStrs) + " from " + tableName + " as r "+ String.join(" ", allJoins)+ where + " " + groupBy);

            } else {
                ps = conn.prepareStatement("select id" + attrStr + " from " + tableName + where);
            }
        }
        if(findId!=null) {
            ps.setInt(1, findId);
        }
        System.out.println("QUERY: "+ps.toString());
        ResultSet rs = ps.executeQuery();
        List<Model> models = new ArrayList<>();
        if(isRevenueModel) {
            attrList.add(0, Constants.NAME);
        }
        while(rs.next()) {
            AtomicInteger queryIdx = new AtomicInteger(1);
            Map<String, Object> data = new HashMap<>();
            int id = rs.getInt(queryIdx.getAndIncrement());
            for (int i = 0; i < attrList.size(); i++) {
                data.put(attrList.get(i), rs.getObject(queryIdx.getAndIncrement()));
            }
            Model m = buildModelFromDataAndType(id, data, model);
            Map<Association, List<Model>> associationsMap = new HashMap<>();
            if (associations != null) {
                if(allJoins.size()>0 && joinAttrStrs.size()>0) {
                    List<String> prefixes = new ArrayList<>(prefixToAssocMap.keySet());
                    prefixes.sort(Comparator.naturalOrder());
                    for (String prefix : prefixes) {
                        Association association = prefixToAssocMap.get(prefix);
                        // add association
                        Association modelsAssociation = m.getAssociationsMeta().stream().filter(a -> a.getAssociationName().equals(association.getAssociationName())).findAny().orElse(null);
                        if (modelsAssociation != null) {
                            Model assoc = buildModelFromDataAndType(null, null, modelsAssociation.getModel());
                            List<Model> assocList = new ArrayList<>();
                            if (assoc != null) {
                                if(modelsAssociation.getType().equals(Association.Type.ManyToOne)) {
                                    Integer assocId = (Integer) rs.getObject(queryIdx.getAndIncrement());
                                    if (assocId != null) {
                                        Map<String, Object> assocData = new HashMap<>();
                                        for (String attr : assoc.getAvailableAttributes()) {
                                            assocData.put(attr, rs.getObject(queryIdx.getAndIncrement()));
                                        }
                                        assoc = buildModelFromDataAndType(assocId, assocData, association.getModel());
                                        if (assoc != null) {
                                            assocList.add(assoc);
                                        }
                                    }
                                } else if(modelsAssociation.getType().equals(Association.Type.OneToMany)) {
                                    Array assocIdsArr = rs.getArray(queryIdx.getAndIncrement());
                                    if (assocIdsArr != null) {
                                        Integer[] assocIds = (Integer[])assocIdsArr.getArray();
                                        List<Map<String,Object>> dataMaps = new ArrayList<>(assocIds.length);
                                        for(int i = 0; i < assocIds.length; i++) {
                                            dataMaps.add(new HashMap<>());
                                        }
                                        for (String attr : assoc.getAvailableAttributes()) {
                                            Object[] assocFieldArr = (Object[])rs.getArray(queryIdx.getAndIncrement()).getArray();
                                            for(int i = 0; i < assocFieldArr.length; i++) {
                                                dataMaps.get(i).put(attr, assocFieldArr[i]);
                                            }
                                        }
                                        for(int i = 0; i < assocIds.length; i++) {
                                            if(assocIds[i] != null) {
                                                Model newAssoc = buildModelFromDataAndType(assocIds[i], dataMaps.get(i), association.getModel());
                                                if (newAssoc != null) {
                                                    assocList.add(newAssoc);
                                                }
                                            }
                                        }
                                    }
                                }

                            }
                            associationsMap.put(modelsAssociation, assocList);
                        }
                    }
                }
                m.setAssociations(associationsMap);
            }
            models.add(m);

        }
        rs.close();
        ps.close();
        return models;
    }

    public static Model buildModelFromDataAndType(Integer id, Map<String,Object> data, Association.Model model) {
        Model m = null;
        switch (model) {
            case Company: {
                m = new Company(id, data);
                break;
            }
            case Product: {
                m = new Product(id, data);
                break;
            }
            case Market: {
                m = new Market(id, data);
                break;
            }
            case MarketRevenue: {
                m = new MarketRevenue(id, data);
                break;
            }
            case CompanyRevenue: {
                m = new CompanyRevenue(id, data);
                break;
            }
            case ProductRevenue: {
                m = new ProductRevenue(id, data);
                break;
            }
            case MarketShareRevenue: {
                m = new MarketShareRevenue(id, data);
                break;
            }
            case Region: {
                m = new Region(id, data);
                break;
            }
        }
        return m;
    }

}
