package init;

import auth.PasswordHandler;
import seed.SeedMarketTaxonomy;

public class Setup {
    public static void main(String[] args) throws Exception {
        PasswordHandler.main(args);
        SeedMarketTaxonomy.main(args);
    }
}
