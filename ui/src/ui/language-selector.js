import { $ } from '../core/dom.js';
import { bus } from '../core/bus.js';
import { language } from '../core/language.js';

export function mountLanguageSelector() {
  // Find the existing EN placeholder
  const candidates = Array.from(document.querySelectorAll('.actions .kbd[aria-hidden="true"], .kbd[aria-hidden="true"]'));
  const langPlaceholder = candidates.find(el => (el.textContent || '').trim().toUpperCase() === 'EN');
  if (!langPlaceholder) return;
  
  // Replace with an interactive language selector
  const selector = document.createElement('select');
  selector.className = 'lang-select';
  selector.setAttribute('aria-label', 'Language selector');
  
  // Add styles
  selector.style.cssText = `
    background: #fff;
    border: 1px solid #e0e6ef;
    border-radius: 8px;
    padding: 4px 8px;
    font-size: 12px;
    font-weight: 500;
    color: #374151;
    cursor: pointer;
    min-width: 65px;
  `;
  
  // Populate options
  const languages = language.getLanguageNames();
  const currentLang = language.getLanguage();
  
  Object.entries(languages).forEach(([code, name]) => {
    const option = document.createElement('option');
    option.value = code;
    option.textContent = name;
    if (code === currentLang) {
      option.selected = true;
    }
    selector.appendChild(option);
  });
  
  // Handle changes
  selector.addEventListener('change', (e) => {
    const newLang = e.target.value;
    language.setLanguage(newLang);
    // Dynamic UI updates are handled via language:changed bus event
  });
  
  // Replace the placeholder
  langPlaceholder.parentNode.replaceChild(selector, langPlaceholder);
  
  // Listen for language changes from other sources
  bus.addEventListener('language:changed', (lang) => {
    selector.value = lang;
  });
}
