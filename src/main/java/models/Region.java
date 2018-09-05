package models;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class Region extends Model {
    private static final List<Association> ASSOCIATIONS = Arrays.asList(
            new Association("Market Revenue", Association.Model.MarketRevenue, Constants.REGION_TABLE,
                    Constants.MARKET_REVENUE_TABLE, null, Association.Type.OneToMany,
                    Constants.REGION_ID, Constants.MARKET_REVENUE_ID, false, "Region"),
            new Association("Company Revenue", Association.Model.CompanyRevenue, Constants.REGION_TABLE,
                    Constants.COMPANY_REVENUE_TABLE, null, Association.Type.OneToMany,
                    Constants.REGION_ID, Constants.COMPANY_REVENUE_ID, false, "Region"),
            new Association("Product Revenue", Association.Model.ProductRevenue, Constants.REGION_TABLE,
                    Constants.PRODUCT_REVENUE_TABLE, null, Association.Type.OneToMany,
                    Constants.REGION_ID, Constants.PRODUCT_REVENUE_ID, false, "Region"),
            new Association("Market Share", Association.Model.MarketShareRevenue, Constants.REGION_TABLE,
                    Constants.COMPANY_MARKETS_JOIN_TABLE, null, Association.Type.OneToMany,
                    Constants.REGION_ID, Constants.MARKET_SHARE_ID, false, "Region"),
            new Association("Sub Region", Association.Model.Region, Constants.REGION_TABLE,
                    Constants.REGION_TABLE, null, Association.Type.OneToMany,
                    Constants.PARENT_REGION_ID, Constants.REGION_ID, true, "Parent Region"),
            new Association("Parent Region", Association.Model.Region, Constants.REGION_TABLE,
                    Constants.REGION_TABLE, null, Association.Type.ManyToOne,
                    Constants.PARENT_REGION_ID, Constants.REGION_ID, false, "Sub Region")

            );
    private static final List<String> ATTRS = Collections.synchronizedList(Arrays.asList(
            Constants.NAME,
            Constants.PARENT_REGION_ID
    ));
    public Region(Integer id, Map<String,Object> data) {
        super(ASSOCIATIONS, ATTRS, Constants.REGION_TABLE, id, data, false);
    }
}
