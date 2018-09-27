package seed;

import com.opencsv.CSVReader;
import graph.Graph;
import models.Association;
import models.Constants;
import models.MarketShareRevenue;
import models.Model;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class IngestCompanyDataSheet {

    public static void ingestDataSheetForCompany(String company, int year, CSVReader reader) throws IOException {
        Graph graph = Graph.load();
        Model companyModel = graph.findByName(Association.Model.Company, company, true);
        if(companyModel == null) {
            throw new RuntimeException("Could not create company: "+company);
        }

        List<String[]> data = reader.readAll();
        // first two rows are headers
        String[] headers = data.get(1);
        Model latestIndustry = null;
        for(int i = 2; i < data.size(); i++) {
            String[] row = data.get(i);
            String industry = row[0];
            String segment = row[1];
            String source = row[2];
            String notes = row[3];
            String estimateQualityStr = row[4];
            Integer estimateQuality = null;
            if(estimateQualityStr.equalsIgnoreCase("high")) estimateQuality = 2;
            else if(estimateQualityStr.equalsIgnoreCase("medium")) estimateQuality = 1;
            else if(estimateQualityStr.equalsIgnoreCase("low")) estimateQuality = 0;
            String segmentRevenueStr = row[5];
            Double segmentGlobalRevenue = null;
            if(segmentRevenueStr.length()>0) {
                segmentGlobalRevenue = Double.valueOf(segmentRevenueStr);
            }
            Model globalRevenueModel = null;
            if(industry.isEmpty()) {
                if(segmentGlobalRevenue!=null) {
                    // must have a segment
                    if (latestIndustry == null) throw new RuntimeException("No industry for segment: " + segment);
                    if (segment.trim().isEmpty()) {
                        System.out.println("No industry or segment found... exiting.");
                        break;
                    }
                    Model segmentModel = latestIndustry.getAssociations().getOrDefault(latestIndustry.findAssociation("Sub Market"), Collections.emptyList())
                            .stream().filter(m -> m.getName().equalsIgnoreCase(segment))
                            .findAny().orElse(null);
                    if (segmentModel == null) {
                        segmentModel = graph.findByName(Association.Model.Market, segment, true);
                        segmentModel.updateAttribute(Constants.PARENT_MARKET_ID, latestIndustry.getId());
                        segmentModel.updateInDatabase();
                    }
                    // add global market shares
                    Map<String, Object> revenueData = new HashMap<>();
                    if (!source.isEmpty()) {
                        revenueData.put(Constants.SOURCE, source);
                    }
                    if (!notes.isEmpty()) {
                        revenueData.put(Constants.NOTES, notes);
                    }
                    if (estimateQuality != null) {
                        revenueData.put(Constants.ESTIMATE_TYPE, estimateQuality);
                    }
                    revenueData.put(Constants.VALUE, segmentGlobalRevenue);
                    revenueData.put(Constants.MARKET_ID, segmentModel.getId());
                    revenueData.put(Constants.COMPANY_ID, companyModel.getId());
                    revenueData.put(Constants.YEAR, year);
                    revenueData.put(Constants.IS_ESTIMATE, true);
                    globalRevenueModel = new MarketShareRevenue(null, revenueData);
                    try {
                        globalRevenueModel.createInDatabase();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            } else {
                latestIndustry = graph.findByName(Association.Model.Market, industry, true);
                latestIndustry.loadAssociations();
            }
            if(globalRevenueModel!=null) {
                Map<Integer, Model> regionIdToParentModelMap = new HashMap<>();
                for (int r = 6; r < row.length; r++) {
                    String revStr = row[r];
                    if (revStr.isEmpty()) break;
                    String location = getLocationFor(headers[r]);
                    Model regionModel = graph.findByName(Association.Model.Region, location, false);
                    if(regionModel==null) {
                        throw new RuntimeException("Unable to find region: "+location);
                    }
                    regionModel.loadAttributesFromDatabase();
                    Model parentModel;
                    if(regionModel.getData().get(Constants.PARENT_REGION_ID)==null) {
                        // regional
                        parentModel = globalRevenueModel;
                    } else {
                        // national
                        parentModel = regionIdToParentModelMap.get((Integer)regionModel.getData().get(Constants.PARENT_REGION_ID));
                        if(parentModel==null) {
                            throw new RuntimeException("Could not find parent revenue for: "+ location);
                        }
                    }
                    double rev = Double.valueOf(revStr);
                    // add global market shares
                    Map<String,Object> revenueData = new HashMap<>();
                    if(!source.isEmpty()) {
                        revenueData.put(Constants.SOURCE, source);
                    }
                    if(!notes.isEmpty()) {
                        revenueData.put(Constants.NOTES, notes);
                    }
                    if(estimateQuality!=null) {
                        revenueData.put(Constants.ESTIMATE_TYPE, estimateQuality);
                    }
                    revenueData.put(Constants.VALUE, rev);
                    revenueData.put(Constants.PARENT_REVENUE_ID, parentModel.getId());
                    revenueData.put(Constants.REGION_ID, regionModel.getId());
                    revenueData.put(Constants.YEAR, year);
                    revenueData.put(Constants.IS_ESTIMATE, true);
                    Model revenueModel = new MarketShareRevenue(null, revenueData);
                    try {
                        revenueModel.createInDatabase();
                        regionIdToParentModelMap.put(regionModel.getId(), revenueModel);
                    } catch(Exception e) {
                        e.printStackTrace();
                    }
                }
            }
            System.out.println("Row: "+String.join(", ", row));
        }
    }

    private static String getLocationFor(String locationHeader) {
        if(locationHeader.equals("US")) {
            locationHeader = "United States of America";
        } else if (locationHeader.equals("Korea")) {
            locationHeader = "Korea (Republic of)";
        }
        return locationHeader;
    }

    public static void main(String[] args) {
        String[] companies = new String[]{
                "Honeywell International Inc.",
                "Alphabet Inc."
        };
        File[] files = new File[]{
                new File("honeywell_revenue_2018.csv"),
                new File("alphabet_revenue_2018.csv")
        };
        for(int i = 0; i < companies.length; i++) {
            String company = companies[i];
            File file = files[i];
            try (CSVReader reader = new CSVReader(new FileReader(file))) {
                ingestDataSheetForCompany(company, 2018, reader);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        System.exit(0);
    }
}
