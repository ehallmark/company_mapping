package seed;

import scrape.Ingester;

public class Seed {
    public static void main(String[] args) throws Exception {
        SeedCountriesAndRegions.main(args);
       // SeedTestData.main(args);
        SeedMarketTaxonomy.main(args);
        Ingester.ingestAll(false);
        IngestCompanyDataSheet.main(args);
        System.exit(0);
    }
}
