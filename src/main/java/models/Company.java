package models;

import java.util.*;

public class Company extends Model {
    private static final List<Association> ASSOCIATIONS = Arrays.asList(
            new Association(Association.Model.Product, Constants.COMPANY_TABLE,
                    Constants.PRODUCT_TABLE, null, Association.Type.OneToMany,
                    Constants.COMPANY_ID, Constants.PRODUCT_ID, false, "Company"),
            new Association("Company Revenue", Association.Model.CompanyRevenue, Constants.COMPANY_TABLE,
                    Constants.COMPANY_REVENUE_TABLE, null, Association.Type.OneToMany,
                    Constants.COMPANY_ID, Constants.COMPANY_REVENUE_ID, false, "Company"),
            new Association("Parent Company", Association.Model.Company, Constants.COMPANY_TABLE,
                    Constants.COMPANY_TABLE, null, Association.Type.ManyToOne,
                    Constants.PARENT_COMPANY_ID, Constants.COMPANY_ID, true, "Sub Company"),
            new Association("Sub Company", Association.Model.Company, Constants.COMPANY_TABLE,
                    Constants.COMPANY_TABLE, null, Association.Type.OneToMany,
                    Constants.PARENT_COMPANY_ID, Constants.COMPANY_ID, false, "Parent Company"),
            new Association("Market Share", Association.Model.MarketShareRevenue, Constants.COMPANY_TABLE,
                    Constants.COMPANY_MARKETS_JOIN_TABLE, null, Association.Type.OneToMany,
                    Constants.COMPANY_ID, Constants.MARKET_SHARE_ID, false, "Company")
    );
    private static final List<String> ATTRS = Collections.synchronizedList(Arrays.asList(
            Constants.NAME,
            Constants.PARENT_COMPANY_ID,
            Constants.NOTES,
            Constants.UPDATED_AT,
            Constants.CREATED_AT
    ));
    public Company(Integer id, Map<String,Object> data) {
        super(ASSOCIATIONS, ATTRS, Constants.COMPANY_TABLE, id, data, false);
    }
}
