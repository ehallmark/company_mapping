package models;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class CompanyRevenue extends Model {
    private static final List<Association> ASSOCIATIONS = Arrays.asList(
            new Association(Association.Model.Company, Constants.COMPANY_TABLE,
                    Constants.COMPANY_REVENUE_TABLE, null, Association.Type.ManyToOne,
                    Constants.COMPANY_ID, Constants.COMPANY_REVENUE_ID, false, "Company Revenue"),
            new Association("Sub Revenue", Association.Model.CompanyRevenue, Constants.COMPANY_REVENUE_TABLE,
                    Constants.COMPANY_REVENUE_TABLE, null, Association.Type.OneToMany,
                    Constants.PARENT_REVENUE_ID, Constants.COMPANY_REVENUE_ID, true, "Parent Revenue"),
            new Association("Parent Revenue", Association.Model.CompanyRevenue, Constants.COMPANY_REVENUE_TABLE,
                    Constants.COMPANY_REVENUE_TABLE, null, Association.Type.ManyToOne,
                    Constants.PARENT_REVENUE_ID, Constants.COMPANY_REVENUE_ID, false, "Sub Revenue"),
            new Association(Association.Model.Region, Constants.REGION_TABLE, Constants.COMPANY_REVENUE_TABLE, null,
                    Association.Type.ManyToOne, Constants.REGION_ID, Constants.COMPANY_REVENUE_ID, false, "Company Revenue")
    );

    private static final List<String> ATTRS = Collections.synchronizedList(Arrays.asList(
            Constants.VALUE,
            Constants.YEAR,
            Constants.NOTES,
            Constants.SOURCE,
            Constants.IS_ESTIMATE,
            Constants.ESTIMATE_TYPE,
            Constants.CAGR,
            Constants.COMPANY_ID,
            Constants.UPDATED_AT,
            Constants.CREATED_AT
    ));
    public CompanyRevenue(Integer id, Map<String,Object> data) {
        super(ASSOCIATIONS, ATTRS, Constants.COMPANY_REVENUE_TABLE, id, data, true);
    }
}
