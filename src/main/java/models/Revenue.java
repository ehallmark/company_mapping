package models;

import java.util.*;

public class Revenue extends Model {
    private static final Set<String> ATTRS = Collections.synchronizedSet(new HashSet<>(Arrays.asList(
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
    )));
    public Revenue(Integer id, Map<String,Object> data) {
        super(ATTRS, Constants.REVENUE_TABLE, id, data);
    }
}
