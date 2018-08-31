package models;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class MarketRevenue extends Model {
    private static final List<Association> ASSOCIATIONS = Collections.singletonList(
            new Association(Association.Model.Market, Constants.MARKET_TABLE,
                    Constants.MARKET_REVENUE_TABLE, null, Association.Type.ManyToOne,
                    Constants.MARKET_ID, Constants.MARKET_REVENUE_ID, false, "Market Revenue")
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
            Constants.UPDATED_AT,
            Constants.CREATED_AT
    ));
    public MarketRevenue(Integer id, Map<String,Object> data) {
        super(ASSOCIATIONS, ATTRS, Constants.MARKET_REVENUE_TABLE, id, data, true);
    }
}
