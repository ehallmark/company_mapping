package seed;

import database.Database;
import lombok.NonNull;
import models.*;
import org.apache.commons.math3.distribution.AbstractRealDistribution;
import org.apache.commons.math3.distribution.ExponentialDistribution;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

public class SeedTestData {
    private static final Random rand = new Random(120);

    public static void main(String[] args) throws Exception {
        seedResource(Association.Model.Market,0,  4, null, null);
        seedResource(Association.Model.Company,0,  4, null, null);
        seedResource(Association.Model.Product,0,  4, null, null);

        // add market shares
        List<Model> markets = Database.selectAll(false, Association.Model.Market, Constants.MARKET_TABLE, Collections.singletonList(Constants.PARENT_MARKET_ID));
        List<Model> companies = Database.selectAll(false, Association.Model.Company, Constants.COMPANY_TABLE, Collections.singletonList(Constants.PARENT_COMPANY_ID));

        for(Model market : markets) {
            if(market.getData().get(Constants.PARENT_MARKET_ID) != null) {
                for(Model company : companies) {
                    if(rand.nextBoolean() && rand.nextBoolean()) {
                        addMarketShares(company, market, rand.nextInt(10)+1, new ExponentialDistribution(50d + rand.nextInt(800)));
                    }
                }
            }
        }

    }

    private static void addRevenuesToResource(@NonNull Model model, int numYears, AbstractRealDistribution valueDistribution) {
        for(int i = 1; i <= numYears; i++) {
            Map<String, Object> data = new HashMap<>();
            data.put(Constants.YEAR, LocalDate.now().getYear()-i);
            data.put(Constants.VALUE, valueDistribution.sample());
            data.put(Constants.SOURCE, "The Intelligent Designer");
            if(rand.nextBoolean()) {
                data.put(Constants.CAGR, rand.nextDouble() * 30.0 - 5.0);
            }
            Model revenueModel = null;
            if(model instanceof Company) {
                data.put(Constants.COMPANY_ID, model.getId());
                revenueModel = new CompanyRevenue(null, data);
            } else if(model instanceof Market) {
                data.put(Constants.MARKET_ID, model.getId());
                revenueModel = new MarketRevenue(null, data);
            } else if(model instanceof Product) {
                data.put(Constants.PRODUCT_ID, model.getId());
                revenueModel = new ProductRevenue(null, data);
            }
            if(revenueModel!=null) {
                try {
                    revenueModel.createInDatabase();
                } catch(Exception e) {
                    //e.printStackTrace();
                }
                // sub revenues
                if(rand.nextBoolean()) {
                    // get regions
                    try {
                        List<Model> regions = Database.selectAll(false, Association.Model.Region, Constants.REGION_TABLE, Arrays.asList(Constants.NAME, Constants.PARENT_REGION_ID));
                        regions = regions.stream().filter(region->region.getData().get(Constants.PARENT_REGION_ID)==null).collect(Collectors.toList());
                        for(Model region : regions) {
                            addSubRevenues(revenueModel, region.getId(), new ExponentialDistribution(50d + rand.nextInt(800)));
                        }
                    } catch(Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    }


    private static void addSubRevenues(@NonNull Model model, int regionId, AbstractRealDistribution valueDistribution) {
        Map<String, Object> data = new HashMap<>();
        data.put(Constants.YEAR, model.getData().get(Constants.YEAR));
        data.put(Constants.VALUE, valueDistribution.sample());
        data.put(Constants.SOURCE, "The Intelligent Designer");
        data.put(Constants.REGION_ID, regionId);
        Model revenueModel = null;
        data.put(Constants.PARENT_REVENUE_ID, model.getId());
        if(rand.nextBoolean()) {
            data.put(Constants.CAGR, rand.nextDouble() * 30.0 - 5.0);
        }
        if(model instanceof CompanyRevenue) {
            revenueModel = new CompanyRevenue(null, data);
        } else if(model instanceof MarketRevenue) {
            revenueModel = new MarketRevenue(null, data);
        } else if(model instanceof ProductRevenue) {
            revenueModel = new ProductRevenue(null, data);
        }
        if(revenueModel!=null) {
            try {
                revenueModel.createInDatabase();
            } catch(Exception e) {
                //e.printStackTrace();
            }
        }
    }

    private static void addMarketShares(@NonNull Model company, Model market, int numYears, AbstractRealDistribution valueDistribution) {
        for(int i = 1; i <= numYears; i++) {
            Map<String, Object> data = new HashMap<>();
            data.put(Constants.YEAR, LocalDate.now().getYear()-i);
            data.put(Constants.VALUE, valueDistribution.sample());
            data.put(Constants.SOURCE, "The Intelligent Designer");
            data.put(Constants.MARKET_ID, market.getId());
            data.put(Constants.COMPANY_ID, company.getId());
            Model revenueModel = new MarketShareRevenue(null, data);
            try {
                revenueModel.createInDatabase();
            } catch(Exception e) {
                //e.printStackTrace();
            }
        }
    }

    private static Model getOrCreateByName(@NonNull Association.Model model, @NonNull String name, @NonNull Map<String,Object> data) throws Exception {
        Model resource = null;
        switch (model) {
            case Market: {
                Integer previousId = Database.findIdByName(Constants.MARKET_TABLE, name);
                if(previousId!=null) {
                    resource = new Market(previousId, null);
                    resource.loadAttributesFromDatabase();
                } else {
                    resource = new Market(null, data);
                }
                break;
            }
            case Company: {
                Integer previousId = Database.findIdByName(Constants.COMPANY_TABLE, name);
                if(previousId!=null) {
                    resource = new Company(previousId, null);
                    resource.loadAttributesFromDatabase();
                } else {
                    resource = new Company(null, data);
                }
                break;
            }
            case Product: {
                Integer previousId = Database.findIdByName(Constants.PRODUCT_TABLE, name);
                if(previousId!=null) {
                    resource = new Product(previousId, null);
                    resource.loadAttributesFromDatabase();
                } else {
                    resource = new Product(null, data);
                }
                break;
            }
        }
        return resource;
    }


    private static void seedResource(@NonNull Association.Model model, int depth, int numSamples, String parentField, Integer parentId) throws Exception {
        if(depth > 2) return; // clip depth

        for(int i = 1; i <= numSamples; i++) {
            String name = "Test " + model + (depth > 0 ? " "+depth : "") + " - " + Math.abs(rand.nextInt());
            System.out.println("FOUND: "+name);
            Map<String,Object> data = new HashMap<>();
            if(parentId!=null) {
                data.put(parentField, parentId);
            }
            data.put(Constants.NAME, name);
            Model resource = getOrCreateByName(model, name, data);
            if (resource != null) {
                if(!resource.existsInDatabase()) {
                    resource.createInDatabase();
                }
                for(Association association : resource.getAssociationsMeta()) {
                    if(association.getType().equals(Association.Type.OneToMany) && !association.getType().toString().contains("Revenue")) {
                        if (rand.nextBoolean()) {
                            // add sub data
                            seedResource(association.getModel(), depth + 1, rand.nextInt(15), association.getParentIdField(), resource.getId());
                        }
                    }
                }
                if(rand.nextBoolean()) {
                    addRevenuesToResource(resource, rand.nextInt(5)+5, new ExponentialDistribution(100d + rand.nextInt(1000)));
                }
            }
        }
    }
}
