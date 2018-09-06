package controllers;

import auth.PasswordException;
import auth.PasswordHandler;
import com.google.gson.Gson;
import com.googlecode.wickedcharts.highcharts.jackson.JsonRenderer;
import com.googlecode.wickedcharts.highcharts.options.Options;
import database.Database;
import j2html.tags.ContainerTag;
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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static j2html.TagCreator.*;
import static j2html.TagCreator.head;
import static spark.Spark.*;

public class Main {
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

    private static Model getModelByType(Association.Model type) {
        return loadModel(type, null);
    }

    private static Model loadModel(Association.Model type, Integer id) {
        Model model;
        switch(type) {
            case Market: {
                model = new Market(id, null);
                break;
            }
            case Product: {
                model = new Product(id, null);
                break;
            }
            case Company: {
                model = new Company(id, null);
                break;
            }
            case MarketRevenue: {
                model = new MarketRevenue(id, null);
                break;
            }
            case CompanyRevenue: {
                model = new CompanyRevenue(id, null);
                break;
            }
            case ProductRevenue: {
                model = new ProductRevenue(id, null);
                break;
            }
            case MarketShareRevenue: {
                model = new MarketShareRevenue(id, null);
                break;
            }
            case Region: {
                model = new Region(id, null);
                break;
            }
            default: {
                model = null;
                break;
            }
        }
        if(model.existsInDatabase()) {
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

    public static List<Model> selectAll(Model model, Association.Model type, List<String> headers, List<String> humanHeaders, Set<String> numericAttrs, Association parentAssociation) throws Exception {
        for(String header : model.getAvailableAttributes()) {
            if (!Constants.isHiddenAttr(header) && !Arrays.asList(Constants.UPDATED_AT,Constants.CREATED_AT).contains(header)) {
                headers.add(header);
                humanHeaders.add(Constants.humanAttrFor(header));
            }
        }
        if(parentAssociation!=null) {
            if(model.isRevenueModel()) {
                headers.add(0, parentAssociation.getAssociationName().toLowerCase().replace(" ", "-"));
                boolean pluralize = Arrays.asList(Association.Type.OneToMany, Association.Type.ManyToMany).contains(parentAssociation.getType());
                humanHeaders.add(0, pluralize ? Constants.pluralizeAssociationName(parentAssociation.getAssociationName()) : parentAssociation.getAssociationName());
            } else {
                headers.add(parentAssociation.getAssociationName().toLowerCase().replace(" ", "-"));
                boolean pluralize = Arrays.asList(Association.Type.OneToMany, Association.Type.ManyToMany).contains(parentAssociation.getType());
                humanHeaders.add(pluralize ? Constants.pluralizeAssociationName(parentAssociation.getAssociationName()) : parentAssociation.getAssociationName());
            }
        }
       /* for(Association association : model.getAssociationsMeta()) {
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
        */

        for(String header : headers) {
            if(Constants.fieldTypeForAttr(header).equals(Constants.NUMBER_FIELD_TYPE)) {
                numericAttrs.add(header);
            }
        }

        if(model.isRevenueModel()) {
            humanHeaders.add(0, Constants.humanAttrFor(Constants.NAME));
            headers.add(0, Constants.NAME);
        }

        return Database.selectAll(model.isRevenueModel(), type, model.getTableName(), model.getAvailableAttributes(), parentAssociation);
    }

    public static ContainerTag getReportOptionsForm(Model model, String clazz) {
        return form().attr("data-id",model.getId().toString())
                .attr("data-resource",model.getClass().getSimpleName()).withId(clazz+"-specification-form").with(
                        label("Start Year").with(br(),
                                input().withType("number").withValue(String.valueOf(LocalDate.now().getYear()-5)).withName("start_year")
                        ),
                        br(),
                        label("End Year").with(br(),
                                input().withType("number").withValue(String.valueOf(LocalDate.now().getYear())).withName("end_year")
                        ),
                        br(),
                        label("Use CAGR when applicable?").with(br(),
                                input().withType("checkbox").withValue("true").withName(Constants.CAGR)
                        ),
                        br(),
                        label("Missing Revenue Options").with(br(),
                                select().withClass("multiselect").withName("missing_revenue").with(
                                        option("Exclude missing").withValue(Constants.MissingRevenueOption.exclude.toString()),
                                        option("Replace with zeros").withValue(Constants.MissingRevenueOption.replace.toString()),
                                        option("Raise error").withValue(Constants.MissingRevenueOption.error.toString())
                                )
                        ),
                        br(),
                        button("Generate").withType("submit").withClass("btn btn-sm btn-outline-secondary")
                );
    }

    public static void main(String[] args)  {
        staticFiles.externalLocation(new File("public").getAbsolutePath());
        final PasswordHandler passwordHandler = new PasswordHandler();
        port(6969);

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
            res.redirect("/");
            res.status(200);
            return null;
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
                                                            h4("Company Mapping App")
                                                    ),
                                                    div().withClass("col-12").withId("main-menu").with(
                                                            div().withClass("row").with( authorized ?
                                                                    div().withClass("col-12 btn-group-vertical options").with(
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
            Model model = getModelByType(type);
            Set<Integer> idsToAvoid = new HashSet<>();
            boolean showTopLevelOnly = false;
            if(type.equals(Association.Model.Region)) {
                if(fromId==null) {
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
                    if(_showTopLevelOnly) fieldsToUse.add(Constants.PARENT_REGION_ID);
                    models = Database.selectAll(model.isRevenueModel(), type, model.getTableName(), fieldsToUse, search, null).stream().filter(m -> !idsToAvoid.contains(m.getId())).filter(m -> fromId == null || !(fromType.equals(type) && m.getId().equals(fromId))).collect(Collectors.toList());
                    if(_showTopLevelOnly) {
                        models = models.stream().filter(m->m.getData().get(Constants.PARENT_REGION_ID)==null).collect(Collectors.toList());
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
            Function<String,String> htmlFunction = null; //result -> "<span>"+ (result+" ("+titlePartMap.getOrDefault(result,"")+")").replace(" ()","") + "</span>";
            return handleAjaxRequest(req, resultsSearchFunction, displayFunction, htmlFunction);
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
                String parentAssocName = null;
                switch (type) {
                    case Region: {
                        parentAssocName = Constants.PARENT_REGION_ID;
                        break;
                    }
                    case Market: {
                        parentAssocName = Constants.PARENT_MARKET_ID;
                        break;
                    }
                    case Company: {
                        parentAssocName = Constants.PARENT_COMPANY_ID;
                        break;
                    }
                    case Product: {
                        break;
                    }
                    default: { // Must be revenue
                        parentAssocName = null;
                        break;
                    }
                }
                final String _parentAssocName = parentAssocName;
                Association parentAssoc = _parentAssocName == null ? null :
                        model.getAssociationsMeta().stream().filter(a->a.getAssociationName().equals(_parentAssocName)).findAny().orElse(null);

                List<Map<String,String>> data = selectAll(model, type, headers, humanHeaders, numericAttrs, parentAssoc)
                        .stream().map(m->{
                            Map<String,String> map = new HashMap<>(m.getData().size()+m.getAssociationsMeta().size());
                            m.getData().forEach((k,v)->{
                                map.put(k,v==null?"":v.toString());
                            });
                            map.put(Constants.NAME + Constants.TEXT_ONLY, (String) m.getData().get(Constants.NAME));
                            map.put(Constants.NAME, m.getSimpleLink().render());
                            //m.loadAssociations();
                            m.getAssociationsMeta().forEach(assoc->{
                                List<Model> assocModel = m.getAssociations().get(assoc);
                                String fieldName = assoc.getAssociationName().toLowerCase().replace(" ", "-");
                                String fieldNameTextOnly = fieldName+Constants.TEXT_ONLY;
                                String[] additionalClasses;
                                if(assoc.getModel().equals(Association.Model.MarketShareRevenue)) {
                                    if(type.equals(Association.Model.Company)) {
                                        additionalClasses = new String[]{"market-share-company"};
                                    } else if(type.equals(Association.Model.Market)) {
                                        additionalClasses = new String[]{"market-share-market"};
                                    } else {
                                        additionalClasses = new String[]{};
                                    }
                                } else {
                                    additionalClasses = new String[]{};
                                }

                                if(assocModel==null) {
                                    map.put(fieldName, "");
                                    map.put(fieldNameTextOnly, "");
                                } else {
                                    map.put(fieldName, String.join("<br/>", assocModel.stream().map(a -> a.getSimpleLink(additionalClasses).render()).collect(Collectors.toList())));
                                    map.put(fieldNameTextOnly, String.join(" ", assocModel.stream().map(a -> (String)a.getData().get(Constants.NAME)).collect(Collectors.toList())));
                                }
                            });

                            // check for estimate type field
                            if(m.getData().get(Constants.ESTIMATE_TYPE)!=null) {
                                map.put(Constants.ESTIMATE_TYPE, Constants.estimateTypeForNumber((Integer)m.getData().get(Constants.ESTIMATE_TYPE)));
                            }
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


        post("/diagram/:resource/:id", (req, res)-> {
            authorize(req,res);
            Model model = loadModel(req);
            if(model!=null) {
                ContainerTag diagram = model.loadNestedAssociations();

                ContainerTag html = div().withClass("col-12").with(
                        model.getSimpleLink("btn", "btn-sm", "btn-outline-secondary", "add-back-text"),
                        h3("Diagram of "+model.getData().get(Constants.NAME)),
                        diagram
                );

                return new Gson().toJson(Collections.singletonMap("result", html.render()));
            }
            return null;
        });


        post("/graph/:resource/:id", (req, res)-> {
            authorize(req,res);
            Model model = loadModel(req);
            if(model!=null) {

                ContainerTag html = div().withClass("col-12").with(
                        model.getSimpleLink("btn", "btn-sm", "btn-outline-secondary", "add-back-text"),
                        h3("Graphs of "+model.getData().get(Constants.NAME)),
                        getReportOptionsForm(model,"graph"),
                        div().withId("inner-results")
                );

                return new Gson().toJson(Collections.singletonMap("result", html.render()));
            }
            return null;
        });


        post("/generate-graph/:resource/:id", (req, res)->{
            Model model = loadModel(req);
            if(model!=null) {
                boolean useCAGR = req.queryParams(Constants.CAGR)!=null && req.queryParams(Constants.CAGR).trim().toLowerCase().startsWith("t");
                int startYear = DataTable.extractInt(req, "start_year", LocalDate.now().getYear());
                int endYear = DataTable.extractInt(req, "end_year", LocalDate.now().getYear());
                Constants.MissingRevenueOption missingRevenueOption = Constants.MissingRevenueOption.valueOf(req.queryParams("missing_revenue"));
                Map<String, Object> results = new HashMap<>();
                AtomicInteger idx = new AtomicInteger(0);
                try {
                    for(Association association : model.getAssociationsMeta()) {
                        List<Options> allOptions = model.buildCharts(association.getAssociationName(), startYear, endYear, useCAGR, missingRevenueOption);
                        if(allOptions!=null) {
                            for(Options options : allOptions) {
                                String json = new JsonRenderer().toJson(options);
                                results.put("chart_" + idx.getAndIncrement(), json);
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
                boolean useCAGR = req.queryParams(Constants.CAGR)!=null && req.queryParams(Constants.CAGR).trim().toLowerCase().startsWith("t");
                int startYear = DataTable.extractInt(req, "start_year", LocalDate.now().getYear());
                int endYear = DataTable.extractInt(req, "end_year", LocalDate.now().getYear());
                Constants.MissingRevenueOption missingRevenueOption = Constants.MissingRevenueOption.valueOf(req.queryParams("missing_revenue"));

                try {
                    ContainerTag diagram = model.loadReport(startYear, endYear, useCAGR, missingRevenueOption);

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
                        ContainerTag helperLink = modelWithError.getAddAssociationPanel(revenueAssociation, null, model, "(Add Revenue)", true);
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

        post("/report/:resource/:id", (req, res)-> {
            authorize(req,res);
            Model model = loadModel(req);
            if(model!=null) {

                ContainerTag html = div().withClass("col-12").with(
                        model.getSimpleLink("btn", "btn-sm", "btn-outline-secondary", "add-back-text"),
                        h3("Report of "+model.getData().get(Constants.NAME)),
                        getReportOptionsForm(model, "report"),
                        div().withId("inner-results")
                );

                return new Gson().toJson(Collections.singletonMap("result", html.render()));
            }
            return null;
        });

        // diagram all

        post("/diagram/:resource", (req, res)-> {
            authorize(req,res);
            Association.Model type;
            String resource = req.params("resource");
            try {
                type = Association.Model.valueOf(resource);
            } catch (Exception e) {
                e.printStackTrace();
                return null;
            }
            Model _model = loadModel(type, null);
            if(_model!=null) {
                List<String> headers = new ArrayList<>();
                Set<String> numericAttrs = new HashSet<>();
                List<String> humanHeaders = new ArrayList<>();
                List<Model> data = selectAll(_model, type, headers, humanHeaders, numericAttrs, null);
                ContainerTag html  = div().withClass("col-12").with(
                        h3("Diagram of All "+Constants.pluralizeAssociationName(Constants.humanAttrFor(_model.getClass().getSimpleName())))
                );
                for(Model model : data) {
                    if(!model.getClass().getSimpleName().equals(Association.Model.Market.toString()) || model.getData().get(Constants.PARENT_MARKET_ID)==null) {
                        ContainerTag diagram = model.loadNestedAssociations();
                        html.with(div().withClass("col-12").with(
                                h4("Diagram of " + model.getData().get(Constants.NAME)).attr("style", "cursor: pointer;")
                                .attr("onclick", "$(this).next().slideToggle();"),
                                diagram
                                )
                        );
                    }
                }
                return new Gson().toJson(Collections.singletonMap("result", html.render()));
            }
            return null;
        });


        get("/resources/:resource/:id", (req, res) -> {
            authorize(req, res);
            Model model = loadModel(req);
            if(model != null) {
                model.loadAssociations();
                model.loadShowTemplate(true);
                return new Gson().toJson(model);
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
                model.getAvailableAttributes().forEach(attr->{
                    Object val = extractString(req,attr,null);
                    if(req.queryParams().contains(attr)) {
                        String fieldType = Constants.fieldTypeForAttr(attr);
                        if(val!=null) {
                            val = val.toString().trim();
                        }
                        if(val!=null && fieldType.equals(Constants.NUMBER_FIELD_TYPE)) {
                            try {
                                val = Double.valueOf(val.toString().trim());
                            } catch(Exception e) {
                                e.printStackTrace();
                                val = 0;
                            }
                        } else if(fieldType.equals(Constants.BOOL_FIELD_TYPE)) {
                            val = val!=null && val.toString().toLowerCase().trim().startsWith("t");
                        }
                        model.updateAttribute(attr, val);
                    }
                });
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
                model.getAvailableAttributes().forEach(attr->{
                    String val = req.queryParams(attr);
                    if(val != null && val.trim().length()>0) {
                        model.updateAttribute(attr, val);
                    }
                });
                try {
                    model.createInDatabase();
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
                    model.deleteFromDatabase(false);
                    return new Gson().toJson(Collections.singletonMap("result", "success"));
                } catch(Exception e) {
                    e.printStackTrace();
                    return new Gson().toJson(Collections.singletonMap("error", "Error: "+e.getMessage()));
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
                    toDelete.cleanUpParentIds(assoc, baseId);
                    return new Gson().toJson(Collections.singletonMap("result", "success"));
                }
            }
            return null;
        });


        get("/resources/:resource", (req, res) -> {
            authorize(req,res);
            String resource = req.params("resource");
            Association.Model type;
            try {
                type = Association.Model.valueOf(resource);
            } catch(Exception e) {
                e.printStackTrace();
                return null;
            }
            Model model = getModelByType(type);
            Map<String,Object> result = new HashMap<>();
            if(!type.toString().contains("Revenue")&&!type.equals(Association.Model.Region)) {
                result.put("new_form", model.getCreateNewForm(type, null).attr("style", "display: none;").render());
            }
            return new Gson().toJson(result);
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
                if(!(baseModel.isRevenueModel() && relatedModel.isRevenueModel())) {
                    baseModel.removeManyToOneAssociations(associationName);
                    baseModel.associateWith(relatedModel, associationName, Collections.emptyMap());
                } else {
                    try {
                        baseModel.associateWith(relatedModel, associationName, Collections.emptyMap());
                    } catch(Exception e) {
                        e.printStackTrace();
                    }
                }
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
