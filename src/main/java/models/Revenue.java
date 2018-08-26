package models;

import java.util.*;

public class Revenue extends Model {
    private static final List<Association> ASSOCIATIONS = Arrays.asList(
            new Association(Association.Model.Market, Constants.MARKET_TABLE,
                    Constants.REVENUE_TABLE, null, Association.Type.ManyToOne,
                    Constants.MARKET_ID, Constants.REVENUE_ID, false, "Revenue"),
            new Association(Association.Model.Product, Constants.PRODUCT_TABLE,
                    Constants.REVENUE_TABLE, null, Association.Type.ManyToOne,
                    Constants.PRODUCT_ID, Constants.REVENUE_ID, false, "Revenue"),
            new Association(Association.Model.Segment, Constants.SEGMENT_TABLE,
                    Constants.REVENUE_TABLE, null, Association.Type.ManyToOne,
                    Constants.SEGMENT_ID, Constants.REVENUE_ID, false, "Revenue"),
            new Association(Association.Model.Company, Constants.COMPANY_TABLE,
                    Constants.REVENUE_TABLE, null, Association.Type.ManyToOne,
                    Constants.COMPANY_ID, Constants.REVENUE_ID, false, "Revenue")
    );

    private static final List<String> ATTRS = Collections.synchronizedList(Arrays.asList(
            Constants.NAME,
            Constants.VALUE,
            Constants.PRODUCT_ID,
            Constants.SEGMENT_ID,
            Constants.MARKET_ID,
            Constants.COMPANY_ID,
            Constants.IS_ESTIMATE,
            Constants.IS_PERCENTAGE,
            Constants.NOTES,
            Constants.UPDATED_AT,
            Constants.CREATED_AT
    ));
    public Revenue(Integer id, Map<String,Object> data) {
        super(ASSOCIATIONS, ATTRS, Constants.REVENUE_TABLE, id, data);
    }
}
