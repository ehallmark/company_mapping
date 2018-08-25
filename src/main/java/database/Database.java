package database;

import lombok.NonNull;
import models.*;

import java.sql.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class Database {
    private static final String dbUrl = "jdbc:postgresql://localhost/companydb?user=postgres&password=password&tcpKeepAlive=true";

    private static Connection conn;
    static {
        resetConn();
    }

    public static synchronized void resetConn() {
        try {
            conn = DriverManager.getConnection(dbUrl);
            conn.setAutoCommit(false);
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
                qs.add("?");
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
        conn.commit();
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
                case Revenue: {
                    models.add(new Revenue(id, null));
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
                case Segment: {
                    models.add(new Segment(id, null));
                    break;
                }
            }
        }
        rs.close();
        ps.close();
        return models;
    }

    public static synchronized List<Model> loadManyToManyAssociation(@NonNull Association.Model associationType, @NonNull Model baseModel, @NonNull String joinTableName, @NonNull String parentIdField, @NonNull String childIdField) throws SQLException {
        if(!baseModel.existsInDatabase()) {
            System.out.println("Trying to load association of model that does not exist in the database.");
        }
        PreparedStatement ps = conn.prepareStatement("select "+childIdField+" from "+joinTableName+" where "+parentIdField+"=?");
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
                case Revenue: {
                    models.add(new Revenue(id, null));
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
                case Segment: {
                    models.add(new Segment(id, null));
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
            case Revenue: {
                model = new Revenue(parentId, null);
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
            case Segment: {
                model = new Segment(parentId, null);
                break;
            }
        }
        return model;
    }

    public static synchronized void update(@NonNull String tableName, int id, Map<String,Object> data) throws SQLException {
        data = new HashMap<>(data);
        List<String> keys = new ArrayList<>(data.keySet());
        List<String> qs = new ArrayList<>(keys.size());
        for(int i = 0; i < keys.size(); i++) {
            if(data.get(keys.get(i)) != null && data.get(keys.get(i)) instanceof LocalDate) {
                qs.add("?::date");
                data.put(keys.get(i), ((LocalDate) data.get(keys.get(i))).format(DateTimeFormatter.ISO_DATE));
            } else {
                qs.add("?");
            }
        }
        PreparedStatement ps = conn.prepareStatement("update "+tableName+" set ("+String.join(",",keys)+") = ("+ String.join(",",qs) + ") where id=?");
        for(int i = 0; i < keys.size(); i++) {
            ps.setObject(i+1, data.get(keys.get(i)));
        }
        ps.setObject(keys.size()+1, id);
        ps.executeUpdate();
        ps.close();
        conn.commit();
    }

    public static synchronized void delete(@NonNull String tableName, int id) throws SQLException {
        PreparedStatement ps = conn.prepareStatement("delete from "+tableName+" where id=?");
        ps.setObject(1, id);
        ps.executeUpdate();
        ps.close();
        conn.commit();
    }

    public static synchronized Map<String,Object> select(@NonNull String tableName, int id, @NonNull Collection<String> attributes) throws SQLException {
        List<String> attrList = new ArrayList<>(new HashSet<>(attributes));
        PreparedStatement ps = conn.prepareStatement("select ("+String.join(",", attrList)+") from "+tableName+" where id=?");
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

}
