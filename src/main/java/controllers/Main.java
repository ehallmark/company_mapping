package controllers;

import com.google.gson.Gson;
import database.Database;
import models.*;
import spark.Request;

import java.util.Collections;

import static spark.Spark.*;

public class Main {
    private static Model loadModel(Request req) {
        String resource = req.queryParams("resource");
        int id;
        Association.Model type;
        try {
            id = Integer.valueOf(req.queryParams("resource"));
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
