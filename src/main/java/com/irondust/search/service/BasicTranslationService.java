package com.irondust.search.service;

import org.springframework.stereotype.Service;
import java.util.*;

/**
 * Basic fallback translation service for common e-commerce terms.
 * Used when OpenAI is not available or to reduce costs for common translations.
 */
@Service
public class BasicTranslationService {
    
    private static final Map<String, Map<String, String>> COMMON_TERMS = new HashMap<>();
    private static final Map<String, Map<String, String>> FORM_TRANSLATIONS = new HashMap<>();
    private static final Map<String, Map<String, String>> CATEGORY_TRANSLATIONS = new HashMap<>();
    
    static {
        // Common product terms
        addTerm("protein", "en", "protein", "ru", "протеин", "est", "valk");
        addTerm("whey", "en", "whey", "ru", "сывороточный", "est", "vadak");
        addTerm("creatine", "en", "creatine", "ru", "креатин", "est", "kreatiin");
        addTerm("bcaa", "en", "BCAA", "ru", "БЦАА", "est", "BCAA");
        addTerm("vitamin", "en", "vitamin", "ru", "витамин", "est", "vitamiin");
        addTerm("omega", "en", "omega", "ru", "омега", "est", "oomega");
        addTerm("pre-workout", "en", "pre-workout", "ru", "предтреник", "est", "enne trenni");
        addTerm("post-workout", "en", "post-workout", "ru", "после тренировки", "est", "pärast trenni");
        
        // Product forms
        addForm("powder", "en", "powder", "ru", "порошок", "est", "pulber");
        addForm("capsules", "en", "capsules", "ru", "капсулы", "est", "kapslid");
        addForm("tabs", "en", "tablets", "ru", "таблетки", "est", "tabletid");
        addForm("liquid", "en", "liquid", "ru", "жидкость", "est", "vedelik");
        addForm("bar", "en", "bar", "ru", "батончик", "est", "batoon");
        addForm("gel", "en", "gel", "ru", "гель", "est", "geel");
        
        // Categories
        addCategory("sports nutrition", "en", "Sports Nutrition", "ru", "Спортивное питание", "est", "Sporditoitained");
        addCategory("vitamins", "en", "Vitamins", "ru", "Витамины", "est", "Vitamiinid");
        addCategory("supplements", "en", "Supplements", "ru", "Добавки", "est", "Toidulisandid");
        addCategory("weight loss", "en", "Weight Loss", "ru", "Похудение", "est", "Kaalulangus");
        addCategory("muscle building", "en", "Muscle Building", "ru", "Набор массы", "est", "Lihasmassi kasv");
    }
    
    private static void addTerm(String key, String... langValuePairs) {
        Map<String, String> translations = new HashMap<>();
        for (int i = 0; i < langValuePairs.length; i += 2) {
            translations.put(langValuePairs[i], langValuePairs[i + 1]);
        }
        COMMON_TERMS.put(key.toLowerCase(), translations);
    }
    
    private static void addForm(String key, String... langValuePairs) {
        Map<String, String> translations = new HashMap<>();
        for (int i = 0; i < langValuePairs.length; i += 2) {
            translations.put(langValuePairs[i], langValuePairs[i + 1]);
        }
        FORM_TRANSLATIONS.put(key.toLowerCase(), translations);
    }
    
    private static void addCategory(String key, String... langValuePairs) {
        Map<String, String> translations = new HashMap<>();
        for (int i = 0; i < langValuePairs.length; i += 2) {
            translations.put(langValuePairs[i], langValuePairs[i + 1]);
        }
        CATEGORY_TRANSLATIONS.put(key.toLowerCase(), translations);
    }
    
    /**
     * Attempts basic translation using dictionary lookup.
     * Returns null if translation not found.
     */
    public String translateTerm(String term, String fromLang, String toLang) {
        if (term == null || fromLang.equals(toLang)) {
            return term;
        }
        
        String normalized = term.toLowerCase().trim();
        
        // Check common terms
        Map<String, String> termTranslations = COMMON_TERMS.get(normalized);
        if (termTranslations != null && termTranslations.containsKey(toLang)) {
            return termTranslations.get(toLang);
        }
        
        // Check if it contains common terms
        for (Map.Entry<String, Map<String, String>> entry : COMMON_TERMS.entrySet()) {
            if (normalized.contains(entry.getKey())) {
                Map<String, String> trans = entry.getValue();
                if (trans.containsKey(fromLang) && trans.containsKey(toLang)) {
                    String from = trans.get(fromLang);
                    String to = trans.get(toLang);
                    return term.replaceAll("(?i)" + from, to);
                }
            }
        }
        
        return null;
    }
    
    public String translateForm(String form, String toLang) {
        if (form == null) return null;
        
        Map<String, String> translations = FORM_TRANSLATIONS.get(form.toLowerCase());
        if (translations != null && translations.containsKey(toLang)) {
            return translations.get(toLang);
        }
        
        return null;
    }
    
    public String translateCategory(String category, String toLang) {
        if (category == null) return null;
        
        Map<String, String> translations = CATEGORY_TRANSLATIONS.get(category.toLowerCase());
        if (translations != null && translations.containsKey(toLang)) {
            return translations.get(toLang);
        }
        
        return null;
    }
    
    /**
     * Checks if a basic translation is available for common terms.
     * This can be used to skip OpenAI calls for basic translations.
     */
    public boolean hasBasicTranslation(String text) {
        if (text == null) return false;
        
        String normalized = text.toLowerCase();
        
        // Check if it's a simple term we can translate
        if (COMMON_TERMS.containsKey(normalized) || 
            FORM_TRANSLATIONS.containsKey(normalized) ||
            CATEGORY_TRANSLATIONS.containsKey(normalized)) {
            return true;
        }
        
        // Check if it contains mostly translatable terms
        int translatableWords = 0;
        String[] words = normalized.split("\\s+");
        for (String word : words) {
            if (COMMON_TERMS.containsKey(word)) {
                translatableWords++;
            }
        }
        
        return translatableWords > 0 && translatableWords >= words.length / 2;
    }
}
