package models;

import java.util.*;

public class Segment extends Model {
    private static final List<Association> ASSOCIATIONS = Arrays.asList(
            new Association(Association.Model.Product, Constants.SEGMENT_TABLE,
                    Constants.PRODUCT_TABLE, Association.Type.OneToMany,
                    Constants.SEGMENT_ID, Constants.PRODUCT_ID, false),
            new Association(Association.Model.Segment, Constants.SEGMENT_TABLE,
                    Constants.SEGMENT_TABLE, Association.Type.OneToMany,
                    Constants.PARENT_SEGMENT_ID, Constants.SEGMENT_ID, true),
            new Association(Association.Model.Segment, Constants.SEGMENT_TABLE,
                    Constants.SEGMENT_TABLE, Association.Type.ManyToOne,
                    Constants.SEGMENT_ID, Constants.PARENT_SEGMENT_ID, false),
            new Association(Association.Model.Revenue, Constants.SEGMENT_TABLE,
                    Constants.REVENUE_TABLE, Association.Type.OneToMany,
                    Constants.SEGMENT_ID, Constants.REVENUE_ID, true),
            new Association(Association.Model.Market, Constants.SEGMENT_TABLE,
                    Constants.MARKET_TABLE, Association.Type.ManyToMany,
                    Constants.SEGMENT_ID, Constants.MARKET_ID, false)

    );
    private static final Set<String> ATTRS = Collections.synchronizedSet(new HashSet<>(Arrays.asList(
            Constants.NAME,
            Constants.COMPANY_ID,
            Constants.PARENT_SEGMENT_ID,
            Constants.NOTES,
            Constants.UPDATED_AT,
            Constants.CREATED_AT
    )));
    public Segment(Integer id, Map<String,Object> data) {
        super(ASSOCIATIONS, ATTRS, Constants.SEGMENT_TABLE, id, data);
    }
}
