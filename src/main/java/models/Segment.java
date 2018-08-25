package models;

import java.util.*;

public class Segment extends Model {
    private static final List<Association> ASSOCIATIONS = Arrays.asList(
            new Association(Association.Model.Product, Constants.SEGMENT_TABLE,
                    Constants.PRODUCT_TABLE, null, Association.Type.OneToMany,
                    Constants.SEGMENT_ID, Constants.PRODUCT_ID, false),
            new Association("Parent Segment", Association.Model.Segment, Constants.SEGMENT_TABLE,
                    Constants.SEGMENT_TABLE, null, Association.Type.OneToMany,
                    Constants.PARENT_SEGMENT_ID, Constants.SEGMENT_ID, true),
            new Association("Sub Segment", Association.Model.Segment, Constants.SEGMENT_TABLE,
                    Constants.SEGMENT_TABLE, null, Association.Type.ManyToOne,
                    Constants.PARENT_SEGMENT_ID, Constants.SEGMENT_ID, false),
            new Association(Association.Model.Revenue, Constants.SEGMENT_TABLE,
                    Constants.REVENUE_TABLE, null, Association.Type.OneToMany,
                    Constants.SEGMENT_ID, Constants.REVENUE_ID, true),
            new Association(Association.Model.Market, Constants.SEGMENT_TABLE,
                    Constants.MARKET_TABLE, Constants.SEGMENT_MARKETS_JOIN_TABLE, Association.Type.ManyToMany,
                    Constants.SEGMENT_ID, Constants.MARKET_ID, false),
            new Association(Association.Model.Company, Constants.COMPANY_TABLE,
                            Constants.SEGMENT_TABLE, null, Association.Type.ManyToOne,
                            Constants.COMPANY_ID, Constants.SEGMENT_ID, false)

    );
    private static final List<String> ATTRS = Collections.synchronizedList(Arrays.asList(
            Constants.NAME,
            Constants.COMPANY_ID,
            Constants.PARENT_SEGMENT_ID,
            Constants.NOTES,
            Constants.UPDATED_AT,
            Constants.CREATED_AT
    ));
    public Segment(Integer id, Map<String,Object> data) {
        super(ASSOCIATIONS, ATTRS, Constants.SEGMENT_TABLE, id, data);
    }
}
