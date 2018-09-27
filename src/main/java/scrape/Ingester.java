package scrape;

import com.opencsv.CSVReader;
import database.Database;
import models.*;
import org.apache.commons.io.IOUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.zip.GZIPInputStream;

public class Ingester {
    // GO HERE http://www.nasdaq.com/screening/companies-by-name.aspx?letter=0&exchange=nasdaq&render=download
    public static void ingestAll(boolean seedMarkets) throws Exception {
        CSVReader nasdaqReader = new CSVReader(new BufferedReader(new FileReader(new File("NASDAQ_tickers.csv"))));
        List<String[]> nasdaqLines = nasdaqReader.readAll();
        CSVReader nyseReader = new CSVReader(new BufferedReader(new FileReader(new File("NYSE_tickers.csv"))));
        List<String[]> nyseLines = nyseReader.readAll();

        CSVReader aseReader = new CSVReader(new BufferedReader(new FileReader(new File("AMEX_tickers.csv"))));
        List<String[]> aseLines = aseReader.readAll();

        nasdaqReader.close();
        nyseReader.close();
        aseReader.close();

        ingest(nasdaqLines, Scraper.Prefix.xnas, seedMarkets);
        ingest(nyseLines, Scraper.Prefix.xnys, seedMarkets);
        ingest(aseLines, Scraper.Prefix.xase, seedMarkets);

    }


    private static void ingest(List<String[]> stockLines, Scraper.Prefix prefix, boolean seedMarkets) {

        for(String[] row : stockLines.subList(1, stockLines.size())) {
            String code = row[0].toLowerCase().trim();
            if(code.length()>0) {
                try {
                    File file = Scraper.filenameFor(prefix, code);
                    if(file.exists()) {
                        ByteArrayOutputStream oos = new ByteArrayOutputStream();
                        InputStream is = new GZIPInputStream(new FileInputStream(file));
                        IOUtils.copy(is, oos);
                        is.close();
                        String content = oos.toString();
                        if(content.length()>0) {
                           // System.out.println("Content: "+content);
                            String sector = row[5].trim();
                            String industry = row[6].trim();
                            if(sector.equals("n/a")) sector = null;
                            if(industry.equals("n/a")) industry = null;
                            parseContent(content, prefix, code, row[1].trim(), sector, industry, seedMarkets);
                        }
                    }
                } catch(Exception e) {
                    e.printStackTrace();
                }
            }
        }

    }

    private enum RevenueType {
        Mil,
        Bil,
        Th
    }

    private static AtomicLong counter = new AtomicLong(0);
    private static void parseContent(String content, Scraper.Prefix prefix, String code, String name, String sector, String industry, boolean seedMarkets) throws Exception {
        Integer industryId = null;
        if(sector!=null && seedMarkets) {
            Integer sectorId = null;
            sectorId = Database.findIdByName(Constants.MARKET_TABLE, sector);
            if(sectorId == null) {
                Map<String,Object> data = new HashMap<>();
                data.put(Constants.NAME, sector);
                Model industryModel = new Market(null, data);
                industryModel.createInDatabase();
                sectorId = industryModel.getId();
            }
            if(industry!=null) {
                industryId = Database.findIdByName(Constants.MARKET_TABLE, industry);
                if(industryId == null) {
                    Map<String,Object> data = new HashMap<>();
                    data.put(Constants.NAME, industry);
                    data.put(Constants.PARENT_MARKET_ID, sectorId);
                    Model industryModel = new Market(null, data);
                    industryModel.createInDatabase();
                    industryId = industryModel.getId();
                }
            }
        }

        Document doc = Jsoup.parse(content);
        Elements fixedTable = doc.select("table.fixed-table tbody tr");
        Elements reportTable = doc.select("table.report-table tbody tr");

        RevenueType revenueType = null;
        if(fixedTable.size()>1 && reportTable.size()>1) {
            Element revenueHeader = fixedTable.get(1);
            String revenueStr = revenueHeader.text().replace("Revenue", "").replace("(", "").replace(")", "").trim();
            if (!revenueStr.contains("%")&&revenueStr.length()>0) {
                revenueType = RevenueType.valueOf(revenueStr);
                System.out.println("Rev "+counter.getAndIncrement()+": " + revenueType);
            }
        }

        Map<String, Object> data = new HashMap<>();
        data.put(Constants.NAME, name);
        String notes = "Stock ticker "+code.toUpperCase()+".";
        data.put(Constants.NOTES, notes);
        Model company = new Company(null, data);
        if(revenueType!=null) {
            // look through report
            Elements headers = reportTable.get(0).children();
            Elements revenues = reportTable.get(1).children();
            List<Double> previousRevenues = new ArrayList<>();
            for(int i = 0; i < headers.size(); i++) {
                int year;
                try {
                    if(headers.get(i).text().equals("Fiscal")) continue;
                    year = Integer.valueOf(headers.get(i).text());
                } catch(Exception e) {
                    break;
                }
                if(!company.existsInDatabase()) {
                    company.createInDatabase();
                }
                double rev;
                try {
                    rev = Double.valueOf(revenues.get(i).text());
                } catch(Exception e) {
                    continue;
                }

                switch(revenueType) {
                    case Th: {
                        rev *= 1000L;
                        break;
                    }
                    case Bil: {
                        rev *= 1000000000L;
                        break;
                    }
                    case Mil: {
                        rev *= 1000000L;
                        break;
                    }
                }
                rev /= 1000000L;
                previousRevenues.add(rev);
                Double cagr = null;
                if(previousRevenues.size()>1) {
                    cagr = null;//MathHelper.calculateCagr(previousRevenues);
                }

                System.out.println("Revenue for "+year+": "+rev);
                Map<String, Object> revenueData = new HashMap<>();
                revenueData.put(Constants.YEAR, year);
                revenueData.put(Constants.VALUE, rev);
                revenueData.put(Constants.SOURCE, "https://morningstar.com/stocks/"+prefix+"/"+code+"/quote.html");
                revenueData.put(Constants.COMPANY_ID, company.getId());
                revenueData.put(Constants.CAGR, cagr);

                Model revenueModel;
                if(industryId!=null && seedMarkets) {
                    revenueData.put(Constants.MARKET_ID, industryId);
                    revenueModel = new MarketShareRevenue(null, revenueData);
                } else {
                    revenueModel = new CompanyRevenue(null, revenueData);
                }
                revenueModel.createInDatabase();
            }
        }
    }
}
