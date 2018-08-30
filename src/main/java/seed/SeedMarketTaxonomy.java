package seed;

import com.opencsv.CSVReader;
import models.Constants;
import models.Market;
import models.Model;

import java.io.File;
import java.io.FileReader;
import java.util.*;

public class SeedMarketTaxonomy {
    public static void main(String[] args) throws Exception {
        File file = new File("Taxonomy.csv");
        CSVReader reader = new CSVReader(new FileReader(file));
        List<String[]> data = reader.readAll();
        reader.close();

        int currentDepth = 0;
        List<Model> models = new ArrayList<>();
        Model priorModel = null;
        for(String[] line : data) {
            int endIdx = 0;
            for(int i = 0; i < line.length; i ++) {
                String r = line[i];
                if(r!=null && r.trim().length() > 0) {
                    endIdx = i;
                }
            }
            line = Arrays.copyOf(line, endIdx+1);
            if(line.length==0) continue;
            String name = line[line.length-1];
            System.out.println("line: "+String.join("; ",line));
            if(name.length()==0) throw new RuntimeException("Illegal empty name at index: "+(line.length-1));
            // get number of blank spaces
            int depth = line.length - 1;
            for(int i = 0; i < depth; i++) System.out.print("\t");
            System.out.println("Name: "+name);
            if (depth > currentDepth) { // went down the tree
                // children of parent
                if(priorModel!=null) {
                    models.add(0, priorModel);
                }
            } else if (depth < currentDepth) { // went up the tree
                if(models.size()>0) {
                    models.remove(0);
                }
            }
            Model model;
            Map<String,Object> d = new HashMap<>();
            d.put(Constants.NAME, name);
            if(models.size() > 0) {
                model = models.get(0);
                d.put(Constants.PARENT_MARKET_ID, model.getId());
            }
            model = new Market(null, d);
            model.createInDatabase();
            priorModel = model;
            currentDepth = depth;
        }

    }
}
