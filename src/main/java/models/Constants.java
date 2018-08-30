package models;

import java.util.*;

public class Constants {
    public static final String COMPANY_TABLE = "companies";
    public static final String MARKET_TABLE = "markets";
    public static final String PRODUCT_TABLE = "products";
    public static final String NAME = "name";
    public static final String NOTES = "notes";
    public static final String COMPANY_ID = "company_id";
    public static final String CREATED_AT = "created_at";
    public static final String UPDATED_AT = "updated_at";
    public static final String PARENT_COMPANY_ID = "parent_company_id";
    public static final String PRODUCT_ID = "product_id";
    public static final String MARKET_ID = "market_id";
    public static final String PARENT_MARKET_ID = "parent_market_id";
    public static final String VALUE = "value";
    public static final String YEAR = "year";
    public static final String SOURCE = "source";
    public static final String IS_PERCENTAGE = "is_percentage";
    public static final String IS_ESTIMATE = "is_estimate";
    public static final String ESTIMATE_TYPE = "estimate_type";
    public static final String CAGR = "cagr";
    public static final String MARKET_REVENUE_TABLE = "market_revenues";
    public static final String MARKET_REVENUE_ID = "market_revenue_id";
    public static final String COMPANY_REVENUE_ID = "company_revenue_id";
    public static final String COMPANY_REVENUE_TABLE = "company_revenues";
    public static final String COMPANY_MARKETS_JOIN_TABLE = "companies_markets";
    public static final String PRODUCT_REVENUE_ID = "product_revenue_id";
    public static final String PRODUCT_REVENUE_TABLE = "product_revenues";
    public static final String TEXT_AREA_FIELD_TYPE = "textarea";
    public static final String TEXT_FIELD_TYPE = "text";
    public static final String BOOL_FIELD_TYPE = "boolean";
    public static final String NUMBER_FIELD_TYPE = "number";
    public static final String TEXT_ONLY = "_TEXTONLY";

    private static final Map<String, String> ATTR_MAP = Collections.synchronizedMap(new HashMap<>());
    static {
        ATTR_MAP.put(NOTES, "Notes");
        ATTR_MAP.put(COMPANY_ID, "Company");
        ATTR_MAP.put(NAME, "Name");
        ATTR_MAP.put(PRODUCT_ID, "Product");
        ATTR_MAP.put(MARKET_ID, "Market");
        ATTR_MAP.put(Association.Model.MarketRevenue.toString(), "Market Revenue");
        ATTR_MAP.put(Association.Model.ProductRevenue.toString(), "Product Revenue");
        ATTR_MAP.put(Association.Model.CompanyRevenue.toString(), "Company Revenue");
        ATTR_MAP.put(PARENT_MARKET_ID, "Parent Market");
        ATTR_MAP.put(PARENT_COMPANY_ID, "Parent Company");
        ATTR_MAP.put(VALUE, "Revenue (% or $)");
        ATTR_MAP.put(SOURCE, "Source");
        ATTR_MAP.put(YEAR, "Applicable Year");
        ATTR_MAP.put(IS_ESTIMATE, "Is Estimate?");
        ATTR_MAP.put(IS_PERCENTAGE, "Is Percentage?");
        ATTR_MAP.put(ESTIMATE_TYPE, "Estimate Quality (High/Medium/Low)");
        ATTR_MAP.put(UPDATED_AT, "Last Updated");
        ATTR_MAP.put(CREATED_AT, "Date Created");
        ATTR_MAP.put(MARKET_REVENUE_ID, "Company Revenue");
        ATTR_MAP.put(COMPANY_REVENUE_ID, "Market Revenue");
        ATTR_MAP.put(PRODUCT_REVENUE_ID, "Product Revenue");
        ATTR_MAP.put(CAGR, "CAGR (%)");
    }

    private static final Set<String> HIDDEN_ATTRS = Collections.synchronizedSet(new HashSet<>());
    static {
        HIDDEN_ATTRS.add(PARENT_COMPANY_ID);
        HIDDEN_ATTRS.add(PARENT_MARKET_ID);
        HIDDEN_ATTRS.add(MARKET_ID);
        HIDDEN_ATTRS.add(PRODUCT_ID);
        HIDDEN_ATTRS.add(COMPANY_ID);
        HIDDEN_ATTRS.add(COMPANY_REVENUE_ID);
        HIDDEN_ATTRS.add(PRODUCT_REVENUE_ID);
        HIDDEN_ATTRS.add(MARKET_REVENUE_ID);
    }

    private static final Map<String,String> FIELD_TYPE_MAP = Collections.synchronizedMap(new HashMap<>());
    static {
        FIELD_TYPE_MAP.put(NAME, TEXT_FIELD_TYPE);
        FIELD_TYPE_MAP.put(SOURCE, TEXT_FIELD_TYPE);
        FIELD_TYPE_MAP.put(IS_PERCENTAGE, BOOL_FIELD_TYPE);
        FIELD_TYPE_MAP.put(IS_ESTIMATE, BOOL_FIELD_TYPE);
        FIELD_TYPE_MAP.put(ESTIMATE_TYPE, NUMBER_FIELD_TYPE);
        FIELD_TYPE_MAP.put(YEAR, NUMBER_FIELD_TYPE);
        FIELD_TYPE_MAP.put(VALUE, NUMBER_FIELD_TYPE);
        FIELD_TYPE_MAP.put(CAGR, NUMBER_FIELD_TYPE);
        FIELD_TYPE_MAP.put(MARKET_ID, NUMBER_FIELD_TYPE);
        FIELD_TYPE_MAP.put(PRODUCT_ID, NUMBER_FIELD_TYPE);
        FIELD_TYPE_MAP.put(COMPANY_ID, NUMBER_FIELD_TYPE);

    }

    public static boolean isHiddenAttr(String attr) {
        return HIDDEN_ATTRS.contains(attr);
    };

    public static String humanAttrFor(String attr) {
        return ATTR_MAP.getOrDefault(attr, attr);
    };

    public static String fieldTypeForAttr(String attr) {
        return FIELD_TYPE_MAP.getOrDefault(attr, Constants.TEXT_AREA_FIELD_TYPE);
    };

    public static String pluralizeAssociationName(String associationName) {
        if(associationName.endsWith("y")) {
            return associationName.substring(0, associationName.length()-1)+"ies";
        } else {
            return associationName + "s";
        }
    }
}
