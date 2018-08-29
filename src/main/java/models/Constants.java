package models;

import java.util.*;

public class Constants {
    public static final String COMPANY_TABLE = "companies";
    public static final String MARKET_TABLE = "markets";
    public static final String PRODUCT_TABLE = "products";
    public static final String NAME = "name";
    public static final String NOTES = "notes";
    public static final String REVENUE = "revenue";
    public static final String COMPANY_ID = "company_id";
    public static final String CREATED_AT = "created_at";
    public static final String UPDATED_AT = "updated_at";
    public static final String PARENT_COMPANY_ID = "parent_company_id";
    public static final String PRODUCT_ID = "product_id";
    public static final String MARKET_ID = "market_id";
    public static final String PARENT_MARKET_ID = "parent_market_id";
    public static final String COMPANY_MARKETS_JOIN_TABLE = "companies_markets";
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
        ATTR_MAP.put(PARENT_MARKET_ID, "Parent Market");
        ATTR_MAP.put(PARENT_COMPANY_ID, "Parent Company");
        ATTR_MAP.put(REVENUE, "Revenue");
        ATTR_MAP.put(UPDATED_AT, "Last Updated");
        ATTR_MAP.put(CREATED_AT, "Date Created");
    }

    private static final Set<String> HIDDEN_ATTRS = Collections.synchronizedSet(new HashSet<>());
    static {
        HIDDEN_ATTRS.add(PARENT_COMPANY_ID);
        HIDDEN_ATTRS.add(PARENT_MARKET_ID);
        HIDDEN_ATTRS.add(MARKET_ID);
        HIDDEN_ATTRS.add(PRODUCT_ID);
        HIDDEN_ATTRS.add(COMPANY_ID);
    }

    private static final Map<String,String> FIELD_TYPE_MAP = Collections.synchronizedMap(new HashMap<>());
    static {
        FIELD_TYPE_MAP.put(REVENUE, NUMBER_FIELD_TYPE);
        FIELD_TYPE_MAP.put(NAME, TEXT_FIELD_TYPE);
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
