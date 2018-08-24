package models;

import java.util.*;

public class Market extends Model {
    private static final Set<String> ATTRS = Collections.synchronizedSet(new HashSet<>(Arrays.asList(
            Constants.NAME,
            Constants.PARENT_MARKET_ID,
            Constants.NOTES,
            Constants.UPDATED_AT,
            Constants.CREATED_AT
    )));
    public Market(Integer id, Map<String,Object> data) {
        super(ATTRS, Constants.MARKET_TABLE, id, data);
    }
}
