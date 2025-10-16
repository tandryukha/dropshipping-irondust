import { useStore } from '../store.jsx'

export function Topbar({ q, setQ, onSearch }){
  const { state, dispatch } = useStore();
  const badgeCount = Array.from(state.basket.values()).reduce((a, b) => a + b, 0);
  return (
    <div className="topbar">
      <button className="burger" id="burgerBtn" aria-label="Menu">≡</button>
      <div className="mobile-signin" id="mobileSignin"><span className="mi">👤</span><span>Sign in ›</span></div>
      <div className="logo">health<span className="tld">.ee</span></div>

      <div className="location-picker" id="openLocationDesktop">
        <span className="icon" aria-hidden="true">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
            <path d="M12 21c-4.6-4.9-7-8.2-7-11.2a7 7 0 1 1 14 0c0 3-2.4 6.3-7 11.2z"></path>
            <circle cx="12" cy="10" r="3"></circle>
          </svg>
        </span>
        <div className="text">
          <div className="label">Deliver to</div>
          <div className="city">Estonia</div>
        </div>
      </div>

      <div className="searchbar">
        <select id="catSelect">
          <option>All</option>
          <option>Protein</option>
          <option>Creatine</option>
          <option>Pre-Workout</option>
          <option>Amino Acids</option>
          <option>Vitamins & Health</option>
        </select>
        <div className="search">
          <input id="q" placeholder="Search protein, creatine, vanilla…" autoComplete="off" value={q} onChange={(e)=>setQ(e.target.value)} onKeyDown={(e)=>{ if(e.key==='Enter') onSearch(); }} />
        </div>
        <button className="search-btn" id="go" onClick={onSearch}>
          <span className="icon">🔍</span>
        </button>
      </div>

      <div className="nav-right">
        <div className="lang-selector">
          <span className="flag">🇪🇪</span>
          <span className="lang" id="currentLang">EN</span>
          <span> ⌵</span>
          <div className="lang-dropdown">
            <div className="lang-option active" data-lang="EN">English</div>
            <div className="lang-option" data-lang="RU">Русский</div>
            <div className="lang-option" data-lang="EE">Eesti</div>
          </div>
        </div>

        <div className="nav-item">
          <div className="top">Hello, sign in</div>
          <div className="bottom">Account & Lists ⌵</div>
          <div className="account-dropdown">
            <a href="#" className="signin-btn">Sign in</a>
            <div className="new-customer">New customer? <a href="#">Start here.</a></div>
            <div className="dropdown-header">Your Account</div>
            <div className="dropdown-section">
              <a href="#" className="dropdown-item">Your Account</a>
              <a href="#" className="dropdown-item">Your Orders</a>
              <a href="#" className="dropdown-item">Your Wish List</a>
              <a href="#" className="dropdown-item">Your Recommendations</a>
              <a href="#" className="dropdown-item">Your Subscribe & Save Items</a>
              <a href="#" className="dropdown-item">Memberships & Subscriptions</a>
            </div>
          </div>
        </div>
      </div>

      <div className="cart-nav" onClick={()=>dispatch({type:'toggleBasket'})}>
        <span className="badge-top" id="cartBadge">{badgeCount}</span>
        <div className="cart-icon">
          <svg viewBox="0 0 40 32" xmlns="http://www.w3.org/2000/svg">
            <path d="M 8 8 L 12 8 L 16 24 L 34 24 M 16 24 L 18 28 L 32 28" strokeLinecap="round" strokeLinejoin="round"/>
            <circle cx="20" cy="30" r="1.5" fill="#fff"/>
            <circle cx="30" cy="30" r="1.5" fill="#fff"/>
          </svg>
        </div>
        <div className="label">Shopping-<br/>Basket</div>
      </div>

      <div className="mobile-delivery" id="openLocation">
        <span className="icon" aria-hidden="true">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
            <path d="M12 21c-4.6-4.9-7-8.2-7-11.2a7 7 0 1 1 14 0c0 3-2.4 6.3-7 11.2z"></path>
            <circle cx="12" cy="10" r="3"></circle>
          </svg>
        </span>
        <span id="mobileDeliveryLabel">Deliver to Estonia ⌵</span>
        <span className="chev" aria-hidden="true">
          <svg viewBox="0 0 24 24" fill="currentColor"><path d="M7 10l5 5 5-5z"></path></svg>
        </span>
      </div>
    </div>
  );
}


