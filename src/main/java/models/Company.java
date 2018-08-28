package models;

import java.util.*;

public class Company extends Model {
    private static final List<Association> ASSOCIATIONS = Arrays.asList(
            new Association(Association.Model.Segment, Constants.COMPANY_TABLE,
                    Constants.SEGMENT_TABLE,null,  Association.Type.OneToMany,
                    Constants.COMPANY_ID, Constants.SEGMENT_ID, true, "Company")
    );
    private static final List<String> ATTRS = Collections.synchronizedList(Arrays.asList(
            Constants.NAME,
            Constants.REVENUE,
            Constants.NOTES,
            Constants.UPDATED_AT,
            Constants.CREATED_AT
    ));
    public Company(Integer id, Map<String,Object> data) {
        super(ASSOCIATIONS, ATTRS, Constants.COMPANY_TABLE, id, data);
    }
}
