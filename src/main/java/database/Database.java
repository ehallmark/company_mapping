package database;

import lombok.Getter;
import lombok.NonNull;
import models.*;

import java.sql.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
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

    private static void selectAllHelper(String parentPrefix, String prefix, List<Association> associations, Map<String,Association> prefixToAssocMap, Collection<String> joinAttrStrs, Collection<String> allJoins, Collection<String> groups, AtomicBoolean useGroups)  {
        for (int i = 0; i < associations.size(); i++) {
            Association association = associations.get(i);
            Model m = buildModelFromDataAndType(null, null, association.getModel());
            if (m != null) {
                String j = prefix + i;
                String joinStr;
                List<String> assocAttrList = new ArrayList<>(m.getAvailableAttributes());
                if (m.isRevenueModel()) {
                    assocAttrList.remove(Constants.NAME);
                }
                if (association.getType().equals(Association.Type.ManyToOne)) {
                    allJoins.add("left join " + association.getParentTableName() + " as " + j + " on (" + j + ".id="+parentPrefix+"." + association.getParentIdField() + ")");
                    joinStr = j + ".id as "+j+"_id," + String.join(",", assocAttrList.stream().map(a -> j + "." + a + " as "+j+"_"+a).collect(Collectors.toList()));
                    groups.add(j + ".id");
                } else if (association.getType().equals(Association.Type.OneToMany)) {
                    allJoins.add("left join " + association.getChildTableName() + " as " + j + " on (" + j + "." + association.getParentIdField() + "="+parentPrefix+".id)");
                    joinStr = "array_agg(" + j + ".id) as "+j+"_id," + String.join(",", assocAttrList.stream().map(a -> "array_agg(" + j + "." + a + ") as "+j+"_"+a).collect(Collectors.toList()));
                    groups.add(parentPrefix + ".id");
                    useGroups.set(true);
                } else {
                    throw new RuntimeException("Unsupported join type: " + association.getType());
                }
                joinAttrStrs.add(joinStr);
                prefixToAssocMap.put(j, association);
            }
        }
    }

    public static synchronized List<Model> selectAll(boolean isRevenueModel, @NonNull Association.Model model, @NonNull String tableName, @NonNull Collection<String> attributes, List<Association> associations, String searchName) throws SQLException {
        List<String> attrList = new ArrayList<>(new HashSet<>(attributes));
        boolean addNameToRevenue = attrList.contains(Constants.NAME);
        if(isRevenueModel && addNameToRevenue) {
            attrList.remove(Constants.NAME);
        }
        PreparedStatement ps;
        List<String> joinAttrStrs = new ArrayList<>();
        List<String> allJoins = new ArrayList<>();
        Map<String, Association> prefixToAssocMap = new HashMap<>();
        Set<String> groups = new HashSet<>();
        AtomicBoolean useGroups = new AtomicBoolean(false);
        if(associations!=null) {
            // add other attr strs
            if(searchName!=null) throw new RuntimeException("Searching in associations query is not yet supported.");
            selectAllHelper("r", "j", associations, prefixToAssocMap, joinAttrStrs, allJoins, groups, useGroups);
        }
        String groupBy = "";
        if(useGroups.get() && groups.size() > 0) {
            groups.add("r.id");
            groupBy = " group by "+String.join(",", groups);
        }
        String where = "";
        if(searchName!=null) {
            if(!addNameToRevenue) {
                throw new RuntimeException("Searching without a valid name attribute");
            }
            if(model.equals(Association.Model.MarketShareRevenue)) {
                where = " where (lower(m.name) || lower(c.name)) like '%'||?||'%' ";
            } else if(isRevenueModel) {
                where = " where lower(j.name) like '%'||?||'%' ";
            } else {
                where = " where lower(r.name) like '%'||?||'%' ";
            }
        }

        String attrStr = String.join(",", attrList.stream().map(a -> "r." + a + " as r_"+a).collect(Collectors.toList()));
        if(attrList.size()>0) {
            attrStr = ","+attrStr;
        }
        if(isRevenueModel) {
            String revenueNameSql = addNameToRevenue ? ",'Revenue ('||r.year::text||')' as name " : "";
            attrStr = revenueNameSql + attrStr;
            if(addNameToRevenue) {
                if (model.equals(Association.Model.MarketShareRevenue)) {
                    allJoins.add(" left join companies as c on (c.id=r.company_id) ");
                    allJoins.add(" left join markets as m on (r.market_id=m.id) ");
                } else {
                    String parentTableName;
                    String parentIdField;
                    parentTableName = tableName.replace("_revenue", "");
                    if (parentTableName.equals("companys")) {
                        parentTableName = "companies"; // handle weird english language
                    }
                    parentIdField = model.toString().replace("Revenue", "").toLowerCase() + "_id";
                    allJoins.add(" left join "+parentTableName+" as j on (r." + parentIdField+"=j.id) ");
                }
            }
        }

        String sqlStr;
        if (associations != null && allJoins.size()>0 && joinAttrStrs.size()>0) {
            sqlStr ="select r.id as id " + attrStr + ","+String.join(",", joinAttrStrs)+" from " + tableName + " as r " + String.join(" ", allJoins) + where + " " + groupBy;
        } else {
            sqlStr ="select r.id as id " + attrStr + " from " + tableName + " as r " + where;
        }
        ps = conn.prepareStatement(sqlStr);

        if(searchName!=null) {
            ps.setString(1, searchName);
        }
        System.out.println("QUERY: "+ps.toString());
        ResultSet rs = ps.executeQuery();
        List<Model> models = new ArrayList<>();
        if(isRevenueModel && addNameToRevenue) {
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
                                        if(!assocData.containsKey(Constants.NAME)) {
                                            assocData.put(Constants.NAME, association.getAssociationName());
                                        }
                                        assoc = buildModelFromDataAndType(assocId, assocData, association.getModel());
                                        if (assoc != null) {
                                            assocList.add(assoc);
                                        }
                                    } else {
                                        for(int i = 0; i < assoc.getAvailableAttributes().size(); i++) {
                                            queryIdx.getAndIncrement();
                                        }
                                    }
                                } else if(modelsAssociation.getType().equals(Association.Type.OneToMany)) {
                                    Array assocIdsArr = rs.getArray(queryIdx.getAndIncrement());
                                    if (assocIdsArr != null && assocIdsArr.getResultSet()!=null) {
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
                                                if(!dataMaps.get(i).containsKey(Constants.NAME)) {
                                                    dataMaps.get(i).put(Constants.NAME, association.getAssociationName());
                                                }
                                                Model newAssoc = buildModelFromDataAndType(assocIds[i], dataMaps.get(i), association.getModel());
                                                if (newAssoc != null) {
                                                    assocList.add(newAssoc);
                                                }
                                            }
                                        }
                                    } else {
                                        for(int i = 0; i < assoc.getAvailableAttributes().size(); i++) {
                                            queryIdx.getAndIncrement();
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
