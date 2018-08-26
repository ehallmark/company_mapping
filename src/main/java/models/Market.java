package models;

import java.util.*;

public class Market extends Model {
    private static final List<Association> ASSOCIATIONS = Arrays.asList(
            new Association(Association.Model.Product, Constants.MARKET_TABLE,
                    Constants.PRODUCT_TABLE, null, Association.Type.OneToMany,
                    Constants.MARKET_ID, Constants.PRODUCT_ID, false, "Market"),
            new Association(Association.Model.Segment, Constants.MARKET_TABLE,
                    Constants.SEGMENT_TABLE, Constants.SEGMENT_MARKETS_JOIN_TABLE, Association.Type.ManyToMany,
                    Constants.MARKET_ID, Constants.SEGMENT_ID, false, "Market"),
            new Association(Association.Model.Revenue, Constants.MARKET_TABLE,
                    Constants.REVENUE_TABLE, null, Association.Type.OneToMany,
                    Constants.MARKET_ID, Constants.REVENUE_ID, true, "Market"),
            new Association(Association.Model.Company, Constants.MARKET_TABLE,
                    Constants.COMPANY_TABLE, Constants.COMPANY_MARKETS_JOIN_TABLE, Association.Type.ManyToMany,
                    Constants.MARKET_ID, Constants.COMPANY_ID, false, "Market"),
            new Association("Sub Market", Association.Model.Market, Constants.MARKET_TABLE,
                    Constants.MARKET_TABLE, null, Association.Type.OneToMany,
                    Constants.PARENT_MARKET_ID, Constants.MARKET_ID, true, "Parent Market"),
            new Association("Parent Market", Association.Model.Market, Constants.MARKET_TABLE,
                    Constants.MARKET_TABLE, null, Association.Type.ManyToOne,
                    Constants.PARENT_MARKET_ID, Constants.MARKET_ID, false, "Sub Market")
    );
    private static final List<String> ATTRS = Collections.synchronizedList(Arrays.asList(
            Constants.NAME,
            Constants.PARENT_MARKET_ID,
            Constants.NOTES,
            Constants.UPDATED_AT,
            Constants.CREATED_AT
    ));
    public Market(Integer id, Map<String,Object> data) {
        super(ASSOCIATIONS, ATTRS, Constants.MARKET_TABLE, id, data);
    }
}
