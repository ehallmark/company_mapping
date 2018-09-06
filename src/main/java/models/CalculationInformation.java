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
    @Getter
    private Model reference;
    public CalculationInformation(Integer year, Double cagrUsed, boolean calculatedFromSubmarkets, boolean calculatedFromMarketShares, Double revenue, Model reference) {
        this.year=year;
        this.reference=reference;
        this.revenue=revenue;
        this.cagrUsed=cagrUsed;
        this.calculatedFromMarketShares=calculatedFromMarketShares;
        this.calculatedFromSubmarkets=calculatedFromSubmarkets;
    }
}
