package models;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class MarketShareRevenue extends Model {
    private static final List<Association> ASSOCIATIONS = Arrays.asList(
            new Association("Company", Association.Model.Company, Constants.COMPANY_TABLE,
                    Constants.COMPANY_MARKETS_JOIN_TABLE, null, Association.Type.ManyToOne,
                    Constants.COMPANY_ID, Constants.MARKET_SHARE_ID, false, "Market Share"),
            new Association("Market", Association.Model.Market, Constants.MARKET_TABLE,
                    Constants.COMPANY_MARKETS_JOIN_TABLE, null, Association.Type.ManyToOne,
                    Constants.MARKET_ID, Constants.MARKET_SHARE_ID, false, "Market Share"),
            new Association("Sub Revenue", Association.Model.MarketShareRevenue, Constants.COMPANY_MARKETS_JOIN_TABLE,
                    Constants.COMPANY_MARKETS_JOIN_TABLE, null, Association.Type.OneToMany,
                    Constants.PARENT_REVENUE_ID, Constants.MARKET_SHARE_ID, true, "Parent Revenue"),
            new Association("Parent Revenue", Association.Model.MarketShareRevenue, Constants.COMPANY_MARKETS_JOIN_TABLE,
                    Constants.COMPANY_MARKETS_JOIN_TABLE, null, Association.Type.ManyToOne,
                    Constants.PARENT_REVENUE_ID, Constants.MARKET_SHARE_ID, false, "Sub Revenue"),
            new Association(Association.Model.Region, Constants.REGION_TABLE, Constants.COMPANY_MARKETS_JOIN_TABLE, null,
                    Association.Type.ManyToOne, Constants.REGION_ID, Constants.MARKET_SHARE_ID, false, "Market Share")

    );
    private static final List<String> ATTRS = Collections.synchronizedList(Arrays.asList(
            Constants.VALUE,
            Constants.YEAR,
            Constants.NOTES,
            Constants.SOURCE,
            Constants.IS_ESTIMATE,
            Constants.ESTIMATE_TYPE,
            Constants.CAGR,
            Constants.MARKET_ID,
            Constants.COMPANY_ID,
            Constants.REGION_ID,
            Constants.PARENT_REVENUE_ID,
            Constants.UPDATED_AT,
            Constants.CREATED_AT
    ));
    public MarketShareRevenue(Integer id, Map<String,Object> data) {
        super(ASSOCIATIONS, ATTRS, Constants.COMPANY_MARKETS_JOIN_TABLE, id, data, true);
    }
}
