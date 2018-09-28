package controllers;

import auth.PasswordException;
import auth.PasswordHandler;
import com.google.gson.Gson;
import com.googlecode.wickedcharts.highcharts.jackson.JsonRenderer;
import com.googlecode.wickedcharts.highcharts.options.Options;
import database.Database;
import graph.Graph;
import graph.Node;
import j2html.tags.ContainerTag;
import lombok.NonNull;
import models.*;
import spark.QueryParamsMap;
import spark.Request;
import spark.Response;
import spark.Session;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.OutputStream;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.RecursiveTask;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static j2html.TagCreator.*;
import static j2html.TagCreator.head;
import static spark.Spark.*;

public class Main {
    private static final String NAVIGATION_HANDLER = "navigation_handler";
    private static final String NAVIGATION_LOCK = "navigation_lock";
    private static final String CHART_CACHE = "chart_cache";
    private static final String EXPANDED_NODES_SET = "expanded_nodes_set";
    private static final String SHOW_PAGE_ID = "show_page_resource_id";
    private static final String SHOW_PAGE_RESOURCE = "show_page_resource";
    public static final String DEFAULT_FORM_OPTIONS = "default_form_options";
    public static final String DEFAULT_REPORT_OPTIONS = "default_report_options";
    private static final int MAX_NAVIGATION_HISTORY = 30;

    public static ContainerTag getBackButton(@NonNull Request req) {
        return a("Back").withClass("btn btn-sm btn-outline-secondary back-button");
    }

    private static void registerLatestForm(Request req, String sessionParam) {
        Map<String,String[]> latestForm = new HashMap<>();

        for(String key : req.queryParams()) {
            latestForm.put(key, req.queryParamsValues(key));
        }

        System.out.println("Registered form: "+new Gson().toJson(latestForm));
        req.session().attribute(sessionParam, latestForm);
    }

    public static void registerNextPage(@NonNull Request req, @NonNull Response res) {
        if(req.queryParams("redirected")!=null) {
            return;
        }
        Lock lock = req.session().attribute(NAVIGATION_LOCK);
        if(lock==null) {
            lock = new ReentrantLock();
            req.session().attribute(NAVIGATION_LOCK);
        }
        lock.lock();
        try {
            List<String> navigation = req.session().attribute(NAVIGATION_HANDLER);
            if (navigation == null) {
                navigation = Collections.synchronizedList(new ArrayList<>());
                req.session().attribute(NAVIGATION_HANDLER, navigation);
            }
            while(navigation.size() >= MAX_NAVIGATION_HISTORY) {
                navigation.remove(0);
            }
            String url = req.url();
            navigation.add(url);

        } finally {
            lock.unlock();
        }
    }

    private synchronized static void registerShowPage(Request req, Response res) {
        Model model = loadModel(req);
        req.session().attribute(EXPANDED_NODES_SET, Collections.synchronizedSet(new HashSet<Node>()));
        req.session().attribute(SHOW_PAGE_ID, model.getType().toString()+model.getId());
        req.session().attribute(SHOW_PAGE_RESOURCE, model.getType().toString());
    }

    private synchronized static boolean stayedOnSameShowPage(Request req, Response res) {
        Model model = loadModel(req);
        String prevId = req.session().attribute(SHOW_PAGE_ID);
        return (prevId!=null && prevId.equals(model.getType().toString()+model.getId()));
    }

    private synchronized static void clearShowPage(Request req, Response res) {
        req.session().removeAttribute(EXPANDED_NODES_SET);
        req.session().removeAttribute(SHOW_PAGE_ID);
        req.session().removeAttribute(SHOW_PAGE_RESOURCE);
    }

    private synchronized static Set<Node> getRegisteredExpandedResourcesForShowPage(Request req, Response res) {
        Set<Node> expandedNodes = req.session().attribute(EXPANDED_NODES_SET);
        return expandedNodes;
    }

    private synchronized static void registerExpandedResourceForShowPage(Request req, Response res) {
        Model model = loadModel(req);
        Graph graph = Graph.load();
        Node node = graph.findNode(model.getType(), model.getId());
        Set<Node> nodes = getRegisteredExpandedResourcesForShowPage(req, res);
        nodes.add(node);
        // add parent associations
        if(model.isRevenueModel()) {
            model.loadAssociations();
            for(Association association : model.getAssociationsMeta()) {
                if(!association.getAssociationName().startsWith("Sub") && !association.getModel().equals(Association.Model.Region)) {
                    List<Model> parents = model.getAssociations().get(association);
                    if(parents!=null && parents.size()==1) {
                        Model parent = parents.get(0);
                        nodes.add(graph.findNode(parent.getType(), parent.getId()));
                    }
                }
            }
        } else {
            if(model instanceof Market) {
                // check parent markets
                for(Association association : model.getAssociationsMeta()) {
                    if(association.getAssociationName().startsWith("Parent ")) {
                        List<Model> parents = model.getAssociations().get(association);
                        if(parents!=null && parents.size()==1) {
                            Model parent = parents.get(0);
                            nodes.add(graph.findNode(parent.getType(), parent.getId()));
                        }
                    }
                }
            }
        }
    }

    public static String goBack(@NonNull Request req) {
        Lock lock = req.session().attribute(NAVIGATION_LOCK);
        if(lock==null) {
            lock = new ReentrantLock();
            req.session().attribute(NAVIGATION_LOCK);
        }
        lock.lock();
        try {
            List<String> navigation = req.session().attribute(NAVIGATION_HANDLER);
            if (navigation == null || navigation.size()==0) {
                return null; // nothing found
            }
            if(navigation.size()==1) {
                return navigation.get(0); // return only page
            }
            navigation.remove(navigation.size()-1); // delete current page
            return  navigation.get(navigation.size()-1); // get previous page
        } finally {
            lock.unlock();
        }
    }


    private static Model loadModel(Request req) {
        String resource = req.params("resource");
        int id;
        Association.Model type;
        try {
            id = Integer.valueOf(req.params("id"));
            type = Association.Model.valueOf(resource);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
        return loadModel(type, id);
    }

    public static Model getModelByType(Association.Model type) {
        return loadModel(type, null);
    }

    private static Model loadModel(@NonNull Association.Model type, Integer id) {
        Model model;
        if(id!=null) {
            Graph graph = Graph.load();
            Node node = graph.findNode(type, id);
            if(node!=null) {
                model = node.getModel();
            } else {
                model = null;
            }
        } else {
            switch (type) {
                case Market: {
                    model = new Market(null, null);
                    break;
                }
                case Product: {
                    model = new Product(null, null);
                    break;
                }
                case Company: {
                    model = new Company(null, null);
                    break;
                }
                case MarketRevenue: {
                    model = new MarketRevenue(null, null);
                    break;
                }
                case CompanyRevenue: {
                    model = new CompanyRevenue(null, null);
                    break;
                }
                case ProductRevenue: {
                    model = new ProductRevenue(null, null);
                    break;
                }
                case MarketShareRevenue: {
                    model = new MarketShareRevenue(null, null);
                    break;
                }
                case Region: {
                    model = new Region(null, null);
                    break;
                }
                default: {
                    model = null;
                    break;
                }
            }
        }
        if(model != null && model.existsInDatabase()) {
            model.loadAttributesFromDatabase();
        }
        return model;
    }


    private static Object handleAjaxRequest(Request req, Function<String,List<String>> resultsSearchFunction, Function<String,String> labelFunction, Function<String,String> htmlResultFunction) {
        int PER_PAGE = 30;
        String search = req.queryParams("search");
        int page = Integer.valueOf(req.queryParamOrDefault("page", "1"));

        System.out.println("Search: " + search);
        System.out.println("Page: " + page);

        List<String> allResults = resultsSearchFunction.apply(search);

        int start = (page - 1) * PER_PAGE;
        int end = start + PER_PAGE;

        List<Map<String, Object>> results;
        if (start >= allResults.size()) {
            results = Collections.emptyList();
        } else {
            results = allResults.subList(start, Math.min(allResults.size(), end)).stream().map(result -> {
                Map<String, Object> map = new HashMap<>();
                map.put("id", result);
                String html = htmlResultFunction == null ? null : htmlResultFunction.apply(result);
                if(result!=null) {
                    map.put("html_result", html);
                }
                map.put("text", labelFunction.apply(result));
                return map;
            }).collect(Collectors.toList());
        }

        Map<String, Boolean> pagination = new HashMap<>();
        pagination.put("more", end < allResults.size());

        Map<String, Object> response = new HashMap<>();
        response.put("results", results);
        response.put("pagination", pagination);
        return new Gson().toJson(response);
    }

    public static String extractString(Request req, String param, String defaultVal) {
        return extractString(req.queryMap(),param, defaultVal);
    }
    public static String extractString(QueryParamsMap paramsMap, String param, String defaultVal) {
        if(paramsMap.value(param)!=null&&paramsMap.value(param).trim().length()>0) {
            return paramsMap.value(param).replaceAll("\\r","");
        } else {
            return defaultVal;
        }
    }

    private static void authorize(Request req, Response res) {
        try {
            if (req.session().attribute("authorized") == null || ! (Boolean) req.session().attribute("authorized")) {
                res.redirect("/");
                halt("Access expired. Please sign in.");
            }
        } catch(Exception e) {
            e.printStackTrace();
            res.redirect("/");
            halt("Error during authentication.");
        }
    }

    private static boolean softAuthorize(Request req, Response res) {
        try {
            if (req.session().attribute("authorized") == null || ! (Boolean) req.session().attribute("authorized")) {
                return false;
            }
        } catch(Exception e) {
            e.printStackTrace();
            return false;
        }
        return true;
    }

    public static List<Model> selectAll(Model model, Association.Model type, List<String> headers, List<String> humanHeaders, Set<String> numericAttrs) throws Exception {
        for(String header : model.getAvailableAttributes()) {
            if (!Constants.isHiddenAttr(header) && !Arrays.asList(Constants.UPDATED_AT,Constants.CREATED_AT).contains(header)) {
                headers.add(header);
                humanHeaders.add(Constants.humanAttrFor(header));
            }
        }
        List<Association> associations = model.getAssociationsMeta();
        associations = associations.stream().filter(association->{
            return association.getType().equals(Association.Type.ManyToOne);
        }).collect(Collectors.toList());

        for(Association association : associations) {
            if(model.isRevenueModel()) {
                headers.add(0, association.getAssociationName().toLowerCase().replace(" ", "-"));
                boolean pluralize = Arrays.asList(Association.Type.OneToMany, Association.Type.ManyToMany).contains(association.getType());
                humanHeaders.add(0, pluralize ? Constants.pluralizeAssociationName(association.getAssociationName()) : association.getAssociationName());
            } else {
                headers.add(association.getAssociationName().toLowerCase().replace(" ", "-"));
                boolean pluralize = Arrays.asList(Association.Type.OneToMany, Association.Type.ManyToMany).contains(association.getType());
                humanHeaders.add(pluralize ? Constants.pluralizeAssociationName(association.getAssociationName()) : association.getAssociationName());
            }
        }

        for(String header : headers) {
            if(Constants.fieldTypeForAttr(header).equals(Constants.NUMBER_FIELD_TYPE)) {
                numericAttrs.add(header);
            }
        }

        List<String> availableAttributes = new ArrayList<>(model.getAvailableAttributes());
        if(model.isRevenueModel()) {
            humanHeaders.add(0, Constants.humanAttrFor(Constants.NAME));
            headers.add(0, Constants.NAME);
            availableAttributes.add(0, Constants.NAME);
        }
        return Database.selectAll(model.isRevenueModel(), type, model.getTableName(), availableAttributes, associations, null);
    }

    public static ContainerTag getReportOptionsForm(Request req, Model model, String clazz, ContainerTag... additionalTags) {
        Map<String,String[]> defaultValues = req.session().attribute(DEFAULT_FORM_OPTIONS);
        if(defaultValues==null) defaultValues = new HashMap<>();
        boolean showCountry = "national".equals(defaultValues.getOrDefault("revenue_domain", new String[]{null})[0]);
        boolean showRegion = "regional".equals(defaultValues.getOrDefault("revenue_domain", new String[]{null})[0]);
        boolean defaultRegionIsRegion = defaultValues.containsKey(Constants.REGION_ID) && Graph.load().findNode(Association.Model.Region, Integer.valueOf(defaultValues.get(Constants.REGION_ID)[0])).getModel().getData().get(Constants.PARENT_REGION_ID) == null;
        boolean defaultRegionIsCountry = defaultValues.containsKey(Constants.REGION_ID) && Graph.load().findNode(Association.Model.Region, Integer.valueOf(defaultValues.get(Constants.REGION_ID)[0])).getModel().getData().get(Constants.PARENT_REGION_ID) != null;
        return form().attr("data-id",model.getId().toString())
                .attr("data-resource",model.getClass().getSimpleName()).withId(clazz+"-specification-form").with(
                        label("Start Year").with(br(),
                                input().withType("number").withValue(defaultValues.getOrDefault("start_year", new String[]{String.valueOf(LocalDate.now().getYear())})[0]).withName("start_year")
                        ),
                        br(),
                        label("End Year").with(br(),
                                input().withType("number").withValue(defaultValues.getOrDefault("end_year", new String[]{String.valueOf(LocalDate.now().getYear()+5)})[0]).withName("end_year")
                        ),br(),
                        label("Discount Rate (%)").with(br(),
                                input().withType("number").withValue(defaultValues.getOrDefault("discount_rate", new String[]{"10"})[0]).withName("discount_rate")
                        ),br(),
                        label("Market Depth").with(br(),
                                select().withClass("multiselect").withName("market_depth").with(
                                        option("Top Level")
                                                .attr(Arrays.asList("0", null).contains(defaultValues.getOrDefault("market_depth", new String[]{null})[0])?"selected":"")
                                                .withValue("0"),
                                        option("Second Level")
                                                .attr("1".equals(defaultValues.getOrDefault("market_depth", new String[]{null})[0])?"selected":"")
                                                .withValue("1"),
                                        option("Third Level")
                                                .attr("2".equals(defaultValues.getOrDefault("market_depth", new String[]{null})[0])?"selected":"")
                                                .withValue("2")
                                )
                        ),br(),
                        label("Revenue Domain").with(br(),
                                select().withClass("multiselect revenue_domain").withName("revenue_domain").with(
                                        option("Global").withValue("global").attr(!showCountry&&!showRegion?"selected":""),
                                        option("Regional").withValue("regional").attr(showRegion?"selected":""),
                                        option("National").withValue("national").attr(showCountry?"selected":"")
                                )
                        ),
                        label("Country").attr("style", "display: "+(showCountry?"block":"none")+"; width: 250px; margin-left: auto; margin-right: auto;").with(
                                select().attr(showCountry?"":"disabled").attr("style","width: 100%").withClass("form-control multiselect-ajax revenue-national")
                                        .withName(Constants.REGION_ID)
                                        .attr("data-url", "/ajax/resources/"+Association.Model.Region+"/"+model.getType()+"/-1?nationalities_only=true")
                                        .with(defaultRegionIsCountry?option(Graph.load().findNode(Association.Model.Region, Integer.valueOf(defaultValues.get(Constants.REGION_ID)[0])).getModel().getName())
                                                .attr("selected").withValue(defaultValues.get(Constants.REGION_ID)[0]): null)
                        ),
                        label("Region").attr("style", "display: "+(showRegion?"block":"none")+"; width: 250px; margin-left: auto; margin-right: auto;").with(
                                select().attr(showRegion?"":"disabled").attr("style","width: 100%").withClass("form-control multiselect-ajax revenue-regional")
                                        .withName(Constants.REGION_ID)
                                        .attr("data-url", "/ajax/resources/"+Association.Model.Region+"/"+model.getType()+"/-1?regions_only=true")
                                .with(defaultRegionIsRegion?option(Graph.load().findNode(Association.Model.Region, Integer.valueOf(defaultValues.get(Constants.REGION_ID)[0])).getModel().getName())
                                    .attr("selected").withValue(defaultValues.get(Constants.REGION_ID)[0]): null)
                        ), br(),
                        label("Use CAGR when applicable?").with(br(),
                                input().attr(defaultValues.containsKey(Constants.CAGR)?"checked":"").withType("checkbox").withValue("true").withName(Constants.CAGR)
                        ),
                        br(),
                        label("Estimate CAGR when applicable?").with(br(),
                                input().attr(defaultValues.containsKey(Constants.ESTIMATE_CAGR)?"checked":"").withType("checkbox").withValue("true").withName(Constants.ESTIMATE_CAGR)
                        ),
                        br(),
                        label("Missing Revenue Options").with(br(),
                                select().withClass("multiselect").withName("missing_revenue").with(
                                        option("Exclude missing")
                                                .attr(Constants.MissingRevenueOption.exclude.toString().equals(defaultValues.get("missing_revenue"))?"selected":"")
                                                .withValue(Constants.MissingRevenueOption.exclude.toString()),
                                        option("Replace with zeros")
                                                .attr(Constants.MissingRevenueOption.replace.toString().equals(defaultValues.get("missing_revenue"))?"selected":"")
                                                .withValue(Constants.MissingRevenueOption.replace.toString()),
                                        option("Raise error")
                                                .attr(Constants.MissingRevenueOption.error.toString().equals(defaultValues.get("missing_revenue"))?"selected":"")
                                                .withValue(Constants.MissingRevenueOption.error.toString())
                                )
                        ),br()
                ).with(additionalTags).with(br(),
                        button("Generate").withType("submit").withClass("btn btn-sm btn-outline-secondary")
                );
    }

    private static void addAttributesToModel(Model model, Request req, boolean withAssociations, boolean requireParam) {
        model.getAvailableAttributes().forEach(attr->{
            if(!Arrays.asList(Constants.UPDATED_AT, Constants.CREATED_AT).contains(attr)) {
                Object val = extractString(req, attr, null);
                if (!requireParam || req.queryParams().contains(attr)) {
                    String fieldType = Constants.fieldTypeForAttr(attr);
                    if (val != null) {
                        val = val.toString().trim();
                    }
                    if (attr.endsWith("_id")) {
                        if (withAssociations) {
                            // update association
                            Association association = model.getAssociationsMeta().stream().filter(a -> a.getParentIdField().equals(attr) && a.getType().equals(Association.Type.ManyToOne)).findAny().orElse(null);
                            if (association != null) {
                                try {
                                    val = val == null || val.toString().trim().isEmpty() ? null : Integer.valueOf(val.toString().trim());
                                } catch (Exception e) {
                                    e.printStackTrace();
                                    val = null;
                                }
                                if (val != null) {
                                    Model related = loadModel(association.getModel(), (Integer) val);
                                    handleNewAssociation(model, related, association.getAssociationName());
                                    return;
                                }
                            }
                        } else if (val != null) {
                            try {
                                val = Double.valueOf(val.toString().trim());
                            } catch (Exception e) {
                                e.printStackTrace();
                                val = 0;
                            }
                        }
                    } else if (val != null && fieldType.equals(Constants.NUMBER_FIELD_TYPE)) {
                        if(attr.equals(Constants.ESTIMATE_TYPE)) {
                            try {
                                val = Integer.valueOf(val.toString().trim());
                            } catch (Exception e) {
                                e.printStackTrace();
                                val = 0;
                            }
                        } else {
                            try {
                                val = Double.valueOf(val.toString().trim());
                            } catch (Exception e) {
                                e.printStackTrace();
                                val = 0;
                            }
                        }
                    } else if (fieldType.equals(Constants.BOOL_FIELD_TYPE)) {
                        val = val != null && val.toString().toLowerCase().trim().startsWith("t");
                    }
                    model.updateAttribute(attr, val);
                }
            }
        });
    }

    private static String handleShowReports(Request req, Response res, ContainerTag... additionalTags) {
        Map<String,String[]> defaultValues = req.session().attribute(DEFAULT_REPORT_OPTIONS);
        if(defaultValues==null) defaultValues = new HashMap<>();
        final boolean showCountry = true;
        final boolean showRegion = true;
        String html = div().withClass("col-12").with(
                h4("Report Generation"),
                form().withClass("main_reports_options_form").with(
                        label("Start Year (used for NPV)").with(br(),
                                input().withType("number").withValue(defaultValues.getOrDefault("start_year", new String[]{String.valueOf(LocalDate.now().getYear())})[0]).withName("start_year")
                        ),
                        br(),
                        label("End Year (used for NPV)").with(br(),
                                input().withType("number").withValue(defaultValues.getOrDefault("end_year", new String[]{String.valueOf(LocalDate.now().getYear()+5)})[0]).withName("end_year")
                        ),br(),
                        label("Discount Rate (%)").with(br(),
                                input().withType("number").withValue(defaultValues.getOrDefault("discount_rate", new String[]{"10"})[0]).withName("discount_rate")
                        ),br(),
                        label("Market Depth").with(br(),
                                select().withClass("multiselect").withName("market_depth").with(
                                        option("Top Level")
                                                .attr(Arrays.asList("0", null).contains(defaultValues.getOrDefault("market_depth", new String[]{null})[0])?"selected":"")
                                                .withValue("0"),
                                        option("Second Level")
                                                .attr("1".equals(defaultValues.getOrDefault("market_depth", new String[]{null})[0])?"selected":"")
                                                .withValue("1"),
                                        option("Third Level")
                                                .attr("2".equals(defaultValues.getOrDefault("market_depth", new String[]{null})[0])?"selected":"")
                                                .withValue("2")
                                )
                        ),br(),
                        label("Applicable Company").with(
                                br(),
                                select().withClass("multiselect-ajax")
                                        .withName(Constants.COMPANY_ID).attr("data-url", "/ajax/resources/Company/Market/-1")
                                        .with(defaultValues.containsKey(Constants.COMPANY_ID)?option(Graph.load().findNode(Association.Model.Company, Integer.valueOf(defaultValues.get(Constants.COMPANY_ID)[0])).getModel().getName())
                                                .attr("selected").withValue(defaultValues.get(Constants.COMPANY_ID)[0]): null)
                        ),br(),
                        label("Regions").attr("style", "display: "+(showRegion?"block":"none")+"; width: 250px; margin-left: auto; margin-right: auto;").with(
                                select().attr("multiple").attr("style","width: 100%").withClass("form-control multiselect-ajax revenue-regional")
                                        .withName(Constants.REGION_ID)
                                        .attr("data-url", "/ajax/resources/"+Association.Model.Region+"/Market/-1?regions_only=true")
                                        .with(defaultValues.containsKey(Constants.REGION_ID)?Stream.of(defaultValues.get(Constants.REGION_ID)).map(val->Graph.load().findNode(Association.Model.Region, Integer.valueOf(val)).getModel())
                                                .filter(m->m.getData().get(Constants.PARENT_REGION_ID)==null)
                                                .map(m->option(m.getName()).attr("selected").withValue(m.getId().toString()))
                                                .collect(Collectors.toList()) : null)
                        ),
                        label("Countries").attr("style", "display: "+(showCountry?"block":"none")+"; width: 250px; margin-left: auto; margin-right: auto;").with(
                                select().attr("multiple").attr("style","width: 100%").withClass("form-control multiselect-ajax revenue-national")
                                        .withName(Constants.REGION_ID)
                                        .attr("data-url", "/ajax/resources/"+Association.Model.Region+"/Market/-1?nationalities_only=true")
                                        .with(defaultValues.containsKey(Constants.REGION_ID)?Stream.of(defaultValues.get(Constants.REGION_ID)).map(val->Graph.load().findNode(Association.Model.Region, Integer.valueOf(val)).getModel())
                                                .filter(m->m.getData().get(Constants.PARENT_REGION_ID)!=null)
                                                .map(m->option(m.getName()).attr("selected").withValue(m.getId().toString()))
                                                .collect(Collectors.toList()) : null)
                        ),br(),
                        label("Use CAGR when applicable?").with(br(),
                                input().attr(defaultValues.containsKey(Constants.CAGR)?"checked":"").withType("checkbox").withValue("true").withName(Constants.CAGR)
                        ),
                        br(),
                        label("Estimate CAGR when applicable?").with(br(),
                                input().attr(defaultValues.containsKey(Constants.ESTIMATE_CAGR)?"checked":"").withType("checkbox").withValue("true").withName(Constants.ESTIMATE_CAGR)
                        ),
                        br(),
                        label("Missing Revenue Options").with(br(),
                                select().withClass("multiselect").withName("missing_revenue").with(
                                        option("Exclude missing")
                                                .attr(Constants.MissingRevenueOption.exclude.toString().equals(defaultValues.getOrDefault("missing_revenue", new String[]{null})[0])?"selected":"")
                                                .withValue(Constants.MissingRevenueOption.exclude.toString()),
                                        option("Replace with zeros")
                                                .attr(Constants.MissingRevenueOption.replace.toString().equals(defaultValues.getOrDefault("missing_revenue", new String[]{null})[0])?"selected":"")
                                                .withValue(Constants.MissingRevenueOption.replace.toString()),
                                        option("Raise error")
                                                .attr(Constants.MissingRevenueOption.error.toString().equals(defaultValues.getOrDefault("missing_revenue", new String[]{null})[0])?"selected":"")
                                                .withValue(Constants.MissingRevenueOption.error.toString())
                                )
                        ),br()
                ).with(additionalTags).with(br(),
                        button("Generate").withType("submit").withClass("btn btn-sm btn-outline-secondary")
                ), br(),
                div().withClass("col-12").withId("inner-results")
        ).render();
        return new Gson().toJson(Collections.singletonMap("result", html));
    }

    private static void handleNewAssociation(Model baseModel, Model relatedModel, String associationName) {
        if(!(baseModel.isRevenueModel() && relatedModel.isRevenueModel())) {
            try {
                baseModel.removeManyToOneAssociations(associationName);
            } catch(Exception e) {
                e.printStackTrace();
            }
            baseModel.associateWith(relatedModel, associationName, Collections.emptyMap());
        } else {
            try {
                baseModel.associateWith(relatedModel, associationName, Collections.emptyMap());
            } catch(Exception e) {
                e.printStackTrace();
            }
        }
    }

    public static void main(String[] args)  {
        staticFiles.externalLocation(new File("public").getAbsolutePath());
        final PasswordHandler passwordHandler = new PasswordHandler();
        port(6969);
        Graph.load();

        get("/create_user", (req, res)->{
            authorize(req,res);
            if(!req.session(false).attribute("username").equals("ehallmark")) {
                halt("Error");
            }
            String message = req.session().attribute("message");
            req.session().removeAttribute("message");
            ContainerTag form = form().withId("create-user-form").withAction("/new_user").withMethod("POST").attr("style","margin-top: 100px;").with(
                    (message == null ? span() : div().withClass("not-implemented").withText(
                            message
                    )),br(),
                    label("Username").with(
                            input().withType("text").withClass("form-control").withName("username")
                    ), br(), br(), label("Password").with(
                            input().withType("password").withClass("form-control").withName("password")
                    ), br(), br(), button("Create User").withClass("btn btn-outline-secondary")
            );
            return templateWrapper(form).render();
        });

        post("/new_user", (req,res)->{
            authorize(req,res);
            if(!req.session(false).attribute("username").equals("ehallmark")) {
                halt("Error");
            }
            String username = extractString(req, "username", null);
            String password = extractString(req, "password", null);
            String redirect;
            String message = null;
            if(password == null || username == null) {
                message = "Please enter a username and password.";
            }
            if(message == null) {
                try {
                    passwordHandler.createUser(username, password);
                    redirect = "/";
                    message = "Successfully created user "+username+".";
                } catch (Exception e) {
                    System.out.println("Error while creating user...");
                    e.printStackTrace();
                    redirect = "/create_user";
                    message = e.getMessage();
                }
            } else {
                redirect = "/create_user";
            }
            res.redirect(redirect);
            req.session().attribute("message", message);
            return null;
        });

        post("/login", (req,res)->{
            Session session = req.session(true);
            String username = extractString(req, "username", "");
            String password = extractString(req, "password", "");
            boolean authorized = passwordHandler.authorizeUser(username,password);
            session.attribute("authorized",authorized);
            if(!authorized) {
                halt("User not found.");
            }
            session.attribute("username",username);
            res.status(200);
            res.redirect("/");
            req.session().attribute(NAVIGATION_LOCK, new ReentrantLock());
            req.session().removeAttribute(NAVIGATION_HANDLER);
            return null;
        });

        post("/update_password", (req, res) -> {
            authorize(req, res);
            String username = req.session(false).attribute("username");
            String newPassword = extractString(req, "password", "");
            try {
                passwordHandler.forceChangePassword(username, newPassword);
                req.session(false).attribute("password", newPassword);
                return new Gson().toJson(Collections.singletonMap("success", "Successfully updated password."));
            } catch(PasswordException e) {
                e.printStackTrace();
                return new Gson().toJson(Collections.singletonMap("result", "Error: "+e.getMessage()));
            }
        });

        get("/logout", (req,res)->{
            req.session(true).attribute("authorized",false);
            req.session().removeAttribute("username");
            req.session().removeAttribute("password");
            req.session().removeAttribute(NAVIGATION_LOCK);
            req.session().removeAttribute(NAVIGATION_HANDLER);
            res.redirect("/");
            res.status(200);
            return null;
        });

        get("/back", (req, res) -> {
            String redirect = goBack(req);
            Map<String, Object> results = new HashMap<>();
            if(redirect==null) {
                results.put("error", "Unable to go back.");
            } else {
                res.redirect(redirect+"?redirected=true");
                return null;
            }
            return new Gson().toJson(results);
        });

        get("/", (req, res)->{
            // home page
            req.session(true);
            boolean authorized = softAuthorize(req, res);
            return templateWrapper(
                   div().withClass("container-fluid text-center").attr("style","height: 100%; z-index: 1;").with(
                            div().withClass("row").attr("style","height: 100%;").with(
                                    nav().withClass("sidebar col-3").attr("style","z-index: 2; overflow-y: auto; height: 100%; position: fixed; padding-top: 75px;").with(
                                            div().withClass("row").with(
                                                    div().withClass("col-12").with(
                                                        authorized?a("Sign Out").withHref("/logout"):span()
                                                    ),
                                                    div().withClass("col-12").with(
                                                            authorized?div().with(
                                                                    a("Change Password").withClass("change-password-link").withHref("/edit_user"),
                                                                    form().withClass("change-password-form").attr("style", "display: none;").with(
                                                                            input().withType("password").withClass("form-control").withName("password"),
                                                                            br(), button("Update").withType("submit").withClass("btn btn-outline-secondary")
                                                                    )
                                                            ):span()
                                                    ),
                                                    div().withClass("col-12").with(
                                                            h4("Revenue Mapping App")
                                                    ),
                                                    div().withClass("col-12").withId("main-menu").with(
                                                            div().withClass("row").with( authorized ?
                                                                    div().withClass("col-12 btn-group-vertical options").with(
                                                                            button("Reports").attr("data-resource", "Report").withId("reports_index_btn").withClass("btn btn-outline-secondary"),
                                                                            button("Markets").attr("data-resource", "Market").withId("markets_index_btn").withClass("btn btn-outline-secondary"),
                                                                            button("Companies").attr("data-resource", "Company").withId("companies_index_btn").withClass("btn btn-outline-secondary"),
                                                                            button("Products").attr("data-resource", "Product").withId("products_index_btn").withClass("btn btn-outline-secondary"),
                                                                            button("Market Revenues").attr("data-resource", "MarketRevenue").withId("market_revenues_index_btn").withClass("btn btn-outline-secondary"),
                                                                            button("Company Revenues").attr("data-resource", "CompanyRevenue").withId("company_revenues_index_btn").withClass("btn btn-outline-secondary"),
                                                                            button("Product Revenues").attr("data-resource", "ProductRevenue").withId("product_revenues_index_btn").withClass("btn btn-outline-secondary"),
                                                                            button("Company Market Shares").attr("data-resource", "MarketShareRevenue").withId("companies_markets_index_btn").withClass("btn btn-outline-secondary"),
                                                                            button("Regions").attr("data-resource", "Region").withId("countries_index_btn").withClass("btn btn-outline-secondary")
                                                                    ) : div().withClass("col-12").with(
                                                                        form().withClass("form-group").withMethod("POST").withAction("/login").with(
                                                                                p("Log in"),
                                                                                label("Username").with(
                                                                                        input().withType("text").withClass("form-control").withName("username")
                                                                                ), br(), br(), label("Password").with(
                                                                                        input().withType("password").withClass("form-control").withName("password")
                                                                                ), br(), br(), button("Login").withType("submit").withClass("btn btn-outline-secondary")
                                                                        )
                                                                    )
                                                            )
                                                    )

                                            ), hr()
                                    ), div().withClass("col-9 offset-3").attr("style","padding-top: 58px; padding-left:0px; padding-right:0px;").with(
                                            div().withId("results").attr("style", "margin-bottom: 200px;").with(

                                            ),
                                            br(),
                                            br(),
                                            br()
                                    )
                            )
                    )
            ).render();
        });

        post("/clear_dynatable", (req,res)->{
            authorize(req,res);
            try {
                DataTable.unregisterDataTable(req);
            } catch(Exception e) {

            }
            return new Gson().toJson(Collections.singletonMap("success", "true"));
        });

        get("/ajax/resources/:resource/:from_resource/:from_id", (req, res)->{
            authorize(req,res);
            String resource = req.params("resource");
            String fromResource = req.params("from_resource");
            Integer _fromId;
            Association.Model fromType;
            Association.Model type;
            boolean nationalitiesOnly = req.queryParams("nationalities_only")!=null && req.queryParams("nationalities_only").trim().length()>0;
            boolean regionsOnly = req.queryParams("regions_only")!=null && req.queryParams("regions_only").trim().length()>0;
            if(nationalitiesOnly&&regionsOnly) {
                throw new RuntimeException("Nationalities and regions only settings at the same time.");
            }
            try {
                type = Association.Model.valueOf(resource);
                fromType = Association.Model.valueOf(fromResource);
                _fromId = Integer.valueOf(req.params("from_id"));
                if(_fromId < 0) {
                    _fromId = null;
                }
            } catch(Exception e) {
                e.printStackTrace();
                return null;
            }
            final Integer fromId = _fromId;
            Integer parentRegionId = null;
            Model model = getModelByType(type);
            Set<Integer> idsToAvoid = new HashSet<>();
            boolean showTopLevelOnly = false;
            if(type.equals(Association.Model.Region)) {
                if(fromId==null && !nationalitiesOnly) {
                    showTopLevelOnly = true;
                }
            }
            if(fromId!=null) { // remove existing associations
                Model actualModel = loadModel(fromType, fromId);
                if(actualModel!=null) {
                    actualModel.loadAttributesFromDatabase();
                    actualModel.loadAssociations();
                    if(type.equals(Association.Model.Region)) {
                        if(actualModel.getData().get(Constants.PARENT_REVENUE_ID)==null) {
                            showTopLevelOnly = true;
                        }
                        parentRegionId = (Integer) actualModel.getData().get(Constants.REGION_ID);
                        if(parentRegionId!=null) idsToAvoid.add(parentRegionId);
                        // find all regions of any subrevenues
                        if(actualModel.isRevenueModel()) {
                            // get all associated regions
                            // start with getting grandparents
                            Model temp = actualModel;
                            int i = 0;
                            while(temp.getData().get(Constants.PARENT_REVENUE_ID)!=null && i < 2) {
                                temp = temp.getParentRevenue();
                                i++;
                            }
                            // then add region ids of all descendants
                            Integer regionId = (Integer) temp.getData().get(Constants.REGION_ID);
                            if(regionId!=null) {
                                idsToAvoid.add(regionId);
                            }
                            List<Model> tempList = Collections.singletonList(temp);
                            i = 0;
                            while(tempList.size()>0 && i < 2) {
                                tempList = tempList.stream().flatMap(t->t.getSubRevenues().stream()).collect(Collectors.toList());
                                for(Model _temp : tempList) {
                                    regionId = (Integer) _temp.getData().get(Constants.REGION_ID);
                                    if (regionId != null) {
                                        idsToAvoid.add(regionId);
                                    }
                                }
                                i++;
                            }
                        }
                    }
                    for(Association association : actualModel.getAssociationsMeta()) {
                        if(association.getModel().equals(type)) {
                            // found
                            List<Model> assocs = actualModel.getAssociations().get(association);
                            if(assocs!=null && assocs.size()>0) {
                                assocs.forEach(assoc->{
                                    idsToAvoid.add(assoc.getId());
                                });
                            }
                        }
                    }
                }
            }
            Map<String,String> idToNameMap = new HashMap<>();
            final boolean _showTopLevelOnly = showTopLevelOnly;
            final Integer _parentRegionId = parentRegionId;
            Function<String,List<String>> resultsSearchFunction = search -> {
                try {
                    if(search!=null&&search.trim().length()==0) {
                        search = null;
                    }
                    if(search!=null) {
                        search = search.toLowerCase().trim();
                    }
                    final String fieldToUse = Constants.NAME;
                    List<Model> models;
                    List<String> fieldsToUse = new ArrayList<>();
                    fieldsToUse.add(fieldToUse);
                    if(_showTopLevelOnly || type.equals(Association.Model.Region)) fieldsToUse.add(Constants.PARENT_REGION_ID);
                    models = Database.selectAll(model.isRevenueModel(), type, model.getTableName(), fieldsToUse, null, search).stream().filter(m -> !idsToAvoid.contains(m.getId())).filter(m -> fromId == null || !(fromType.equals(type) && m.getId().equals(fromId))).collect(Collectors.toList());
                    if(_showTopLevelOnly||regionsOnly) {
                        models = models.stream().filter(m->m.getData().get(Constants.PARENT_REGION_ID)==null).collect(Collectors.toList());
                    } else if(type.equals(Association.Model.Region) && _parentRegionId!=null) {
                        models = models.stream().filter(m->m.getData().get(Constants.PARENT_REGION_ID)!=null&&m.getData().get(Constants.PARENT_REGION_ID).equals(_parentRegionId))
                                .collect(Collectors.toList());
                    } else if(nationalitiesOnly) {
                        models = models.stream().filter(m->m.getData().get(Constants.PARENT_REGION_ID)!=null).collect(Collectors.toList());
                    }
                    models.forEach(m -> idToNameMap.put(m.getId().toString(), (String) m.getData().get(fieldToUse)));
                    List<String> r = models.stream().map(m->m.getId().toString()).collect(Collectors.toCollection(ArrayList::new));
                    r.add(0, "");
                    return r;
                } catch(Exception e) {
                    e.printStackTrace();
                    return Collections.emptyList();
                }
            };
            Function<String,String> displayFunction = result ->  idToNameMap.getOrDefault(result, "");
            return handleAjaxRequest(req, resultsSearchFunction, displayFunction, null);
        });

        // Host my own image asset!
        get("/images/brand.png", (request, response) -> {
            response.type("image/png");
            String pathToImage = "public/images/brand.png";
            File f = new File(pathToImage);
            BufferedImage bi = ImageIO.read(f);
            OutputStream out = response.raw().getOutputStream();
            ImageIO.write(bi, "png", out);
            out.close();
            response.status(200);
            return response.body();
        });

        get("/images/favicon-16x16.png", (request, response) -> {
            response.type("image/png");
            String pathToImage = "public/images/favicon-16x16.png";
            File f = new File(pathToImage);
            BufferedImage bi = ImageIO.read(f);
            OutputStream out = response.raw().getOutputStream();
            ImageIO.write(bi, "png", out);
            out.close();
            response.status(200);
            return response.body();
        });

        get("/images/favicon-32x32.png", (request, response) -> {
            response.type("image/png");
            String pathToImage = "public/images/favicon-32x32.png";
            File f = new File(pathToImage);
            BufferedImage bi = ImageIO.read(f);
            OutputStream out = response.raw().getOutputStream();
            ImageIO.write(bi, "png", out);
            out.close();
            response.status(200);
            return response.body();
        });

        post("/init/datatable/:resource", (req, res)-> {
            authorize(req,res);
            DataTable.unregisterDataTable(req);

            Association.Model type;
            String resource = req.params("resource");
            try {
                type = Association.Model.valueOf(resource);
            } catch (Exception e) {
                e.printStackTrace();
                return null;
            }
            Model model = loadModel(type, null);
            if(model!=null) {
                List<String> headers = new ArrayList<>();
                Set<String> numericAttrs = new HashSet<>();
                List<String> humanHeaders = new ArrayList<>();

                List<Map<String,String>> data = selectAll(model, type, headers, humanHeaders, numericAttrs)
                        .stream().map(m->{
                            Map<String,String> map = new HashMap<>(m.getData().size()+m.getAssociationsMeta().size());
                            m.getData().forEach((k,v)->{
                                if(v instanceof Number || numericAttrs.contains(k)) {
                                    map.put(k + Constants.TEXT_ONLY, v == null ? null : v.toString());
                                }
                                map.put(k,Constants.getFieldFormatter(k).apply(v));
                            });
                            String name = m.getName();
                            if(name==null) {
                                name = "";
                            }
                            map.put(Constants.NAME + Constants.TEXT_ONLY, name);
                            map.put(Constants.NAME, m.getSimpleLink().render());

                            //m.loadAssociations();
                            m.getAssociationsMeta().forEach(assoc->{
                                if(!assoc.getType().equals(Association.Type.ManyToOne)) {
                                    return;
                                }
                                List<Model> assocModel = m.getAssociations().get(assoc);
                                String fieldName = assoc.getAssociationName().toLowerCase().replace(" ", "-");
                                String fieldNameTextOnly = fieldName+Constants.TEXT_ONLY;
                                if(assocModel==null) {
                                    map.put(fieldName, "");
                                    map.put(fieldNameTextOnly, "");
                                } else {
                                    map.put(fieldName, String.join("<br/>", assocModel.stream().map(a -> a.getSimpleLink().render()).collect(Collectors.toList())));
                                    map.put(fieldNameTextOnly, String.join(" ", assocModel.stream().map(a -> a.getName()).collect(Collectors.toList())));
                                }
                            });

                            return map;
                        }).collect(Collectors.toList());
                DataTable.registerDataTabe(req, headers, data, numericAttrs);

                ContainerTag html = div().withClass("col-12").with(
                        table().withClass("table table-striped dynatable").with(
                                thead().with(
                                        tr().with(
                                                IntStream.range(0, humanHeaders.size()).mapToObj(i->{
                                                    return th(humanHeaders.get(i)).attr("data-dynatable-column", headers.get(i));
                                                }).collect(Collectors.toList())
                                        )
                                ),tbody().with(

                                )
                        )
                );
                Map<String,Object> result = new HashMap<>();
                result.put("result", html.render());
                return new Gson().toJson(result);
            }
            return null;
        });

        get("/dataTable.json", (req, res)->{
            authorize(req,res);
            // something
            return DataTable.handleDataTable(req, res);
        });

        // gets inner json only
        post("/diagram/:resource/:id", (req, res)-> {
            authorize(req,res);
            Model model = loadModel(req);
            Integer withinGroupId = DataTable.extractInt(req, "group_id", null);
            if(model!=null) {
                Set<Node> expandedNodes = getRegisteredExpandedResourcesForShowPage(req, res);
                ContainerTag diagram = model.loadNestedAssociations(true, 0, false, expandedNodes, withinGroupId, null);
                registerExpandedResourceForShowPage(req, res);
                return new Gson().toJson(Collections.singletonMap("result", diagram.render()));
            }
            return null;
        });

        post("/chart_cache/:chart_id", (req, res)-> {
            authorize(req, res);
            int chartId = Integer.valueOf(req.params("chart_id"));
            Map<Integer, RecursiveTask<List<Options>>> taskMap = req.session().attribute(CHART_CACHE);
            Map<String,Object> result = new HashMap<>();
            if(taskMap!=null) {
                RecursiveTask<List<Options>> optionsTask = taskMap.get(chartId);
                if(optionsTask!=null) {
                    List<Options> options;
                    if(optionsTask.isDone()) {
                        options = optionsTask.getRawResult();
                    } else {
                        options = optionsTask.invoke();
                    }
                    if(options!=null) {
                        for(int i = 0; i < options.size(); i++) {
                            String json = new JsonRenderer().toJson(options.get(i));
                            result.put("chart_"+chartId+"_"+i, json);
                        }
                    }
                }
            }
            return new Gson().toJson(result);
        });

        get("/comparison/:resource/:id", (req, res)-> {
            authorize(req,res);
            Model model = loadModel(req);
            if(model!=null) {
                // get previous value (if any) of comparison models
                ContainerTag html = div().withClass("col-12").with(
                        label(Constants.humanAttrFor(model.getType().toString())+" Name:").with(
                                select().attr("multiple", "multiple").attr("id", "compare-model-select").attr("style","width: 100%").withClass("form-control multiselect-ajax")
                                        .attr("data-url", "/ajax/resources/"+model.getType()+"/"+model.getType()+"/"+model.getId()).with(

                                )
                        ), br(),
                        getReportOptionsForm(req, model,"comparison", ChartHelper.getChartOptionsForm(req)),
                        div().withId("inner-results")
                );

                model.loadShowTemplate(getBackButton(req), "Compare", h5("Comparison of "+model.getName()), html);
                String str = new Gson().toJson(Collections.singletonMap("result", model.getTemplate()));
                registerNextPage(req, res);
                return str;
            }
            return null;
        });


        post("/generate-comparison/:resource/:id", (req, res)->{
            authorize(req, res);
            req.session().removeAttribute(CHART_CACHE);
            Model model = loadModel(req);
            List<Model> compareModels = new ArrayList<>();
            String[] otherIds = req.queryParamsValues("other_ids[]");
            for(String otherId : otherIds) {
                Model compareModel = loadModel(Association.Model.valueOf(req.params("resource")), Integer.valueOf(otherId));
                compareModels.add(compareModel);
            }

            if(model!=null && compareModels.size()>0) {
                registerLatestForm(req, DEFAULT_FORM_OPTIONS);
                double discountRate = req.queryParams("discount_rate")!=null && req.queryParams("discount_rate").length()>0 ? Double.valueOf(req.queryParams("discount_rate")) : 0;
                boolean useCAGR = req.queryParams(Constants.CAGR)!=null && req.queryParams(Constants.CAGR).trim().toLowerCase().startsWith("t");
                int startYear = DataTable.extractInt(req, "start_year", LocalDate.now().getYear());
                boolean column = extractString(req, ChartHelper.TIME_SERIES_CHART_TYPE, ChartHelper.LineChartType.column.toString()).equals(ChartHelper.LineChartType.column.toString());
                int maxGroups = DataTable.extractInt(req, ChartHelper.MAX_NUM_GROUPS, 15);
                int endYear = DataTable.extractInt(req, "end_year", LocalDate.now().getYear());
                int marketDepth = DataTable.extractInt(req, "market_depth", 1);
                boolean estimateCagr = req.queryParams(Constants.ESTIMATE_CAGR)!=null && req.queryParams(Constants.ESTIMATE_CAGR).trim().toLowerCase().startsWith("t");
                Constants.MissingRevenueOption missingRevenueOption = Constants.MissingRevenueOption.valueOf(req.queryParams("missing_revenue"));
                Map<String, Object> results = new HashMap<>();
                Association.Model resource;
                try {
                    resource = Association.Model.valueOf(req.params("resource"));
                    Integer regionId = DataTable.extractInt(req, Constants.REGION_ID, null);
                    Model.RevenueDomain revenueDomain;
                    try {
                        revenueDomain = Model.RevenueDomain.valueOf(req.queryParams("revenue_domain"));
                    } catch (Exception e) {
                        throw new RuntimeException("Please select a valid Revenue Domain.");
                    }
                    model.loadAssociations();
                    Graph graph = Graph.load();
                    for(Model compareModel : compareModels) {
                        compareModel.loadAssociations();
                    }
                    Collection<Node> commonRelatives = compareModels.stream().flatMap(compareModel->graph.findMutualRelatives(model, compareModel).stream())
                            .filter(n->!n.getModel().getType().equals(model.getType())).collect(Collectors.toSet());

                    Map<Association.Model,List<Model>> modelGroups = commonRelatives.stream().map(n->n.getModel()).collect(Collectors.groupingBy(e->e.getType()));
                    final Set<Integer> modelIds = compareModels.stream().map(m->m.getId()).collect(Collectors.toCollection(HashSet::new));
                    modelIds.add(model.getId());

                    List<String> commonElements = model instanceof Company ? modelGroups.getOrDefault(Association.Model.Market, Collections.emptyList()).stream().map(m->m.getName()).collect(Collectors.toList()) : Collections.emptyList();

                    AtomicInteger idx = new AtomicInteger(0);
                    final List<Model> allComparables = new ArrayList<>(compareModels);
                    allComparables.add(model);

                    // add Overall revenue pie chart and revenue by year
                    Model fakeParents = getModelByType(model.getType());
                    fakeParents.setData(Collections.singletonMap(Constants.NAME, "Total Revenue"));
                    Association fakeAssoc = new Association("Sub "+Model.capitalize(model.getType().toString()), model.getType(), model.getTableName(),  model.getTableName(), null, Association.Type.OneToMany,  "parent_"+model.getType().toString()+"_id", model.getType().toString()+"_id", false, "All Revenue");
                    fakeParents.setAssociations(Collections.singletonMap(fakeAssoc, allComparables));
                    List<Options> parentOptions = fakeParents.buildCharts(true, column, maxGroups, allComparables, fakeAssoc,
                            revenueDomain, regionId, startYear, endYear, useCAGR, estimateCagr, missingRevenueOption, discountRate, marketDepth, commonElements);
                    for(Options options : parentOptions) {
                        String json = new JsonRenderer().toJson(options);
                        results.put("chart_" + idx.getAndIncrement(), json);
                    }

                    ContainerTag select = select().withClass("multiselect form-control chart-ajax-select");
                    ContainerTag html = div().withClass("col-12").with(
                            h5("Additional Charts"),
                            select.attr("style", "width: 300px;").with(
                                    option("")
                            ),
                            div().withClass("col-12").withId("additional-charts")
                    );

                    Map<Integer, RecursiveTask<List<Options>>> idxToChartTaskMap = Collections.synchronizedMap(new HashMap<>());

                    Stream.of(Association.Model.values()).forEach(type->{
                        if(type.equals(Association.Model.Region)) return;
                        if(modelGroups.containsKey(type)) {
                            List<Model> models = modelGroups.get(type);
                            for(Model assoc : models) {
                                final int index = idx.getAndIncrement();
                                select.with(
                                        option().with(assoc.getSimpleLink()).withValue(String.valueOf(index))
                                );
                                RecursiveTask<List<Options>> task = new RecursiveTask<List<Options>>() {
                                    @Override
                                    protected List<Options> compute() {
                                        assoc.loadAssociations();
                                        // get associations relevant to model and compareModel
                                        Association association = assoc.findAssociation("Market Share");
                                        if (association != null) {
                                            List<Model> marketShares = assoc.getAssociations().get(association);
                                            if (marketShares != null) {
                                                marketShares = marketShares.stream().filter(share -> {
                                                    if (resource.equals(Association.Model.Market)) {
                                                        return modelIds.contains(share.getData().get(Constants.MARKET_ID));
                                                    } else if (resource.equals(Association.Model.Company)) {
                                                        return modelIds.contains(share.getData().get(Constants.COMPANY_ID));
                                                    } else {
                                                        return false;
                                                    }
                                                }).collect(Collectors.toList());
                                                // convert to regions
                                                marketShares = Model.getSubRevenuesByRegionId(marketShares, revenueDomain, regionId);
                                                if (marketShares.size() > 0) {
                                                    List<Options> allOptions = assoc.buildCharts(true, column, maxGroups, marketShares, assoc.findAssociation("Market Share"), revenueDomain, regionId, startYear, endYear, useCAGR, estimateCagr, missingRevenueOption, discountRate, marketDepth, null);
                                                    return allOptions;
                                                }
                                            }
                                        }
                                        return Collections.emptyList();
                                    }
                                };
                                idxToChartTaskMap.put(index, task);
                            }
                        }
                    });
                    req.session().attribute(CHART_CACHE, idxToChartTaskMap);
                    if(idxToChartTaskMap.size()>0) {
                        results.put("template", html.render());
                    } else {
                        results.put("template", h5("No additional charts found.").render());
                    }
                    return new Gson().toJson(results);
                } catch (Exception e) {
                    e.printStackTrace();
                    return new Gson().toJson(Collections.singletonMap("error", e.getMessage()));
                }
            }
            return null;
        });


        get("/graph/:resource/:id", (req, res)-> {
            authorize(req,res);
            Model model = loadModel(req);
            if(model!=null) {
                ContainerTag html = div().withClass("col-12").with(
                        getReportOptionsForm(req, model,"graph", ChartHelper.getChartOptionsForm(req)),
                        div().withId("inner-results")
                );
                model.loadShowTemplate(getBackButton(req), "Graph", h5("Graphs of "+model.getName()), html);
                String str = new Gson().toJson(Collections.singletonMap("result", model.getTemplate()));
                registerNextPage(req, res);
                return str;
            }
            return null;
        });


        post("/generate-graph/:resource/:id", (req, res)->{
            authorize(req, res);
            Model model = loadModel(req);
            if(model!=null) {
                try {
                    registerLatestForm(req, DEFAULT_FORM_OPTIONS);
                    double discountRate = req.queryParams("discount_rate")!=null && req.queryParams("discount_rate").length()>0 ? Double.valueOf(req.queryParams("discount_rate")) : 0;
                    boolean useCAGR = req.queryParams(Constants.CAGR)!=null && req.queryParams(Constants.CAGR).trim().toLowerCase().startsWith("t");
                    int startYear = DataTable.extractInt(req, "start_year", LocalDate.now().getYear());
                    int endYear = DataTable.extractInt(req, "end_year", LocalDate.now().getYear());
                    boolean estimateCagr = req.queryParams(Constants.ESTIMATE_CAGR)!=null && req.queryParams(Constants.ESTIMATE_CAGR).trim().toLowerCase().startsWith("t");
                    boolean column = extractString(req, ChartHelper.TIME_SERIES_CHART_TYPE, ChartHelper.LineChartType.column.toString()).equals(ChartHelper.LineChartType.column.toString());
                    int maxGroups = DataTable.extractInt(req, ChartHelper.MAX_NUM_GROUPS, 15);
                    int marketDepth = DataTable.extractInt(req, "market_depth", 1);
                    Constants.MissingRevenueOption missingRevenueOption = Constants.MissingRevenueOption.valueOf(req.queryParams("missing_revenue"));
                    Integer regionId = DataTable.extractInt(req, Constants.REGION_ID, null);
                    Model.RevenueDomain revenueDomain;
                    try {
                        revenueDomain = Model.RevenueDomain.valueOf(req.queryParams("revenue_domain"));
                    } catch (Exception e) {
                        throw new RuntimeException("Please select a valid Revenue Domain.");
                    }
                    Map<String, Object> results = new HashMap<>();
                    AtomicInteger idx = new AtomicInteger(0);
                    for(Association association : model.getAssociationsMeta()) {
                        List<Options> allOptions = model.buildCharts(false, column, maxGroups, association.getAssociationName(), revenueDomain, regionId, startYear, endYear, useCAGR, estimateCagr, missingRevenueOption, discountRate, marketDepth, null);
                        if(allOptions!=null) {
                            for(Options options : allOptions) {
                                if(options.getSeries()!=null && options.getSeries().size()>0 && options.getSeries().get(0).getData()!=null &&
                                        options.getSeries().get(0).getData().size()>0) {
                                    String json = new JsonRenderer().toJson(options);
                                    results.put("chart_" + idx.getAndIncrement(), json);
                                }
                            }
                        }
                    }
                    return new Gson().toJson(results);
                } catch (Exception e) {
                    e.printStackTrace();
                    return new Gson().toJson(Collections.singletonMap("error", e.getMessage()));
                }
            }
            return null;
        });


        post("/generate-report/:resource/:id", (req, res)-> {
            authorize(req,res);
            Model model = loadModel(req);
            if(model!=null) {
                registerLatestForm(req, DEFAULT_FORM_OPTIONS);
                double discountRate = req.queryParams("discount_rate")!=null && req.queryParams("discount_rate").length()>0 ? Double.valueOf(req.queryParams("discount_rate")) : 0;
                boolean useCAGR = req.queryParams(Constants.CAGR)!=null && req.queryParams(Constants.CAGR).trim().toLowerCase().startsWith("t");
                boolean estimateCagr = req.queryParams(Constants.ESTIMATE_CAGR)!=null && req.queryParams(Constants.ESTIMATE_CAGR).trim().toLowerCase().startsWith("t");
                int startYear = DataTable.extractInt(req, "start_year", LocalDate.now().getYear());
                int endYear = DataTable.extractInt(req, "end_year", LocalDate.now().getYear());
                int marketDepth = DataTable.extractInt(req, "market_depth", 1);
                Constants.MissingRevenueOption missingRevenueOption = Constants.MissingRevenueOption.valueOf(req.queryParams("missing_revenue"));
                try {
                    Integer regionId = DataTable.extractInt(req, Constants.REGION_ID, null);
                    Model.RevenueDomain revenueDomain;
                    try {
                        revenueDomain = Model.RevenueDomain.valueOf(req.queryParams("revenue_domain"));
                    } catch (Exception e) {
                        throw new RuntimeException("Please select a valid Revenue Domain.");
                    }

                    ContainerTag diagram = model.loadReport(revenueDomain, regionId, startYear, endYear, useCAGR, estimateCagr, missingRevenueOption, discountRate, marketDepth);

                    ContainerTag html = div().withClass("col-12").with(h4("Date Range: "+startYear+" - "+endYear), br(), diagram);
                    return new Gson().toJson(Collections.singletonMap("result", html.render()));
                } catch(Exception e) {
                    e.printStackTrace();
                    if(e instanceof MissingRevenueException) {
                        MissingRevenueException mre = (MissingRevenueException) e;
                        Map<String,Object> response = new HashMap<>();
                        response.put("error", mre.getMessage());
                        Model modelWithError = loadModel(mre.getModel(), mre.getId());
                        Association revenueAssociation = mre.getAssociation();
                        ContainerTag helperLink = modelWithError.getAddAssociationPanel(revenueAssociation,null, null, model, "(Add Revenue)", true);
                        ContainerTag link = modelWithError.getSimpleLink();
                        ContainerTag html = div().with(link, br(), helperLink);
                        response.put("helper", html.render());

                        return new Gson().toJson(response);
                    } else {
                        return new Gson().toJson(Collections.singletonMap("error", e.getMessage()));
                    }
                }
            }
            return null;
        });

        get("/report/:resource/:id", (req, res)-> {
            authorize(req,res);
            Model model = loadModel(req);
            if(model!=null) {

                ContainerTag html = div().withClass("col-12").with(
                        getReportOptionsForm(req, model, "report"),
                        div().withId("inner-results")
                );

                model.loadShowTemplate(getBackButton(req), "Report", h5("Report of "+model.getName()), html);
                String str = new Gson().toJson(Collections.singletonMap("result", model.getTemplate()));
                registerNextPage(req, res);
                return str;
            }
            return null;
        });

        get("/show/:resource/:id", (req, res) -> {
            authorize(req, res);
            boolean stayedOnSameShowPage = stayedOnSameShowPage(req, res);
            if(!stayedOnSameShowPage) {
                clearShowPage(req, res);
            }
            Model model = loadModel(req);
            if(model != null) {
                model.loadAttributesFromDatabase();
                model.loadAssociations();
                Set<Node> expanded = getRegisteredExpandedResourcesForShowPage(req, res);
                if(expanded==null) expanded = Collections.emptySet();
                model.loadShowTemplate(getBackButton(req), "Diagram", h5("Diagram"), model.loadNestedAssociations(false, 0, false, expanded, null, null));
                String html = new Gson().toJson(model);
                registerNextPage(req, res);
                if(!stayedOnSameShowPage) {
                    registerShowPage(req, res);
                }
                return html;
            }
            else return null;
        });

        post("/resources/:resource/:id", (req, res) -> {
            authorize(req,res);
            Model model = loadModel(req);
            if(model != null) {
                if(model.getClass().getSimpleName().equals(Association.Model.Region.toString())) {
                    return new Gson().toJson(Collections.singletonMap("error", "Cannot edit regions."));
                }
                addAttributesToModel(model, req, true, true);
                try {
                    model.updateInDatabase();
                    return new Gson().toJson(model);
                } catch(Exception e) {
                    return new Gson().toJson(Collections.singletonMap("error", e.getMessage()));
                }
            }
            else return null;
        });

        post("/new/:resource", (req, res) -> {
            authorize(req,res);
            Association.Model type;
            String resource = req.params("resource");
            try {
                type = Association.Model.valueOf(resource);
            } catch (Exception e) {
                e.printStackTrace();
                return null;
            }
            Model model = loadModel(type, null);
            if(model != null) {
                if(model.getClass().getSimpleName().equals(Association.Model.Region.toString())) {
                    return new Gson().toJson(Collections.singletonMap("error", "Cannot edit regions."));
                }
                model.setData(new HashMap<>());
                addAttributesToModel(model, req, false, false);
                try {
                    model.createInDatabase();
                    // add associations
                    model.getAvailableAttributes().forEach(attr-> {
                        if (attr.endsWith("_id")) {
                            Object val = extractString(req,attr,null);
                            if(req.queryParams().contains(attr)) {
                                if (val != null) {
                                    val = val.toString().trim();
                                }
                                // update association
                                Association association = model.getAssociationsMeta().stream().filter(a -> a.getParentIdField().equals(attr) && a.getType().equals(Association.Type.ManyToOne)).findAny().orElse(null);
                                if (association != null) {
                                    try {
                                        val = val == null || val.toString().trim().isEmpty() ? null : Integer.valueOf(val.toString().trim());
                                    } catch (Exception e) {
                                        e.printStackTrace();
                                        val = null;
                                    }
                                    if (val != null) {
                                        Model related = loadModel(association.getModel(), (Integer) val);
                                        handleNewAssociation(model, related, association.getAssociationName());
                                    }
                                }
                            }
                        }
                    });
                    return new Gson().toJson(model);
                } catch(Exception e) {
                    e.printStackTrace();
                    return new Gson().toJson(Collections.singletonMap("error", e.getMessage()));
                }
            }
            else return null;
        });

        delete("/resources/:resource/:id", (req, res) -> {
            authorize(req,res);
            Model model = loadModel(req);
            if(model != null) {
                if(model.getClass().getSimpleName().equals(Association.Model.Region.toString())) {
                    return new Gson().toJson(Collections.singletonMap("error", "Cannot edit regions."));
                }
                try {
                    model.deleteFromDatabase(false, true);
                    return new Gson().toJson(Collections.singletonMap("result", "success"));
                } catch(Exception e) {
                    e.printStackTrace();
                    return new Gson().toJson(Collections.singletonMap("error", e.getMessage()));
                }
            }
            else return null;
        });

        post("/resources_delete", (req, res) -> {
            authorize(req,res);
            String resource = req.queryParams("resource");
            String association = req.queryParams("association");
            String resourceId = req.queryParams("id");
            String associationId = req.queryParams("association_id");
            String associationName = req.queryParams("associationRef");
            Association.Model type;
            Integer id;
            Association.Model baseType;
            Integer baseId;
            try {
                type = Association.Model.valueOf(resource);
                id = Integer.valueOf(resourceId);
                baseType = Association.Model.valueOf(association);
                baseId = Integer.valueOf(associationId);
            } catch(Exception e) {
                e.printStackTrace();
                return null;
            }
            Model baseModel = loadModel(baseType, baseId);
            Model toDelete = loadModel(type, id);
            if(baseModel != null && toDelete!=null) {
                if(baseModel.getClass().getSimpleName().equals(Association.Model.Region.toString())
                        && toDelete.getClass().getSimpleName().equals(Association.Model.Region.toString())) {
                    return new Gson().toJson(Collections.singletonMap("error", "Cannot edit regions."));
                }
                // remove association
                Association assoc = toDelete.getAssociationsMeta().stream().filter(m->m.getAssociationName().equals(associationName)).findAny().orElse(null);
                if(assoc!=null) {
                    try {
                        toDelete.cleanUpParentIds(assoc, baseId);
                        return new Gson().toJson(Collections.singletonMap("result", "success"));
                    } catch (Exception e) {
                        return new Gson().toJson(Collections.singletonMap("error", e.getMessage()));
                    }
                }
            }
            return null;
        });


        get("/resources/:resource", (req, res) -> {
            authorize(req,res);
            String resource = req.params("resource");
            String html;
            if(resource.equals("Report")) {
                // report
                html = handleShowReports(req,res);
            } else {
                Association.Model type;
                try {
                    type = Association.Model.valueOf(resource);
                } catch (Exception e) {
                    e.printStackTrace();
                    return null;
                }
                Model model = getModelByType(type);
                Map<String, Object> result = new HashMap<>();
                if (!type.toString().contains("Revenue") && !type.equals(Association.Model.Region)) {
                    result.put("new_form", model.getCreateNewForm(type, null, null).attr("style", "display: none;").render());
                }
                result.put("resource_list_show", "#" + model.getTableName() + "_index_btn");
                html = new Gson().toJson(result);
            }
            registerNextPage(req, res);
            return html;
        });

        post("/main_report", (req, res)-> {
            authorize(req, res);
            registerLatestForm(req, DEFAULT_REPORT_OPTIONS);
            try {
                int companyId = DataTable.extractInt(req, Constants.COMPANY_ID, -1);
                if (companyId < 0) throw new RuntimeException("Please select a company");
                String[] regionIds = req.queryParamsValues(Constants.REGION_ID);
                boolean useCAGR = req.queryParams(Constants.CAGR) != null && req.queryParams(Constants.CAGR).trim().toLowerCase().startsWith("t");
                double discountRate = req.queryParams("discount_rate") != null && req.queryParams("discount_rate").length() > 0 ? Double.valueOf(req.queryParams("discount_rate")) : 0;
                int startYear = DataTable.extractInt(req, "start_year", LocalDate.now().getYear());
                int endYear = DataTable.extractInt(req, "end_year", LocalDate.now().getYear());
                boolean estimateCagr = req.queryParams(Constants.ESTIMATE_CAGR) != null && req.queryParams(Constants.ESTIMATE_CAGR).trim().toLowerCase().startsWith("t");
                Constants.MissingRevenueOption missingRevenueOption = Constants.MissingRevenueOption.valueOf(req.queryParams("missing_revenue"));
                int marketDepth = DataTable.extractInt(req, "market_depth", 1);
                // get all global markets
                Model company = Graph.load().findNode(Association.Model.Company, companyId).getModel();
                List<Model> globalMarkets = Graph.load().getModelList(Association.Model.Market)
                        .stream().filter(m -> m.getData().get(Constants.PARENT_MARKET_ID) == null)
                        .sorted(Comparator.comparing(e -> e.getName()))
                        .collect(Collectors.toList());

                String html = div().withClass("col-12").with(
                        table().withClass("table table-striped").with(
                                thead().with(
                                        tr().with(
                                                th("Industry"),
                                                th("Global Industry Revenue (NPV in $M)"),
                                                th("Industry Segment"),
                                                th("CF"),
                                                th("Industry Segment Revenue (NPV in $M)"),
                                                th("Applicable Companies"),
                                                th("Applicable Products"),
                                                th("Source"),
                                                th("Notes")
                                        )
                                ), tbody().with(
                                        globalMarkets.stream().flatMap(market -> {
                                            double revenue = market.calculateRevenue(Model.RevenueDomain.global, null, startYear, endYear, useCAGR, estimateCagr, missingRevenueOption, null, false, discountRate, companyId, marketDepth);
                                            List<ContainerTag> rows = new ArrayList<>();
                                            rows.add(
                                                    tr().with(
                                                            td(market.getSimpleLink()),
                                                            td(String.valueOf(Math.round(revenue / 1000000))),
                                                            td(),
                                                            td(),
                                                            td(),
                                                            td(),
                                                            td(),
                                                            td(),
                                                            td()
                                                    )
                                            );
                                            market.loadAssociations();
                                            Association assoc = market.findAssociation("Sub Market");
                                            List<Model> subMarkets = market.getAssociations().get(assoc);
                                            if (subMarkets != null) {
                                                subMarkets = subMarkets.stream().sorted(Comparator.comparing(e -> e.getName()))
                                                        .collect(Collectors.toList());
                                                for (Model subMarket : subMarkets) {
                                                    double subRevenue = subMarket.calculateRevenue(Model.RevenueDomain.global, null, startYear, endYear, useCAGR, estimateCagr, missingRevenueOption, revenue, true, discountRate, companyId, marketDepth);
                                                    rows.add(tr().with(
                                                            td(),
                                                            td(),
                                                            td(subMarket.getSimpleLink()),
                                                            td(),
                                                            td(String.valueOf(Math.round(subRevenue / 1000000))),
                                                            td(),
                                                            td(),
                                                            td(),
                                                            td()
                                                    ));
                                                }
                                            }
                                            return rows.stream();
                                        }).collect(Collectors.toList())
                                )
                        )
                ).render();
                return new Gson().toJson(Collections.singletonMap("result", html));
            } catch(Exception e) {
                e.printStackTrace();
                return new Gson().toJson(Collections.singletonMap("error", e.getMessage()));
            }
        });

        post("/new_association/:resource/:association/:resource_id/:association_id", (req,res)->{
            authorize(req,res);
            String resource = req.params("resource");
            String association = req.params("association");
            String resourceId = req.params("resource_id");
            String associationId = req.params("association_id");
            Association.Model type;
            Integer id;
            Association.Model relatedType;
            Integer relatedId;
            try {
                type = Association.Model.valueOf(resource);
                id = Integer.valueOf(resourceId);
                relatedType = Association.Model.valueOf(association);
                relatedId = Integer.valueOf(associationId);
            } catch(Exception e) {
                e.printStackTrace();
                return null;
            }
            Model baseModel = loadModel(type, id);
            Model relatedModel = loadModel(relatedType, relatedId);

            if(baseModel.getClass().getSimpleName().equals(Association.Model.Region.toString())
                    && relatedModel.getClass().getSimpleName().equals(Association.Model.Region.toString())) {
                return new Gson().toJson(Collections.singletonMap("error", "Cannot edit regions."));
            }

            String associationName = req.queryParams("_association_name");
            Association associationModel = baseModel.getAssociationsMeta().stream().filter(m->m.getAssociationName().equals(associationName)).findAny().orElse(null);

            try {
                /*// can only assign company to leaf node of market
                if(type.equals(Association.Model.Company) && relatedType.equals(Association.Model.Market)) {
                    if(relatedModel.hasSubMarkets()) {
                        throw new RuntimeException("Cannot assign a company to a market that has sub markets. Please assign the company to a market without sub markets.");
                    }
                } else if(type.equals(Association.Model.Market) && relatedType.equals(Association.Model.Company)) {
                    if(baseModel.hasSubMarkets()) {
                        throw new RuntimeException("Cannot assign a company to a market that has sub markets. Please assign the company to a market without sub markets.");
                    }
                }*/
                handleNewAssociation(baseModel, relatedModel, associationName);
            } catch(Exception e) {
                e.printStackTrace();
                return new Gson().toJson(Collections.singletonMap("error", "Error: "+e.getMessage()));
            }
            return new Gson().toJson(Collections.singletonMap("template", relatedModel.getLink(associationModel.getReverseAssociationName(), baseModel.getClass().getSimpleName(), baseModel.getId()).render()));
        });

    }


    private static ContainerTag templateWrapper(ContainerTag inner) {
        return html().with(
                head().with(
                        title("GTT Group"),
                        link().withRel("icon").withType("image/png").attr("sizes", "32x32").withHref("/images/favicon-32x32.png"),
                        link().withRel("icon").withType("image/png").attr("sizes", "16x16").withHref("/images/favicon-16x16.png"),
                        script().withSrc("/js/jquery-3.3.1.min.js"),
                        script().withSrc("/js/jquery-ui-1.12.1.min.js"),
                        script().withSrc("/js/popper.min.js"),
                        script().withSrc("/js/jquery.dynatable.js"),
                        script().withSrc("/js/highcharts.js"),
                        script().withSrc("/js/exporting.js"),
                        script().withSrc("/js/drilldown.js"),
                        script().withSrc("/js/word_cloud.js"),
                        script().withSrc("/js/heatmap.js"),
                        script().withSrc("/js/offline-exporting.js"),
                        script().withSrc("/js/no-data-to-display.js"),
                        script().withSrc("/js/customEvents.js"),
                        script().withSrc("/js/defaults.js"),
                        script().withSrc("/js/jquery.miniTip.js"),
                        script().withSrc("/js/jstree.min.js"),
                        script().withSrc("/js/jstree.misc.js"),
                        script().withSrc("/js/select2.min.js"),
                        script().withSrc("/js/bootstrap.min.js"),
                        script().withSrc("/js/tether.min.js"),
                        script().withSrc("/js/notify.min.js"),
                        link().withRel("stylesheet").withHref("/css/bootstrap.min.css"),
                        link().withRel("stylesheet").withHref("/css/select2.min.css"),
                        link().withRel("stylesheet").withHref("/css/defaults.css"),
                        link().withRel("stylesheet").withHref("/css/jquery.dynatable.css"),
                        link().withRel("stylesheet").withHref("/css/jquery-ui.min.css")

                ),
                body().with(inner)
        );
    }


}
