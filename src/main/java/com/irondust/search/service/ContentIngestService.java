package com.irondust.search.service;

import com.irondust.search.model.ContentDoc;
import com.irondust.search.service.content.FdaRecallFetcher;
import com.irondust.search.service.content.WikipediaFetcher;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;

@Service
public class ContentIngestService {
    private final ContentIndexService contentIndexService;
    private final WikipediaFetcher wikipediaFetcher;
    private final FdaRecallFetcher fdaRecallFetcher;

    public ContentIngestService(ContentIndexService contentIndexService,
                                WikipediaFetcher wikipediaFetcher,
                                FdaRecallFetcher fdaRecallFetcher) {
        this.contentIndexService = contentIndexService;
        this.wikipediaFetcher = wikipediaFetcher;
        this.fdaRecallFetcher = fdaRecallFetcher;
    }

    public Mono<Integer> ingestMinimalSeed() {
        return contentIndexService.ensureIndex()
                .thenMany(Flux.merge(
                        // Seed a few core supplement topics from Wikipedia
                        wikipediaFetcher.fetchSummary("Creatine", "en"),
                        wikipediaFetcher.fetchSummary("Whey protein", "en"),
                        wikipediaFetcher.fetchSummary("Electrolyte", "en"),
                        // Recent FDA recalls for supplements
                        fdaRecallFetcher.fetchRecent(10)
                ))
                .collectList()
                .flatMap(list -> {
                    List<ContentDoc> docs = new ArrayList<>(list);
                    return contentIndexService.upsert(docs).thenReturn(docs.size());
                });
    }
}


