package models;

import java.util.*;

public class Product extends Model {
    private static final List<Association> ASSOCIATIONS = Arrays.asList(
            new Association(Association.Model.Company, Constants.COMPANY_TABLE,
                    Constants.PRODUCT_TABLE, null, Association.Type.ManyToOne,
                    Constants.COMPANY_ID, Constants.PRODUCT_ID, false, "Product")
    );
    private static final List<String> ATTRS = Collections.synchronizedList(Arrays.asList(
            Constants.NAME,
            Constants.REVENUE,
            Constants.NOTES,
            Constants.COMPANY_ID,
            Constants.UPDATED_AT,
            Constants.CREATED_AT
    ));
    public Product(Integer id, Map<String,Object> data) {
        super(ASSOCIATIONS, ATTRS, Constants.PRODUCT_TABLE, id, data);
    }
}
