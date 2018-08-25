package models;

import java.util.*;

public class Company extends Model {
    private static final List<Association> ASSOCIATIONS = Arrays.asList(
            new Association(Association.Model.Product, Constants.COMPANY_TABLE,
                    Constants.PRODUCT_TABLE, null, Association.Type.OneToMany,
                    Constants.COMPANY_ID, Constants.PRODUCT_ID, true),
            new Association(Association.Model.Segment, Constants.COMPANY_TABLE,
                    Constants.SEGMENT_TABLE,null,  Association.Type.OneToMany,
                    Constants.COMPANY_ID, Constants.SEGMENT_ID, true),
            new Association(Association.Model.Revenue, Constants.COMPANY_TABLE,
                    Constants.REVENUE_TABLE,null,  Association.Type.OneToMany,
                    Constants.COMPANY_ID, Constants.REVENUE_ID, true),
            new Association(Association.Model.Market, Constants.COMPANY_TABLE,
                    Constants.MARKET_TABLE,Constants.COMPANY_MARKETS_JOIN_TABLE,  Association.Type.ManyToMany,
                    Constants.COMPANY_ID, Constants.MARKET_ID, false)

    );
    private static final List<String> ATTRS = Collections.synchronizedList(Arrays.asList(
            Constants.NAME,
            Constants.NOTES,
            Constants.UPDATED_AT,
            Constants.CREATED_AT
    ));
    public Company(Integer id, Map<String,Object> data) {
        super(ASSOCIATIONS, ATTRS, Constants.COMPANY_TABLE, id, data);
    }
}
