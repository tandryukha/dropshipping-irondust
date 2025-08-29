// Derive price per 100g when backend value is missing.
// Returns a Number (EUR per 100g) or null if inputs are insufficient/weak.
export function derivePricePer100g(item){
  try{
    if (!item || typeof item !== 'object') return null;
    // Prefer backend field if present and valid
    if (typeof item.price_per_100g === 'number' && item.price_per_100g > 0) return item.price_per_100g;

    // Compute from price_per_serving and serving_size_g
    const pricePerServing = typeof item.price_per_serving === 'number' ? item.price_per_serving : null;
    const servingSizeGrams = typeof item.serving_size_g === 'number' ? item.serving_size_g : null;
    if (pricePerServing != null && servingSizeGrams && servingSizeGrams > 0) {
      const eurPer100g = pricePerServing / (servingSizeGrams / 100);
      if (isFinite(eurPer100g) && eurPer100g > 0) return eurPer100g;
    }

    // Compute from total net weight (g) and product price
    const priceCents = typeof item.price_cents === 'number' ? item.price_cents : null;
    // Try common weight fields
    const netWeightGrams = (
      typeof item.net_weight_g === 'number' ? item.net_weight_g :
      typeof item.weight_g === 'number' ? item.weight_g :
      null
    );
    if (priceCents != null && netWeightGrams && netWeightGrams > 0) {
      const priceEur = priceCents / 100;
      const eurPer100g = priceEur / (netWeightGrams / 100);
      if (isFinite(eurPer100g) && eurPer100g > 0) return eurPer100g;
    }

    return null;
  }catch(_e){
    return null;
  }
}


// Classify count-based dosage forms where price per 100g is not meaningful
// Examples: capsules, tablets, softgels, gummies, sachets, ampoules
export function isCountBasedForm(form){
  if (typeof form !== 'string' || !form) return false;
  const f = form.toLowerCase().trim();
  const tokens = new Set([
    'capsule','capsules','caps',
    'tab','tabs','tablet','tablets',
    'softgel','softgels',
    'gummy','gummies','chewable','chewables','lozenge','lozenges',
    'sachet','sachets','stick','sticks','packet','packets',
    'ampoule','ampoules','vial','vials','ampul'
  ]);
  return tokens.has(f);
}


