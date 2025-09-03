import { $, $$ } from '../core/dom.js';
import { language, t } from '../core/language.js';
import { bus } from '../core/bus.js';

export function applyTranslations() {
  // Search placeholder
  const searchInput = $('#search');
  if (searchInput) {
    searchInput.placeholder = t('search.placeholder', 'Search whey, creatine, goals…');
  }
  const overlayInput = $('#searchOverlayInput');
  if (overlayInput) {
    overlayInput.placeholder = t('search.placeholder', 'Search whey, creatine, goals…');
  }

  // AI UI
  const askHeader = $('#askAiHeader');
  if (askHeader) askHeader.setAttribute('aria-label', t('ai.ask', 'Ask AI'));
  const askOverlay = $('#askAiOverlay');
  if (askOverlay) askOverlay.textContent = t('ai.ask', 'Ask AI');
  
  // Cart UI (support old pill and new icon styles)
  const cartPill = $('.cart-pill');
  if (cartPill) {
    const count = $('#cartCount')?.textContent || '0';
    cartPill.innerHTML = `${t('cart', 'Cart')} · <span id="cartCount">${count}</span>`;
  } else {
    const cartIcon = $('.cart-icon');
    if (cartIcon) cartIcon.setAttribute('aria-label', t('cart', 'Cart'));
  }
  
  // Sort dropdown options
  const updateSortOptions = (selectId) => {
    const select = $(selectId);
    if (!select) return;
    
    const currentValue = select.value;
    select.innerHTML = `
      <option value="relevance">${t('featured', 'Featured')}</option>
      <option value="price_asc">${t('price_asc', 'Price ↑')}</option>
      <option value="price_desc">${t('price_desc', 'Price ↓')}</option>
      <option value="popular">${t('top_rated', 'Top Rated')}</option>
      <option value="new">${t('new', 'New')}</option>
    `;
    select.value = currentValue;
  };
  
  updateSortOptions('#sortDropdown');
  updateSortOptions('#sortDropdownHeader');
  updateSortOptions('#homeSort');
  
  // Goal chips
  const goalChips = $$('#goalChips .chip[data-preset]');
  goalChips.forEach(chip => {
    const preset = chip.getAttribute('data-preset');
    const icon = chip.querySelector('span')?.textContent || '';
    const text = t(preset, chip.textContent.replace(icon, '').trim());
    chip.innerHTML = `<span>${icon}</span> ${text}`;
  });
  
  // Add buttons
  $$('.add').forEach(btn => {
    if (btn.textContent === 'Add') {
      btn.textContent = t('add', 'Add');
    } else if (btn.textContent === 'Choose flavor') {
      btn.textContent = t('choose_flavor', 'Choose flavor');
    }
  });
  
  // In stock badges
  $$('.badge').forEach(badge => {
    if (badge.textContent === 'In stock') {
      badge.textContent = t('in_stock', 'In stock');
    }
  });
}

// Apply translations on load
document.addEventListener('DOMContentLoaded', applyTranslations);

// Reapply when language changes
bus.addEventListener('language:changed', applyTranslations);
