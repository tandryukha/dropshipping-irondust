import { bus } from './bus.js';

// Language state management
const SUPPORTED_LANGUAGES = ['en', 'ru', 'est'];
const DEFAULT_LANGUAGE = 'en';
const STORAGE_KEY = 'irondust_language';

class LanguageStore {
  constructor() {
    this.current = this.loadLanguage();
    try { document.documentElement.setAttribute('lang', this.current); } catch(_e) {}
  }
  
  loadLanguage() {
    const stored = localStorage.getItem(STORAGE_KEY);
    if (stored && SUPPORTED_LANGUAGES.includes(stored)) {
      return stored;
    }
    
    // Try to detect from browser
    const browserLang = navigator.language.toLowerCase();
    if (browserLang.startsWith('ru')) return 'ru';
    if (browserLang.startsWith('et') || browserLang.startsWith('est')) return 'est';
    
    return DEFAULT_LANGUAGE;
  }
  
  setLanguage(lang) {
    if (!SUPPORTED_LANGUAGES.includes(lang)) return;
    
    this.current = lang;
    localStorage.setItem(STORAGE_KEY, lang);
    try { document.documentElement.setAttribute('lang', lang); } catch(_e) {}
    bus.dispatchEvent(new CustomEvent('language:changed', { detail: lang }));
  }
  
  getLanguage() {
    return this.current;
  }
  
  getLanguageNames() {
    return {
      'en': 'English',
      'ru': 'Русский',
      'est': 'Eesti'
    };
  }
}

export const language = new LanguageStore();

// Language-aware text helper
export function t(key, fallback = '') {
  // For now, just return the key or fallback
  // This can be extended to use proper i18n files later
  const translations = {
    'search.placeholder': {
      'en': 'Search whey, creatine, goals…',
      'ru': 'Поиск протеин, креатин, цели…',
      'est': 'Otsi valku, kreatiini, eesmärke…'
    },
    'cart': {
      'en': 'Cart',
      'ru': 'Корзина',
      'est': 'Ostukorv'
    },
    'add': {
      'en': 'Add',
      'ru': 'Добавить',
      'est': 'Lisa'
    },
    'choose_flavor': {
      'en': 'Choose flavor',
      'ru': 'Выберите вкус',
      'est': 'Vali maitse'
    },
    'in_stock': {
      'en': 'In stock',
      'ru': 'В наличии',
      'est': 'Laos'
    },
    'featured': {
      'en': 'Featured',
      'ru': 'Рекомендуемые',
      'est': 'Esile tõstetud'
    },
    'price_asc': {
      'en': 'Price ↑',
      'ru': 'Цена ↑',
      'est': 'Hind ↑'
    },
    'price_desc': {
      'en': 'Price ↓',
      'ru': 'Цена ↓',
      'est': 'Hind ↓'
    },
    'top_rated': {
      'en': 'Top Rated',
      'ru': 'Популярные',
      'est': 'Kõrgeimalt hinnatud'
    },
    'new': {
      'en': 'New',
      'ru': 'Новинки',
      'est': 'Uus'
    },
    'strength': {
      'en': 'Strength',
      'ru': 'Сила',
      'est': 'Jõud'
    },
    'preworkout': {
      'en': 'Pre-workout',
      'ru': 'Предтреник',
      'est': 'Enne trenni'
    },
    'wellness': {
      'en': 'Wellness',
      'ru': 'Здоровье',
      'est': 'Heaolu'
    },
    'endurance': {
      'en': 'Endurance',
      'ru': 'Выносливость',
      'est': 'Vastupidavus'
    }
    ,
    // AI search ui
    'ai.ask': {
      'en': 'Ask AI',
      'ru': 'Спросить ИИ',
      'est': 'Küsi AI-lt'
    },
    // PDP labels
    'pdp.dosage': {
      'en': 'Dosage',
      'ru': 'Дозировка',
      'est': 'Annustamine'
    },
    'pdp.timing': {
      'en': 'Timing',
      'ru': 'Время приёма',
      'est': 'Aeg'
    },
    'pdp.ingredients': {
      'en': 'Ingredients',
      'ru': 'Ингредиенты',
      'est': 'Koostisosad'
    },
    'pdp.product_info': {
      'en': 'Product Information',
      'ru': 'Информация о продукте',
      'est': 'Toote teave'
    },
    'pdp.net_weight': {
      'en': 'Net weight',
      'ru': 'Чистый вес',
      'est': 'Netokogus'
    },
    'pdp.serving_size': {
      'en': 'Serving size',
      'ru': 'Порция',
      'est': 'Portsjoni suurus'
    },
    'pdp.servings': {
      'en': 'Servings',
      'ru': 'Порций',
      'est': 'Portsjonit'
    },
    'pdp.form': {
      'en': 'Form',
      'ru': 'Форма',
      'est': 'Vorm'
    },
    'pdp.net': {
      'en': 'Net',
      'ru': 'Нетто',
      'est': 'Netto'
    },
    'pdp.units': {
      'en': 'Units',
      'ru': 'Штук',
      'est': 'Tükki'
    },
    'pdp.dose': {
      'en': 'Dose',
      'ru': 'Доза',
      'est': 'Annus'
    },
    'pdp.per_unit': {
      'en': 'Per unit',
      'ru': 'За единицу',
      'est': 'Ühe ühiku kohta'
    },
    'pdp.per_100g': {
      'en': 'Per 100g',
      'ru': 'За 100 г',
      'est': '100 g kohta'
    },
    'pdp.per_serving': {
      'en': 'Per serving',
      'ru': 'За порцию',
      'est': 'Portsioni kohta'
    },
    // PDP fallbacks
    'pdp.fallback.dosage': {
      'en': '2 capsules per day with food',
      'ru': '2 капсулы в день с пищей',
      'est': '2 kapslit päevas koos toiduga'
    },
    'pdp.fallback.timing': {
      'en': 'With meals or within 30 min post-workout',
      'ru': 'С приёмом пищи или в течение 30 мин после тренировки',
      'est': 'Koos toiduga või 30 min jooksul pärast trenni'
    },
    'pdp.fallback.no_composition': {
      'en': 'Composition information not available. Please check product packaging for details.',
      'ru': 'Информация о составе недоступна. Пожалуйста, смотрите упаковку.',
      'est': 'Koostise teave pole saadaval. Palun vaadake pakendilt.'
    }
  };
  
  const currentLang = language.getLanguage();
  const translation = translations[key];
  
  if (translation && translation[currentLang]) {
    return translation[currentLang];
  }
  
  return fallback || key;
}
