# Integrated Localization

The application now includes fully integrated multilingual support with automatic translation during product ingestion.

## ✅ Features

### Supported Languages
- **Estonian (est)** - Primary/source language
- **English (en)** - International
- **Russian (ru)** - Regional

### Automatic Translation
- Translations happen automatically during product ingestion
- Uses the same OpenAI API key as AI enrichment
- No additional configuration required
- Source language auto-detection

### What Gets Translated
- Product names
- Benefit snippets
- FAQ questions and answers
- Category names
- Product forms (powder, capsules, etc.)
- Flavors
- Search text

## 🔧 How It Works

### During Ingestion
```
1. Product data arrives (usually in Estonian)
2. AI enrichment runs (as before)
3. Translation service detects source language
4. Translates to other 2 languages
5. Stores all language versions in Meilisearch
```

### During Search
```
1. User searches in any language
2. Search works across all language fields
3. Results returned with user's language preference
4. UI displays localized content
```

## 📡 API Usage

### Search with Language
```bash
# Russian search
curl -X POST http://localhost:4000/search \
  -H "Content-Type: application/json" \
  -d '{
    "q": "омега 3",
    "size": 10,
    "lang": "ru"
  }'

# Estonian search  
curl -X POST http://localhost:4000/search \
  -H "Content-Type: application/json" \
  -d '{
    "q": "valgu pulber",
    "size": 10,
    "lang": "est"
  }'
```

### Product Details with Language
```bash
# Get product in Russian
curl "http://localhost:4000/products/wc_31489?lang=ru"

# Response includes localized fields:
{
  "name": "MST Omega 3 Selected 60 капсул",
  "benefit_snippet": "Поддерживает здоровье сердца...",
  "faq": [
    {
      "q": "Сколько мягких капсул я должен принимать ежедневно?",
      "a": "Рекомендуемая дозировка составляет 2 мягкие капсулы..."
    }
  ]
}
```

## 🎨 UI Integration

### Language Selector
- Dropdown in header shows: English | Русский | Eesti
- Selection persists in localStorage
- Auto-detects browser language on first visit

### Dynamic Content
- Product names update based on selected language
- Categories and filters show translated text
- Search works in selected language
- UI elements (buttons, labels) are translated

## 🚀 Performance

### Translation Speed
- ~80 seconds per product (includes AI enrichment)
- Translations cached for 24 hours
- Parallel processing for efficiency

### Search Performance
- No impact on search speed
- All languages indexed in Meilisearch
- Instant language switching

## 💰 Cost

- Uses same OpenAI API key as enrichment
- Model: GPT-4o-mini
- Cost: ~$0.0003 per product (all 3 languages)
- Example: 10,000 products = ~$3 total

## 🧠 Caching

- Translations are cached in-memory for 24h and persisted to disk at `tmp/translation-cache.json`.
- AI enrichment responses are cached and persisted to `tmp/ai-enrichment-cache.json`.
- You can clear caches per ingest request via headers (admin key required):

```bash
curl -X POST http://localhost:4000/ingest/full \
  -H "x-admin-key: dev_admin_key" \
  -H "x-clear-translation-cache: true" \
  -H "x-clear-ai-cache: true"
```

## 🔍 Example: Complete Flow

1. **Ingest Product**
   ```bash
   curl -X POST http://localhost:4000/ingest/products \
     -H "Content-Type: application/json" \
     -H "x-admin-key: dev_admin_key" \
     -d '{"ids": [31489]}'
   ```

2. **Search in Russian**
   ```bash
   curl -X POST http://localhost:4000/search \
     -H "Content-Type: application/json" \
     -d '{"q": "омега", "lang": "ru"}'
   ```

3. **View in Estonian**
   ```bash
   curl "http://localhost:4000/products/wc_31489?lang=est"
   ```

## 🛠️ Technical Implementation

### Key Components
- `TranslationService.java` - Handles all translations
- Uses environment variables: `OPENAI_API_KEY`, `OPENAI_MODEL`
- Integrated into `IngestService` pipeline
- `SearchController` and `ProductController` apply language preferences

### Data Storage
- Original fields remain unchanged
- Translations stored in `*_i18n` fields
- Example: `name` → `name_i18n: {en: "...", ru: "...", est: "..."}`

### No Configuration Needed
- If `OPENAI_API_KEY` is set (for AI enrichment), translations work automatically
- No separate translation configuration required
- Enabled by default when API key present
