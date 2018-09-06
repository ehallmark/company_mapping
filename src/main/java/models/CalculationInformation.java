package models;

import lombok.Getter;

public class CalculationInformation {

    @Getter
    private Double cagrUsed;
    @Getter
    private Integer year;
    @Getter
    private boolean calculatedFromSubmarkets;
    @Getter
    private boolean calculatedFromMarketShares;
    @Getter
    private Double revenue;
    public CalculationInformation(Integer year, Double cagrUsed, boolean calculatedFromSubmarkets, boolean calculatedFromMarketShares, Double revenue) {
        this.year=year;
        this.revenue=revenue;
        this.cagrUsed=cagrUsed;
        this.calculatedFromMarketShares=calculatedFromMarketShares;
        this.calculatedFromSubmarkets=calculatedFromSubmarkets;
    }
}
