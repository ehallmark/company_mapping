package models;

import com.google.gson.Gson;
import com.googlecode.wickedcharts.highcharts.options.*;
import com.googlecode.wickedcharts.highcharts.options.series.Point;
import com.googlecode.wickedcharts.highcharts.options.series.PointSeries;
import database.Database;
import j2html.tags.ContainerTag;
import j2html.tags.Tag;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;

import java.awt.*;
import java.io.Serializable;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.time.LocalDate;
import java.util.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static j2html.TagCreator.*;

public abstract class Model implements Serializable {
    private static final long serialVersionUID = 1L;

    @Getter
    private final String tableName;
    @Getter
    private Integer id;
    @Getter @Setter
    protected Map<String,Object> data;
    @Getter
    protected transient final List<String> availableAttributes;
    @Getter
    protected transient List<Association> associationsMeta;
    @Getter @Setter
    protected Map<Association,List<Model>> associations;
    protected String template;
    private Set<String> allReferences;
    @Getter
    private final boolean isRevenueModel;
    private Double revenue;
    private Double percentage;
    private List<CalculationInformation> calculationInformation;
    protected Model(@NonNull List<Association> associationsMeta, @NonNull List<String> availableAttributes, @NonNull String tableName, Integer id, Map<String,Object> data, boolean isRevenueModel) {
        this.tableName = tableName;
        this.data = data;
        this.associationsMeta = associationsMeta;
        this.id = id;
        this.isRevenueModel = isRevenueModel;
        this.availableAttributes=availableAttributes;
        if(id != null && data == null) {
            // pull data from database
            loadAttributesFromDatabase();
        }
    }

    public boolean existsInDatabase() {
        return id != null;
    }

    public ContainerTag getLink(@NonNull String associationName, @NonNull String associationModel, @NonNull Integer associationId) {
        if(data==null) {
            loadAttributesFromDatabase();
        }
        String[] additionalClasses = new String[]{};
        if(this.getClass().getSimpleName().equals(MarketShareRevenue.class.getSimpleName())) {
            // check association
            if(associationModel.equals(Market.class.getSimpleName())) {
                additionalClasses = new String[]{"market-share-market"};
            } else if(associationModel.equals(Company.class.getSimpleName())) {
                additionalClasses = new String[]{"market-share-company"};
            }
        }
        return div().withId("node-"+this.getClass().getSimpleName()+"-"+id).with(
                getSimpleLink(additionalClasses),
                (isRegion() || associationName.equals("Sub Revenue") ? span() :
                    span("X").attr("data-association", associationModel)
                        .attr("data-association-name", associationName)
                        .attr("data-association-id", associationId.toString())
                            .attr("style","cursor: pointer;").withClass("delete-node")
                            .attr("data-resource", this.getClass().getSimpleName()).attr("data-id", id)
                )
        );
    }


    public void buildTimelineSeries(String groupByField, Integer minYear, Integer maxYear, boolean useCAGR, Constants.MissingRevenueOption option, List<Model> models, Options options, Association association) {
        // yearly timeline
        if(minYear==null || maxYear==null) return;
        if(maxYear - minYear <= 0) {
            return;
        }
        options.setSubtitle(new Title().setText(data.get(Constants.NAME).toString()));
        List<String> categories = new ArrayList<>();
        for(int year = minYear; year <= maxYear; year ++ ) {
            categories.add(String.valueOf(year));
        }
        options.setPlotOptions(new PlotOptionsChoice().setLine(new PlotOptions().setShowInLegend(groupByField!=null)));
        options.setChartOptions(new ChartOptions().setType(SeriesType.COLUMN).setWidth(800));
        options.setxAxis(new Axis().setCategories(categories).setType(AxisType.CATEGORY));
        options.setyAxis(new Axis().setTitle(new Title().setText("Revenue ($)")));
        String title;
        if(groupByField==null) {
            title = "Revenue Timeline";
        } else {
            title = "Revenue Timeline by "+Constants.humanAttrFor(groupByField);
            options.getTooltip().setPointFormat("<span style=\"color:{point.color}\">\u25CF</span> <b>{series.name}</b><br/><b>Revenue: ${point.y:.2f} </b><br/>");
        }
        options.setTitle(new Title().setText(title));
        if(groupByField==null) {
            PointSeries series = new PointSeries();
            series.setDataLabels(new DataLabels(true)
                    .setRotation(0)
                    .setColor(Color.black)
                    .setAlign(HorizontalAlignment.CENTER)
                    .setFormat("${point.y:.2f}")
                    .setY(-5)
            );
            series.setShowInLegend(false);
            Set<String> missingYears = new HashSet<>(categories);
            for(Model assoc : models) {
                assoc.calculateRevenue(minYear, maxYear, useCAGR, option, revenue, true);
                Double rev = assoc.revenue;
                assoc.getSimpleLink();
                Integer name = (Integer) assoc.getData().get(Constants.YEAR);
                if(rev!=null) {
                    series.addPoint(new Point(name.toString(), assoc.revenue));
                    missingYears.remove(assoc.getData().get(Constants.YEAR).toString());
                }
            }
            for(String missing : missingYears) {
                int missingYear = Integer.valueOf(missing);
                Double missingRev = null;
                if(useCAGR) {
                    missingRev = calculateFromCAGR(models, missingYear);
                }

                if(missingRev!=null) {
                    series.addPoint(new Point(String.valueOf(missingYear), missingRev));

                } else {
                    if(option.equals(Constants.MissingRevenueOption.error)) {
                        throw new MissingRevenueException("Missing revenues in " + missingYear+" for " + data.get(Constants.NAME), missingYear, Association.Model.valueOf(this.getClass().getSimpleName()), id, association);
                    } else if(option.equals(Constants.MissingRevenueOption.replace)) {
                        series.addPoint(new Point(String.valueOf(missingYear), 0));

                    }
                }
            }
            options.addSeries(series);

        } else {
            models.stream().collect(Collectors.groupingBy(e->e.getData().get(groupByField))).forEach((name, list) -> {
                PointSeries series = new PointSeries();
                series.setShowInLegend(true);
                // get name of group by field by id
                Model dataReference;
                if(groupByField.equals(Constants.MARKET_ID)) {
                    // find market
                    dataReference = new Market((Integer)name, null);
                } else if(groupByField.equals(Constants.COMPANY_ID)){
                    // find company
                    dataReference = new Company((Integer)name, null);

                } else {
                    throw new RuntimeException("Unknown group by field in time line chart.");
                }
                dataReference.loadAttributesFromDatabase();
                series.setName((String)dataReference.getData().get(Constants.NAME));
                Set<String> missingYears = new HashSet<>(categories);
                for (Model assoc : list) {
                    assoc.calculateRevenue(minYear, maxYear, useCAGR, option, revenue, true);
                    Double rev = assoc.revenue;
                    assoc.getSimpleLink();
                    Integer year = (Integer) assoc.getData().get(Constants.YEAR);
                    if (rev != null) {
                        series.addPoint(new Point(year.toString(), assoc.revenue));
                        missingYears.remove(year.toString());
                    }
                }
                for(String missing : missingYears) {
                    int missingYear = Integer.valueOf(missing);
                    Double missingRev = null;
                    if(useCAGR) {
                        missingRev = calculateFromCAGR(list, missingYear);
                    }

                    if(missingRev!=null) {
                        series.addPoint(new Point(String.valueOf(missingYear), missingRev));

                    } else {
                        if(option.equals(Constants.MissingRevenueOption.error)) {
                            throw new MissingRevenueException("Missing revenues in " + missingYear+" for " + data.get(Constants.NAME), missingYear, Association.Model.valueOf(this.getClass().getSimpleName()), id, association);
                        } else if(option.equals(Constants.MissingRevenueOption.replace)) {
                            series.addPoint(new Point(String.valueOf(missingYear), 0));

                        }
                    }
                }
                options.addSeries(series);
            });
        }

    }


    public void buildMarketShare(String groupByField, String title, Integer minYear, Integer maxYear, boolean useCAGR, Constants.MissingRevenueOption option, List<Model> models, Options options, Association association) {
        // yearly timeline
        if(minYear==null || maxYear==null) return;
        if(maxYear - minYear <= 0) {
            return;
        }
        List<String> categories = new ArrayList<>();
        for(int year = minYear; year <= maxYear; year ++ ) {
            categories.add(String.valueOf(year));
        }
        options.setPlotOptions(new PlotOptionsChoice().setPie(new PlotOptions().setAllowPointSelect(true).setSize(new PixelOrPercent(80, PixelOrPercent.Unit.PERCENT))));
        options.setChartOptions(new ChartOptions().setType(SeriesType.PIE));
        options.setTitle(new Title().setText(title));
        options.setSubtitle(new Title().setText(data.get(Constants.NAME).toString()));
        options.getTooltip().setPointFormat("<span style=\"color:{point.color}\">\u25CF</span> <b>Percentage: {point.percentage:.1f}%</b><br/><b>Revenue: ${point.y:.2f} </b><br/>");
        PointSeries series = new PointSeries();
        series.setDataLabels(new DataLabels(true)
                .setRotation(0)
                .setColor(Color.black)
                .setAlign(HorizontalAlignment.CENTER)
                .setFormat("<b>{point.name}</b>: {point.percentage:.1f}%")
                .setY(-5)
        );
        if (groupByField == null) {
            for (Model assoc : models) {
                assoc.calculateRevenue(minYear, maxYear, useCAGR, option, revenue, true);
                Double rev = assoc.revenue;
                assoc.getSimpleLink();
                String name = (String) assoc.getData().get(Constants.NAME);
                if (rev != null) {
                    series.addPoint(new Point(name, assoc.revenue));
                }
            }
        } else {
            models.stream().collect(Collectors.groupingBy(e -> e.getData().get(groupByField))).forEach((name, list) -> {
                // get name of group by field by id
                Model dataReference;
                if(groupByField.equals(Constants.MARKET_ID)) {
                    // find market
                    dataReference = new Market((Integer)name, null);
                } else if(groupByField.equals(Constants.COMPANY_ID)){
                    // find company
                    dataReference = new Company((Integer)name, null);

                } else {
                    throw new RuntimeException("Unknown group by field in time line chart.");
                }
                dataReference.loadAttributesFromDatabase();
                String label = (String)dataReference.getData().get(Constants.NAME);
                Set<String> missingYears = new HashSet<>(categories);
                Double y = null;
                for (Model assoc : list) {
                    assoc.calculateRevenue(minYear, maxYear, useCAGR, option, revenue, true);
                    Double rev = assoc.revenue;
                    Integer year = (Integer) assoc.getData().get(Constants.YEAR);
                    if (rev != null) {
                        missingYears.remove(year.toString());
                        y = (y==null? rev : y + rev);
                    }
                }
                for(String missing : missingYears) {
                    int missingYear = Integer.valueOf(missing);
                    Double missingRev = null;
                    if(useCAGR) {
                        missingRev = calculateFromCAGR(list, missingYear);
                    }

                    if(missingRev!=null) {
                        y = (y==null? missingRev : y + missingRev);

                    } else {
                        if(option.equals(Constants.MissingRevenueOption.error)) {
                            throw new MissingRevenueException("Missing revenues in " + missingYear+" for " + data.get(Constants.NAME), missingYear, Association.Model.valueOf(this.getClass().getSimpleName()), id, association);
                        }
                    }
                }
                if(y!=null) {
                    series.addPoint(new Point(label, y));
                }
            });
        }
        options.addSeries(series);
    }


    private static Options getDefaultChartOptions() {
        return new Options()
                .setExporting(new ExportingOptions().setEnabled(true))
                .setTooltip(new Tooltip().setEnabled(true)
                        .setHeaderFormat("{point.key}<br/>")
                        .setPointFormat("<span style=\"color:{point.color}\">\u25CF</span> <b> Revenue: ${point.y:.2f}</b><br/>"))
                .setCreditOptions(new CreditOptions().setEnabled(true).setText("GTT Group").setHref("http://www.gttgrp.com/"));
    }


    public List<Options> buildCharts(@NonNull String associationName, Integer minYear, Integer maxYear, boolean useCAGR, Constants.MissingRevenueOption option) {
        Association association = associationsMeta.stream().filter(a->a.getAssociationName().equals(associationName)).findAny().orElse(null);
        if(association==null) {
            return null;
        }
        if(associations==null) loadAssociations();
        List<Options> allOptions = new ArrayList<>();
        List<Model> assocModels = associations.getOrDefault(association, Collections.emptyList());
        Options options = getDefaultChartOptions();
        allOptions.add(options);
        calculateRevenue(minYear, maxYear, useCAGR, option, null, false);
        if(this instanceof Market) {
            switch(association.getModel()) {
                case MarketRevenue: {
                    buildTimelineSeries(null, minYear, maxYear, useCAGR, option, assocModels, options, association);
                    break;
                }
                case Market: {
                    if(association.getAssociationName().startsWith("Sub")) {
                        // sub market
                        buildMarketShare(null,"Sub Markets", minYear, maxYear, useCAGR, option, assocModels, options, association);
                    } else {
                        options.setTitle(new Title().setText("Parent Market"));
                        // parent market
                        if(assocModels.size()>0) {
                            Model parent = assocModels.get(0);
                            if(parent.getAssociations()==null) parent.loadAssociations();
                            // get sub markets for parent
                            Association subs = parent.getAssociationsMeta().stream().filter(a->a.getAssociationName().equals(association.getReverseAssociationName())).findAny().orElse(null);
                            if(subs!=null) {
                                List<Model> associationSubs = parent.getAssociations().get(subs);
                                if(associationSubs!=null) {
                                    parent.buildMarketShare(null,"Parent Market", minYear, maxYear, useCAGR, option, associationSubs, options, association);
                                }
                            }
                        }
                    }
                    break;
                }
                case MarketShareRevenue: {
                    // graph of all companies associated with this market
                    buildMarketShare(Constants.COMPANY_ID,"Companies", minYear, maxYear, useCAGR, option, assocModels, options, association);
                    if(maxYear - minYear > 0) {
                        Options timelineOptions = getDefaultChartOptions();
                        buildTimelineSeries(Constants.COMPANY_ID, minYear, maxYear, useCAGR, option, assocModels, timelineOptions, association);
                        allOptions.add(timelineOptions);
                    }
                    break;
                }
                case Product: {
                    buildMarketShare(null,"Market Products", minYear, maxYear, useCAGR, option, assocModels, options, association);
                    break;
                }
            }
        } else if(this instanceof Company) {
            switch (association.getModel()) {
                case CompanyRevenue: {
                    // yearly timeline
                    buildTimelineSeries(null, minYear, maxYear, useCAGR, option, assocModels, options, association);
                    break;
                }
                case Company: {
                    // check sub company or parent company
                    if(association.getAssociationName().startsWith("Sub")) {
                        // children
                        buildMarketShare(null,"Subsidiaries", minYear, maxYear, useCAGR, option, assocModels, options, association);
                    } else {
                        // parent
                        options.setTitle(new Title().setText("Parent Company"));
                        if(assocModels.size()>0) {
                            Model parent = assocModels.get(0);
                            if(parent.getAssociations()==null) parent.loadAssociations();
                            // get sub markets for parent
                            Association subs = parent.getAssociationsMeta().stream().filter(a->a.getAssociationName().equals(association.getReverseAssociationName())).findAny().orElse(null);
                            if(subs!=null) {
                                List<Model> associationSubs = parent.getAssociations().get(subs);
                                if(associationSubs!=null) {
                                    parent.buildMarketShare(null,"Parent Company", minYear, maxYear, useCAGR, option, associationSubs, options, association);
                                }
                            }
                        }
                    }
                    break;
                }
                case MarketShareRevenue: {
                    // graph of all markets associated with this company
                    buildMarketShare(Constants.MARKET_ID,"Markets", minYear, maxYear, useCAGR, option, assocModels, options, association);
                    if(maxYear - minYear > 0) {
                        Options timelineOptions = getDefaultChartOptions();
                        buildTimelineSeries(Constants.MARKET_ID, minYear, maxYear, useCAGR, option, assocModels, timelineOptions, association);
                        allOptions.add(timelineOptions);
                    }
                    break;
                } case Product: {
                    buildMarketShare(null,"Company Products", minYear, maxYear, useCAGR, option, assocModels, options, association);
                    break;
                }
            }

        } else if(this instanceof Product) {
            switch (association.getModel()) {
                case ProductRevenue: {
                    // yearly timeline
                    buildTimelineSeries(null, minYear, maxYear, useCAGR, option, assocModels, options, association);
                    break;
                }
                case Company: {
                    // graph of all products of this product's company
                    options.setTitle(new Title().setText("Company Products"));
                    if(assocModels.size()>0) {
                        Model parent = assocModels.get(0);
                        if(parent.getAssociations()==null) parent.loadAssociations();
                        // get sub markets for parent
                        Association subs = parent.getAssociationsMeta().stream().filter(a->a.getAssociationName().equals(association.getReverseAssociationName())).findAny().orElse(null);
                        if(subs!=null) {
                            List<Model> associationSubs = parent.getAssociations().get(subs);
                            if(associationSubs!=null) {
                                parent.buildMarketShare(null,"Company Products", minYear, maxYear, useCAGR, option, associationSubs, options, association);
                            }
                        }
                    }
                    break;
                }
                case Market: {
                    // graph of all products of this product's market
                    options.setTitle(new Title().setText("Market Products"));
                    if(assocModels.size()>0) {
                        Model parent = assocModels.get(0);
                        if(parent.getAssociations()==null) parent.loadAssociations();
                        // get sub markets for parent
                        Association subs = parent.getAssociationsMeta().stream().filter(a->a.getAssociationName().equals(association.getReverseAssociationName())).findAny().orElse(null);
                        if(subs!=null) {
                            List<Model> associationSubs = parent.getAssociations().get(subs);
                            if(associationSubs!=null) {
                                parent.buildMarketShare(null,"Market Products", minYear, maxYear, useCAGR, option, associationSubs, options, association);
                            }
                        }
                    }
                    break;
                }
            }
        }
        return allOptions;
    }

    public boolean hasSubMarkets() {
        if(associations==null) loadAssociations();

        for(Association association : associationsMeta) {
            if(association.getAssociationName().equals("Sub Market")) {
                List<Model> subMarkets = associations.get(association);
                if(subMarkets!=null && subMarkets.size()>0) {
                    return true;
                }
            }
        }
        return false;
    }

    public ContainerTag getSimpleLink(@NonNull String... additionalClasses) {
        if(isRevenueModel) {
            boolean isMarketShare = this.getClass().getSimpleName().equals(Association.Model.MarketShareRevenue.toString());
            boolean removePrefix = additionalClasses.length>0 && additionalClasses[0].equals("resource-data-field");
            // TODO speed up this query
            if(!data.containsKey(Constants.NAME)) {
                loadAssociations();
                if(data.get(Constants.REGION_ID)!=null) {
                    Region region = new Region((Integer)data.get(Constants.REGION_ID), null);
                    region.loadAttributesFromDatabase();
                    data.put(Constants.NAME, region.data.get(Constants.NAME));
                }
                if(isMarketShare) {
                    String companyName = "";
                    String marketName = "";
                    String regionName = "";
                    if(associations==null) {
                        loadNestedAssociations();
                    }
                    for(Association association : associationsMeta) {
                        if (associations.getOrDefault(association, Collections.emptyList()).size() > 0) {
                            if(association.getModel().equals(Association.Model.Company)) {
                                companyName = associations.get(association).get(0).getData().get(Constants.NAME).toString();
                            } else if (association.getModel().equals(Association.Model.Market)) {
                                marketName = associations.get(association).get(0).getData().get(Constants.NAME).toString();
                            } else if(association.getModel().equals(Association.Model.Region)) {
                                regionName = associations.get(association).get(0).getData().get(Constants.NAME).toString();
                            }
                        }
                    }
                    if(regionName.trim().isEmpty()) {
                        regionName = "Global";
                    }
                    String name;
                    if(removePrefix) {
                        name = marketName + " - "+regionName+" (" + data.get(Constants.YEAR) + ")";
                    } else {
                        if (additionalClasses.length > 0 && additionalClasses[0].equals("market-share-market")) {
                            name = companyName +" - "+regionName+ " (" + data.get(Constants.YEAR) + ")";
                        } else if (additionalClasses.length > 0 && additionalClasses[0].equals("market-share-company")) {
                            name = marketName +" - "+regionName+ " (" + data.get(Constants.YEAR) + ")";
                        } else {
                            name = companyName+" - "+regionName+" (" + data.get(Constants.YEAR) + ")";
                        }
                    }
                    if(name.startsWith(" - ")) {
                        name = name.substring(3);
                    }
                    data.put(Constants.NAME, name);
                } else {
                    List<Model> parent = associations.get(associationsMeta.get(0));
                    if (parent != null && parent.size() > 0) {
                        data.put(Constants.NAME, (removePrefix ? "" : (((String) parent.get(0).getData().get(Constants.NAME)) + " ")) + " (" + data.get(Constants.YEAR) + ")");
                    }
                }
            }
        }
        String name = (String)data.get(Constants.NAME);
        return a(name).attr("data-id", getId().toString()).attr("data-resource", this.getClass().getSimpleName()).attr("href", "#").withClass("resource-show-link "+String.join(" ", additionalClasses));
    }

    private static final Map<String,String> fieldToSelectToResourceName = new HashMap<>();
    static {
        fieldToSelectToResourceName.put(Constants.COMPANY_ID, Association.Model.Company.toString());
        fieldToSelectToResourceName.put(Constants.MARKET_ID, Association.Model.Market.toString());
        fieldToSelectToResourceName.put(Constants.PRODUCT_ID, Association.Model.Product.toString());
        fieldToSelectToResourceName.put(Constants.REGION_ID, Association.Model.Region.toString());
    }

    public ContainerTag getCreateNewForm(@NonNull Association.Model type, Integer associationId) {
        if (type.equals(Association.Model.Region) || (type.toString().contains("Revenue") && referencesCountry())) {
            return span();
        }
        if(type.toString().endsWith("Revenue")) {
            List<String> fieldsToSelect = new ArrayList<>();
            String fieldToHide;
            boolean isMarketShare = false;
            boolean isRevenueToRevenue = this.getClass().getSimpleName().contains("Revenue") && type.toString().contains("Revenue");
            boolean isRegionToRevenue = isRegion() && type.toString().contains("Revenue");
            if(isRevenueToRevenue) {
                fieldToHide = Constants.PARENT_REVENUE_ID;
                if(type.equals(Association.Model.MarketShareRevenue)) {
                    isMarketShare = true;
                }
            } else if(isRegionToRevenue) {
                fieldToHide = Constants.REGION_ID;
                if(type.equals(Association.Model.MarketShareRevenue)) {
                    isMarketShare = true;
                    fieldsToSelect.add(Constants.COMPANY_ID);
                    fieldsToSelect.add(Constants.MARKET_ID);
                } else {
                    if(type.equals(Association.Model.MarketRevenue)) {
                        fieldsToSelect.add(Constants.MARKET_ID);
                    } else if(type.equals(Association.Model.CompanyRevenue)) {
                        fieldsToSelect.add(Constants.COMPANY_ID);
                    } else if(type.equals(Association.Model.ProductRevenue)) {
                        fieldsToSelect.add(Constants.PRODUCT_ID);
                    }
                }
            } else if(type.equals(Association.Model.MarketShareRevenue)) {
                isMarketShare = true;
                fieldsToSelect.add(this.getClass().getSimpleName().startsWith("Company") ? Constants.MARKET_ID : Constants.COMPANY_ID);
                fieldToHide = this.getClass().getSimpleName().startsWith("Company") ? Constants.COMPANY_ID : Constants.MARKET_ID;
            } else if(this.getClass().getSimpleName().startsWith("Market")) {
                fieldToHide = Constants.MARKET_ID;
            } else if(this.getClass().getSimpleName().startsWith("Product")) {
                fieldToHide = Constants.PRODUCT_ID;
            } else if(this.getClass().getSimpleName().startsWith("Company")) {
                fieldToHide = Constants.COMPANY_ID;
            } else if(this.getClass().getSimpleName().startsWith("Region")) {
                fieldToHide = Constants.REGION_ID;
            } else {
                throw new RuntimeException("Unknown revenue type exception.");
            }
            ContainerTag associationTag;
            if(!isMarketShare && !isRevenueToRevenue && !isRegionToRevenue && associationId!=null) {
                associationTag = span();
            } else {
                // some funky logic that basically chooses which model is not yet associated with a market share
                // if none are associated, then it provides both select dropdowns
                // default does nothing different if the model type is not a market share
                if(id!=null) {
                    associationTag = div().with(
                            input().withType("hidden")
                                    .withValue(id.toString())
                                    .withName(fieldToHide)
                    );
                } else {
                    associationTag = div();
                }
                if(!isRevenueToRevenue) {
                    for(String fieldToSelect : fieldsToSelect) {
                        String associationResource = fieldToSelectToResourceName.get(fieldToSelect);
                        associationTag.with(label(associationResource).with(
                                br(),
                                select().withClass("multiselect-ajax")
                                        .withName(fieldToSelect)
                                        .attr("data-url", "/ajax/resources/" + associationResource + "/" + this.getClass().getSimpleName() + "/-1")

                        ), br());
                    }
                }
            }
            boolean showRegion = this.isRevenueModel() && type.toString().contains("Revenue");
            int year = LocalDate.now().getYear();
            if(id!=null && isRevenueToRevenue) {
                year = (Integer)data.get(Constants.YEAR);
            }
            return form().with(
                    associationTag,
                    (isRevenueToRevenue ? input().withType("hidden").withName(Constants.YEAR).withValue(String.valueOf(year)) :
                            label(Constants.humanAttrFor(Constants.YEAR)).with(
                            br(),
                            input().attr("value", String.valueOf(year)).withClass("form-control").withName(Constants.YEAR).withType("number")
                    )),
                    (isRevenueToRevenue ? span() : br()),
                    label(Constants.humanAttrFor(Constants.VALUE)).with(
                            br(),
                            input().withClass("form-control").withName(Constants.VALUE).withType("number")
                    ), br(),
                    (showRegion?label(Constants.humanAttrFor(Constants.REGION_ID)).with(
                            br(),
                            select().attr("style","width: 100%").withClass("form-control multiselect-ajax").withName(Constants.REGION_ID)
                                    .attr("data-url", "/ajax/resources/"+Association.Model.Region+"/"+this.getClass().getSimpleName()+"/"+id)
                    ) : span()),
                    (showRegion? br() : span()),
                    label(Constants.humanAttrFor(Constants.CAGR)).with(
                            br(),
                            input().withClass("form-control").withName(Constants.CAGR).withType("number")
                    ), br(),
                    label(Constants.humanAttrFor(Constants.SOURCE)).with(
                            br(),
                            input().withClass("form-control").withName(Constants.SOURCE).withType("text")
                    ), br(),
                    label(Constants.humanAttrFor(Constants.IS_ESTIMATE)).with(
                            br(),
                            input().withName(Constants.IS_ESTIMATE).withType("checkbox").attr("value", "true")
                    ), br(),
                    label(Constants.humanAttrFor(Constants.ESTIMATE_TYPE)).with(
                            br(),
                            select().withClass("multiselect").withName(Constants.ESTIMATE_TYPE).with(
                                    option().attr("selected", "selected"),
                                    option("Low").withValue("0"),
                                    option("Medium").withValue("1"),
                                    option("High").withValue("2")
                            )
                    ), br(),
                    label(Constants.humanAttrFor(Constants.NOTES)).with(
                            br(),
                            textarea().withClass("form-control").withName(Constants.NOTES)
                    ), br(),
                    button("Create").withClass("btn btn-outline-secondary btn-sm").withType("submit")
            );
        } else {
            return form().with(
                    label(Constants.humanAttrFor(Constants.NAME)).with(
                            br(),
                            input().withClass("form-control").withName(Constants.NAME).withType("text")
                    ), br(),
                    button("Create").withClass("btn btn-outline-secondary btn-sm").withType("submit")
            );
        }
    }

    public ContainerTag loadNestedAssociations() {
        final int maxDepth = 10;
        if(data==null) {
            loadAttributesFromDatabase();
        }
        ContainerTag inner = ul();
        calculateRevenue(null, null, false, Constants.MissingRevenueOption.replace, null, false);
        ContainerTag tag = ul().attr("style", "text-align: left !important;").with(
                li().with(h5(getSimpleLink()).attr("style", "display: inline;"),getRevenueAsSpan(this)).attr("style", "list-style: none;").with(
                        br(),inner
                )
        );
        this.allReferences = new HashSet<>(Collections.singleton(this.getClass().getSimpleName()+id));
        loadNestedAssociationHelper(Constants.YEAR,true, null, null, false, Constants.MissingRevenueOption.replace, inner, allReferences, new AtomicInteger(0), this, 0, maxDepth);
        return tag;
    };


    public ContainerTag loadReport(int startYear, int endYear, boolean useCAGR, Constants.MissingRevenueOption option) {
        final int maxDepth = 10;
        if(data==null) {
            loadAttributesFromDatabase();
        }
        calculateRevenue(startYear, endYear, useCAGR, option, null, false);
        ContainerTag inner = ul();
        ContainerTag tag = ul().attr("style", "text-align: left !important;").with(
                li().with(h5(getSimpleLink()).attr("style", "display: inline;"),getRevenueAsSpan(this)).attr("style", "list-style: none;").with(
                        br(),inner
                )
        );
        this.allReferences = new HashSet<>(Collections.singleton(this.getClass().getSimpleName()+id));
        loadNestedAssociationHelper(Constants.YEAR,false, startYear, endYear, useCAGR, option, inner, allReferences, new AtomicInteger(0), this, 0, maxDepth);
        return tag;
    };

    private ContainerTag getRevenueAsSpan(Model originalModel) {
        ContainerTag updateRev = span();
        /*if(isRevenueModel) {
            updateRev = span();
        } else {
            if(associations==null) loadAssociations();
            Association association = associationsMeta.stream().filter(a->!a.getModel().equals(Association.Model.MarketShareRevenue) && a.getModel().toString().contains("Revenue")).findAny().orElse(null);
            if(association!=null) {
                updateRev = getAddAssociationPanel(association,null,originalModel,"(New)").attr("style", "display: inline; margin-left: 10px;");
            } else {
                updateRev = span();
            }
            if(revenueLink!=null) {
                return revenueLink.with(li().attr("style","list-style: none;").with(updateRev));
            }
        }*/
        String revStr = "(Revenue: "+formatRevenueString(revenue)+")";
        if(percentage!=null) {
            double percentageFull = percentage * 100;
            revStr += " - " + String.format("%.1f", percentageFull)+"%";
        }
        return span(revStr).with(updateRev).attr("data-val", revenue).withClass("resource-data-field").attr("style","margin-left: 10px;");
    }

    private Double calculateFromCAGR(Model best, int year) {
        if(best!=null) {
            System.out.println("Using CAGR...");
            int cagrYear = (Integer) best.getData().get(Constants.YEAR);
            double cagrPercent = (Double) best.getData().get(Constants.CAGR);
            double cagr = (Double) best.getData().get(Constants.VALUE);
            if(cagrYear > year) {
                for(int y = year+1; y <= cagrYear; y++) {
                    // apply cagr
                    cagr /= (1.0 + cagrPercent/100.0);
                }
            } else {
                // less than
                for(int y = cagrYear+1; y <= year; y++) {
                    // apply cagr
                    cagr *= (1.0 + cagrPercent/100.0);
                }
            }
            return cagr;

        }
        return null;
    }

    private Double calculateFromCAGR(List<Model> list, int year) {
        // check cagr for other years
        Model best = list.stream().filter(m->m.getData().get(Constants.CAGR)!=null).min((e1,e2)->Integer.compare(Math.abs((Integer)e1.getData().get(Constants.YEAR)-year), Math.abs((Integer)e2.getData().get(Constants.YEAR)-year))).orElse(null);
        return calculateFromCAGR(best, year);
    }

    public double calculateRevenue(Integer startYear, Integer endYear, boolean useCAGR, @NonNull Constants.MissingRevenueOption option, Double previousRevenue, boolean isParentRevenue) {
        //if(revenue!=null && parentRevenue==null) return revenue;
        revenue = null;
        this.calculationInformation = new ArrayList<>();
        if(isRevenueModel) {
            if(startYear!=null && endYear != null) {
                int year = (Integer) data.get(Constants.YEAR);
                if(year >= startYear && year <= endYear) {
                    revenue = ((Number)data.get(Constants.VALUE)).doubleValue();
                } else {
                    if(useCAGR) { // USE CAGR HERE?

                    }
                }
            } else {
                revenue = ((Number) data.get(Constants.VALUE)).doubleValue();
            }
        } else {
            // find revenues
            if(associations==null) loadAssociations();
            double totalRevenueOfLevel = 0d;
            double totalRevenueFromMarketShares = 0d;
            boolean foundRevenueInSubMarket = false;
            boolean foundRevenueInMarketShares = false;
            for(Association association : associationsMeta) {
                if (this.getClass().getSimpleName().equals(Association.Model.Market.toString())) {
                    List<Model> assocModels = associations.getOrDefault(association, Collections.emptyList());
                    // look for sub markets
                    if (association.getAssociationName().equals("Sub Market")) {
                        if (assocModels != null && assocModels.size() > 0) {
                            for (Model assoc : assocModels) {
                                foundRevenueInSubMarket = true;
                                totalRevenueOfLevel += assoc.calculateRevenue(startYear, endYear, useCAGR, option, null, isParentRevenue);
                            }
                        }
                    }
                }
                if (association.getModel().toString().contains("Revenue")) {
                    // found actual revenue data
                    if (this.getClass().getSimpleName().equals(Association.Model.Company.toString()) && association.getModel().equals(Association.Model.MarketShareRevenue)) {
                        // backup company revenue = (sum of market shares)
                        List<Model> revenues = associations.get(association);
                        if (revenues != null && revenues.size() > 0) {
                            // group
                            Map<Integer, List<Model>> revenueGroups = revenues.stream().collect(Collectors.groupingBy(e -> (Integer) e.getData().get(Constants.MARKET_ID), Collectors.toList()));
                            totalRevenueFromMarketShares = revenueGroups.values().stream().mapToDouble(list -> {
                                List<Double> byCagr = new ArrayList<>();
                                if (startYear != null && endYear != null) {
                                    List<Model> byYear = new ArrayList<>();
                                    for (int year = startYear; year <= endYear; year++) {
                                        final int _year = year;
                                        Model modelYear = list.stream().filter(m -> ((Integer) m.getData().get(Constants.YEAR)).equals(_year)).findAny().orElse(null);
                                        if (modelYear != null) {
                                            byYear.add(modelYear);
                                        } else {
                                            if(useCAGR) {
                                                Double cagr = calculateFromCAGR(list, _year);
                                                if(cagr!=null) {
                                                    byCagr.add(cagr);
                                                } else {
                                                    if (option.equals(Constants.MissingRevenueOption.error)) {
                                                        throw new MissingRevenueException("Missing market share in " + year + " for " + data.get(Constants.NAME), year, Association.Model.valueOf(this.getClass().getSimpleName()), id, association);
                                                    }
                                                }

                                            } else {
                                                if (option.equals(Constants.MissingRevenueOption.error)) {
                                                    throw new MissingRevenueException("Missing market share in " + year + " for " + data.get(Constants.NAME), year, Association.Model.valueOf(this.getClass().getSimpleName()), id, association);
                                                }
                                            }
                                        }
                                    }
                                    list = byYear;
                                }
                                return list.stream().mapToDouble(d -> (Double)d.getData().get(Constants.VALUE)).sum() + byCagr.stream().mapToDouble(d->d).sum();
                            }).sum();
                            foundRevenueInMarketShares = true;
                        }
                    }
                }
            }
            for(Association association : associationsMeta) {
                if (association.getModel().toString().contains("Revenue")) {
                    if (!(this.getClass().getSimpleName().equals(Association.Model.Company.toString()) && association.getModel().equals(Association.Model.MarketShareRevenue))) {
                        // only non market shares for other models
                        if(!association.getModel().equals(Association.Model.MarketShareRevenue)) {
                            List<Model> revenues = associations.get(association);
                            if (revenues != null && revenues.size() > 0) {
                                List<Model> revenueModelsSorted = revenues.stream().sorted((e1, e2) -> ((Integer) e2.getData().get(Constants.YEAR)).compareTo((Integer) e1.getData().get(Constants.YEAR))).collect(Collectors.toList());
                                List<Double> byCagr = new ArrayList<>();
                                if(startYear!=null && endYear != null) {
                                    List<Model> byYear = new ArrayList<>();
                                    for(int year = startYear; year <= endYear; year++) {
                                        final int _year = year;
                                        Model modelYear = revenueModelsSorted.stream().filter(m -> ((Integer) m.getData().get(Constants.YEAR)).equals(_year)).findAny().orElse(null);
                                        if(modelYear != null) {
                                            byYear.add(modelYear);
                                        } else {
                                            // check cagr for other years
                                            if(useCAGR) {
                                                Model best = revenueModelsSorted.stream().filter(m->m.getData().get(Constants.CAGR)!=null).min((e1,e2)->Integer.compare(Math.abs((Integer)e1.getData().get(Constants.YEAR)-_year), Math.abs((Integer)e2.getData().get(Constants.YEAR)-_year))).orElse(null);
                                                if(best!=null) {
                                                    double cagr = calculateFromCAGR(best, _year);
                                                    byCagr.add(cagr);
                                                    calculationInformation.add(new CalculationInformation(_year,(Double)best.getData().get(Constants.CAGR),false,false,cagr,best));

                                                } else if (option.equals(Constants.MissingRevenueOption.error) && !foundRevenueInSubMarket && !foundRevenueInMarketShares) {
                                                    throw new MissingRevenueException("Missing revenues in " + year + " for " + data.get(Constants.NAME), year, Association.Model.valueOf(this.getClass().getSimpleName()), id, association);
                                                }

                                            } else{
                                                if (option.equals(Constants.MissingRevenueOption.error) && !foundRevenueInSubMarket && !foundRevenueInMarketShares) {
                                                    throw new MissingRevenueException("Missing revenues in " + year + " for " + data.get(Constants.NAME), year, Association.Model.valueOf(this.getClass().getSimpleName()), id, association);
                                                }
                                            }
                                        }
                                    }
                                    revenueModelsSorted = byYear;
                                }
                                if (revenueModelsSorted.size() > 0 || byCagr.size()>0) {
                                    revenue = revenueModelsSorted.stream().mapToDouble(e->(Double)e.getData().get(Constants.VALUE)).sum() + byCagr.stream().mapToDouble(d->d).sum();

                                } else {
                                    if(option.equals(Constants.MissingRevenueOption.error) &&  !foundRevenueInSubMarket && !foundRevenueInMarketShares) {
                                        throw new MissingRevenueException("Missing revenues in " + startYear+" for " + data.get(Constants.NAME), startYear, Association.Model.valueOf(this.getClass().getSimpleName()), id, association);
                                    }
                                }
                            } else {
                                if(option.equals(Constants.MissingRevenueOption.error) && !foundRevenueInSubMarket && !foundRevenueInMarketShares) {
                                    throw new MissingRevenueException("Missing revenues in " + startYear+" for " + data.get(Constants.NAME), startYear, Association.Model.valueOf(this.getClass().getSimpleName()), id, association);
                                }
                            }
                        }
                    }
                }
            }
            if(foundRevenueInSubMarket && this.getClass().getSimpleName().equals(Association.Model.Market.toString()) && revenue==null) {
                calculationInformation.add(new CalculationInformation(null,null,true,false, totalRevenueOfLevel, null));
                revenue = totalRevenueOfLevel;
            }
            if(foundRevenueInMarketShares && this.getClass().getSimpleName().equals(Association.Model.Company.toString()) && revenue==null) {
                calculationInformation.add(new CalculationInformation(null,null,false,true, totalRevenueFromMarketShares, null));
                revenue = totalRevenueFromMarketShares;
            }
        }

        if(revenue==null && option.equals(Constants.MissingRevenueOption.replace)) {
            revenue = 0.0;
        }

        this.percentage = previousRevenue == null ? null : (revenue==null ? null : (isParentRevenue ? (revenue/previousRevenue) : (previousRevenue/revenue)));
        if(this.percentage!=null && Double.isNaN(this.percentage)) {
            this.percentage = null;
        }
        return revenue==null ? 0. : revenue;
    }

    /*
        To calculate revenue, use the latest attached revenue, unless one is not present, in which case
            if it is a market, then calculate revenue from the total of all the sub children of that type (sub markets).
         If no revenue is present for a company, do nothing. If no revenue is present for a product, do nothing.
         Eventually, we can calculate revenues of markets for other years using the defined CAGR of a recent period.
     */
    private void loadNestedAssociationHelper(String groupRevenuesBy, boolean allowEdit, Integer startYear, Integer endYear, boolean useCAGR, Constants.MissingRevenueOption option, ContainerTag container, Set<String> alreadySeen, AtomicInteger cnt, Model original, int depth, int maxDepth) {
        if(depth > maxDepth) return;
        System.out.println("Load nested... "+this.getClass().getSimpleName()+id);
        if(associations==null) {
            loadAssociations();
        }
        String originalId = original.getClass().getSimpleName()+original.getId();
        Map<Association,List<Model>> modelMap = new HashMap<>();
        for(Association association : associationsMeta) {
            //if(!association.getModel().equals(Association.Model.MarketShareRevenue) && association.getModel().toString().endsWith("Revenue")) {
            //    continue;
            //}
            if(association.getAssociationName().startsWith("Parent ")||association.getAssociationName().equals("Sub Company")) {
                continue;
            }
            if(isRevenueModel() && !(association.getAssociationName().equals("Sub Revenue"))) {
                continue;
            }
            // if not revenue and node expanded then exit
            if(!association.getModel().toString().contains("Revenue") && !association.getModel().equals(Association.Model.Region) && depth >= 2) {
                continue;
            }
            List<Model> assocModels = associations.getOrDefault(association, Collections.emptyList());
            if(startYear!=null && endYear!=null) {
                assocModels = assocModels.stream().filter(m -> {
                    if (!m.getData().containsKey(Constants.YEAR)) return true;
                    return ((Integer) m.getData().get(Constants.YEAR)) >= startYear && ((Integer) m.getData().get(Constants.YEAR)) <= endYear;
                }).collect(Collectors.toList());
            }
            //if(assocModels.size()>0) {
                modelMap.put(association, assocModels);
            //}
        }
        calculateRevenue(startYear, endYear, useCAGR, option, null, true);
        if(modelMap.size()>0) {
            // recurse
            String display = "block;";
            String displayPlus = original==this || modelMap.size()==1 ? "none;" : "block;";
            container.with(
                    li().attr("style", "list-style: none; cursor: pointer; display: "+displayPlus).withText("+")
                            .attr("onclick", "$(this).nextAll().slideToggle();")
            );
            for(Association association : associationsMeta) {
                List<Model> models = modelMap.get(association);
                if(models==null) {
                    continue;
                }
                boolean pluralize = Arrays.asList(Association.Type.OneToMany, Association.Type.ManyToMany).contains(association.getType());
                List<ContainerTag> tag = new ArrayList<>();
                ContainerTag ul = ul();
                String name;
                if (association.getModel().toString().contains("Revenue") && !association.getModel().toString().equals(MarketShareRevenue.class.getSimpleName())) {
                    if(association.getAssociationName().startsWith("Sub")) {
                        name = pluralize ? "Sub Revenues" : "Sub Revenue";
                    } else {
                        name = pluralize ? "Global Revenues" : "Global Revenue";
                    }
                } else {
                    name = pluralize ? Constants.pluralizeAssociationName(association.getAssociationName()) : association.getAssociationName();
                }
                tag.add(
                        li().attr("style", "list-style: none; display: " + display).with(
                                h6(name).attr("style", "cursor: pointer; display: inline;")
                                        .attr("onclick", "$(this).nextAll('ul,li').slideToggle();"),
                                span("(Revenue: " + formatRevenueString(revenue) +")").withClass("association-revenue-totals").attr("style", "margin-left: 10px; display: inline;")
                        ).with(
                                ul.attr("style", "display: " + display)
                        )
                );
                // check for CAGR's used
                if(calculationInformation!=null) {
                    for(CalculationInformation info : calculationInformation.stream().filter(c->c.getYear()!=null).sorted((c1,c2)->Integer.compare(c2.getYear(),c1.getYear())).collect(Collectors.toList())) {
                        if(Arrays.asList(Association.Model.MarketRevenue, Association.Model.CompanyRevenue, Association.Model.ProductRevenue).contains(association.getModel())) {
                            if (info.getCagrUsed() != null && info.getRevenue() != null) {
                                // found cagr
                                ul.with(li().attr("style", "display: inline;").with(
                                        div("Projection for " + info.getYear() + " (Revenue: " + formatRevenueString(info.getRevenue()) + ")").attr("style", "font-weight: bold; cursor: pointer;").withClass("resource-data-field").attr("onclick", "$(this).children().slideToggle();").attr("data-val", info.getRevenue().toString()).with(
                                                div().attr("style", "display: none;").with(
                                                        div("CAGR used: " + Constants.getFieldFormatter(Constants.CAGR).apply(info.getCagrUsed())).attr("style", "font-weight: normal;"),
                                                        div("From revenue: ").with(info.getReference().getSimpleLink().attr("style", "display: inline;")).attr("style", "font-weight: normal;")
                                                )
                                        ))
                                );
                            }
                        } else if(association.getModel().equals(Association.Model.MarketShareRevenue)) {
                            // check for market share projects
                        } else if(association.getAssociationName().equals("Sub Market")) {
                            // check for sub market revenue propagation
                        }
                    }
                }
                boolean revenueAssociation = association.getModel().equals(Association.Model.MarketShareRevenue);
                Map<Integer,List<Model>> groupedModels;
                if(revenueAssociation && groupRevenuesBy!=null) {
                    // group models by year
                    groupedModels = models.stream().collect(Collectors.groupingBy(e->(Integer)e.getData().get(groupRevenuesBy)));
                } else {
                    groupedModels = Collections.singletonMap(null, models);
                }
                List<Integer> groupKeys = new ArrayList<>(groupedModels.keySet());
                Double totalRevAllYears = null;
                Map<Integer, Double> yearToRevenueMap = new HashMap<>();
                if(groupKeys.size()>0 && groupKeys.get(0)!=null) {
                    if (groupRevenuesBy.equals(Constants.YEAR)) {
                        groupKeys.sort((e1, e2) -> Integer.compare(e2, e1));
                        groupedModels.forEach((year, list) -> {
                            double rev = groupedModels.get(year).stream().mapToDouble(d -> d.calculateRevenue(startYear, endYear, useCAGR, option, null, false)).sum();
                            yearToRevenueMap.put(year, rev);
                        });
                        totalRevAllYears = yearToRevenueMap.values().stream().mapToDouble(d -> d).sum();
                        if (totalRevAllYears == 0) totalRevAllYears = null;
                    }
                }

                for (Integer key : groupKeys) {
                    ContainerTag groupUl;
                    Double yearlyRevenue = key == null ? null : yearToRevenueMap.get(key);
                    if(yearlyRevenue!=null) {
                        String percentStr = totalRevAllYears == null ? "" : (String.format("%.1f", (yearlyRevenue * 100d) / totalRevAllYears) + "%");
                        groupUl = ul().attr("data-val", yearlyRevenue.toString()).withClass("resource-data-field");
                        ul.with(li().with(div(String.valueOf(key) + " (Revenue: " + formatRevenueString(yearlyRevenue) + ") - "+percentStr)
                                .attr("style", "cursor: pointer;").attr("onclick", "$(this).next().slideToggle();")
                        ).attr("style", "display: inline; list-style: none;").with(groupUl));
                    } else {
                        groupUl = ul;
                    }
                    List<Model> groupedModelList = groupedModels.get(key);
                    if(association.getModel().toString().contains("Revenue")) {
                        groupedModelList = new ArrayList<>(groupedModelList);
                        groupedModelList.sort((d1,d2)->Integer.compare((Integer)d2.getData().get(Constants.YEAR), (Integer)d1.getData().get(Constants.YEAR)));
                    }
                    for(Model model : groupedModelList) {
                        if (model.isRevenueModel && startYear != null && endYear != null) {
                            int year = (Integer) model.getData().get(Constants.YEAR);
                            if (year < startYear || year > endYear) {
                                continue;
                            }
                        }
                        String _id = model.getClass().getSimpleName() + model.getId();
                        String[] additionalClasses = new String[]{};
                        if (model.getClass().getSimpleName().equals(MarketShareRevenue.class.getSimpleName())) {
                            // check association
                            if (this.getClass().getSimpleName().equals(Market.class.getSimpleName())) {
                                additionalClasses = new String[]{"market-share-market"};
                            } else if (this.getClass().getSimpleName().equals(Company.class.getSimpleName())) {
                                additionalClasses = new String[]{"market-share-company"};
                            }
                        }
                        boolean sameModel = _id.equals(originalId);
                        ContainerTag inner = ul();
                        Double revToUse = null;
                        if (!this.getClass().getSimpleName().equals(MarketShareRevenue.class.getSimpleName())) {
                            revToUse = revenue;
                        }
                        if(yearlyRevenue!=null) {
                            revToUse = yearlyRevenue;
                        }
                        boolean isParentRevenue;
                        // need to decide whether to show percentage of parent or child
                        String thisClass = this.getClass().getSimpleName();
                        if (!model.isRevenueModel && thisClass.equals(Product.class.getSimpleName())) {
                            isParentRevenue = false;
                        } else {
                            isParentRevenue = true;
                        }
                        model.calculateRevenue(startYear, endYear, useCAGR, option, revToUse, isParentRevenue);

                        groupUl.with(li().attr("style", "display: inline;").with(
                                allowEdit ? model.getLink(association.getReverseAssociationName(), this.getClass().getSimpleName(), id).attr("style", "display: inline;")
                                        : model.getSimpleLink(additionalClasses).attr("style", "display: inline;")
                                , model.getRevenueAsSpan(original), inner));
                        if (!sameModel && !alreadySeen.contains(_id)) {
                            alreadySeen.add(_id);
                            String group;
                            if(model.isRevenueModel) {
                                group = Constants.REGION_ID;
                            } else {
                                group = groupRevenuesBy;
                            }
                            model.loadNestedAssociationHelper(group, allowEdit, startYear, endYear, useCAGR, option, inner, new HashSet<>(alreadySeen), cnt, original, depth + 1, maxDepth);
                        }
                        alreadySeen.add(_id);
                    }
                }
                String listRef = "association-" + association.getAssociationName().toLowerCase().replace(" ", "-") + cnt.getAndIncrement();

                ul.with(li().attr("style", "display: inline;").with(
                        allowEdit?getAddAssociationPanel(association, listRef, original):span())
                );
                container.with(tag);
            }
        }
    }

    public static String formatRevenueString(Double revenue) {
        if(revenue==null) return "";
        return "$"+String.format("%.2f", revenue);
    }

    public void removeManyToOneAssociations(String associationName) {
        for(Association association : associationsMeta) {
            if(association.getAssociationName().equals(associationName)) {
                switch (association.getType()) {
                    case ManyToOne: {
                        // need to set parent id of current model
                        updateAttribute(association.getParentIdField(), null);
                        updateInDatabase();
                        break;
                    }
                }
                break;
            }
        }
    }

    private boolean referencesCountry() {
        if(!isRevenueModel) {
            return false;
        }
        if(data==null) loadAttributesFromDatabase();
        if(data.get(Constants.REGION_ID)==null) return false;
        Model region = new Region((Integer)data.get(Constants.REGION_ID), null);
        region.loadAttributesFromDatabase();
        return region.getData().get(Constants.PARENT_REGION_ID)!=null;
    }

    public void associateWith(@NonNull Model otherModel,@NonNull String associationName, @NonNull Map<String,Object> joinData) {
        // find association
        for(Association association : associationsMeta) {
            if(association.getAssociationName().equals(associationName)) {
                // make sure we haven't introduced in cycles
                if(association.getModel().toString().equals(this.getClass().getSimpleName())) {
                    System.out.println("Checking for cycles...");
                    loadNestedAssociations(); // hack to access allReferences
                    String otherRef = otherModel.getClass().getSimpleName() + otherModel.getId();
                    if (this.allReferences.contains(otherRef)) {
                        throw new RuntimeException("Unable to assign association. Cycle detected.");
                    }
                }
                // make sure that we don't assign the wrong region type
                if(association.getModel().toString().contains("Revenue") && otherModel.getClass().getSimpleName().contains("Revenue")) {
                    if(associationName.contains("Parent")) {
                        // make sure the otherModel does not reference a country
                        if(otherModel.referencesCountry()) {
                            throw new RuntimeException("Cannot associate a sub revenue to a revenue referencing a country.");
                        }
                    } else if(associationName.contains("Sub")) {
                        // make sure this node does not reference a country
                        if(referencesCountry()) {
                            throw new RuntimeException("Cannot associate a sub revenue to a revenue referencing a country.");
                        }
                    }
                }
                switch (association.getType()) {
                    case OneToMany: {
                        // need to set parent id of association
                        otherModel.updateAttribute(association.getParentIdField(), id);
                        otherModel.updateInDatabase();
                        break;
                    }
                    case ManyToOne: {
                        // need to set parent id of current model
                        updateAttribute(association.getParentIdField(), otherModel.getId());
                        updateInDatabase();
                        break;
                    }
                    case ManyToMany: {
                        try {
                            // need to add to join table
                            Connection conn = Database.getConn();
                            PreparedStatement ps = null;
                            String valueStr = "?,?";
                            String fieldStr = association.getParentIdField()+","+association.getChildIdField();
                            for(int i = 0; i < association.getJoinAttributes().size(); i++) {
                                valueStr += ",?";
                                fieldStr += ","+association.getJoinAttributes().get(i);
                            }
                            ps = conn.prepareStatement("insert into "+association.getJoinTableName() + " ("+fieldStr+") values ("+valueStr+") on conflict do nothing");
                            ps.setInt(1, id);
                            ps.setInt(2, otherModel.getId());
                            for(int i = 0; i < association.getJoinAttributes().size(); i++) {
                                ps.setObject(3 + i, joinData.get(association.getJoinAttributes().get(i)));
                            }
                            System.out.println("JOIN: "+new Gson().toJson(joinData));
                            System.out.println("PS: "+ps);
                            ps.executeUpdate();
                            ps.close();
                        } catch(Exception e) {
                            e.printStackTrace();
                        }
                        break;
                    }
                    case OneToOne: {
                        // NOT IMPLEMENTED
                        break;
                    }
                }
                break;
            }
        }
    }

    private static String capitalize(String in) {
        return in.substring(0, 1).toUpperCase() + in.substring(1);
    }

    public ContainerTag getAddAssociationPanel(@NonNull Association association, String listRef, Model diagramModel) {
        return getAddAssociationPanel(association, listRef, diagramModel, null, false);
    }

    public ContainerTag getAddAssociationPanel(@NonNull Association association, String listRef, Model diagramModel, boolean report) {
        return getAddAssociationPanel(association, listRef, diagramModel, null, report);
    }
    public ContainerTag getAddAssociationPanel(@NonNull Association association, String listRef, Model diagramModel, String overrideCreateText, boolean report) {
        if(association.getAssociationName().equals("Parent Revenue") || (association.getAssociationName().equals("Sub Revenue") && referencesCountry()) || association.getModel().equals(Association.Model.Region)) {
            return span();
        }
        Association.Type type = association.getType();
        String prepend = "false";
        String createText = "(New)";
        switch(type) {
            case OneToMany: {
                prepend = "prepend";
                break;
            }
            case ManyToOne: {
                prepend = "false";
                createText = "(Update)";
                if(getData().get(association.getParentIdField())==null) {
                    createText = "(Set)";
                }
                break;
            }
            case ManyToMany: {
                prepend = "prepend";
                break;
            }
            case OneToOne: {
                // not implemented
                break;
            }
        }
        if(overrideCreateText!=null) {
            createText = overrideCreateText;
        }
        boolean isRegion = association.getModel().equals(Association.Model.Region);
        boolean isGlobalRegion = isRegion && data.get(Constants.PARENT_REVENUE_ID) == null;
        Collection<Tag> inputs = new ArrayList<>(Arrays.asList(input().withType("hidden").withName("_association_name").withValue(association.getAssociationName()),
                label(association.getAssociationName()+" Name:").with(
                        select().attr("style","width: 100%").withClass("form-control multiselect-ajax").withName("id")
                                .attr("data-url", "/ajax/resources/"+association.getModel()+"/"+this.getClass().getSimpleName()+"/"+id)
                ), br()));
        ContainerTag panel = div().with(isGlobalRegion? p("Global") : a(createText).withHref("#").withClass("resource-new-link"),div().attr("style", "display: none;").with(
                (isRegion ? span() :
                        getCreateNewForm(association.getModel(),id).attr("data-prepend",prepend).attr("data-list-ref",listRef==null ? null : ("#"+listRef)).attr("data-association", association.getModel().toString())
                                .attr("data-resource", this.getClass().getSimpleName())
                                .attr("data-refresh",diagramModel!=null ? "refresh" : "f")
                                .attr("data-report", report ? "true" : null)
                                .attr("data-original-id",diagramModel!=null ? diagramModel.id.toString() : "f")
                                .attr("data-original-resource",diagramModel!=null ? diagramModel.getClass().getSimpleName() : "f")
                                .attr("data-id", id.toString()).withClass("association").with(
                                input().withType("hidden").withName("_association_name").withValue(association.getAssociationName())
                        )
                ),(isGlobalRegion ? span() : form().attr("data-association-name-reverse", association.getReverseAssociationName()).attr("data-prepend",prepend).attr("data-list-ref",listRef==null ? null : ("#"+listRef)).attr("data-id", id.toString()).withClass("update-association").attr("data-association", association.getModel().toString())
                        .attr("data-resource", this.getClass().getSimpleName())
                        .attr("data-refresh",diagramModel!=null ? "refresh" : "f")
                        .attr("data-original-id",diagramModel!=null ? diagramModel.id.toString() : "f")
                        .attr("data-original-resource",diagramModel!=null ? diagramModel.getClass().getSimpleName() : "f")
                        .attr("data-report", report ? "true" : null)
                        .with(
                                inputs
                        ).with(
                                button("Assign").withClass("btn btn-outline-secondary btn-sm").withType("submit")

                        )
                )
        ));
        return panel;
    }

    public boolean isRegion() {
        return this.getClass().getSimpleName().equals(Association.Model.Region.toString());
    }

    public void loadShowTemplate(boolean back) {
        ContainerTag button;
        if(back) {
            String previousTarget = "#"+tableName+"_index_btn";
            button = button("Back to "+Constants.pluralizeAssociationName(Constants.humanAttrFor(this.getClass().getSimpleName()))).attr("data-target", previousTarget)
                    .withClass("btn btn-outline-secondary btn-sm back-button");
        } else {
            button = span();
        }
        boolean isRegion = isRegion();
        ContainerTag html = div().withClass("col-12").with(
                div().withClass("col-12").with(
                        button,
                        h4(Constants.humanAttrFor(this.getClass().getSimpleName())+" Information"),
                        (isRegion ? span() : button("Delete this "+Constants.humanAttrFor(this.getClass().getSimpleName())).withClass("btn btn-outline-danger btn-sm delete-button"))
                        .attr("data-id", id.toString())
                        .attr("data-resource", this.getClass().getSimpleName())
                        .attr("data-resource-name", Constants.humanAttrFor(this.getClass().getSimpleName()))
                ).with(
                        form().withClass("update-model-form").attr("data-id", id.toString()).attr("data-resource", this.getClass().getSimpleName()).with(
                            availableAttributes.stream().filter(attr->!Constants.isHiddenAttr(attr)).map(attr->{
                                Object val = data.get(attr);
                                Object valOriginal = val;
                                if(attr.equals(Constants.ESTIMATE_TYPE)) {
                                    if(val!=null) {
                                        String v = val.toString().trim();
                                        if(v.equals("0")) {
                                            val = "Low";
                                        } else if (v.equals("1")) {
                                            val = "Medium";
                                        } else if (v.equals("2")) {
                                            val = "High";
                                        }
                                    }
                                }
                                String orginalAttr = attr;
                                boolean editable = !Arrays.asList(Constants.CREATED_AT, Constants.UPDATED_AT).contains(attr) && !isRegion();
                                attr = Constants.humanAttrFor(attr);
                                if(val==null || val.toString().trim().length()==0) val = "";
                                return div().with(
                                        div().attr("data-attr", orginalAttr)
                                                .attr("data-attrname", attr)
                                                .attr("data-val-text", val)
                                                .attr("data-val", valOriginal)
                                                .attr("data-id", id.toString())
                                                .attr("data-resource", this.getClass().getSimpleName())
                                                .attr("data-field-type", orginalAttr.equals(Constants.ESTIMATE_TYPE)?Constants.ESTIMATE_TYPE:Constants.fieldTypeForAttr(orginalAttr))
                                                .withClass("resource-data-field" + (editable ? " editable" : ""))
                                                .withText(attr+": "+val.toString())
                                );
                            }).collect(Collectors.toList())
                        )
                )
        ).with(
                div().withClass("col-12").with(Arrays.asList(Association.Model.Company.toString(),Association.Model.Product.toString(),Association.Model.Market.toString()).contains(this.getClass().getSimpleName()) ?
                        div().withClass("btn-group").attr("style", "display: inline;").with(
                                button("Diagram")
                                        .attr("data-id", id.toString())
                                        .attr("data-resource", this.getClass().getSimpleName())
                                        .withClass("btn btn-outline-secondary btn-md diagram-button"),
                                button("Report")
                                        .attr("data-id", id.toString())
                                        .attr("data-resource", this.getClass().getSimpleName())
                                        .withClass("btn btn-outline-secondary btn-md report-button"),
                                button("Graph")
                                        .attr("data-id", id.toString())
                                        .attr("data-resource", this.getClass().getSimpleName())
                                        .withClass("btn btn-outline-secondary btn-md graph-button")

                                ) : div()
                ),
                div().withClass("col-12").with(
                        h5("Associations"),
                        div().with(
                                ul().withClass("nav nav-tabs").attr("role", "tablist").with(
                                        associationsMeta.stream().map(association-> {
                                            boolean pluralize = Arrays.asList(Association.Type.OneToMany, Association.Type.ManyToMany).contains(association.getType());
                                            String assocId = "tab-link-"+association.getAssociationName().toLowerCase().replace(" ","-");
                                            return li().withClass("nav-item").with(
                                                    a(pluralize?Constants.pluralizeAssociationName(association.getAssociationName()):association.getAssociationName()).withClass("nav-link").attr("data-toggle", "tab").withHref("#" + assocId).attr("role", "tab")
                                            );
                                        }).collect(Collectors.toList())
                                )
                        ),
                        div().withClass("row tab-content").withId("main-container").with(
                                associationsMeta.stream().map(association->{
                                    String assocId = "tab-link-"+association.getAssociationName().toLowerCase().replace(" ","-");
                                    List<Model> models = associations.get(association);
                                    if(models==null) {
                                        models = Collections.emptyList();
                                    }
                                    String listRef = "association-"+association.getAssociationName().toLowerCase().replace(" ","-");
                                    ContainerTag panel = getAddAssociationPanel(association, listRef, null);
                                    return div().attr("role", "tabpanel").withId(assocId).withClass("col-12 tab-pane fade").with(
                                            panel, br(),
                                            div().withId(listRef).with(models.stream().map(model->{
                                                return model.getLink(association.getReverseAssociationName(), this.getClass().getSimpleName(), id);
                                            }).collect(Collectors.toList()))
                                    );
                                }).collect(Collectors.toList())
                        )
                )
        );
        template = html.render();
    }

    public void updateAttribute(String attr, Object val) {
        this.data.put(attr, val);
    }

    public void removeAttribute(String attr) {
        this.data.remove(attr);
    }

    public void loadAssociations() {
        if(!existsInDatabase()) {
            throw new RuntimeException("Cannot load associations if the model does not yet exist in the database.");
        }
        if(data==null) {
            loadAttributesFromDatabase();
        }
        this.associations = new HashMap<>();
        for(Association association : associationsMeta) {
            try {
                if (association.getType().equals(Association.Type.OneToMany)) {
                    List<Model> children = Database.loadOneToManyAssociation(association.getModel(), this, association.getChildTableName(), association.getParentIdField());
                    data.put(association.getModel().toString(), children);
                    associations.put(association, children);
                } else if (association.getType().equals(Association.Type.ManyToOne)) {
                    Model parent = Database.loadManyToOneAssociation(association.getModel(), this, association.getChildTableName(), association.getParentIdField());
                    if(parent!=null) {
                        data.put(association.getModel().toString(), parent);
                        associations.put(association, Collections.singletonList(parent));
                    }
                } else if (association.getType().equals(Association.Type.OneToOne)) {
                    if(association.getChildIdField()==null) {

                    } else {

                    }
                    throw new RuntimeException("One to one associations are not yet implemented.");
                } else if (association.getType().equals(Association.Type.ManyToMany)) {
                    Map<Model,Map<String,Object>> children = Database.loadManyToManyAssociation(association.getModel(), this, association.getJoinTableName(), association.getParentIdField(), association.getChildIdField(), association.getJoinAttributes());
                    List<Model> childrenList = new ArrayList<>(children.keySet());
                    data.put(association.getModel().toString(), childrenList);
                    associations.put(association, childrenList);
                }
            } catch(Exception e) {
                e.printStackTrace();
            }
        }
    }

    public void loadAttributesFromDatabase() {
        if(!existsInDatabase()) {
            throw new RuntimeException("Trying to select a record that does not exist in the database...");
        }
        try {
            this.data = Database.select(tableName, id, availableAttributes);
        } catch(Exception e) {
            e.printStackTrace();
            throw new RuntimeException("Error loading attributes from database: " + e.getMessage());
        }
    }

    public void updateInDatabase() {
        // update database
        if(!existsInDatabase()) {
            throw new RuntimeException("Trying to update a record that does not exist in the database...");
        }
        validateState();
        try {
            Map<String,Object> dataCopy = new HashMap<>(data);
            List<String> keys = new ArrayList<>(availableAttributes);
            keys.remove(Constants.UPDATED_AT);
            keys.remove(Constants.CREATED_AT);
            Database.update(tableName, id, dataCopy, keys);
        } catch(Exception e) {
            e.printStackTrace();
            throw new RuntimeException("Error inserting record into database: " + e.getMessage());
        }
    }


    private void validateState() {
        if(isRevenueModel) {
            List<String> fieldsToHave = Arrays.asList(
                    Constants.YEAR,
                    Constants.VALUE
            );
            for(String field : fieldsToHave) {
                if(data.get(field)==null) {
                    throw new RuntimeException(Constants.humanAttrFor(field)+" must be present");
                }
            }
            // make sure both notes and source are not null
            if(data.get(Constants.NOTES)==null && data.get(Constants.SOURCE)==null) {
                throw new RuntimeException("Must enter a source (for non-estimates) or notes (for estimates)");
            }
            // if note an estimate, a source must exist
            if((data.get(Constants.IS_ESTIMATE)==null || !data.get(Constants.IS_ESTIMATE).toString().toLowerCase().startsWith("t")) && data.get(Constants.SOURCE)==null) {
                throw new RuntimeException("Must enter a source for non-estimates");
            }
            // if is_estimate, we need to have an estimate_type
            if((data.get(Constants.IS_ESTIMATE)!=null && data.get(Constants.IS_ESTIMATE).toString().toLowerCase().startsWith("t")) && data.get(Constants.ESTIMATE_TYPE)==null) {
                throw new RuntimeException("Must enter an estimate type for estimates");
            }
            if((data.get(Constants.ESTIMATE_TYPE)!=null && !Arrays.asList("0","1","2").contains(data.get(Constants.ESTIMATE_TYPE).toString()))) {
                throw new RuntimeException("Invalid value for estimate type.");
            }

        } else {
            if(data.get(Constants.NAME)==null) {
                throw new RuntimeException("Name must be present");
            }
        }

    }


    // save to database for the first time
    public void createInDatabase() {
        if(existsInDatabase()) {
            throw new RuntimeException("Trying to create a record that already exists in the database...");
        }
        validateState();
        try {
            id = Database.insert(tableName, data);
        } catch(Exception e) {
            e.printStackTrace();
            throw new RuntimeException("Error inserting record into database: "+e.getMessage());
        }
    }

    // delete record from the database
    public void deleteFromDatabase(boolean cascade) {
        if(!existsInDatabase()) {
            throw new RuntimeException("Trying to delete a record that does not exist in the database...");
        }
        loadAssociations();
        // cannot delete a market that has submarkets
        for(Map.Entry<Association,List<Model>> entry : associations.entrySet()) {
            if(entry.getKey().getAssociationName().equals("Sub Market")) {
                if(entry.getValue()!=null&&entry.getValue().size()>0) {
                    throw new RuntimeException("Cannot delete a market that has sub markets. Please delete the sub markets first.");
                }
            }
            for(Model association : entry.getValue()) {
                if(cascade && entry.getKey().isDependent()) {
                    association.deleteFromDatabase(true);
                }
                cleanUpParentIds(entry.getKey(), association.getId());
            }
        }
        try {
            Database.delete(tableName, id);
        } catch(Exception e) {
            e.printStackTrace();
            throw new RuntimeException("Error deleting record from database: "+e.getMessage());
        }
    }

    public void cleanUpParentIds(@NonNull Association association, int assocId) {
        // clean up join table if necessary
        if (association.getType().equals(Association.Type.ManyToMany)) {
            try {
                Database.deleteByFieldName(association.getJoinTableName(), association.getParentIdField(), id);
            } catch (Exception e) {
                e.printStackTrace();
                throw new RuntimeException("Error deleting record from database: " + e.getMessage());
            }
        } else if(Arrays.asList(Association.Type.OneToMany, Association.Type.ManyToOne).contains(association.getType())) {
            // child table has the key
            try {
                Integer idToUse;
                if(association.getType().equals(Association.Type.ManyToOne)) {
                    idToUse = id;
                } else {
                    idToUse = assocId;
                }
                if(isRevenueModel && association.getModel().toString().contains("Revenue")) {
                    // revenue to revenue model - need to delete dependent stuff
                    Database.delete(association.getChildTableName(), idToUse);
                } else {
                    Database.nullifyFieldName(association.getChildTableName(), association.getParentIdField(), idToUse);
                }
            } catch (Exception e) {
                e.printStackTrace();
                throw new RuntimeException("Error deleting record from database: " + e.getMessage());
            }
        }
    }
}
