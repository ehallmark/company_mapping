package models;

import lombok.Getter;

public class MissingRevenueException extends RuntimeException {
    @Getter
    private int year;
    @Getter
    private Association.Model model;
    @Getter
    private int id;
    @Getter
    private Association association;
    public MissingRevenueException(String message, int year, Association.Model model, int id, Association association) {
        super(message);
        this.year=year;
        this.association=association;
        this.id=id;
        this.model=model;
    }
}
