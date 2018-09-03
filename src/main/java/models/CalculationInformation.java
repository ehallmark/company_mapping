package models;

import lombok.Getter;
import lombok.Setter;

public class CalculationInformation {

    @Getter
    private Double cagrUsed;
    @Getter
    private Integer year;
    @Getter
    private boolean calculatedFromSubmarkets;
    @Getter
    private boolean calculatedFromMarketShares;
    public CalculationInformation(Integer year, Double cagrUsed, boolean calculatedFromSubmarkets, boolean calculatedFromMarketShares) {
        this.year=year;
        this.cagrUsed=cagrUsed;
        this.calculatedFromMarketShares=calculatedFromMarketShares;
        this.calculatedFromSubmarkets=calculatedFromSubmarkets;
    }
}
