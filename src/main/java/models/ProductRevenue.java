package models;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class ProductRevenue extends Model {
    private static final List<Association> ASSOCIATIONS = Collections.singletonList(
            new Association(Association.Model.Product, Constants.PRODUCT_TABLE,
                    Constants.PRODUCT_REVENUE_TABLE, null, Association.Type.ManyToOne,
                    Constants.PRODUCT_ID, Constants.PRODUCT_REVENUE_ID, false, "Product Revenue")
    );

    private static final List<String> ATTRS = Collections.synchronizedList(Arrays.asList(
            Constants.VALUE,
            Constants.YEAR,
            Constants.NOTES,
            Constants.SOURCE,
            Constants.IS_PERCENTAGE,
            Constants.IS_ESTIMATE,
            Constants.ESTIMATE_TYPE,
            Constants.CAGR,
            Constants.PRODUCT_ID,
            Constants.UPDATED_AT,
            Constants.CREATED_AT
    ));
    public ProductRevenue(Integer id, Map<String,Object> data) {
        super(ASSOCIATIONS, ATTRS, Constants.PRODUCT_REVENUE_TABLE, id, data, true);
    }
}
