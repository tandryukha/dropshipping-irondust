# Localization Setup Guide

## Overview

The IronDust dropshipping search application now supports multilingual functionality with support for:
- **English (en)**
- **Russian (ru)**  
- **Estonian (est)**

Users can search in any language regardless of their UI language selection, and the UI will display product information in their selected language.

## Architecture

### Backend Components

1. **TranslationService** (`TranslationService.java`)
   - Uses OpenAI API for translations
   - Caches translations for 24 hours
   - Handles product-specific terminology
   - Supports batch translation during ingestion

2. **Multilingual Data Model**
   - `ProductDoc` now includes `*_i18n` fields for translations
   - Fields supported: `name`, `description`, `categories_names`, `form`, `flavor`, `search_text`
   - Original Estonian data is preserved

3. **Search API**
   - Accepts `lang` parameter in search requests
   - Returns localized product data based on language preference
   - Searches across all language versions

4. **Product API**
   - `/products/{id}?lang=xx` returns localized product details

### Frontend Components

1. **Language Module** (`language.js`)
   - Manages language state
   - Persists selection in localStorage
   - Auto-detects browser language

2. **Language Selector** (`language-selector.js`)
   - Dropdown in header for language selection
   - Triggers page reload on change (can be optimized to dynamic updates)

3. **Translations** (`translations.js`)
   - UI text translations
   - Applied automatically on load and language change

## Configuration

### Environment Variables

```bash
# Required for translation service
export OPENAI_API_KEY="your-openai-api-key"

# Optional - defaults shown
export OPENAI_MODEL="gpt-4o-mini"  # or gpt-3.5-turbo for lower cost
export TRANSLATION_ENABLED="true"   # set to false to disable translations
```

### Application Properties

In `application.yml`:
```yaml
app:
  openaiApiKey: ${OPENAI_API_KEY:your-api-key-here}
  openaiModel: ${OPENAI_MODEL:gpt-4o-mini}
  translationEnabled: ${TRANSLATION_ENABLED:true}
```

## Usage

### 1. Start the Services

```bash
# Start Meilisearch
docker-compose up -d meilisearch

# Start the application with OpenAI API key
export OPENAI_API_KEY="your-key"
mvn spring-boot:run
```

### 2. Reingest Products

Products need to be reingested to generate translations:

```bash
# Reingest all products (may take time and API credits)
curl -X POST http://localhost:4000/ingest/full \
  -H "x-admin-key: dev_admin_key"

# Or reingest specific products for testing
curl -X POST http://localhost:4000/ingest/products \
  -H "Content-Type: application/json" \
  -H "x-admin-key: dev_admin_key" \
  -d '{"ids": [31489, 50533, 50535]}'
```

### 3. Test Localization

```bash
# Run the test script
./test-localization.sh

# Or test manually:
# Search in Russian
curl -X POST http://localhost:4000/search \
  -H "Content-Type: application/json" \
  -d '{"q": "протеин", "size": 5, "lang": "ru"}'

# Get product in English
curl http://localhost:4000/products/wc_31489?lang=en
```

### 4. UI Language Selection

1. Open the UI at http://localhost:3001
2. Use the language dropdown in the header to select language
3. Product names, categories, and UI text will display in selected language
4. Search works in any language

## Cost Considerations

Translation costs depend on:
- Number of products
- Length of descriptions
- OpenAI model used (gpt-4o-mini is recommended for cost-effectiveness)

Approximate costs:
- ~$0.15 per 1M input tokens, ~$0.60 per 1M output tokens (gpt-4o-mini)
- Average product: ~500 tokens input, ~400 tokens output
- Cost per product: ~$0.0003 for all 3 languages

For 10,000 products: ~$3 total (one-time cost, cached for 24 hours)

## Optimization Tips

1. **Batch Processing**: The ingestion process translates products in parallel
2. **Caching**: Translations are cached for 24 hours to reduce API calls
3. **Selective Translation**: Only translate changed products on updates
4. **Model Selection**: Use gpt-4o-mini for cost-effectiveness

## Extending Languages

To add more languages:

1. Update `TranslationService.SUPPORTED_LANGUAGES`
2. Add language names in `getLanguageName()`
3. Update frontend language options in `language.js`
4. Add UI translations in `translations.js`

## Troubleshooting

### No Translations Appearing
- Check OPENAI_API_KEY is set correctly
- Verify `translationEnabled` is true
- Check logs for translation errors
- Ensure products have been reingested

### Search Not Working in Other Languages
- Verify multilingual fields are indexed in Meilisearch
- Check that products have been reingested with translations
- Ensure search request includes `lang` parameter

### UI Not Changing Language
- Check browser console for errors
- Verify language selector is mounted
- Clear browser cache and localStorage
