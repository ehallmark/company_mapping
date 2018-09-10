package models;

import com.googlecode.wickedcharts.highcharts.options.*;
import com.googlecode.wickedcharts.highcharts.options.color.ColorReference;
import com.googlecode.wickedcharts.highcharts.options.color.RgbaColor;
import com.googlecode.wickedcharts.highcharts.options.series.Point;
import com.googlecode.wickedcharts.highcharts.options.series.PointSeries;
import com.googlecode.wickedcharts.highcharts.options.series.Series;
import database.Database;
import graph.Edge;
import graph.Graph;
import graph.Node;
import j2html.tags.ContainerTag;
import j2html.tags.Tag;
import javafx.util.Pair;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;

import java.awt.*;
import java.io.Serializable;
import java.time.LocalDate;
import java.util.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static j2html.TagCreator.*;

public abstract class Model implements Serializable {
    private static final long serialVersionUID = 1L;

    public enum RevenueDomain {
        global,
        regional,
        national
    }


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
    protected transient Map<Association,List<Model>> associations;
    protected String template;
    @Getter
    private final boolean isRevenueModel;
    private Double revenue;
    private Double percentage;
    @Getter @Setter
    private transient Graph nodeCache;
    private transient List<CalculationInformation> calculationInformation;
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

    public String getName() {
        if(isRevenueModel) {
            if(data==null||!data.containsKey(Constants.YEAR)) loadAttributesFromDatabase();
            // need to load region
            String regionName = "Global";
            if(data.get(Constants.REGION_ID)!=null) {
                Model region = Graph.load().findNode(Association.Model.Region, (Integer)data.get(Constants.REGION_ID)).getModel();
                region.loadAttributesFromDatabase();
                regionName = region.getName();
            }
            return "Revenue - "+regionName+" ("+data.get(Constants.YEAR)+")";

        } else {
            if(data==null||!data.containsKey(Constants.NAME)) loadAttributesFromDatabase();
            return (String) data.get(Constants.NAME);
        }
    }

    public boolean existsInDatabase() {
        return id != null;
    }

    public ContainerTag getLink(@NonNull String associationName, @NonNull String associationModel, @NonNull Integer associationId) {
        if(data==null) {
            loadAttributesFromDatabase();
        }
        return div().withId("node-"+this.getClass().getSimpleName()+"-"+id).with(
                getSimpleLink(),
                (isRegion() || associationName.equals("Sub Revenue") ? span() :
                    span("X").attr("data-association", associationModel)
                        .attr("data-association-name", associationName)
                        .attr("data-association-id", associationId.toString())
                            .attr("style","cursor: pointer;").withClass("delete-node")
                            .attr("data-resource", this.getClass().getSimpleName()).attr("data-id", id)
                )
        );
    }

    public Association findAssociation(@NonNull String associationName) {
        return associationsMeta.stream().filter(a->a.getAssociationName().equals(associationName))
                .findAny().orElse(null);
    }

    public void buildTimelineSeries(boolean column, int maxGroups, String groupByField, RevenueDomain revenueDomain, Integer regionId, Integer minYear, Integer maxYear, boolean useCAGR, Constants.MissingRevenueOption option, List<Model> models, Options options, Association association) {
        buildTimelineSeries(column, maxGroups, getName(), getType(), id, revenue, groupByField, revenueDomain, regionId, minYear, maxYear, useCAGR, option, models, options, association);
    }


    public static void buildTimelineSeries(boolean column, int maxGroups, @NonNull String name, @NonNull Association.Model type, Integer id, Double revenue, String groupByField, RevenueDomain revenueDomain, Integer regionId, Integer minYear, Integer maxYear, boolean useCAGR, Constants.MissingRevenueOption option, List<Model> models, Options options, Association association) {
        // yearly timeline
        if(minYear==null || maxYear==null) return;
        if(maxYear - minYear <= 0) {
            return;
        }
        options.setTitle(new Title().setText(name));
        List<String> categories = new ArrayList<>();
        for(int year = minYear; year <= maxYear; year ++ ) {
            categories.add(String.valueOf(year));
        }
        if(column) {
            options.setPlotOptions(new PlotOptionsChoice().setColumn(new PlotOptions().setGroupPadding(0.02f).setShowInLegend(groupByField != null)));
        } else {
            options.setPlotOptions(new PlotOptionsChoice().setLine(new PlotOptions().setShowInLegend(groupByField != null)));
        }

        options.setChartOptions(new ChartOptions().setType(column? SeriesType.COLUMN : SeriesType.LINE).setWidth(1000));
        options.setxAxis(new Axis().setCategories(categories).setType(AxisType.CATEGORY));
        options.setyAxis(new Axis().setTitle(new Title().setText("Revenue ($)")));
        String title;
        if(groupByField==null) {
            title = "Revenue Timeline";
        } else {
            title = "Revenue Timeline by "+Constants.humanAttrFor(groupByField);
            options.getTooltip().setPointFormat("<span style=\"color:{point.color}\">\u25CF</span> <b>{series.name}</b><br/><b>Revenue: ${point.y:.2f} </b><br/>");
        }
        options.setSubtitle(new Title().setText(title));
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
                assoc.calculateRevenue(revenueDomain, regionId, minYear, maxYear, useCAGR, option, revenue, true);
                Double rev = assoc.revenue;
                assoc.getSimpleLink();
                Integer year = (Integer) assoc.getData().get(Constants.YEAR);
                if(rev!=null) {
                    series.addPoint(new Point(year.toString(), rev));
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
                        throw new MissingRevenueException("Missing revenues in " + missingYear+" for " + name, missingYear, type, id, association);
                    } else if(option.equals(Constants.MissingRevenueOption.replace)) {
                        series.addPoint(new Point(String.valueOf(missingYear), 0));
                    }
                }
            }
            options.addSeries(series);

        } else {
            models.stream().collect(Collectors.groupingBy(e -> e.getData().get(groupByField))).forEach((year, list) -> {
                PointSeries series = new PointSeries();
                series.setShowInLegend(true);
                // get name of group by field by id
                Model dataReference;
                if (groupByField.equals(Constants.MARKET_ID)) {
                    // find market
                    dataReference = new Market((Integer) year, null);
                } else if (groupByField.equals(Constants.COMPANY_ID)) {
                    // find company
                    dataReference = new Company((Integer) year, null);

                } else {
                    throw new RuntimeException("Unknown group by field in time line chart.");
                }
                dataReference.loadAttributesFromDatabase();
                series.setName(dataReference.getName());
                Set<String> missingYears = new HashSet<>(categories);
                for (Model assoc : list) {
                    assoc.calculateRevenue(revenueDomain, regionId, minYear, maxYear, useCAGR, option, revenue, true);
                    Double rev = assoc.revenue;
                    assoc.getSimpleLink();
                    Integer _year = (Integer) assoc.getData().get(Constants.YEAR);
                    if (rev != null) {
                        series.addPoint(new Point(_year.toString(), rev));
                        missingYears.remove(_year.toString());
                    }
                }
                for (String missing : missingYears) {
                    int missingYear = Integer.valueOf(missing);
                    Double missingRev = null;
                    if (useCAGR) {
                        missingRev = calculateFromCAGR(list, missingYear);
                    }

                    if (missingRev != null) {
                        series.addPoint(new Point(String.valueOf(missingYear), missingRev));
                    } else {
                        if (option.equals(Constants.MissingRevenueOption.error)) {
                            throw new MissingRevenueException("Missing revenues in " + missingYear + " for " + name, missingYear, type, id, association);
                        } else if (option.equals(Constants.MissingRevenueOption.replace)) {
                            series.addPoint(new Point(String.valueOf(missingYear), 0));
                        }
                    }
                }
                options.addSeries(series);
            });
            // trim smallest series
            if (options.getSeries() != null) {
                options.setSeries(((List<PointSeries>) options.getSeries()).stream().map(s -> new Pair<>(s, s.getData() == null ? 0d : s.getData().stream().mapToDouble(d -> ((Point) d).getY().doubleValue()).sum())).
                        filter(s -> s.getValue() > 0d).sorted((e1, e2) -> Double.compare(e2.getValue(), e1.getValue()))
                        .limit(maxGroups).map(p -> p.getKey()).collect(Collectors.toList()));
            }
        }
        if(options.getSeries()!=null) {
            // sort series data
            if(!column) {
                for (PointSeries series : (List<PointSeries>) options.getSeries()) {
                    if (series.getData() != null) {
                        series.setData(series.getData().stream().sorted((p1,p2) -> p1.getName().compareTo(p2.getName())).collect(Collectors.toList()));
                    }
                }
            }
        }

    }


    public PointSeries buildPieSeries(String groupByField, String title,RevenueDomain revenueDomain, Integer regionId, Integer minYear, Integer maxYear, boolean useCAGR, Constants.MissingRevenueOption option, List<Model> models, Options options, Association association) {
        // yearly timeline
        if(minYear==null || maxYear==null) return null;
        if(maxYear - minYear <= 0) return null;

        List<String> categories = new ArrayList<>();
        for(int year = minYear; year <= maxYear; year ++ ) {
            categories.add(String.valueOf(year));
        }
        options.setPlotOptions(new PlotOptionsChoice().setPie(new PlotOptions().setAllowPointSelect(false).setSize(new PixelOrPercent(80, PixelOrPercent.Unit.PERCENT))));
        options.setChartOptions(new ChartOptions().setWidth(1000).setType(SeriesType.PIE));
        options.setSubtitle(new Title().setText(title));
        options.setTitle(new Title().setText(getName()));
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
                assoc.calculateRevenue(revenueDomain, regionId, minYear, maxYear, useCAGR, option, revenue, true);
                Double rev = assoc.revenue;
                assoc.getSimpleLink();
                String name = assoc.getName();
                if (rev == null) {
                    rev = 0d;
                }
                series.addPoint(new Point(name, rev));
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
                String label = dataReference.getName();
                Set<String> missingYears = new HashSet<>(categories);
                Double y = null;
                for (Model assoc : list) {
                    assoc.calculateRevenue(revenueDomain, regionId, minYear, maxYear, useCAGR, option, revenue, true);
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
                            throw new MissingRevenueException("Missing revenues in " + missingYear+" for " + getName(), missingYear, Association.Model.valueOf(this.getClass().getSimpleName()), id, association);
                        }
                    }
                }
                if(y==null) {
                    y = 0d;
                }
                series.addPoint(new Point(label, y));

            });
        }
        // sort series
        if (series.getData() != null) {
            series.setData(series.getData().stream().sorted((e1, e2) -> Double.compare(e2.getY().doubleValue(), e1.getY().doubleValue())).collect(Collectors.toList()));

        }
        return series;
    }


    public void buildMarketShare(String groupByField, String title, RevenueDomain revenueDomain, Integer regionId, Integer minYear, Integer maxYear, boolean useCAGR, Constants.MissingRevenueOption option, List<Model> models, Options options, Association association, Map<String,String> groupToFieldMap, String... groupByFields) {
        final PointSeries series = buildPieSeries(groupByField, title,revenueDomain, regionId,  minYear, maxYear, useCAGR, option, models, options, association);
        if (series != null) {
            options.addSeries(series);
            if (groupByFields.length > 0) {
                series.setSize(new PixelOrPercent(60, PixelOrPercent.Unit.PERCENT));
                series.setDataLabels(new DataLabels(true).setColor(Color.WHITE).setDistance(-40));
                PointSeries priorSeries = series;
                for (String additionalGroup : groupByFields) {
                    List<PointSeries> groups = new ArrayList<>();
                    models = models.stream().flatMap(model -> {
                        model.loadAssociations();
                        List<Model> assocs = model.getAssociations().getOrDefault(model.findAssociation(additionalGroup), Collections.emptyList());
                        PointSeries innerSeries = buildPieSeries(groupToFieldMap.get(additionalGroup), title, revenueDomain, regionId, minYear, maxYear, useCAGR, option, assocs, options, association);
                        if (innerSeries != null) {
                            groups.add(innerSeries);
                        } else {
                            groups.add(new PointSeries());
                        }
                        return assocs.stream();
                    }).collect(Collectors.toList());
                    PointSeries innerSeries = combineSeriesGroups(groups, priorSeries);
                    priorSeries = innerSeries;
                    innerSeries.setSize(new PixelOrPercent(80, PixelOrPercent.Unit.PERCENT));
                    innerSeries.setInnerSize(new PixelOrPercent(60, PixelOrPercent.Unit.PERCENT));
                    options.addSeries(innerSeries);
                }
            } else {
                // set colors
                List<int[]> colors = ChartHelper.RGB_COLORS;
                List<ColorReference> colorReferences = new ArrayList<>(colors.size());
                for (int[] color : colors) {
                    ColorReference colorRef = ChartHelper.radialColorReference(color);
                    colorReferences.add(colorRef);
                }
                series.setInnerSize(new PixelOrPercent(55, PixelOrPercent.Unit.PERCENT));
                series.setSize(new PixelOrPercent(80, PixelOrPercent.Unit.PERCENT));
                options.setColors(colorReferences);
            }
            // clear Zero points
            for(Series<?> seriesOptions : options.getSeries()) {
                PointSeries pointSeries = (PointSeries)seriesOptions;
                if (pointSeries.getData() != null) {
                    pointSeries.setData(pointSeries.getData().stream().filter(p -> p.getY().doubleValue() > 0).collect(Collectors.toList()));
                }
            }
        }

    }

    private PointSeries combineSeriesGroups(@NonNull List<PointSeries> groups, @NonNull PointSeries priorSeries) {
        if(priorSeries.getData()==null) return new PointSeries();
        if(groups.size()!=priorSeries.getData().size()) throw new RuntimeException("Groups and models must have the same cardinality "+groups.size()+" != "+priorSeries.getData().size());
        final int n = groups.size();
        PointSeries series = new PointSeries();
        for(int i = 0; i < n; i++) {
            PointSeries group = groups.get(i);
            if(group.getData()==null) continue;;
            Point oldPoint = priorSeries.getData().get(i);
            int[] color = ChartHelper.getColor(i, 0);
            RgbaColor colorRef = new RgbaColor(color[0], color[1], color[2], 1f);
            oldPoint.setColor(colorRef);

            double sumOfGroup = group.getData() == null ? 0d : group.getData().stream().mapToDouble(p->p.getY().doubleValue()).sum();
            double modelRevenue = oldPoint.getY().doubleValue();
            int p = 0;
            for(Point point : group.getData()) {
                series.addPoint(point);
                int[] innerColor = ChartHelper.getColor(i, Math.min(90, p*10));
                RgbaColor innerColorRef = new RgbaColor(innerColor[0], innerColor[1], innerColor[2], 1f);
                point.setColor(innerColorRef);
                p++;
            }

            // add other point
            if(modelRevenue-sumOfGroup > 0.00001) {
                Point point = new Point("Remaining", modelRevenue-sumOfGroup);
                series.addPoint(point);
                int[] innerColor = ChartHelper.getColor(i, Math.min(90, p*10));
                RgbaColor innerColorRef = new RgbaColor(innerColor[0], innerColor[1], innerColor[2], 1f);
                point.setColor(innerColorRef);
            }
        }
        return series;
    }

    private static Options getDefaultChartOptions() {
        return new Options()
                .setExporting(new ExportingOptions().setEnabled(true))
                .setTooltip(new Tooltip().setEnabled(true)
                        .setHeaderFormat("{point.key}<br/>")
                        .setPointFormat("<span style=\"color:{point.color}\">\u25CF</span> <b> Revenue: ${point.y:.2f}</b><br/>"))
                .setCreditOptions(new CreditOptions().setEnabled(true).setText("GTT Group").setHref("http://www.gttgrp.com/"));
    }

    public List<Options> buildCharts(boolean column, int maxGroups, @NonNull String associationName, RevenueDomain revenueDomain, Integer regionId, Integer minYear, Integer maxYear, boolean useCAGR, Constants.MissingRevenueOption option) {
        Association association = findAssociation(associationName);
        if(association==null) {
            return null;
        }
        loadAssociations();
        List<Model> assocModels = associations.getOrDefault(association, Collections.emptyList());
        return buildCharts(column, maxGroups, assocModels, association, revenueDomain, regionId, minYear, maxYear, useCAGR, option);
    }


    public List<Options> buildCharts(boolean column, int maxGroups, @NonNull List<Model> assocModels, @NonNull Association association, RevenueDomain revenueDomain, Integer regionId, Integer minYear, Integer maxYear, boolean useCAGR, Constants.MissingRevenueOption option) {
        List<Options> allOptions = new ArrayList<>();
        Options options = getDefaultChartOptions();
        allOptions.add(options);
        calculateRevenue(revenueDomain, regionId, minYear, maxYear, useCAGR, option, null, false);
        if(this instanceof Market) {
            switch(association.getModel()) {
                case MarketRevenue: {
                    buildTimelineSeries(column, maxGroups, null,revenueDomain, regionId, minYear, maxYear, useCAGR, option, assocModels, options, association);
                    break;
                }
                case Market: {
                    if(association.getAssociationName().startsWith("Sub")) {
                        // sub market
                        buildMarketShare(null,association.getReverseAssociationName().equals("All Revenue") ? "" : "Sub Markets", revenueDomain, regionId, minYear, maxYear, useCAGR, option, assocModels, options, association, null); //, Collections.singletonMap("Market Share",Constants.COMPANY_ID), "Market Share");
                    } else {
                        options.setSubtitle(new Title().setText("Parent Market"));
                        // parent market
                        if(assocModels.size()>0) {
                            Model parent = assocModels.get(0);
                            if(parent.getAssociations()==null) parent.loadAssociations();
                            // get sub markets for parent
                            Association subs = parent.getAssociationsMeta().stream().filter(a->a.getAssociationName().equals(association.getReverseAssociationName())).findAny().orElse(null);
                            if(subs!=null) {
                                List<Model> associationSubs = parent.getAssociations().get(subs);
                                if(associationSubs!=null) {
                                    parent.buildMarketShare(null,"Parent Market",revenueDomain, regionId, minYear, maxYear, useCAGR, option, associationSubs, options, association, null);
                                }
                            }
                        }
                    }
                    break;
                }
                case MarketShareRevenue: {
                    // graph of all companies associated with this market
                    buildMarketShare(Constants.COMPANY_ID,"Companies",revenueDomain, regionId, minYear, maxYear, useCAGR, option, assocModels, options, association, null);
                    if(maxYear - minYear > 0) {
                        Options timelineOptions = getDefaultChartOptions();
                        buildTimelineSeries(column, maxGroups, Constants.COMPANY_ID,revenueDomain, regionId, minYear, maxYear, useCAGR, option, assocModels, timelineOptions, association);
                        allOptions.add(timelineOptions);
                    }
                    break;
                }
                case Product: {
                    buildMarketShare(null,"Market Products",revenueDomain, regionId, minYear, maxYear, useCAGR, option, assocModels, options, association, null);
                    break;
                }
            }
        } else if(this instanceof Company) {
            switch (association.getModel()) {
                case CompanyRevenue: {
                    // yearly timeline
                    buildTimelineSeries(column, maxGroups, null, revenueDomain, regionId, minYear, maxYear, useCAGR, option, assocModels, options, association);
                    break;
                }
                case Company: {
                    // check sub company or parent company
                    if(association.getAssociationName().startsWith("Sub")) {
                        // children
                        buildMarketShare(null,association.getReverseAssociationName().equals("All Revenue") ? "" : "Subsidiaries", revenueDomain, regionId,minYear, maxYear, useCAGR, option, assocModels, options, association, null);
                    } else {
                        // parent
                        options.setSubtitle(new Title().setText("Parent Company"));
                        if(assocModels.size()>0) {
                            Model parent = assocModels.get(0);
                            if(parent.getAssociations()==null) parent.loadAssociations();
                            // get sub markets for parent
                            Association subs = parent.getAssociationsMeta().stream().filter(a->a.getAssociationName().equals(association.getReverseAssociationName())).findAny().orElse(null);
                            if(subs!=null) {
                                List<Model> associationSubs = parent.getAssociations().get(subs);
                                if(associationSubs!=null) {
                                    parent.buildMarketShare(null,"Parent Company", revenueDomain, regionId,minYear, maxYear, useCAGR, option, associationSubs, options, association, null);
                                }
                            }
                        }
                    }
                    break;
                }
                case MarketShareRevenue: {
                    // graph of all markets associated with this company
                    buildMarketShare(Constants.MARKET_ID,"Markets", revenueDomain, regionId,minYear, maxYear, useCAGR, option, assocModels, options, association, null);
                    if(maxYear - minYear > 0) {
                        Options timelineOptions = getDefaultChartOptions();
                        buildTimelineSeries(column, maxGroups, Constants.MARKET_ID,revenueDomain, regionId, minYear, maxYear, useCAGR, option, assocModels, timelineOptions, association);
                        allOptions.add(timelineOptions);
                    }
                    break;
                } case Product: {
                    buildMarketShare(null,"Company Products", revenueDomain, regionId,minYear, maxYear, useCAGR, option, assocModels, options, association, null);
                    break;
                }
            }

        } else if(this instanceof Product) {
            switch (association.getModel()) {
                case ProductRevenue: {
                    // yearly timeline
                    buildTimelineSeries(column, maxGroups, null,revenueDomain, regionId, minYear, maxYear, useCAGR, option, assocModels, options, association);
                    break;
                }
                case Company: {
                    // graph of all products of this product's company
                    options.setSubtitle(new Title().setText("Company Products"));
                    if(assocModels.size()>0) {
                        Model parent = assocModels.get(0);
                        if(parent.getAssociations()==null) parent.loadAssociations();
                        // get sub markets for parent
                        Association subs = parent.getAssociationsMeta().stream().filter(a->a.getAssociationName().equals(association.getReverseAssociationName())).findAny().orElse(null);
                        if(subs!=null) {
                            List<Model> associationSubs = parent.getAssociations().get(subs);
                            if(associationSubs!=null) {
                                parent.buildMarketShare(null,"Company Products", revenueDomain, regionId,minYear, maxYear, useCAGR, option, associationSubs, options, association, null);
                            }
                        }
                    }
                    break;
                }
                case Market: {
                    // graph of all products of this product's market
                    options.setSubtitle(new Title().setText("Market Products"));
                    if(assocModels.size()>0) {
                        Model parent = assocModels.get(0);
                        if(parent.getAssociations()==null) parent.loadAssociations();
                        // get sub markets for parent
                        Association subs = parent.getAssociationsMeta().stream().filter(a->a.getAssociationName().equals(association.getReverseAssociationName())).findAny().orElse(null);
                        if(subs!=null) {
                            List<Model> associationSubs = parent.getAssociations().get(subs);
                            if(associationSubs!=null) {
                                parent.buildMarketShare(null,"Market Products", revenueDomain, regionId,minYear, maxYear, useCAGR, option, associationSubs, options, association, null);
                            }
                        }
                    }
                    break;
                }
            }
        }
        return allOptions;
    }

    public ContainerTag getSimpleLink(@NonNull String... additionalClasses) {
        String name = getName();
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

    public ContainerTag loadNestedAssociations(boolean nested, int maxDepth, boolean expandAll) {
        RevenueDomain revenueDomain = RevenueDomain.global;
        if(data==null) {
            loadAttributesFromDatabase();
        }
        if(nodeCache==null) {
            try {
                nodeCache = Graph.load();
            } catch(Exception e) {
                e.printStackTrace();
                throw new RuntimeException("Error loading node cache...");
            }
        }
        ContainerTag inner = ul();
        calculateRevenue(revenueDomain, null, null, null, false, Constants.MissingRevenueOption.replace, null, false);
        ContainerTag tag = ul().attr("style", "text-align: left !important;").with(
                li().with(getSimpleLink().attr("style", "display: inline;"),getRevenueAsSpan(this)).attr("style", "list-style: none;").with(
                        br(),inner
                )
        );
        Set<String> allReferences = new HashSet<>(Collections.singleton(this.getClass().getSimpleName()+id));
        loadNestedAssociationHelper(getRegionDomainName(revenueDomain, null),true, revenueDomain, null, null, null, false, Constants.MissingRevenueOption.replace, inner, new HashSet<>(allReferences), allReferences, new AtomicInteger(0), this, 0, maxDepth, expandAll);
        if(nested) return inner;
        return tag;
    };

    private static String getRegionDomainName(RevenueDomain domain, Integer regionId) {
        String name;
        switch(domain) {
            case global: {
                if(regionId!=null) throw new RuntimeException("Cannot specify a region when calculating revenues globally.");
                name = "Global";
                break;
            }
            default: {
                if(regionId==null) throw new RuntimeException("Please specify a region.");
                Model region = Graph.load().findNode(Association.Model.Region, regionId).getModel();
                region.loadAttributesFromDatabase();
                name = region.getName();
                break;
            }
        }
        return name;
    }

    public ContainerTag loadReport(RevenueDomain revenueDomain, Integer regionId, int startYear, int endYear, boolean useCAGR, Constants.MissingRevenueOption option) {
        final int maxDepth = 10;
        if(data==null) {
            loadAttributesFromDatabase();
        }
        if(nodeCache==null) {
            try {
                nodeCache = Graph.load();
            } catch(Exception e) {
                e.printStackTrace();
                throw new RuntimeException("Error loading node cache...");
            }
        }
        calculateRevenue(revenueDomain, regionId, startYear, endYear, useCAGR, option, null, false);
        ContainerTag inner = ul();
        ContainerTag tag = ul().attr("style", "text-align: left !important;").with(
                li().with(h5(getSimpleLink()).attr("style", "display: inline;"),getRevenueAsSpan(this)).attr("style", "list-style: none;").with(
                        br(),inner
                )
        );
        Set<String> allReferences = new HashSet<>(Collections.singleton(this.getClass().getSimpleName()+id));
        loadNestedAssociationHelper(getRegionDomainName(revenueDomain, regionId),false, revenueDomain, regionId, startYear, endYear, useCAGR, option, inner, new HashSet<>(allReferences), allReferences, new AtomicInteger(0), this, 0, maxDepth, false);
        return tag;
    };

    private ContainerTag getRevenueAsSpan(Model originalModel) {
        ContainerTag updateRev = span();
        String revStr = "(Revenue: "+formatRevenueString(revenue)+")";
        if(percentage!=null) {
            double percentageFull = percentage * 100;
            revStr += " - " + String.format("%.1f", percentageFull)+"%";
        }
        return span(revStr).with(updateRev).attr("data-val", revenue).withClass("resource-data-field").attr("style","margin-left: 10px;");
    }

    private static Double calculateFromCAGR(Model best, int year) {
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

    private static Double calculateFromCAGR(List<Model> list, int year) {
        // check cagr for other years
        Model best = list.stream().filter(m->m.getData().get(Constants.CAGR)!=null).min((e1,e2)->Integer.compare(Math.abs((Integer)e1.getData().get(Constants.YEAR)-year), Math.abs((Integer)e2.getData().get(Constants.YEAR)-year))).orElse(null);
        return calculateFromCAGR(best, year);
    }


    private List<Model> getSubRevenues() {
        if(!isRevenueModel) throw new RuntimeException("Unable to get subrevenues for non revenue model: "+getType());
        Association association = findAssociation("Sub Revenue");
        List<Model> subRevenues = new ArrayList<>();
        if(associations==null) loadAssociations();
        if(association!=null) {
            subRevenues.addAll(associations.getOrDefault(association, Collections.emptyList()));
        }
        return subRevenues;
    }

    private double calculateRevenueForRevenueModel(Integer startYear, Integer endYear) {
        if(!isRevenueModel) throw new RuntimeException("Unable to get subrevenues for non revenue model: "+getType());
        revenue = null;
        if(startYear==null || endYear == null) {
            revenue = ((Number)data.get(Constants.VALUE)).doubleValue();
        } else {
            int year = (Integer) data.get(Constants.YEAR);
            if (year >= startYear && year <= endYear) {
                revenue = ((Number) data.get(Constants.VALUE)).doubleValue();
            }// else {
            // if(useCAGR) { // USE CAGR HERE?

            // }
            //}
        }
        return revenue==null ? 0 : revenue;
    }

    private double calculateSubRevenueForRevenueModel(@NonNull RevenueDomain revenueDomain, Integer regionId, Integer startYear, Integer endYear) {
        revenue = null;
        Model subRevenue = getSubRevenueByRegionId(revenueDomain, regionId);
        if(subRevenue!=null) {
            subRevenue.calculateRevenueForRevenueModel(startYear, endYear);
        }
        return revenue == null ? 0 : revenue;
    }

    public static List<Model> getSubRevenuesByRegionId(@NonNull List<Model> models, @NonNull RevenueDomain revenueDomain, Integer regionId) {
        return models.stream().map(m->m.getSubRevenueByRegionId(revenueDomain, regionId)).filter(m->m!=null).collect(Collectors.toList());
    }

    private Model getSubRevenueByRegionId(@NonNull RevenueDomain revenueDomain, Integer regionId) {
        if(!isRevenueModel) throw new RuntimeException("Unable to get subrevenue for non revenue model.");
        Model model;
        switch (revenueDomain) {
            case global: {
                if(regionId!=null) throw new RuntimeException("Cannot specify a region when calculating revenues globally.");
                // proceed
                model = this;
                break;
            }
            case regional: {
                if(regionId==null) throw new RuntimeException("Please specify a region.");
                model = Stream.of(Stream.of(this),getSubRevenues().stream()).flatMap(stream->stream)
                        .filter(m->regionId.equals(m.getData().get(Constants.REGION_ID))).findAny().orElse(null);
                break;
            }
            case national: {
                if(regionId==null) throw new RuntimeException("Please specify a country.");
                model = Stream.of(Stream.of(this),getSubRevenues().stream(),getSubRevenues().stream().flatMap(m->m.getSubRevenues().stream())).flatMap(stream->stream)
                        .filter(m->regionId.equals(m.getData().get(Constants.REGION_ID))).findAny().orElse(null);
                break;
            }default: {
                model = null;
                break;
            }
        }
        return model;
    }

    public synchronized double calculateRevenue(@NonNull RevenueDomain revenueDomain, Integer regionId, Integer startYear, Integer endYear, boolean useCAGR, @NonNull Constants.MissingRevenueOption option, Double previousRevenue, boolean isParentRevenue) {
        //if(revenue!=null && parentRevenue==null) return revenue;
        revenue = null;
        this.calculationInformation = new ArrayList<>();
        if(isRevenueModel) {
            calculateSubRevenueForRevenueModel(revenueDomain, regionId, startYear, endYear);
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
                                totalRevenueOfLevel += assoc.calculateRevenue(revenueDomain, regionId, startYear, endYear, useCAGR, option, null, isParentRevenue);
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
                                list = getSubRevenuesByRegionId(list, revenueDomain, regionId); // sub revenues if necessary

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
                                                        throw new MissingRevenueException("Missing market share in " + year + " for " + getName(), year, Association.Model.valueOf(this.getClass().getSimpleName()), id, association);
                                                    }
                                                }

                                            } else {
                                                if (option.equals(Constants.MissingRevenueOption.error)) {
                                                    throw new MissingRevenueException("Missing market share in " + year + " for " + getName(), year, Association.Model.valueOf(this.getClass().getSimpleName()), id, association);
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
                                List<Model> revenueModelsSorted = getSubRevenuesByRegionId(revenues, revenueDomain, regionId).stream().peek(Model::loadAttributesFromDatabase).sorted((e1, e2) -> ((Integer) e2.getData().get(Constants.YEAR)).compareTo((Integer) e1.getData().get(Constants.YEAR))).collect(Collectors.toList());
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
                                                    throw new MissingRevenueException("Missing revenues in " + year + " for " + getName(), year, Association.Model.valueOf(this.getClass().getSimpleName()), id, association);
                                                }

                                            } else{
                                                if (option.equals(Constants.MissingRevenueOption.error) && !foundRevenueInSubMarket && !foundRevenueInMarketShares) {
                                                    throw new MissingRevenueException("Missing revenues in " + year + " for " + getName(), year, Association.Model.valueOf(this.getClass().getSimpleName()), id, association);
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
                                        throw new MissingRevenueException("Missing revenues in " + startYear+" for " + getName(), startYear, Association.Model.valueOf(this.getClass().getSimpleName()), id, association);
                                    }
                                }
                            } else {
                                if(option.equals(Constants.MissingRevenueOption.error) && !foundRevenueInSubMarket && !foundRevenueInMarketShares) {
                                    throw new MissingRevenueException("Missing revenues in " + startYear+" for " + getName(), startYear, Association.Model.valueOf(this.getClass().getSimpleName()), id, association);
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
    private void loadNestedAssociationHelper(@NonNull String regionDomainName, boolean allowEdit, RevenueDomain revenueDomain, Integer regionId, Integer startYear, Integer endYear, boolean useCAGR, Constants.MissingRevenueOption option, ContainerTag container, Set<String> alreadySeen, Set<String> references, AtomicInteger cnt, Model original, int depth, int maxDepth, boolean expandAll) {
        if(depth > maxDepth) return;
        System.out.println("Load nested... "+this.getClass().getSimpleName()+id);
        String originalId = original.getClass().getSimpleName()+original.getId();
        Map<Association,List<Model>> modelMap = new HashMap<>();
        Set<Association> linkToAssociations = new HashSet<>();
        for(Association association : associationsMeta) {
            if(!expandAll && association.shouldNotExpand(isRevenueModel())) {
                continue;
            }
            if(depth == maxDepth) {
                linkToAssociations.add(association);
            }

            if(associations==null) {
                loadAssociations();
            }
            List<Model> assocModels = associations.getOrDefault(association, Collections.emptyList());
            if(startYear!=null && endYear!=null) {
                assocModels = assocModels.stream().filter(m -> {
                    if (!m.getData().containsKey(Constants.YEAR)) return true;
                    return ((Integer) m.getData().get(Constants.YEAR)) >= startYear && ((Integer) m.getData().get(Constants.YEAR)) <= endYear;
                }).collect(Collectors.toList());
            }
            modelMap.put(association, assocModels);
        }
        calculateRevenue(revenueDomain, regionId, startYear, endYear, useCAGR, option, null, true);
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
                if(models==null) continue; // IMPORTANT
                boolean pluralize = Arrays.asList(Association.Type.OneToMany, Association.Type.ManyToMany).contains(association.getType());
                List<ContainerTag> tag = new ArrayList<>();
                ContainerTag ul = ul();
                String name;
                if (association.getModel().toString().contains("Revenue") && !association.getModel().toString().equals(MarketShareRevenue.class.getSimpleName())) {
                    if(association.getAssociationName().startsWith("Sub")) {
                        name = pluralize ? "Sub Revenues" : "Sub Revenue";
                    } else {
                        name = regionDomainName + (pluralize ? " Revenues" : " Revenue");
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
                String groupRevenuesBy;
                if(revenueAssociation) {
                    if (this instanceof Market) {
                        groupRevenuesBy = Constants.COMPANY_ID;
                    } else if (this instanceof Company) {
                        groupRevenuesBy = Constants.MARKET_ID;
                    } else {
                        groupRevenuesBy = null;
                    }
                } else if(isRevenueModel) {
                    groupRevenuesBy = Constants.REGION_ID;
                } else {
                    groupRevenuesBy = null;
                }
                if(revenueAssociation && groupRevenuesBy!=null) {
                    // group models by year
                    groupedModels = models.stream().collect(Collectors.groupingBy(e->(Integer)e.getData().get(groupRevenuesBy)));
                } else {
                    groupedModels = Collections.singletonMap(null, models);
                }
                List<Integer> groupKeys = new ArrayList<>(groupedModels.keySet());
                Double totalRevAllGroups = null;
                Map<Integer, Double> groupToRevenueMap = new HashMap<>();
                if(groupKeys.size()>0 && groupKeys.get(0)!=null) {
                    groupedModels.forEach((group, list) -> {
                        double rev = groupedModels.get(group).stream().mapToDouble(d -> d.calculateRevenue(revenueDomain, regionId, startYear, endYear, useCAGR, option, null, false)).sum();
                        groupToRevenueMap.put(group, rev);
                    });
                    totalRevAllGroups = groupToRevenueMap.values().stream().mapToDouble(d -> d).sum();
                    if (groupRevenuesBy.equals(Constants.YEAR)) {
                        groupKeys.sort((e1, e2) -> Integer.compare(e2, e1));
                    } else {
                        groupKeys.sort((e1, e2) -> Double.compare(groupToRevenueMap.get(e2), groupToRevenueMap.get(e1)));
                    }
                    if (totalRevAllGroups == 0) totalRevAllGroups = null;
                }
                String listRef = "association-"+getType().toString().toLowerCase() + "-" + id + "-" + association.getAssociationName().toLowerCase().replace(" ", "-");
                ul = ul.withClass(listRef);
                for (Integer key : groupKeys) {
                    ContainerTag groupUl;
                    Double groupRevenue = key == null ? null : groupToRevenueMap.get(key);
                    if(groupRevenue!=null) {
                        String groupName;
                        if(groupRevenuesBy.endsWith(("_id"))) {
                            Association.Model resource = Association.Model.valueOf(capitalize(groupRevenuesBy.substring(0, groupRevenuesBy.length()-3)));
                            Node node = nodeCache.findNode(resource, key);
                            if(node!=null) {
                                Model assoc = node.getModel();
                                assoc.loadAttributesFromDatabase();
                                groupName = assoc.getName();
                            } else {
                                throw new RuntimeException("Unable to find group name...");
                            }
                        } else {
                            groupName = key.toString();
                        }
                        String percentStr = totalRevAllGroups == null ? "" : (String.format("%.1f", (groupRevenue * 100d) / totalRevAllGroups) + "%");
                        groupUl = ul().attr("data-val", groupRevenue.toString()).withClass("resource-data-field");
                        ul.with(li().with(div( String.valueOf(groupName) + " - "+regionDomainName  + " (Revenue: " + formatRevenueString(groupRevenue) + ") - "+percentStr)
                                .attr("style", "cursor: pointer;").attr("onclick", "$(this).next().slideToggle();")
                        ).attr("style", "display: inline; list-style: none;").with(groupUl));
                    } else {
                        groupUl = ul;
                    }
                    List<Model> groupedModelList = groupedModels.get(key);
                    if(groupedModelList==null) groupedModelList = Collections.emptyList();
                    if(association.getModel().toString().contains("Revenue")) {
                        groupedModelList = new ArrayList<>(groupedModelList);
                        groupedModelList.forEach(Model::loadAttributesFromDatabase);
                        groupedModelList.sort((d1,d2)->Integer.compare((Integer)d2.getData().get(Constants.YEAR), (Integer)d1.getData().get(Constants.YEAR)));
                    }
                    for(Model model : groupedModelList) {
                        if(model.isRevenueModel() && model.getData().get(Constants.REGION_ID)==null) { // global revenue model
                            model = model.getSubRevenueByRegionId(revenueDomain, regionId);
                        }
                        if(model==null) continue;
                        if (model.isRevenueModel && startYear != null && endYear != null) {
                            int year = (Integer) model.getData().get(Constants.YEAR);
                            if (year < startYear || year > endYear) {
                                continue;
                            }
                        }
                        String _id = model.getClass().getSimpleName() + model.getId();
                        boolean sameModel = _id.equals(originalId);
                        ContainerTag inner = ul();
                        Double revToUse = null;
                        if (!this.getClass().getSimpleName().equals(MarketShareRevenue.class.getSimpleName())) {
                            revToUse = revenue;
                        }
                        if(groupRevenue!=null) {
                            revToUse = groupRevenue;
                        }
                        boolean isParentRevenue;
                        // need to decide whether to show percentage of parent or child
                        String thisClass = this.getClass().getSimpleName();
                        if ((!model.isRevenueModel && thisClass.equals(Product.class.getSimpleName())) || association.getAssociationName().startsWith("Parent")) {
                            isParentRevenue = false;
                        } else {
                            isParentRevenue = true;
                        }
                        model.calculateRevenue(revenueDomain, regionId, startYear, endYear, useCAGR, option, revToUse, isParentRevenue);

                        groupUl.with(li().attr("style", "list-style: none;").with(
                                allowEdit ? model.getLink(association.getReverseAssociationName(), this.getClass().getSimpleName(), id).attr("style", "display: inline;")
                                        : model.getSimpleLink().attr("style", "display: inline;")
                                , model.getRevenueAsSpan(original), inner));
                        if (!sameModel && !alreadySeen.contains(_id) && !model.getType().equals(Association.Model.Region)) {
                            alreadySeen.add(_id);
                            if (linkToAssociations.contains(association)) {
                                // just show link
                                inner.attr("style", "display: inline;").with(
                                        a("(Expand)").attr("href", "#").withClass("diagram-button nested").attr("data-id", model.getId())
                                        .attr("data-resource", model.getType().toString())
                                );
                            } else {
                                model.loadNestedAssociationHelper(regionDomainName, allowEdit, revenueDomain, regionId, startYear, endYear, useCAGR, option, inner, new HashSet<>(alreadySeen), references, cnt, original, depth + 1, maxDepth, false);
                            }
                        }
                        references.add(_id);
                        alreadySeen.add(_id);
                    }
                }
                ul.with(li().attr("style", "list-style: none;").with(
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
        if(nodeCache==null) {
            throw new RuntimeException("Unable to remove associations without node cache.");
        }
        for(Association association : associationsMeta) {
            if(association.getAssociationName().equals(associationName)) {
                switch (association.getType()) {
                    case ManyToOne: {
                        // need to set parent id of current model
                        updateAttribute(association.getParentIdField(), null);
                        updateInDatabase();
                        nodeCache.unlinkNodeFromAssociation(getType(), id, association);
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

    // UPDATE NODE CACHE
    public void associateWith(@NonNull Model otherModel,@NonNull String associationName, @NonNull Map<String,Object> joinData) {
        // find association
        for(Association association : associationsMeta) {
            if(association.getAssociationName().equals(associationName)) {
                // make sure we haven't introduced in cycles
                if(association.getModel().toString().equals(this.getClass().getSimpleName())) {
                    System.out.println("Checking for cycles...");
                    if(nodeCache.areAlreadyConnected(getType(), id, otherModel.getType(), otherModel.getId())) {
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
                        throw new RuntimeException("Many to many not yet implemented.");
                        /*
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
                        break; */
                    }
                    case OneToOne: {
                        // NOT IMPLEMENTED
                        break;
                    }
                }
                nodeCache.linkNodeWithAssociation(this, otherModel, association);
                break;
            }
        }
    }

    public static String capitalize(String in) {
        return in.substring(0, 1).toUpperCase() + in.substring(1);
    }

    public ContainerTag getAddAssociationPanel(@NonNull Association association, String listRef, Model diagramModel) {
        return getAddAssociationPanel(association, listRef, diagramModel, null, false);
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
        boolean revenueAssociation = association.getModel().toString().contains("Revenue");
        Collection<Tag> inputs = new ArrayList<>(Arrays.asList(input().withType("hidden").withName("_association_name").withValue(association.getAssociationName()),
                label(association.getAssociationName()+" Name:").with(
                        select().attr("style","width: 100%").withClass("form-control multiselect-ajax").withName("id")
                                .attr("data-url", "/ajax/resources/"+association.getModel()+"/"+this.getClass().getSimpleName()+"/"+id)
                ), br()));
        ContainerTag panel = div().with(isGlobalRegion? p("Global") : a(createText).withHref("#").withClass("resource-new-link"),div().attr("style", "display: none;").with(
                (isRegion ? span() :
                        getCreateNewForm(association.getModel(),id).attr("data-prepend",prepend).attr("data-list-ref",listRef==null ? null : ("."+listRef)).attr("data-association", association.getModel().toString())
                                .attr("data-resource", this.getClass().getSimpleName())
                                .attr("data-refresh",diagramModel!=null ? "refresh" : "f")
                                .attr("data-report", report ? "true" : null)
                                .attr("data-original-id",diagramModel!=null ? diagramModel.id.toString() : "f")
                                .attr("data-original-resource",diagramModel!=null ? diagramModel.getClass().getSimpleName() : "f")
                                .attr("data-id", id.toString()).withClass("association").with(
                                input().withType("hidden").withName("_association_name").withValue(association.getAssociationName())
                        )
                ),(isGlobalRegion || revenueAssociation ? span() : form().attr("data-association-name-reverse", association.getReverseAssociationName()).attr("data-prepend",prepend).attr("data-list-ref",listRef==null ? null : ("."+listRef)).attr("data-id", id.toString()).withClass("update-association").attr("data-association", association.getModel().toString())
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

    public void loadShowTemplate(ContainerTag back) {
        ContainerTag backButton;
        if(back!=null) {
            backButton = back;
        } else {
            backButton = span();
        }
        boolean isRegion = isRegion();
        ContainerTag html = div().withClass("col-12").with(
                div().withClass("col-12").with(
                        backButton,
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
                                button("Report")
                                        .attr("data-id", id.toString())
                                        .attr("data-resource", this.getClass().getSimpleName())
                                        .withClass("btn btn-outline-secondary btn-md report-button"),
                                button("Graph")
                                        .attr("data-id", id.toString())
                                        .attr("data-resource", this.getClass().getSimpleName())
                                        .withClass("btn btn-outline-secondary btn-md graph-button"),
                                button("Compare")
                                        .attr("data-id", id.toString())
                                        .attr("data-resource", this.getClass().getSimpleName())
                                        .withClass("btn btn-outline-secondary btn-md comparison-button")

                                ) : div()
                ),
                div().withClass("col-12").with(
                        h5("Associations"),
                       loadNestedAssociations(false, 0, true)
                )
        );
        template = html.render();
    }

    public synchronized void updateAttribute(String attr, Object val) {
        this.data.put(attr, val);
    }

    public synchronized void removeAttribute(String attr) {
        this.data.remove(attr);
    }

    public synchronized void loadAssociations() {
        if (!existsInDatabase()) {
            throw new RuntimeException("Cannot load associations if the model does not yet exist in the database.");
        }
        if(associations!=null) {
            return;
        }
        if (data == null || isMissingAttributes()) {
            loadAttributesFromDatabase();
        }
        this.associations = new HashMap<>();
        if(nodeCache!=null) {
            Node node = nodeCache.findNode(Association.Model.valueOf(getClass().getSimpleName()), id);
            for (Association association : associationsMeta) {
                List<Edge> edges = node.getEdgeMap().get(association.getAssociationName());
                if(edges!=null && edges.size()>0) {
                    List<Model> assocs = edges.stream().map(edge->edge.getTarget().getModel())
                            .collect(Collectors.toList());
                    for(Model assoc : assocs) {
                        assoc.loadAttributesFromDatabase();
                    }
                    if (association.getType().equals(Association.Type.OneToMany)) {
                        associations.put(association, assocs);
                    } else if (association.getType().equals(Association.Type.ManyToOne)) {
                        associations.put(association, assocs);
                    } else if (association.getType().equals(Association.Type.OneToOne)) {
                        throw new RuntimeException("One to one associations are not yet implemented.");
                    } else if (association.getType().equals(Association.Type.ManyToMany)) {
                        associations.put(association, assocs);
                    }
                }
            }
        } else {
            throw new RuntimeException("Cannot load associations without node cache!");
            /*
            for (Association association : associationsMeta) {
                try {
                    if (association.getType().equals(Association.Type.OneToMany)) {
                        List<Model> children = Database.loadOneToManyAssociation(association.getModel(), this, association.getChildTableName(), association.getParentIdField());
                        associations.put(association, children);
                    } else if (association.getType().equals(Association.Type.ManyToOne)) {
                        Model parent = Database.loadManyToOneAssociation(association.getModel(), this, association.getChildTableName(), association.getParentIdField());
                        if (parent != null) {
                            associations.put(association, Collections.singletonList(parent));
                        }
                    } else if (association.getType().equals(Association.Type.OneToOne)) {
                        throw new RuntimeException("One to one associations are not yet implemented.");
                    } else if (association.getType().equals(Association.Type.ManyToMany)) {
                        Map<Model, Map<String, Object>> children = Database.loadManyToManyAssociation(association.getModel(), this, association.getJoinTableName(), association.getParentIdField(), association.getChildIdField(), association.getJoinAttributes());
                        List<Model> childrenList = new ArrayList<>(children.keySet());
                        associations.put(association, childrenList);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }*/
        }
    }

    public boolean isMissingAttributes () {
        if(data==null) return true;
        Set<String> missing = new HashSet<>(getAvailableAttributes());
        missing.removeAll(getData().keySet());
        return missing.size()>0;
    }


    public void loadAttributesFromDatabase(boolean force) {
        if(!existsInDatabase()) {
            throw new RuntimeException("Trying to select a record that does not exist in the database...");
        }
        if(!isMissingAttributes() && !force) return;
        try {
            System.out.println("Select "+getType()+" - "+id);
            this.data = Database.select(tableName, id, availableAttributes);
        } catch(Exception e) {
            e.printStackTrace();
            throw new RuntimeException("Error loading attributes from database: " + e.getMessage());
        }
    }

    public void loadAttributesFromDatabase() {
        loadAttributesFromDatabase(false);
    }

    public void updateInDatabase() {
        if(nodeCache==null) throw new RuntimeException("Cannot update database without node cache...");
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
            if(getName()==null) {
                throw new RuntimeException("Name must be present");
            }
        }

    }


    // save to database for the first time
    public void createInDatabase() {
        if(existsInDatabase()) {
            throw new RuntimeException("Trying to create a record that already exists in the database...");
        }
        if(nodeCache==null) {
            nodeCache = Graph.load();
        }
        validateState();
        try {
            id = Database.insert(tableName, data);
            nodeCache.addNode(getType(), id, new Node(this));
        } catch(Exception e) {
            e.printStackTrace();
            throw new RuntimeException("Error inserting record into database: "+e.getMessage());
        }
    }

    // delete record from the database
    public synchronized void deleteFromDatabase(boolean cascade) {
        if(nodeCache==null) {
            nodeCache = Graph.load();
        }
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
            nodeCache.deleteNode(getType(), id);
        } catch(Exception e) {
            e.printStackTrace();
            throw new RuntimeException("Error deleting record from database: "+e.getMessage());
        }
    }

    public Association.Model getType() {
        return Association.Model.valueOf(this.getClass().getSimpleName());
    }

    public synchronized void purgeMemory() {
        if(data!=null) {
            for(String attr : getAvailableAttributes()) {
                if(!attr.endsWith("_id") && Arrays.asList(Constants.YEAR, Constants.VALUE, Constants.CAGR).contains(attr)) {
                    data.remove(attr);
                }
            }
        }
        if(associations!=null) {
            associations.clear();
            associations = null;
        }
    }

    public void cleanUpParentIds(@NonNull Association association, int assocId) {
        // clean up join table if necessary
        if(nodeCache==null) {
            nodeCache = Graph.load();
        }
        if (association.getType().equals(Association.Type.ManyToMany)) {
            try {
                // TODO update Graph is there is every any many to many relationships
                Database.deleteByFieldName(association.getJoinTableName(), association.getParentIdField(), id);
            } catch (Exception e) {
                e.printStackTrace();
                throw new RuntimeException("Error deleting record from database: " + e.getMessage());
            }
        } else if(Arrays.asList(Association.Type.OneToMany, Association.Type.ManyToOne).contains(association.getType())) {
            // child table has the key
            try {
                Integer idToUse;
                Association.Model typeToUse;
                if(association.getType().equals(Association.Type.ManyToOne)) {
                    idToUse = id;
                    typeToUse = getType();
                } else {
                    idToUse = assocId;
                    typeToUse = association.getModel();
                }
                if(isRevenueModel && association.getModel().toString().contains("Revenue")) {
                    // revenue to revenue model - need to delete dependent stuff
                    Database.delete(association.getChildTableName(), idToUse);
                    nodeCache.deleteNode(typeToUse, idToUse);
                } else {
                    Database.nullifyFieldName(association.getChildTableName(), association.getParentIdField(), idToUse);
                    Node node = nodeCache.findNode(typeToUse, idToUse);
                    if(node!=null) {
                        node.getModel().getData().put(association.getParentIdField(), null);
                        nodeCache.unlinkNodeFromAssociation(node, association);
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
                throw new RuntimeException("Error deleting record from database: " + e.getMessage());
            }
        }
    }
}
