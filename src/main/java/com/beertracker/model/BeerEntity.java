package com.beertracker.model;

public class BeerEntity {
    private String name;
    private String style;
    private Integer volumeMl;
    private Double abv; // Alcohol by volume

    public BeerEntity(String name, String style, Integer volumeMl, double abv) {
        this.name = name;
        this.volumeMl = volumeMl;
        this.style = style;
        this.abv = abv;
    }

    public String getName() {
        return name;
    }

    public Integer getVolumeMl() {
        return volumeMl;
    }

    public String getStyle() {
        return style;
    }

    public Double getAbv() {
        return abv;
    }
}