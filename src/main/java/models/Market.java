package models;

import java.util.*;

public class Market extends Model {
    private static final List<Association> ASSOCIATIONS = Arrays.asList(
            new Association("Sub Market", Association.Model.Market, Constants.MARKET_TABLE,
                    Constants.MARKET_TABLE, null, Association.Type.OneToMany,
                    Constants.PARENT_MARKET_ID, Constants.MARKET_ID, true, "Parent Market"),
            new Association("Parent Market", Association.Model.Market, Constants.MARKET_TABLE,
                    Constants.MARKET_TABLE, null, Association.Type.ManyToOne,
                    Constants.PARENT_MARKET_ID, Constants.MARKET_ID, false, "Sub Market")
    );
    private static final List<String> ATTRS = Collections.synchronizedList(Arrays.asList(
            Constants.NAME,
            Constants.REVENUE,
            Constants.PARENT_MARKET_ID,
            Constants.NOTES,
            Constants.UPDATED_AT,
            Constants.CREATED_AT
    ));
    public Market(Integer id, Map<String,Object> data) {
        super(ASSOCIATIONS, ATTRS, Constants.MARKET_TABLE, id, data);
    }
}
