// Simple app-wide event bus
export const bus = new EventTarget();
// usage: bus.dispatchEvent(new CustomEvent('event', { detail: {...} }))


