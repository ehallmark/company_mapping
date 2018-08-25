package models;

import java.util.*;

public class Product extends Model {
    private static final List<Association> ASSOCIATIONS = Arrays.asList(
            new Association(Association.Model.Company, Constants.COMPANY_TABLE,
                    Constants.PRODUCT_TABLE, Association.Type.ManyToOne,
                    Constants.COMPANY_ID, Constants.PRODUCT_ID, false),
            new Association(Association.Model.Segment, Constants.SEGMENT_TABLE,
                    Constants.PRODUCT_TABLE, Association.Type.ManyToOne,
                    Constants.SEGMENT_ID, Constants.PRODUCT_ID, false),
            new Association(Association.Model.Revenue, Constants.PRODUCT_TABLE,
                    Constants.REVENUE_TABLE, Association.Type.OneToMany,
                    Constants.PRODUCT_ID, Constants.REVENUE_ID, true),
            new Association(Association.Model.Market, Constants.MARKET_TABLE,
                    Constants.PRODUCT_TABLE, Association.Type.ManyToOne,
                    Constants.MARKET_ID, Constants.PRODUCT_ID, false)

    );
    private static final Set<String> ATTRS = Collections.synchronizedSet(new HashSet<>(Arrays.asList(
            Constants.NAME,
            Constants.NOTES,
            Constants.COMPANY_ID,
            Constants.MARKET_ID,
            Constants.SEGMENT_ID,
            Constants.UPDATED_AT,
            Constants.CREATED_AT
    )));
    public Product(Integer id, Map<String,Object> data) {
        super(ASSOCIATIONS, ATTRS, Constants.PRODUCT_TABLE, id, data);
    }
}
