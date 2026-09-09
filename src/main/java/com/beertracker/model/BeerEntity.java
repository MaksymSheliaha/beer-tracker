package com.beertracker.model;

public class BeerEntity {
    private String name;
    private Integer volumeMl;
    private Double abv; // Alcohol by volume

    public BeerEntity(String name, Integer volumeMl, Double abv) {
        this.name = name;
        this.volumeMl = volumeMl;
        this.abv = abv;
    }

    public String getName() {
        return name;
    }

    public Integer getVolumeMl() {
        return volumeMl;
    }

    public Double getAbv() {
        return abv;
    }
}