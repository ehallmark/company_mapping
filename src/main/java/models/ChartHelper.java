package models;

import com.googlecode.wickedcharts.highcharts.options.color.ColorReference;
import com.googlecode.wickedcharts.highcharts.options.color.RadialGradient;
import com.googlecode.wickedcharts.highcharts.options.color.RgbaColor;
import controllers.Main;
import j2html.tags.ContainerTag;
import spark.Request;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static j2html.TagCreator.*;

public class ChartHelper {
    public static final List<int[]> RGB_COLORS = Arrays.asList(
            new int[]{124,181,236},
            new int[]{67,67,72},
            new int[]{144,237,125},
            new int[]{247,163,92},
            new int[]{128,133,233},
            new int[]{241,92,128},
            new int[]{228,211,84},
            new int[]{43,144,143},
            new int[]{244,91,91},
            new int[]{145,232,225}
    );

    public static ColorReference radialColorReference(int[] color) {
        int[] darkened = brighten(color[0], color[1], color[2], -30);
        return new RadialGradient().setCx(0.5).setCy(0.5).setR(0.5)
                .addStop(0.3, new RgbaColor(darkened[0], darkened[1], darkened[2], 1f))
                .addStop(0.7, new RgbaColor(color[0], color[1], color[2], 1f))
                .addStop(1.0, new RgbaColor(darkened[0], darkened[1], darkened[2], 1f));
    }

    public static int[] brighten(int r, int g, int b, int brightenPercent) {
        if (brightenPercent >= 0) {
            r += (brightenPercent * (255 - r)) / 100;
            g += (brightenPercent * (255 - g)) / 100;
            b += (brightenPercent * (255 - b)) / 100;
        } else {
            r += (brightenPercent * r) / 100;
            g += (brightenPercent * g) / 100;
            b += (brightenPercent * b) / 100;
        }
        return new int[]{r,g,b};
    }

    public static int[] getColor(int i, int brightenPercent) {
        int[] rgb = RGB_COLORS.get(i%RGB_COLORS.size());
        return brighten(rgb[0], rgb[1], rgb[2], brightenPercent);
    }

    public static final String TIME_SERIES_CHART_TYPE = "time_series_chart_type";
    public static final String MAX_NUM_GROUPS = "max_num_groups";
    public enum LineChartType {
        column,
        line
    }


    public static ContainerTag getChartOptionsForm(Request req) {
        Map<String,String[]> defaultValues = req.session().attribute(Main.DEFAULT_FORM_OPTIONS);
        if(defaultValues==null) defaultValues = new HashMap<>();
        return div().with(
                label("Time Series Chart Type").with(br(),
                        select().withClass("multiselect form-control").attr("style", "width: 200px").withName(TIME_SERIES_CHART_TYPE).with(
                                option("Column").attr("column".equals(defaultValues.getOrDefault(TIME_SERIES_CHART_TYPE, new String[]{null})[0])?"selected": "").withValue(LineChartType.column.toString()),
                                option("Line").attr("line".equals(defaultValues.getOrDefault(TIME_SERIES_CHART_TYPE, new String[]{null})[0])?"selected": "").withValue(LineChartType.line.toString())
                        )
                ),br(),
                label("Max Chart Groups").with(br(),
                        input().withClass("form-control").withType("number").withValue(defaultValues.getOrDefault(MAX_NUM_GROUPS, new String[]{"15"})[0]).withName(MAX_NUM_GROUPS)
                )
        );
    }
}
