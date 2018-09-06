package scrape;

import com.opencsv.CSVReader;
import models.*;
import org.apache.commons.io.IOUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.*;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.zip.GZIPInputStream;

public class Ingester {
    // GO HERE http://www.nasdaq.com/screening/companies-by-name.aspx?letter=0&exchange=nasdaq&render=download
    public static void main(String[] args) throws Exception {
        CSVReader nasdaqReader = new CSVReader(new BufferedReader(new FileReader(new File("NASDAQ_tickers.csv"))));
        List<String[]> nasdaqLines = nasdaqReader.readAll();
        CSVReader nyseReader = new CSVReader(new BufferedReader(new FileReader(new File("NYSE_tickers.csv"))));
        List<String[]> nyseLines = nyseReader.readAll();

        nasdaqReader.close();
        nyseReader.close();

        ingest(nasdaqLines, Scraper.Prefix.xnas);
        ingest(nyseLines, Scraper.Prefix.xnys);
        ingest(nasdaqLines, Scraper.Prefix.xase);

    }


    private static void ingest(List<String[]> stockLines, Scraper.Prefix prefix) {

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
                            parseContent(content, prefix, code, row[1].trim());
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
    private static void parseContent(String content, Scraper.Prefix prefix, String code, String name) {
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
                System.out.println("Revenue for "+year+": "+rev);
                Map<String, Object> revenueData = new HashMap<>();
                revenueData.put(Constants.YEAR, year);
                revenueData.put(Constants.VALUE, rev);
                revenueData.put(Constants.SOURCE, "https://morningstar.com/stocks/"+prefix+"/"+code+"/quote.html");
                revenueData.put(Constants.COMPANY_ID, company.getId());

                Model revenueModel = new CompanyRevenue(null, revenueData);
                revenueModel.createInDatabase();
            }
        }
    }
}
