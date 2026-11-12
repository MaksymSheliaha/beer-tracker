package com.beertracker.controller;

import com.beertracker.dto.BeerCreateRequest;
import com.beertracker.dto.BeerPagedResponse;
import com.beertracker.dto.BeerResponse;
import com.beertracker.service.BeerService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/beers")
public class BeerController {

    private final BeerService beerService;

    public BeerController(BeerService beerService) {
        this.beerService = beerService;
    }

    @PostMapping
    public ResponseEntity<BeerResponse> createBeer(@RequestBody BeerCreateRequest request) {
        BeerResponse created = beerService.createBeer(request);
        URI location = URI.create("/api/v1/beers/" + created.id());
        return ResponseEntity.created(location).body(created);
    }

    @GetMapping("/{id}")
    public ResponseEntity<BeerResponse> getBeerById(
            @PathVariable("id") UUID id,
            @RequestParam(name = "verified_only", defaultValue = "false") boolean verifiedOnly
    ) {
        return ResponseEntity.ok(beerService.getBeerById(id, verifiedOnly));
    }

    @GetMapping
    public ResponseEntity<BeerPagedResponse> listBeers(
            @RequestParam(name = "query", required = false) String query,
            @RequestParam(name = "min_abv", required = false) Double minAbv,
            @RequestParam(name = "max_abv", required = false) Double maxAbv,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(beerService.listBeers(query, minAbv, maxAbv, page, size));
    }
}
