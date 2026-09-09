package com.beertracker.model;

public class BeerEntity {
    private String name;
    private String style;
    private double abv;

    public BeerEntity() {
    }

    public BeerEntity(String name, String style, double abv) {
        this.name = name;
        this.style = style;
        this.abv = abv;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getStyle() {
        return style;
    }

    public void setStyle(String style) {
        this.style = style;
    }

    public double getAbv() {
        return abv;
    }

    public void setAbv(double abv) {
        this.abv = abv;
    }
}
