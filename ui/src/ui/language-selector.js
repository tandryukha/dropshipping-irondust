import { $ } from '../core/dom.js';
import { bus } from '../core/bus.js';
import { language } from '../core/language.js';

export function mountLanguageSelector() {
  // If a flag button exists, wire it; else, replace the EN placeholder.
  let flagBtn = $('#langFlag');
  if (!flagBtn) {
    const candidates = Array.from(document.querySelectorAll('.actions .kbd[aria-hidden="true"], .kbd[aria-hidden="true"]'));
    const langPlaceholder = candidates.find(el => (el.textContent || '').trim().toUpperCase() === 'EN');
    if (!langPlaceholder) return;
    flagBtn = document.createElement('button');
    flagBtn.id = 'langFlag';
    flagBtn.className = 'lang-flag';
    langPlaceholder.parentNode.replaceChild(flagBtn, langPlaceholder);
  }

  function flagFor(code){
    switch(code){
      case 'ru': return '🇷🇺';
      case 'est': return '🇪🇪';
      case 'en':
      default: return '🇬🇧';
    }
  }

  // Initialize
  flagBtn.textContent = flagFor(language.getLanguage());
  flagBtn.setAttribute('aria-label', 'Language');
  const menu = $('#langMenu');
  if (menu) {
    menu.innerHTML = '';
    ['en','ru','est'].forEach(code => {
      const b = document.createElement('button');
      b.className = 'lang-item';
      b.type = 'button';
      b.setAttribute('data-code', code);
      b.textContent = flagFor(code);
      if (code === language.getLanguage()) b.setAttribute('aria-pressed','true');
      b.addEventListener('click', (e)=>{
        e.stopPropagation();
        menu.classList.remove('visible');
        language.setLanguage(code);
      });
      menu.appendChild(b);
    });
  }

  // Cycle languages on click
  flagBtn.addEventListener('click', (e)=>{
    e.stopPropagation();
    if (menu) menu.classList.toggle('visible');
  });

  // Close on outside click
  document.addEventListener('click', (e)=>{
    if (!menu) return;
    if (e.target.closest('#langMenu') || e.target.closest('#langFlag')) return;
    menu.classList.remove('visible');
  });

  // Sync on external language changes
  bus.addEventListener('language:changed', (e)=>{
    const code = typeof e?.detail === 'string' ? e.detail : language.getLanguage();
    flagBtn.textContent = flagFor(code);
    if (menu) {
      Array.from(menu.querySelectorAll('.lang-item')).forEach(el=>{
        el.setAttribute('aria-pressed', String(el.getAttribute('data-code')===code));
      });
    }
  });
}
