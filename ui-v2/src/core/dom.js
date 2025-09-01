export const $  = sel => document.querySelector(sel);
export const $$ = sel => Array.from(document.querySelectorAll(sel));

// Minimal HTML sanitizer to allow a safe subset of tags and attributes
// This is not exhaustive; expand allowlists as needed
const ALLOWED_TAGS = new Set(['p','br','strong','em','b','i','ul','ol','li','span','div','article','h3','h4','h5','h6','table','thead','tbody','tr','th','td']);
const ALLOWED_ATTRS = new Set(['style']);

export function sanitizeHtml(unsafeHtml){
  try{
    if(typeof unsafeHtml !== 'string' || !unsafeHtml.trim()) return '';
    const tpl = document.createElement('template');
    tpl.innerHTML = unsafeHtml;
    const walker = document.createTreeWalker(tpl.content, NodeFilter.SHOW_ELEMENT, null);
    const toRemove = [];
    while(walker.nextNode()){
      const el = walker.currentNode;
      const tag = el.tagName ? el.tagName.toLowerCase() : '';
      if(!ALLOWED_TAGS.has(tag)){
        toRemove.push(el);
        continue;
      }
      // Remove disallowed attributes and dangerous protocols
      Array.from(el.attributes).forEach(attr=>{
        const name = attr.name.toLowerCase();
        if(!ALLOWED_ATTRS.has(name)){
          el.removeAttribute(attr.name);
          return;
        }
        if(name === 'style'){
          // Strip url(), expression(), and javascript: from inline styles
          const safe = String(attr.value).replace(/url\s*\([^)]*\)/gi,'').replace(/expression\s*\([^)]*\)/gi,'').replace(/javascript:/gi,'');
          el.setAttribute('style', safe);
        }
      });
      // Remove inline event handlers (on*) if present
      for(const k of Object.keys(el)){
        if(/^on/i.test(k)){
          try{ el[k] = null; }catch(_){ }
        }
      }
    }
    toRemove.forEach(node=>{
      const parent = node.parentNode;
      if(parent){
        // Replace removed node with its text content to preserve wording
        parent.replaceChild(document.createTextNode(node.textContent || ''), node);
      }
    });
    return tpl.innerHTML;
  }catch(_){
    return '';
  }
}


