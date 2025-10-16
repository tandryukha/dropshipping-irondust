export function BasketSidebar(){
  return (
    <aside className="basket" id="basketPanel">
      <button className="close-btn" id="closeBasket" aria-label="Close basket">✕</button>
      <div className="panel summary">
        <div className="subhead">Subtotal</div>
        <div className="subtotal"><span className="price" id="subtotal" aria-live="polite">€0.00</span></div>
        <div className="free-msg" id="freeMsg">Add <b>€0.00</b> of eligible items to your order to qualify for <b>FREE</b> Delivery.</div>
        <div className="free-link"><a href="#">Delivery Details</a></div>
        <button className="go" id="goBasket">Go to Basket</button>
      </div>
      <div className="panel items">
        <div id="basketItems">
          <div className="muted" style={{padding: '10px 0', textAlign: 'center', fontSize: 10}}>No items yet</div>
        </div>
      </div>
    </aside>
  );
}


