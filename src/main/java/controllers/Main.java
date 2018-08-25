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
        } catch(Exception e) {
            e.printStackTrace();
            return null;
        }
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
        return model;
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
                                                            ),
                                                            div().withClass("col-12").withId("main-menu").attr("style","display: none;").with(
                                                                    div().withClass("row").with(
                                                                            div().withClass("col-12 btn-group-vertical").with(

                                                                            )
                                                                    )
                                                            )

                                                    ), hr()
                                            ), div().withClass("col-9 offset-3").attr("style","padding-top: 58px; padding-left:0px; padding-right:0px;").with(
                                                    h4("Company Mapping App"),
                                                    br(),
                                                    br(),
                                                    br()
                                            )
                                    )
                            )
                    )
            ).render();
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
                model.loadAttributesFromDatabase();
                return new Gson().toJson(model.getData());
            }
            else return null;
        });

        post("/resources/:resource/:id", (req, res) -> {
            Model model = loadModel(req);
            if(model != null) {
                model.loadAttributesFromDatabase();
                model.getAvailableAttributes().forEach(attr->{
                    String val = req.queryParams(attr);
                    if(val != null && val.trim().length()>0) {
                        model.updateAttribute(attr, val);
                    }
                });
                model.updateInDatabase();
                return new Gson().toJson(model.getData());
            }
            else return null;
        });

        delete("/resources/:resource/:id", (req, res) -> {
            Model model = loadModel(req);
            if(model != null) {
                model.deleteFromDatabase(true);
                return new Gson().toJson(Collections.singletonMap("result", "success"));
            }
            else return null;
        });

    }
}
