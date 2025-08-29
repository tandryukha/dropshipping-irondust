import { $ } from '../core/dom.js';
import { bus } from '../core/bus.js';
import { language } from '../core/language.js';

export function mountLanguageSelector() {
  // Find the existing EN placeholder
  const langPlaceholder = document.querySelector('.kbd[aria-hidden="true"]');
  if (!langPlaceholder || langPlaceholder.textContent !== 'EN') return;
  
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
    // Reload the page to apply new language
    // In a real app, we'd update the UI dynamically
    window.location.reload();
  });
  
  // Replace the placeholder
  langPlaceholder.parentNode.replaceChild(selector, langPlaceholder);
  
  // Listen for language changes from other sources
  bus.addEventListener('language:changed', (lang) => {
    selector.value = lang;
  });
}
