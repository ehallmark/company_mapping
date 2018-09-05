package database;

import lombok.Getter;
import lombok.NonNull;
import models.*;

import java.sql.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
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
                if (Constants.fieldTypeForAttr(keys.get(i)).equals(Constants.NUMBER_FIELD_TYPE)) {
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


    public static synchronized List<Model> selectAllFromRevenueModel(@NonNull Association.Model model, @NonNull String tableName, Association association, String searchName) throws SQLException {
        List<String> attrList = Collections.singletonList(Constants.NAME);
        PreparedStatement ps = conn.prepareStatement("select r.id as id,j.name||' Revenue ('||r.year::text||')' as name from "+tableName+" as r join "+association.getParentTableName()+" as j on (r."+association.getParentIdField()+"=j.id) " + (searchName==null?"" : ( " where lower(j.name) like '%'||?||'%' order by lower(j.name)")));
        if(searchName!=null) {
            ps.setString(1, searchName);
        }
        ResultSet rs = ps.executeQuery();
        List<Model> models = new ArrayList<>();
        while(rs.next()) {
            Map<String, Object> data = new HashMap<>();
            int id = rs.getInt(1);
            for (int i = 0; i < attrList.size(); i++) {
                data.put(attrList.get(i), rs.getObject(i + 2));
            }
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
            models.add(m);

        }
        rs.close();
        ps.close();
        return models;
    }

    public static synchronized List<Model> selectAll(boolean isRevenueModel, @NonNull Association.Model model, @NonNull String tableName, @NonNull Collection<String> attributes) throws SQLException {
        return selectAll(isRevenueModel, model, tableName, attributes, null);
    }

    public static synchronized List<Model> selectAll(boolean isRevenueModel, @NonNull Association.Model model, @NonNull String tableName, @NonNull Collection<String> attributes, String searchName) throws SQLException {
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
                String sqlStr = "select r.id as id,c.name||'''s share of ' || m.name||' market ('||r.year::text||')' as name" + attrStr + " from " + tableName + " as r join companies as c on (c.id=r.company_id) join markets as m on (r.market_id=m.id) " + (searchName == null ? "" : (" where (lower(m.name) || lower(c.name)) like '%'||?||'%' ")) +" order by c.name";
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
                ps = conn.prepareStatement("select r.id as id,j.name||' Revenue ('||r.year::text||')' as name" + attrStr + " from " + tableName + " as r join " + parentTableName + " as j on (r." + parentIdField + "=j.id) " + (searchName == null ? "" : (" where lower(j.name) like '%'||?||'%' ")) + " order by lower(j.name)");
            }

        } else {
            ps = conn.prepareStatement("select id,"+String.join(",", attrList)+" from "+tableName+"" + (searchName==null?"" : ( " where lower(name) like '%'||?||'%' ")) + " order by lower(name)");
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
            models.add(m);

        }
        rs.close();
        ps.close();
        return models;
    }

}
