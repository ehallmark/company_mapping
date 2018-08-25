package models;

import java.util.*;

public class Market extends Model {
    private static final List<Association> ASSOCIATIONS = Arrays.asList(
            new Association(Association.Model.Product, Constants.MARKET_TABLE,
                    Constants.PRODUCT_TABLE, Association.Type.OneToMany,
                    Constants.MARKET_ID, Constants.PRODUCT_ID, false),
            new Association(Association.Model.Segment, Constants.MARKET_TABLE,
                    Constants.SEGMENT_TABLE, Association.Type.ManyToMany,
                    Constants.MARKET_ID, Constants.SEGMENT_ID, false),
            new Association(Association.Model.Revenue, Constants.MARKET_TABLE,
                    Constants.REVENUE_TABLE, Association.Type.OneToMany,
                    Constants.MARKET_ID, Constants.REVENUE_ID, true),
            new Association(Association.Model.Company, Constants.MARKET_TABLE,
                    Constants.COMPANY_TABLE, Association.Type.ManyToMany,
                    Constants.MARKET_ID, Constants.COMPANY_ID, false),
            new Association(Association.Model.Market, Constants.MARKET_TABLE,
                    Constants.MARKET_TABLE, Association.Type.OneToMany,
                    Constants.PARENT_MARKET_ID, Constants.MARKET_ID, true),
            new Association(Association.Model.Market, Constants.MARKET_TABLE,
                    Constants.MARKET_TABLE, Association.Type.ManyToOne,
                    Constants.MARKET_ID, Constants.PARENT_MARKET_ID, false)
    );
    private static final Set<String> ATTRS = Collections.synchronizedSet(new HashSet<>(Arrays.asList(
            Constants.NAME,
            Constants.PARENT_MARKET_ID,
            Constants.NOTES,
            Constants.UPDATED_AT,
            Constants.CREATED_AT
    )));
    public Market(Integer id, Map<String,Object> data) {
        super(ASSOCIATIONS, ATTRS, Constants.MARKET_TABLE, id, data);
    }
}
