export default function App() {
  return (
    <>
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
            <input id="q" placeholder="Search protein, creatine, vanilla…" autoComplete="off" />
          </div>
          <button className="search-btn" id="go">
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

        <div className="cart-nav">
          <span className="badge-top" id="cartBadge">0</span>
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

      <div className="sugg" id="sugg" style={{display:'none'}}>
        <div className="box" id="suggBox"></div>
      </div>

      <div className="mobile-filterbar" id="mobileFilterbar">
        <div className="mf-scroll">
          <button className="mf-chip" data-range="0-20">Up to 20 EUR</button>
          <button className="mf-chip" data-range="20-30">20 - 30 EUR</button>
          <button className="mf-chip" data-range="30-35">30 - 35 EUR</button>
          <button className="mf-chip" data-range="35-">Over 35 EUR</button>
        </div>
        <div className="mf-divider"></div>
        <span className="mf-right" id="openFilters2">Filters ⌵</span>
      </div>

      <div className="toolbar">
        <div className="left">
          <div id="count">0 results</div>
          <div className="small muted meta" id="meta">Fast shipping • Free returns</div>
        </div>
        <div className="right">
          <button className="mobile-filters-btn" id="openFilters">
            <span>☰</span>
            <span>Filters ⌵</span>
            <span className="badge" id="filtersBadge">0</span>
          </button>
          <span className="sort-label">Sort by:</span>
          <select id="sort">
            <option value="relevance">Featured</option>
            <option value="price-asc">Price: Low to High</option>
            <option value="price-desc">Price: High to Low</option>
            <option value="rating-desc">Avg. Customer Review</option>
            <option value="newest">Newest</option>
          </select>
        </div>
      </div>

      <div className="shell">
        <aside className="filters">
          <div className="panel">
            <div className="h">
              <div>Refine</div>
              <button className="link" id="clearAll">Clear all</button>
            </div>
            <div className="chips" id="activePills"></div>
          </div>

          <div className="panel" data-key="category">
            <div className="h"><span>Category</span></div>
            <div id="fCategory"></div>
          </div>
          <div className="panel" data-key="brand">
            <div className="h"><span>Brand</span></div>
            <div id="fBrand"></div>
            <button className="link" id="brandMore">Show more</button>
          </div>
          <div className="panel" data-key="price">
            <div className="h"><span>Price</span></div>
            <div>
              <div className="price-range">
                <div className="price-display" id="priceDisplay">€4–€390+</div>
                <div className="price-slider-container">
                  <div className="price-slider-wrapper">
                    <div className="price-slider" id="priceSlider">
                      <div className="price-slider-track" id="priceTrack"></div>
                      <div className="price-slider-thumb" id="priceThumbMin" data-thumb="min"></div>
                      <div className="price-slider-thumb" id="priceThumbMax" data-thumb="max"></div>
                    </div>
                  </div>
                  <button className="price-go-btn" id="priceGo">Go</button>
                </div>
              </div>
              <div className="chips">
                <div className="chip" data-val="0-20">€0–20</div>
                <div className="chip" data-val="20-40">€20–40</div>
                <div className="chip" data-val="40-80">€40–80</div>
                <div className="chip" data-val="80-">€80+</div>
              </div>
            </div>
          </div>
          <div className="panel" data-key="flavour">
            <div className="h"><span>Flavour</span></div>
            <div id="fFlavour"></div>
          </div>
          <div className="panel" data-key="diet">
            <div className="h"><span>Dietary</span></div>
            <div id="fDiet"></div>
          </div>
          <div className="panel" data-key="form">
            <div className="h"><span>Item Form</span></div>
            <div id="fForm"></div>
            <button className="link" id="formMore">See Less</button>
          </div>
          <div className="panel" data-key="container">
            <div className="h"><span>Container Type</span></div>
            <div id="fContainer"></div>
          </div>
          <div className="panel" data-key="size">
            <div className="h"><span>Size</span></div>
            <div id="fSize"></div>
          </div>
          <div className="panel" data-key="availability">
            <div className="h"><span>Availability</span></div>
            <div id="fAvailability">
              <button className="chip" id="availToggle" type="button">Include Out of Stock</button>
            </div>
          </div>
        </aside>

        <main className="main">
          <div className="active-pills" id="pills"></div>
          <div className="grid" id="grid"></div>
          <div className="pager" id="pager"></div>
          <div className="panel" id="relatedPanel" style={{display:'none', marginTop: 8}}>
            <div className="h">Related searches</div>
            <div className="chips" id="relatedChips"></div>
          </div>

          <div className="panel" id="browsing-history" style={{marginTop: 16}}>
            <div className="h" style={{marginBottom: 12}}>Inspired by your browsing history</div>
            <div className="carousel-wrapper">
              <button className="carousel-nav prev" id="carouselPrev" disabled>‹</button>
              <div className="carousel" id="historyCarousel"></div>
              <button className="carousel-nav next" id="carouselNext">›</button>
            </div>
            <div style={{textAlign: 'right', marginTop: 8}}>
              <span style={{fontSize: 12, color: 'var(--muted)'}} id="carouselPage">Page 1 of 2</span>
            </div>
          </div>
        </main>

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
      </div>

      <div className="filters-modal" id="filtersModal">
        <div className="filters-modal-content">
          <div className="filters-modal-header">
            <h2>Filters ⌵</h2>
            <button className="filters-modal-close" id="closeFilters">✕</button>
          </div>
          <div className="filters-modal-body" id="filtersModalBody"></div>
          <div className="filters-modal-footer">
            <button className="btn" id="clearFilters">Clear all</button>
            <button className="btn primary" id="applyFilters">Apply</button>
          </div>
        </div>
      </div>

      <div className="location-modal" id="locationModal" aria-hidden="true">
        <div className="location-modal-content" role="dialog" aria-modal="true" aria-labelledby="locTitle">
          <button className="floating-done" id="closeLocationTop">DONE</button>
          <div className="location-modal-header">
            <div className="title" id="locTitle">Choose your location</div>
          </div>
          <div className="location-modal-body">
            <div className="desc">Delivery options and delivery speeds may vary for different locations</div>
            <button className="signin">Sign in to see your addresses</button>
            <div className="divider"></div>
            <div className="actions">
              <button className="row"><span className="ico"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><path d="M12 21c-4.6-4.9-7-8.2-7-11.2a7 7 0 1 1 14 0c0 3-2.4 6.3-7 11.2z"></path><circle cx="12" cy="10" r="3"></circle></svg></span>Enter a postal code</button>
              <button className="row"><span className="ico"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><path d="M12 5v2"></path><path d="M12 17v2"></path><path d="M5 12h2"></path><path d="M17 12h2"></path><circle cx="12" cy="12" r="4"></circle></svg></span>Use my current location</button>
              <button className="row" id="deliverOutsideBtn"><span className="ico"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><circle cx="12" cy="12" r="9"></circle></svg></span>Deliver outside Estonia</button>
            </div>
          </div>
        </div>
      </div>

      <div id="ariaLive" aria-live="polite" style={{position:'absolute',left:-9999,width:1,height:1,overflow:'hidden'}}></div>
    </>
  );
}
