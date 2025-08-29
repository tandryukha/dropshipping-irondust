package com.irondust.search.controller;

import com.irondust.search.service.MeiliService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

@RestController
public class ProductController {
    private final MeiliService meiliService;

    public ProductController(MeiliService meiliService) { this.meiliService = meiliService; }

    @GetMapping("/products/{id}")
    public Mono<ResponseEntity<Map<String, Object>>> getById(
            @PathVariable("id") String id,
            @RequestParam(value = "lang", required = false) String lang) {
        return meiliService.getDocumentRaw(id)
                .map(doc -> {
                    // Apply language-specific fields if requested
                    if (lang != null && !lang.isEmpty()) {
                        applyLanguageFieldsToRaw(doc, lang);
                    }
                    return ResponseEntity.ok(doc);
                })
                .onErrorResume(e -> Mono.just(ResponseEntity.notFound().build()));
    }
    
    private void applyLanguageFieldsToRaw(Map<String, Object> doc, String lang) {
        // Apply language-specific name
        Map<String, String> nameI18n = (Map<String, String>) doc.get("name_i18n");
        if (nameI18n != null && nameI18n.containsKey(lang)) {
            doc.put("name", nameI18n.get(lang));
        }
        
        // Apply language-specific categories
        Map<String, List<String>> categoriesI18n = (Map<String, List<String>>) doc.get("categories_names_i18n");
        if (categoriesI18n != null && categoriesI18n.containsKey(lang)) {
            doc.put("categories_names", categoriesI18n.get(lang));
        }
        
        // Apply language-specific form
        Map<String, String> formI18n = (Map<String, String>) doc.get("form_i18n");
        if (formI18n != null && formI18n.containsKey(lang)) {
            doc.put("form", formI18n.get(lang));
        }
        
        // Apply language-specific flavor
        Map<String, String> flavorI18n = (Map<String, String>) doc.get("flavor_i18n");
        if (flavorI18n != null && flavorI18n.containsKey(lang)) {
            doc.put("flavor", flavorI18n.get(lang));
        }
        
        // Apply language-specific benefit snippet
        Map<String, String> benefitSnippetI18n = (Map<String, String>) doc.get("benefit_snippet_i18n");
        if (benefitSnippetI18n != null && benefitSnippetI18n.containsKey(lang)) {
            doc.put("benefit_snippet", benefitSnippetI18n.get(lang));
        }
        
        // Apply language-specific FAQ
        Map<String, List<Map<String, String>>> faqI18n = (Map<String, List<Map<String, String>>>) doc.get("faq_i18n");
        if (faqI18n != null && faqI18n.containsKey(lang)) {
            doc.put("faq", faqI18n.get(lang));
        }

        // Apply language-specific description if present
        Map<String, String> descriptionI18n = (Map<String, String>) doc.get("description_i18n");
        if (descriptionI18n != null && descriptionI18n.containsKey(lang)) {
            doc.put("description", descriptionI18n.get(lang));
        }

        // Apply language-specific search text if present
        Map<String, String> searchTextI18n = (Map<String, String>) doc.get("search_text_i18n");
        if (searchTextI18n != null && searchTextI18n.containsKey(lang)) {
            doc.put("search_text", searchTextI18n.get(lang));
        }
    }
}


