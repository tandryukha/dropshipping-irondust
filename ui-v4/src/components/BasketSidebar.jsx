import { useEffect, useMemo } from 'react'
import { useStore } from '../store.jsx'

export function BasketSidebar(){
  const { state, dispatch } = useStore();
  const isOpen = !!state.ui.isBasketOpen;
  const items = useMemo(() => Array.from(state.basket.entries()).map(([id, qty]) => {
    const product = state.productsById.get(id) || null;
    return { id, qty, product };
  }), [state.basket, state.productsById]);

  useEffect(() => {
    if (isOpen) document.body.classList.add('basket-open');
    else document.body.classList.remove('basket-open');
    return () => { document.body.classList.remove('basket-open'); };
  }, [isOpen]);

  const subtotal = items.reduce((sum, it) => {
    const p = it.product;
    const price = (p && typeof p.price === 'number') ? p.price : (p && typeof p.price_cents === 'number' ? p.price_cents/100 : 0);
    return sum + price * it.qty;
  }, 0);

  return (
    <aside className={"basket" + (isOpen ? " visible" : "")} id="basketPanel">
      <button className="close-btn" id="closeBasket" aria-label="Close basket" onClick={()=>dispatch({ type:'toggleBasket' })}>✕</button>
      <div className="panel summary">
        <div className="subhead">Subtotal</div>
        <div className="subtotal"><span className="price" id="subtotal" aria-live="polite">€{subtotal.toFixed(2)}</span></div>
        <div className="free-msg" id="freeMsg">Add <b>€0.00</b> of eligible items to your order to qualify for <b>FREE</b> Delivery.</div>
        <div className="free-link"><a href="#">Delivery Details</a></div>
        <button className="go" id="goBasket">Go to Basket</button>
      </div>
      <div className="panel items">
        <div id="basketItems">
          {items.length === 0 ? (
            <div className="muted" style={{padding: '10px 0', textAlign: 'center', fontSize: 10}}>No items yet</div>
          ) : (
            items.map(({ id, qty, product }) => {
              const firstImg = product && Array.isArray(product.images) && product.images.length ? product.images[0] : null;
              const price = product && typeof product.price === 'number' ? product.price : (product && typeof product.price_cents === 'number' ? product.price_cents/100 : null);
              return (
              <div key={String(id)} className="item-block">
                <div className="pimg">{firstImg ? <img src={firstImg} alt="" style={{maxWidth:'100%',maxHeight:'100%',objectFit:'contain'}} /> : 'IMG'}</div>
                {price != null ? <div className="price">Price: <span className="val">€{price.toFixed(2)}</span></div> : null}
                <div className="qty-ctrl">
                  <button className="trash" aria-label="Remove" onClick={()=>dispatch({ type:'basketRemove', id })}>🗑️</button>
                  <span className="count" aria-live="polite">{qty}</span>
                  <button className="plus" aria-label="Add one" onClick={()=>dispatch({ type:'basketAdd', id, product })}>＋</button>
                </div>
              </div>
              );
            })
          )}
        </div>
      </div>
    </aside>
  );
}


