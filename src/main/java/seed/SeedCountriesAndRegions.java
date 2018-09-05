package seed;

import com.opencsv.CSVReader;
import controllers.DataTable;
import database.Database;

import java.io.File;
import java.io.FileReader;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.*;

public class SeedCountriesAndRegions {
    public static void main(String[] args) throws Exception {
        CSVReader reader = new CSVReader(new FileReader(new File("countries.csv")));
        List<String[]> data = reader.readAll();
        reader.close();

        System.out.println("Num rows: "+data.size());
        Connection conn = Database.getConn();

        PreparedStatement ps = conn.prepareStatement("insert into countries (name, parent_country_id) values (?, ?) returning id");

        Map<String, Integer> regionToId = new HashMap<>();
        for(String[] row : data.subList(1, data.size())) {
            if(row.length==0) continue;
            String country = row[0];
            String region = row.length > 2 ? row[2] : null;
            if(region!=null && region.trim().isEmpty()) {
                region = null;
            }
            System.out.println(country+", "+region);
            if (region!=null && !regionToId.containsKey(region)){
                ps.setString(1, region);
                ps.setObject(2, null);
                ps.execute();
                ResultSet rs = ps.getResultSet();
                Integer id = null;
                if (rs.next()) {
                    id = rs.getInt(1);
                }
                rs.close();
                if(id!=null) {
                    regionToId.put(region, id);
                }
            }

            // add country
            Integer parentId = region == null ? null : regionToId.get(region);
            ps.setString(1, country);
            ps.setObject(2, parentId);
            ps.execute();
        }


        conn.close();
    }
}
