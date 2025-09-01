// Super-tiny hash router with params
const routes = [];
export function route(pattern, handler) {
  // pattern examples: '/', '/p/:id'
  const re = new RegExp('^' + pattern.replace(/:[^/]+/g, '([^/]+)') + '$');
  routes.push([re, handler]);
}
export function navigate(path) {
  if (location.hash.slice(1) === path) return window.dispatchEvent(new HashChangeEvent('hashchange'));
  location.hash = '#'+path;
}
export function start() {
  const go = () => {
    const h = location.hash.slice(1) || '/';
    for (const [re, fn] of routes) {
      const m = h.match(re);
      if (m) return fn(...m.slice(1));
    }
  };
  addEventListener('hashchange', go);
  go();
}


