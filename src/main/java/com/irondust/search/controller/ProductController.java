package com.irondust.search.controller;

import com.irondust.search.service.MeiliService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.Map;

@RestController
public class ProductController {
    private final MeiliService meiliService;

    public ProductController(MeiliService meiliService) { this.meiliService = meiliService; }

    @GetMapping("/products/{id}")
    public Mono<ResponseEntity<Map<String, Object>>> getById(@PathVariable("id") String id) {
        return meiliService.getDocumentRaw(id)
                .map(doc -> ResponseEntity.ok(doc))
                .onErrorResume(e -> Mono.just(ResponseEntity.notFound().build()));
    }
}


