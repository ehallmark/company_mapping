package models;

import java.util.*;

public class Product extends Model {
    private static final List<Association> ASSOCIATIONS = Arrays.asList(
            new Association("Product Revenue", Association.Model.ProductRevenue, Constants.PRODUCT_TABLE,
                    Constants.PRODUCT_REVENUE_TABLE, null, Association.Type.OneToMany,
                    Constants.PRODUCT_ID, Constants.PRODUCT_REVENUE_ID, false, "Product"),
            new Association(Association.Model.Company, Constants.COMPANY_TABLE,
                    Constants.PRODUCT_TABLE, null, Association.Type.ManyToOne,
                    Constants.COMPANY_ID, Constants.PRODUCT_ID, false, "Product"),
            new Association(Association.Model.Market, Constants.MARKET_TABLE,
                    Constants.PRODUCT_TABLE, null, Association.Type.ManyToOne,
                    Constants.MARKET_ID, Constants.PRODUCT_ID, false, "Product")

    );
    private static final List<String> ATTRS = Collections.synchronizedList(Arrays.asList(
            Constants.NAME,
            Constants.NOTES,
            Constants.COMPANY_ID,
            Constants.MARKET_ID,
            Constants.UPDATED_AT,
            Constants.CREATED_AT
    ));
    public Product(Integer id, Map<String,Object> data) {
        super(ASSOCIATIONS, ATTRS, Constants.PRODUCT_TABLE, id, data, false);
    }
}
