package controllers;

import com.google.gson.Gson;
import database.Database;
import models.*;
import spark.Request;
import spark.Spark;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.OutputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static j2html.TagCreator.*;
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
        Model model;
        switch(type) {
            case Market: {
                model = new Market(null, null);
                break;
            }
            case Segment: {
                model = new Segment(null, null);
                break;
            }
            case Revenue: {
                model = new Revenue(null, null);
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
            default: {
                model = null;
                break;
            }
        }
        return model;
    }

    private static Model loadModel(Association.Model type, Integer id) {
        Model model;
        switch(type) {
            case Market: {
                model = new Market(id, null);
                break;
            }
            case Segment: {
                model = new Segment(id, null);
                break;
            }
            case Revenue: {
                model = new Revenue(id, null);
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

    public static void main(String[] args) throws Exception {
        staticFiles.externalLocation(new File("public").getAbsolutePath());
        port(6969);

        get("/", (req, res)->{
            // home page
            return html().with(
                    head().with(
                            title("GTT Group"),
                            link().withRel("icon").withType("image/png").attr("sizes", "32x32").withHref("/images/favicon-32x32.png"),
                            link().withRel("icon").withType("image/png").attr("sizes", "16x16").withHref("/images/favicon-16x16.png"),
                            script().withSrc("/js/jquery-3.3.1.min.js"),
                            script().withSrc("/js/jquery-ui-1.12.1.min.js"),
                            script().withSrc("/js/popper.min.js"),
                            script().withSrc("/js/jquery.dynatable.js"),
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
                    body().with(
                           div().withClass("container-fluid text-center").attr("style","height: 100%; z-index: 1;").with(
                                    div().withClass("row").attr("style","height: 100%;").with(
                                            nav().withClass("sidebar col-3").attr("style","z-index: 2; overflow-y: auto; height: 100%; position: fixed; padding-top: 75px;").with(
                                                    div().withClass("row").with(
                                                            div().withClass("col-12").with(
                                                                    h4("Company Mapping App")
                                                            ),
                                                            div().withClass("col-12").withId("main-menu").with(
                                                                    div().withClass("row").with(
                                                                            div().withClass("col-12 btn-group-vertical options").with(
                                                                                    button("Markets").attr("data-resource", "Market").withId("markets_index_btn").withClass("btn btn-outline-secondary"),
                                                                                    button("Companies").attr("data-resource", "Company").withId("companies_index_btn").withClass("btn btn-outline-secondary"),
                                                                                    button("Segments").attr("data-resource", "Segment").withId("segments_index_btn").withClass("btn btn-outline-secondary"),
                                                                                    button("Products").attr("data-resource", "Product").withId("products_index_btn").withClass("btn btn-outline-secondary"),
                                                                                    button("Revenues").attr("data-resource", "Revenue").withId("revenues_index_btn").withClass("btn btn-outline-secondary")
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
                    )
            ).render();
        });


        get("/ajax/resources/:resource/:from_resource/:from_id", (req, res)->{
            String resource = req.params("resource");
            String fromResource = req.params("from_resource");
            int fromId;
            Association.Model fromType;
            Association.Model type;
            try {
                type = Association.Model.valueOf(resource);
                fromType = Association.Model.valueOf(fromResource);
                fromId = Integer.valueOf(req.params("from_id"));
            } catch(Exception e) {
                e.printStackTrace();
                return null;
            }
            Model model = getModelByType(type);
            Map<String,String> idToNameMap = new HashMap<>();
            Function<String,List<String>> resultsSearchFunction = search -> {
                try {
                    if(search!=null&&search.trim().length()==0) {
                        search = null;
                    }
                    List<Model> models = Database.selectAll(type, model.getTableName(), Collections.singletonList(Constants.NAME), search).stream().filter(m->!(fromType.equals(type) && m.getId().equals(fromId))).collect(Collectors.toList());
                    models.forEach(m->idToNameMap.put(m.getId().toString(),(String)m.getData().get(Constants.NAME)));
                    return models.stream().map(m->m.getId().toString()).collect(Collectors.toList());
                } catch(Exception e) {
                    e.printStackTrace();
                    return Collections.emptyList();
                }
            };
            Function<String,String> displayFunction = result ->  idToNameMap.get(result);
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


        get("/resources/:resource/:id", (req, res) -> {
            Model model = loadModel(req);
            if(model != null) {
                model.loadAssociations();
                model.loadShowTemplate();
                return new Gson().toJson(model);
            }
            else return null;
        });

        post("/resources/:resource/:id", (req, res) -> {
            Model model = loadModel(req);
            if(model != null) {
                model.getAvailableAttributes().forEach(attr->{
                    String val = req.queryParams(attr);
                    if(val != null && val.trim().length()>0) {
                        model.updateAttribute(attr, val);
                    }
                });
                model.updateInDatabase();
                return new Gson().toJson(model);
            }
            else return null;
        });

        post("/new/:resource", (req, res) -> {
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
                model.setData(new HashMap<>());
                model.getAvailableAttributes().forEach(attr->{
                    String val = req.queryParams(attr);
                    if(val != null && val.trim().length()>0) {
                        model.updateAttribute(attr, val);
                    }
                });
                model.createInDatabase();
                return new Gson().toJson(model);
            }
            else return null;
        });

        delete("/resources/:resource/:id", (req, res) -> {
            Model model = loadModel(req);
            if(model != null) {
                model.deleteFromDatabase(false);
                return new Gson().toJson(Collections.singletonMap("result", "success"));
            }
            else return null;
        });

        get("/resources/:resource", (req, res) -> {
            String resource = req.params("resource");
            Association.Model type;
            try {
                type = Association.Model.valueOf(resource);
            } catch(Exception e) {
                e.printStackTrace();
                return null;
            }
            Model model = getModelByType(type);
            return new Gson().toJson(
                    Database.selectAll(type, model.getTableName(), model.getAvailableAttributes())
            );
        });

        post("/new_association/:resource/:association/:resource_id/:association_id", (req,res)->{
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

            String associationName = req.queryParams("_association_name");

            baseModel.associateWith(relatedModel, associationName);

            return new Gson().toJson(Collections.singletonMap("template", relatedModel.getLink(null, null).render()));
        });

    }



}
