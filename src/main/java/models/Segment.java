package models;

import java.util.*;

public class Segment extends Model {
    private static final Set<String> ATTRS = Collections.synchronizedSet(new HashSet<>(Arrays.asList(
            Constants.NAME,
            Constants.COMPANY_ID,
            Constants.PARENT_SEGMENT_ID,
            Constants.NOTES,
            Constants.UPDATED_AT,
            Constants.CREATED_AT
    )));
    public Segment(Integer id, Map<String,Object> data) {
        super(ATTRS, Constants.SEGMENT_TABLE, id, data);
    }
}
