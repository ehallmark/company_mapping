package models;

import java.util.*;

public class Company extends Model {
    private static final Set<String> ATTRS = Collections.synchronizedSet(new HashSet<>(Arrays.asList(
            Constants.NAME,
            Constants.MARKET_ID,
            Constants.NOTES,
            Constants.UPDATED_AT,
            Constants.CREATED_AT
    )));
    public Company(Integer id, Map<String,Object> data) {
        super(ATTRS, Constants.COMPANY_TABLE, id, data);
    }
}
