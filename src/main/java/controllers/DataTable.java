package controllers;

import com.google.gson.Gson;
import lombok.NonNull;
import spark.QueryParamsMap;
import spark.Request;
import spark.Response;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

public class DataTable {
    public static final String EXCEL_SESSION = "datatable";

    public static void registerDataTabe(@NonNull Request req, @NonNull List<String> headers, @NonNull List<Map<String,String>> data, @NonNull Set<String> numericAttrNames) {
        Map<String,Object> excelSession = Collections.synchronizedMap(new HashMap<>());

        // headers
        excelSession.put("headers", new ArrayList<>(headers));

        // rows
        excelSession.put("rows", new ArrayList<>(data));

        // numericAttrNames
        excelSession.put("numericAttrNames", new HashSet<>(numericAttrNames));

        // lock
        excelSession.put("lock", new ReentrantLock());

        req.session(false).attribute(EXCEL_SESSION, excelSession);
    }

    public static void unregisterDataTable(Request req) {
        req.session(false).removeAttribute(EXCEL_SESSION);
    }


    public static Object handleDataTable(Request req, Response res) {
        System.out.println("Received data table request.....");
        Map<String,Object> response = new HashMap<>();
        // try to get custom data
        List<String> headers;
        List<Map<String,String>> data;
        Set<String> numericAttrNames;
        try {
            System.out.println("Received datatable request");
            Map<String,Object> map = req.session(false).attribute(EXCEL_SESSION);
            if(map==null) return null;

            headers = (List<String>)map.getOrDefault("headers",Collections.emptyList());
            data = (List<Map<String,String>>)map.getOrDefault("rows",Collections.emptyList());
            numericAttrNames = (Set<String>)map.getOrDefault("numericAttrNames",Collections.emptySet());
            System.out.println("Number of headers: "+headers.size());

            Lock lock = (Lock)map.get("lock");
            try {
                lock.lock();
                int perPage = extractInt(req, "perPage", 10);
                int page = extractInt(req, "page", 1);
                int offset = extractInt(req, "offset", 0);

                long totalCount = data.size();
                // check for search
                List<Map<String, String>> queriedData;
                String searchStr;
                if (req.queryMap("queries") != null && req.queryMap("queries").hasKey("search")) {
                    searchStr = req.queryMap("queries").value("search").toLowerCase();
                    if (searchStr == null || searchStr.trim().isEmpty()) {
                        queriedData = data;
                    } else {
                        queriedData = new ArrayList<>(data.stream().filter(m -> m.values().stream().anyMatch(val -> val.toLowerCase().contains(searchStr))).collect(Collectors.toList()));
                    }
                } else {
                    searchStr = "";
                    queriedData = data;
                }
                long queriedCount = queriedData.size();
                // check for sorting
                if (req.queryMap("sorts") != null) {
                    req.queryMap("sorts").toMap().forEach((k, v) -> {
                        System.out.println("Sorting " + k + ": " + v);
                        if (v == null || k == null) return;
                        boolean isNumericField = numericAttrNames.contains(k);
                        boolean reversed = (v.length > 0 && v[0].equals("-1"));

                        String directionStr = reversed ? "-1" : "1";

                        String sortStr = k + directionStr + searchStr;
                        System.out.println("New sort string: " + sortStr);

                        Comparator<Map<String, String>> comp = (d1, d2) -> {
                            if (isNumericField) {
                                Double v1 = null;
                                Double v2 = null;
                                try {
                                    v1 = Double.valueOf(d1.get(k));
                                } catch (Exception nfe) {
                                }
                                try {
                                    v2 = Double.valueOf(d2.get(k));
                                } catch (Exception e) {
                                }
                                if (v1 == null && v2 == null) return 0;
                                if (v1 == null) return 1;
                                if (v2 == null) return -1;
                                return v1.compareTo(v2) * (reversed ? -1 : 1);
                            } else {
                                return d1.get(k).compareTo(d2.get(k)) * (reversed ? -1 : 1);
                            }
                        };
                        queriedData.sort(comp);

                    });
                }
                List<Map<String, String>> dataPage;
                if (offset < totalCount) {
                    dataPage = queriedData.subList(offset, Math.min(queriedData.size(), offset + perPage));
                } else {
                    dataPage = Collections.emptyList();
                }
                response.put("totalRecordCount",totalCount);
                response.put("queryRecordCount",queriedCount);
                response.put("records", dataPage);

            } catch(Exception e) {
                e.printStackTrace();
            } finally {
                lock.unlock();
            }

            return new Gson().toJson(response);
        } catch (Exception e) {
            System.out.println(e.getClass().getName() + ": " + e.getMessage());
            response.put("totalRecordCount",0);
            response.put("queryRecordCount",0);
            response.put("records",Collections.emptyList());
        }
        return new Gson().toJson(response);
    }

    public static Integer extractInt(Request req, String param, Integer defaultVal) {
        return extractInt(req.queryMap(),param, defaultVal);
    }
    static Integer extractInt(QueryParamsMap req, String param, Integer defaultVal) {
        try {
            return Integer.valueOf(req.value(param));
        } catch(Exception e) {
            System.out.println("No "+param+" parameter specified... using default");
            return defaultVal;
        }
    }

}
