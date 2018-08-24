package models;

import java.util.*;

public class Product extends Model {
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
        super(ATTRS, Constants.PRODUCT_TABLE, id, data);
    }
}
