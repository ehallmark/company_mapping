package models;

import com.googlecode.wickedcharts.highcharts.options.series.Point;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;

public class CalculationPoint extends Point implements Serializable {
    private static final long serialVersionUID = 15235L;
    @Getter @Setter
    protected String info;
    public CalculationPoint(String name, String calculationStr, Number y) {
        super(name, y);
        this.info=calculationStr;
    }
}
