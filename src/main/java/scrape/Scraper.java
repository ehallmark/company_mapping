package scrape;

import com.google.common.base.Charsets;
import com.opencsv.CSVReader;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;

import java.io.*;
import java.nio.charset.Charset;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public class Scraper {
    private static WebDriver driver;
    public enum Prefix {
        xnys,
        xnas
    }

    private static void scrapeHtmlTable(String symbol, Prefix exchangePrefix) throws Exception {
        File saveTo = filenameFor(exchangePrefix, symbol);
        if(!saveTo.exists()) {
            String url = "https://www.morningstar.com/stocks/"+exchangePrefix+"/"+symbol+"/quote.html";
            ChromeOptions options = new ChromeOptions();
            if(driver==null) {
                System.setProperty("webdriver.chrome.driver", "/usr/bin/chromedriver");
                System.setProperty("webdriver.firefox.driver", "/usr/bin/geckodriver");
                //options.addArguments("--headless", "--disable-gpu", "--window-size=1920,1200", "--ignore-certificate-errors");
                //options.setCapability(CapabilityType.UNEXPECTED_ALERT_BEHAVIOUR, UnexpectedAlertBehaviour.ACCEPT);
                //options.addArguments("--disable-gpu", "--window-size=1920,1200", "--ignore-certificate-errors");
                // options.setExperimentalOption("prefs", Collections.singletonMap("profile.default_content_setting_values.notifications", 2));
                driver = new ChromeDriver(options);
            }


            driver.get(url);
            TimeUnit.MILLISECONDS.sleep(600);
            driver.findElement(By.cssSelector(".msiip-application-navigation__list li[data-link=\"sal-components-financials\"] button")).click();
            String result = "";
            final long timeLimit = 2000L;
            long time = 0L;
            String finalResult = "";
            while (result.trim().length() == 0 && time < timeLimit) {
                long t = 200L;
                TimeUnit.MILLISECONDS.sleep(t);
                Document doc = Jsoup.parse(driver.getPageSource());
                result = doc.select("table.report-table").outerHtml();
                time += t;
            }
            TimeUnit.MILLISECONDS.sleep(600);
            Document doc = Jsoup.parse(driver.getPageSource());
            try {
                finalResult = doc.select("table.report-table").first().parent().html();
            } catch(Exception e) {
                finalResult = "";
            }
            // no need to over write for now
            System.out.println("Result: " + finalResult);
            OutputStream writer = new GZIPOutputStream(new FileOutputStream(saveTo));
            IOUtils.copy(new ByteArrayInputStream(finalResult.getBytes(Charsets.UTF_8)), writer);
            writer.flush();
            writer.close();
        }
    }

    public static File filenameFor(Prefix exchangePrefix, String symbol) {
        File saveTo = new File("company_data/"+exchangePrefix);
        if(!saveTo.exists()) {
            saveTo.mkdir();
        }
        saveTo = new File(saveTo, symbol+".gzip");
        return  saveTo;
    }


    public static void main(String[] args) throws Exception {
        CSVReader nasdaqReader = new CSVReader(new BufferedReader(new FileReader(new File("NASDAQ_tickers.csv"))));
        List<String[]> nasdaqLines = nasdaqReader.readAll();
        CSVReader nyseReader = new CSVReader(new BufferedReader(new FileReader(new File("NYSE_tickers.csv"))));
        List<String[]> nyseLines = nyseReader.readAll();

        nasdaqReader.close();
        nyseReader.close();

        for(String[] nasdaq : nasdaqLines.subList(1, nasdaqLines.size())) {
            String code = nasdaq[0].toLowerCase().trim();
            if(code.length()>0) {
                System.out.println("Scraping NASDAQ code: "+code);
                try {
                    scrapeHtmlTable(code, Prefix.xnas);
                } catch(Exception e) {
                    e.printStackTrace();
                }
            }
        }

        for(String[] nyse : nyseLines.subList(1, nyseLines.size())) {
            String code = nyse[0].toLowerCase().trim();
            if(code.length()>0) {
                System.out.println("Scraping NYSE code: "+code);
                try {
                    scrapeHtmlTable(code, Prefix.xnys);
                } catch(Exception e) {
                    e.printStackTrace();
                }
            }
        }


        if(driver!=null) {
            driver.close();
        }
    }
}
