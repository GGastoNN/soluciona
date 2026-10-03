(() => {
  if (window.__soluciona080Loaded) return;
  window.__soluciona080Loaded = true;

  const F = window.SolucionaFeatures;
  if (!F || !F.isAvailable || !F.isAvailable()) return;

  const extra = {
    settings: { theme: 'system', biometricAvailable: false, biometricEnabled: false, marketplaceConfigured: false, cardPaymentsConfigured: false },
    bootstrap: { role: '', primaryZone: '', zones: [] },
    zones: [],
    referrals: null,
    mp: null,
    payment: null,
    charge: { requestId: '', amount: '', note: '', location: null, locating: false, locationError: '', pending: false },
    paymentRequest: null,
    paymentView: ''
  };

  let zonesLoadInFlight = false;
  let zonesLoadedOnce = false;

  function registrationZones() {
    try {
      if (extra.zones && extra.zones.length) return extra.zones;
      if (typeof state !== 'undefined' && Array.isArray(state.zones) && state.zones.length) return state.zones;
    } catch (_) {}
    return [];
  }

  function loadZonesOnce(force=false) {
    if (zonesLoadInFlight) return;
    if (zonesLoadedOnce && !force) return;
    zonesLoadInFlight = true;
    try { F.loadZones(); }
    catch (_) { zonesLoadInFlight = false; }
    setTimeout(() => { zonesLoadInFlight = false; }, 5000);
  }

  const style = document.createElement('style');
  style.textContent = `
    html[data-sol-theme="light"]{
      --bg:#f4f7fc;--card:#fff;--text:#14213d;--muted:#64748b;--soft:#eaf1fe;--line:#dce4ef;--shadow:0 10px 30px #183b6b12
    }
    html[data-sol-theme="dark"]{
      --bg:#0f172a;--card:#172033;--text:#f1f5f9;--muted:#94a3b8;--soft:#193767;--line:#2d3b52;--shadow:none
    }
    html[data-sol-theme="dark"] .header{background:#0f172af5}
    html[data-sol-theme="dark"] .nav,
    html[data-sol-theme="dark"] .input,
    html[data-sol-theme="dark"] textarea,
    html[data-sol-theme="dark"] select,
    html[data-sol-theme="dark"] .bubble{background:#172033}
    .sol-feature-grid{display:grid;grid-template-columns:repeat(3,1fr);gap:7px}
    .sol-choice{border:1px solid var(--line);background:var(--card);color:var(--text);border-radius:13px;padding:10px 5px;font-size:12px;font-weight:800}
    .sol-choice.on{border-color:var(--blue);background:var(--soft);color:var(--blue)}
    .sol-switch{display:flex;align-items:center;justify-content:space-between;gap:12px}
    .sol-switch input{width:22px;height:22px}
    .sol-zone-list{display:flex;flex-direction:column;gap:8px;margin:10px 0}
    .sol-zone{display:flex;align-items:center;gap:9px;padding:9px 10px;border:1px solid var(--line);border-radius:12px;background:var(--card)}
    .sol-zone input{width:20px;height:20px}
    .sol-modal-backdrop{position:fixed;inset:0;background:#0206179c;z-index:90;display:flex;align-items:flex-end;justify-content:center}
    .sol-modal{width:min(760px,100%);max-height:88vh;overflow:auto;background:var(--bg);color:var(--text);border-radius:24px 24px 0 0;padding:20px;box-shadow:0 -15px 60px #0006}
    .sol-modal-head{display:flex;align-items:center;gap:10px;margin-bottom:12px}
    .sol-modal-head h2{margin:0;flex:1}
    .sol-x{border:0;background:var(--card);color:var(--text);border-radius:12px;width:42px;height:42px;font-size:20px}
    .sol-code{font:800 22px ui-monospace,SFMono-Regular,Menlo,monospace;letter-spacing:1px;text-align:center;padding:16px;border:2px dashed var(--blue);border-radius:15px;color:var(--blue);background:var(--soft)}
    .sol-qr svg{width:min(82vw,360px);height:auto;display:block;margin:14px auto;background:white;padding:12px;border-radius:18px}
    .sol-kpi{display:grid;grid-template-columns:repeat(3,1fr);gap:8px;margin:10px 0}
    .sol-kpi>div{background:var(--card);border:1px solid var(--line);border-radius:14px;padding:12px;text-align:center}
    .sol-kpi b{display:block;font-size:20px}
    .sol-charge-summary{padding:14px;border:1px solid var(--line);border-radius:16px;background:var(--card);margin-bottom:12px}
    .sol-charge-summary b{display:block;font-size:15px}
    .sol-money-box{display:flex;align-items:center;gap:8px;border:1.5px solid var(--line);border-radius:16px;background:var(--card);padding:9px 14px;margin:8px 0 5px}
    .sol-money-box:focus-within{border-color:var(--blue);box-shadow:0 0 0 3px #2563eb15}
    .sol-money-symbol{font-size:27px;font-weight:900;color:var(--muted)}
    .sol-money-input{border:0!important;box-shadow:none!important;background:transparent!important;padding:5px 0!important;font-size:30px;font-weight:900;letter-spacing:-1px;min-width:0}
    .sol-location-card{border:1px solid var(--line);background:var(--card);border-radius:16px;padding:13px;margin:12px 0}
    .sol-location-head{display:flex;align-items:flex-start;gap:10px}
    .sol-location-icon{width:38px;height:38px;border-radius:12px;background:var(--soft);display:grid;place-items:center;flex:none}
    .sol-location-text{flex:1;min-width:0}
    .sol-location-text b{display:block;font-size:14px}
    .sol-location-ok{color:var(--ok);font-weight:800}
    .sol-location-warn{color:var(--warn);font-weight:800}
    .sol-mini-btn{border:1px solid var(--line);background:var(--card);color:var(--blue);border-radius:11px;padding:8px 10px;font-weight:800;font-size:12px}
    .sol-spin{width:16px;height:16px;border:2px solid #cbd5e1;border-top-color:var(--blue);border-radius:50%;animation:spin .8s linear infinite;display:inline-block;vertical-align:-3px;margin-right:6px}
    .sol-confirm-backdrop{position:fixed;inset:0;background:#020617a8;z-index:110;display:flex;align-items:center;justify-content:center;padding:22px}
    .sol-confirm-card{width:min(430px,100%);background:var(--card);color:var(--text);border:1px solid var(--line);border-radius:22px;padding:20px;box-shadow:0 24px 70px #0007}
    .sol-confirm-icon{width:46px;height:46px;border-radius:14px;background:#fff1f2;color:#b91c1c;display:grid;place-items:center;font-size:22px;margin-bottom:14px}
    .sol-confirm-card h3{margin:0 0 7px;font-size:20px;letter-spacing:-.4px}
    .sol-confirm-card p{margin:0;color:var(--muted);font-size:13.5px;line-height:1.5}
    .sol-confirm-actions{display:grid;grid-template-columns:1fr 1fr;gap:8px;margin-top:18px}
    .sol-confirm-actions .btn{margin:0}
    .sol-confirm-error{display:none;margin-top:12px;padding:10px 11px;border-radius:11px;background:#fef2f2;border:1px solid #fecaca;color:#991b1b;font-size:12.5px;line-height:1.4}
  `;
  document.head.appendChild(style);

  function applyTheme(theme) {
    const root = document.documentElement;
    if (theme === 'dark' || theme === 'light') root.dataset.solTheme = theme;
    else delete root.dataset.solTheme;
  }

  function featureToast(message, bad=false) {
    try { toast(message, bad); } catch (_) {
      const d = document.createElement('div');
      d.textContent = message;
      d.style.cssText = `position:fixed;z-index:120;left:50%;bottom:100px;transform:translateX(-50%);background:${bad?'#991b1b':'#14213d'};color:white;padding:11px 14px;border-radius:12px`;
      document.body.appendChild(d);
      setTimeout(() => d.remove(), 2800);
    }
  }

  function role() {
    try { return state?.profile?.role || extra.bootstrap.role || ''; } catch (_) { return extra.bootstrap.role || ''; }
  }

  function currentProfile() {
    try { return state?.profile || {}; } catch (_) { return {}; }
  }

  function isTextEditing() {
    try {
      const a = document.activeElement;
      return !!a && ['INPUT','TEXTAREA','SELECT'].includes(a.tagName);
    } catch (_) { return false; }
  }

  function isProtectedFormScreen() {
    try {
      return typeof state !== 'undefined' && ['registerClient','registerPro','login'].includes(state.screen);
    } catch (_) { return false; }
  }

  function requestRerender(force=false) {
    try {
      if (typeof render !== 'function') return;
      // Replacing #screen.innerHTML destroys the focused input and Android closes
      // the soft keyboard. Never redraw a registration/login form because an
      // asynchronous settings/zones response arrived in the background.
      if (!force && (isTextEditing() || isProtectedFormScreen())) return;
      render();
    } catch (_) {}
  }

  window.solucionaFeaturesEvent = (event, ok, payload={}) => {
    if (!ok) {
      // During biometric resume, auxiliary feature calls can briefly arrive before
      // Firebase Auth finishes restoring the persisted user. Do not expose that
      // transient race as a raw NO_SESSION error.
      if (payload.message === 'NO_SESSION' &&
          ['bootstrap','referrals','marketplaceStatus','paymentStatus','paymentRequest'].includes(event)) {
        return;
      }

      if (event === 'currentLocation') {
        extra.charge.locating = false;
        extra.charge.location = null;
        extra.charge.locationError = payload.message || 'No pudimos obtener tu ubicación actual.';
        updateChargeLocationUi();
        updateChargeButton();
        const err = document.getElementById('sol-pay-center-error');
        if (err) err.textContent = extra.charge.locationError;
        const btn = document.getElementById('sol-show-qr-btn');
        if (btn) {
          btn.disabled = false;
          btn.textContent = 'Reintentar QR híbrido';
        }
        return;
      }

      if (['paymentRequestCreated','paymentRequestQr','cardSession'].includes(event)) {
        extra.charge.pending = false;
        const err = document.getElementById('sol-charge-error') ||
                    document.getElementById('sol-pay-center-error');
        if (err) err.textContent = payload.message || 'No se pudo completar la operación.';
        updateChargeButton();
      }

      if (event === 'paymentQr') {
        extra.charge.pending = false;
        const err = document.getElementById('sol-charge-error');
        if (err) err.textContent = payload.message || 'No se pudo generar el QR.';
        updateChargeButton();
      }

      if (event === 'marketplaceDisconnect') {
        const message = payload.message || 'No se pudo desvincular Mercado Pago.';
        const confirm = document.getElementById('sol-mp-disconnect-confirm');
        const errorBox = document.getElementById('sol-mp-disconnect-error');
        const confirmBtn = document.getElementById('sol-mp-disconnect-confirm-btn');
        const cancelBtn = document.getElementById('sol-mp-disconnect-cancel-btn');
        if (confirm) {
          if (errorBox) {
            errorBox.textContent = message;
            errorBox.style.display = 'block';
          }
          if (confirmBtn) {
            confirmBtn.disabled = false;
            confirmBtn.textContent = 'Sí, desvincular';
          }
          if (cancelBtn) cancelBtn.disabled = false;
        } else {
          featureToast(message, true);
        }
        return;
      }

      featureToast(payload.message || 'No se pudo completar la operación.', true);
      return;
    }

    if (event === 'settings') {
      extra.settings = {...extra.settings, ...payload};
      applyTheme(extra.settings.theme || 'system');
      requestRerender();
      return;
    }
    if (event === 'theme') {
      extra.settings.theme = payload.theme || 'system';
      applyTheme(extra.settings.theme);
      requestRerender();
      return;
    }
    if (event === 'biometric') {
      extra.settings.biometricEnabled = !!payload.enabled;
      featureToast(payload.enabled ? 'Ingreso biométrico activado.' : 'Ingreso biométrico desactivado.');
      requestRerender();
      return;
    }
    if (event === 'zones') {
      zonesLoadInFlight = false;
      zonesLoadedOnce = true;
      extra.zones = payload.zones || [];
      fillRegistrationZone();
      if (!isProtectedFormScreen() && !isTextEditing()) requestRerender();
      return;
    }
    if (event === 'bootstrap') {
      extra.bootstrap = {...extra.bootstrap, ...payload};
      extra.settings.theme = payload.theme || extra.settings.theme;
      extra.settings.biometricAvailable = !!payload.biometricAvailable;
      extra.settings.biometricEnabled = !!payload.biometricEnabled;
      extra.settings.marketplaceConfigured = !!payload.marketplaceConfigured;
      extra.settings.cardPaymentsConfigured = !!payload.cardPaymentsConfigured;
      applyTheme(extra.settings.theme);
      requestRerender();
      return;
    }
    if (event === 'workZones') {
      extra.bootstrap.primaryZone = payload.primaryZone || '';
      extra.bootstrap.zones = payload.zones || [];
      featureToast('Zonas de trabajo actualizadas.');
      requestRerender();
      return;
    }
    if (event === 'referrals') {
      extra.referrals = payload;
      renderReferralModal();
      requestRerender();
      return;
    }
    if (event === 'referralApply') {
      featureToast('Código de referido aplicado.');
      F.getReferralDashboard();
      return;
    }
    if (event === 'marketplaceStatus') {
      extra.mp = payload;
      requestRerender();
      const modal = document.getElementById('sol-mp-modal');
      if (modal) renderMpModal();
      return;
    }
    if (event === 'marketplaceConnect') {
      featureToast('Continuá la vinculación en Mercado Pago.');
      return;
    }
    if (event === 'marketplaceDisconnect') {
      document.getElementById('sol-mp-disconnect-confirm')?.remove();
      extra.mp = {
        connected: false,
        mpUserId: '',
        publicKeyReady: false,
        posReady: false,
        setupError: ''
      };
      featureToast(payload.message || 'Mercado Pago fue desvinculado.');
      const modal = document.getElementById('sol-mp-modal');
      if (modal) renderMpModal();
      requestRerender();
      setTimeout(() => {
        try { F.getMarketplaceStatus(); } catch (_) {}
      }, 350);
      return;
    }
    if (event === 'currentLocation') {
      extra.charge.locating = false;
      extra.charge.locationError = '';
      extra.charge.location = {
        latitude: Number(payload.latitude),
        longitude: Number(payload.longitude),
        accuracyMeters: Number(payload.accuracyMeters || 0)
      };
      const paymentCenter = document.getElementById('sol-payment-center');
      if (paymentCenter && extra.paymentRequest?.id && role() === 'PROFESSIONAL') {
        F.createPaymentRequestQr(
          extra.paymentRequest.id,
          String(extra.charge.location.latitude),
          String(extra.charge.location.longitude)
        );
        return;
      }
      updateChargeLocationUi();
      updateChargeButton();
      return;
    }
    if (event === 'paymentRequestCreated') {
      extra.charge.pending = false;
      closeModal();
      featureToast('Cobro enviado al cliente.');
      try { N.listProfessionalJobs(); } catch (_) {}
      return;
    }
    if (event === 'paymentRequest') {
      extra.paymentRequest = payload;
      renderPaymentCenter();
      return;
    }
    if (event === 'paymentRequestQr') {
      extra.charge.pending = false;
      extra.paymentRequest = payload;
      extra.paymentView = 'PROFESSIONAL';
      renderPaymentCenter();
      return;
    }
    if (event === 'cardSession') {
      // The native Android checkout is opened by FeaturesBridge.
      return;
    }
    if (event === 'cardCheckoutResult') {
      const requestId = payload.requestId || extra.paymentRequest?.requestId || '';
      if (payload.status === 'SUCCESS') {
        featureToast('Mercado Pago procesó la tarjeta. Confirmando pago…');
      } else if (payload.status === 'CANCELLED') {
        featureToast('Pago cancelado.');
      } else {
        featureToast(payload.message || 'No se pudo procesar la tarjeta.', true);
      }
      if (requestId) {
        setTimeout(() => F.getPaymentRequestForService(requestId), 700);
        setTimeout(() => F.getPaymentRequestForService(requestId), 2200);
      }
      return;
    }

    // Legacy 0.8.x payment events
    if (event === 'paymentQr') {
      extra.charge.pending = false;
      extra.payment = payload;
      renderPaymentModal();
      try { N.listProfessionalJobs(); } catch (_) {}
      return;
    }
    if (event === 'paymentStatus') {
      extra.payment = payload;
      renderPaymentModal();
      return;
    }
  };

  const oldNativeEvent = window.solucionaNativeEvent;
  if (typeof oldNativeEvent === 'function') {
    window.solucionaNativeEvent = function(event, ok, payload={}) {
      oldNativeEvent(event, ok, payload);
      if (ok && (event === 'session' || event === 'signIn' || event === 'registerClient' || event === 'registerProfessional')) {
        setTimeout(() => {
          F.getSettings();
          F.getFeatureBootstrap();
          F.loadZones();
          F.getReferralDashboard();
          if (role() === 'PROFESSIONAL' && extra.settings.marketplaceConfigured) F.getMarketplaceStatus();
        }, 120);
      }
    };
  }

  // Email-only verification: remove all SMS requirements from the visible app.
  try {
    if (typeof views !== 'undefined') {
      views.verify = () => `<h1>Verificá tu cuenta</h1>
        <p class="sub">Te enviamos un enlace al correo <b>${esc(currentProfile().email || state.pendingEmail || '')}</b>. Abrilo y volvé a la app.</p>
        <div class="notice">La verificación por correo es el único requisito de acceso. Tu teléfono se usa solamente como dato de contacto.</div>
        <button class="btn primary" onclick="N.refreshSession()">Ya verifiqué mi correo</button>
        <div style="height:8px"></div>
        <button class="btn secondary" onclick="N.resendEmailVerification()">Reenviar correo</button>
        <div style="height:8px"></div>
        <button class="link" style="width:100%" onclick="N.signOut()">Usar otra cuenta</button>`;
    }
  } catch (_) {}

  const oldStatusLabel = (() => { try { return statusLabel; } catch (_) { return null; } })();
  try {
    statusLabel = function(s) {
      const extraLabels = {
        AWAITING_PAYMENT: 'Esperando pago',
        PAID: 'Pagado',
        PAYMENT_REVIEW: 'Pago en revisión'
      };
      return extraLabels[s] || (oldStatusLabel ? oldStatusLabel(s) : s);
    };
  } catch (_) {}

  const oldRequestCard = (() => { try { return requestCard; } catch (_) { return null; } })();
  try {
    requestCard = function(r, pro) {
      if (!oldRequestCard) return '';
      let html = oldRequestCard(r, pro);

      if (pro && r.status === 'IN_PROGRESS') {
        html = html.replace(
          /<button class="btn primary" onclick="N\.updateRequestStatus\('[^']+','COMPLETED'\)">Finalizar<\/button>/,
          `<button class="btn primary" onclick="window.solCobrar('${r.id}')">Enviar cobro al cliente</button>`
        );
      }

      if (r.status === 'AWAITING_PAYMENT') {
        const action = pro
          ? `<button class="btn primary" onclick="window.solOpenPaymentCenter('${r.id}','PROFESSIONAL')">Ver cobro / mostrar QR</button>`
          : `<button class="btn primary" onclick="window.solOpenPaymentCenter('${r.id}','CLIENT')">Pagar ahora</button>`;
        html = html.replace(
          '</div></div>',
          `<div class="notice warn">Pago pendiente dentro de Soluciona.</div>${action}</div></div>`
        );
      }

      if (r.status === 'PAID' || r.status === 'COMPLETED') {
        html = html.replace(
          '</div></div>',
          `<div class="notice">✓ Servicio pagado y confirmado.</div></div></div>`
        );
      }
      return html;
    };
  } catch (_) {}

  window.solCobrar = (requestId) => {
    if (!extra.settings.marketplaceConfigured) {
      featureToast('Primero configurá el backend de Mercado Pago.', true);
      return;
    }
    if (!extra.mp?.connected) {
      openMpModal();
      return;
    }
    openChargeRequestModal(requestId);
  };

  function currentJob(requestId) {
    try {
      const lists = [state.jobs || [], state.requests || [], state.open || []];
      for (const list of lists) {
        const found = list.find(x => x.id === requestId);
        if (found) return found;
      }
    } catch (_) {}
    return {};
  }

  function openChargeRequestModal(requestId) {
    closeModal();
    extra.charge = {
      requestId,
      amount: '',
      note: '',
      location: null,
      locating: false,
      locationError: '',
      pending: false
    };
    const job = currentJob(requestId);
    const address = (() => {
      try { return typeof addressText === 'function' ? addressText(job.address) : ''; }
      catch (_) { return ''; }
    })();

    const wrap = document.createElement('div');
    wrap.className = 'sol-modal-backdrop';
    wrap.id = 'sol-charge-modal';
    wrap.onclick = e => { if (e.target === wrap && !extra.charge.pending) closeModal(); };
    wrap.innerHTML = `<div class="sol-modal">
      <div class="sol-modal-head">
        <h2>Enviar cobro</h2>
        <button class="sol-x" onclick="solCloseFeatureModal()" aria-label="Cerrar">×</button>
      </div>

      <div class="sol-charge-summary">
        <b>Trabajo terminado</b>
        <div class="metric">${escapeHtml(job.description || 'Servicio Soluciona')}</div>
        ${address ? `<div class="metric" style="margin-top:7px">📍 ${escapeHtml(address)}</div>` : ''}
      </div>

      <label class="lbl" for="solChargeAmount">Importe final</label>
      <div class="sol-money-box">
        <span class="sol-money-symbol">$</span>
        <input class="sol-money-input" id="solChargeAmount" type="text" inputmode="decimal"
          autocomplete="off" placeholder="0,00"
          oninput="solChargeAmountChanged(this.value)">
      </div>
      <div class="metric">Pesos argentinos (ARS)</div>

      <label class="lbl" for="solChargeNote" style="margin-top:14px">Detalle del trabajo</label>
      <textarea id="solChargeNote" class="input" rows="3" maxlength="500"
        placeholder="Ej.: cambio de térmica y revisión de tablero"
        oninput="extra.charge.note=this.value"></textarea>

      <div class="notice" style="margin-top:12px">
        El cliente verá el importe en Soluciona y elegirá cómo pagar. El servicio se completa
        solamente cuando el backend confirma el pago.
      </div>

      <div id="sol-charge-error" class="error"></div>
      <button id="sol-charge-btn" class="btn primary" disabled onclick="solConfirmChargeRequest()">Enviar cobro al cliente</button>
      <div style="height:8px"></div>
      <button class="btn secondary" onclick="solCloseFeatureModal()">Cancelar</button>
    </div>`;
    document.body.appendChild(wrap);
    if (job.budgetRequired && job.approvedTotalCents > 0) {
      extra.charge.amount = (job.approvedTotalCents / 100).toFixed(2);
      const field = document.getElementById("solChargeAmount");
      if (field) { field.value = extra.charge.amount; field.readOnly = true; }
    }
    updateChargeButton();
  }

  window.solChargeAmountChanged = value => {
    extra.charge.amount = String(value || '');
    const err = document.getElementById('sol-charge-error');
    if (err) err.textContent = '';
    updateChargeButton();
  };

  function chargeAmountNumber() {
    const raw = String(extra.charge.amount || '').replace(/\s/g, '').replace(',', '.');
    const n = Number(raw);
    return Number.isFinite(n) ? n : 0;
  }

  function updateChargeButton() {
    const btn = document.getElementById('sol-charge-btn');
    if (!btn) return;
    const ready = chargeAmountNumber() > 0 && !extra.charge.pending;
    btn.disabled = !ready;
    btn.textContent = extra.charge.pending ? 'Enviando…' : 'Enviar cobro al cliente';
  }

  window.solConfirmChargeRequest = () => {
    const amount = chargeAmountNumber();
    const err = document.getElementById('sol-charge-error');
    if (!(amount > 0)) {
      if (err) err.textContent = 'Ingresá un importe válido.';
      return;
    }
    extra.charge.pending = true;
    updateChargeButton();
    if (err) err.textContent = '';
    F.createPaymentRequest(
      extra.charge.requestId,
      String(amount),
      extra.charge.note || ''
    );
  };

  window.solOpenPaymentCenter = (requestId, view='') => {
    extra.paymentView = view || (role() === 'PROFESSIONAL' ? 'PROFESSIONAL' : 'CLIENT');
    extra.paymentRequest = null;
    openPaymentCenterShell();
    F.getPaymentRequestForService(requestId);
  };

  function openPaymentCenterShell() {
    closeModal();
    const wrap = document.createElement('div');
    wrap.className = 'sol-modal-backdrop';
    wrap.id = 'sol-payment-center';
    wrap.onclick = e => { if (e.target === wrap) closeModal(); };
    wrap.innerHTML = `<div class="sol-modal">
      <div class="sol-modal-head">
        <h2>${extra.paymentView === 'PROFESSIONAL' ? 'Cobro del servicio' : 'Pagar servicio'}</h2>
        <button class="sol-x" onclick="solCloseFeatureModal()">×</button>
      </div>
      <div id="sol-pay-center-body"><div class="loader"></div><div class="sub" style="text-align:center">Cargando cobro…</div></div>
    </div>`;
    document.body.appendChild(wrap);
  }

  function renderPaymentCenter() {
    let wrap = document.getElementById('sol-payment-center');
    if (!wrap) {
      openPaymentCenterShell();
      wrap = document.getElementById('sol-payment-center');
    }
    const body = document.getElementById('sol-pay-center-body');
    if (!body) return;
    const p = extra.paymentRequest;
    if (!p) {
      body.innerHTML = `<div class="loader"></div><div class="sub" style="text-align:center">Cargando cobro…</div>`;
      return;
    }

    if (p.status === 'PAID') {
      body.innerHTML = `<div class="card" style="text-align:center">
        <div style="font-size:46px">✓</div>
        <h2>Pago confirmado</h2>
        <div class="metric">${escapeHtml(p.amountFormatted || '')}</div>
      </div>
      <button class="btn primary" onclick="solCloseFeatureModal()">Listo</button>`;
      try {
        if (role() === 'PROFESSIONAL') N.listProfessionalJobs();
        else N.listClientRequests();
      } catch (_) {}
      return;
    }

    const note = p.note ? `<div class="metric" style="margin-top:7px">${escapeHtml(p.note)}</div>` : '';
    const attempt = p.attempt || null;

    if ((extra.paymentView === 'PROFESSIONAL' || role() === 'PROFESSIONAL')) {
      const qr = p.qrSvg
        ? `<div class="sol-qr">${p.qrSvg}</div>
           <div class="notice"><b>QR híbrido.</b><br>El cliente puede usar este QR dinámico o el QR estático asociado a tu caja. Ambos corresponden al mismo cobro.</div>`
        : '';
      const activeCard = attempt?.method === 'CARD' && attempt?.status === 'CREATED';
      body.innerHTML = `<div class="card">
          <div class="row"><div class="grow"><b>Total a cobrar</b>${note}</div><b>${escapeHtml(p.amountFormatted||'—')}</b></div>
        </div>
        ${activeCard ? `<div class="notice warn">El cliente está pagando con tarjeta. No generes otro medio hasta que termine.</div>` : ''}
        ${qr}
        ${!p.qrSvg && !activeCard ? `<div id="sol-pay-location" class="sol-location-card">
          <div class="sol-location-head">
            <div class="sol-location-icon">📍</div>
            <div class="sol-location-text"><b>QR presencial</b><div class="metric">Usaremos tu ubicación actual para preparar la caja del servicio.</div></div>
          </div>
        </div>
        <button id="sol-show-qr-btn" class="btn secondary" onclick="solPreparePaymentQr()">Mostrar QR híbrido</button>` : ''}
        <div id="sol-pay-center-error" class="error"></div>`;
      return;
    }

    const cardDisabled = !extra.settings.cardPaymentsConfigured;
    const qrActive = attempt?.method === 'QR' && attempt?.status === 'CREATED';
    body.innerHTML = `<div class="card">
        <div class="row"><div class="grow"><b>Total</b>${note}</div><b>${escapeHtml(p.amountFormatted||'—')}</b></div>
      </div>
      <div class="card">
        <b>Elegí cómo pagar</b>
        <div class="metric" style="margin-bottom:12px">El pago queda asociado a este servicio y Soluciona confirma el resultado automáticamente.</div>

        <button class="btn primary" ${cardDisabled||qrActive?'disabled':''} onclick="solPayByCard()">
          💳 Crédito / débito
        </button>
        <div class="metric" style="margin:7px 2px 13px">
          ${cardDisabled ? 'Falta configurar la Public Key de Mercado Pago en esta compilación.' : 'Pago seguro dentro de la app con Mercado Pago.'}
        </div>

        <div class="notice ${qrActive?'':'warn'}">
          ${qrActive
            ? 'Hay un QR presencial activo. Pagalo desde otra billetera/dispositivo o pedile al profesional que te lo muestre.'
            : 'También podés pagar con el QR híbrido que te muestre el profesional.'}
        </div>
      </div>
      <div id="sol-pay-center-error" class="error"></div>`;
  }

  window.solPayByCard = () => {
    const p = extra.paymentRequest;
    if (!p?.id) return;
    const err = document.getElementById('sol-pay-center-error');
    if (err) err.textContent = '';
    F.startCardPayment(p.id);
  };

  window.solPreparePaymentQr = () => {
    const p = extra.paymentRequest;
    if (!p?.id) return;
    const btn = document.getElementById('sol-show-qr-btn');
    if (btn) {
      btn.disabled = true;
      btn.textContent = 'Obteniendo ubicación…';
    }
    extra.charge.location = null;
    extra.charge.locationError = '';
    extra.charge.locating = true;
    try { F.requestCurrentLocation(); }
    catch (_) {
      extra.charge.locating = false;
      const err = document.getElementById('sol-pay-center-error');
      if (err) err.textContent = 'No pudimos iniciar la ubicación.';
    }
  };

  function updateChargeLocationUi() {
    const el = document.getElementById('sol-pay-location');
    if (!el) return;
    if (extra.charge.locationError) {
      el.innerHTML = `<div class="sol-location-head"><div class="sol-location-icon">!</div><div class="sol-location-text"><b class="sol-location-warn">Ubicación no disponible</b><div class="metric">${escapeHtml(extra.charge.locationError)}</div></div></div>`;
    }
  }

  function themeButtons() {
    const t = extra.settings.theme || 'system';
    return `<div class="sol-feature-grid">
      <button class="sol-choice ${t==='light'?'on':''}" onclick="SolucionaFeatures.setTheme('light')">☀️ Claro</button>
      <button class="sol-choice ${t==='dark'?'on':''}" onclick="SolucionaFeatures.setTheme('dark')">🌙 Oscuro</button>
      <button class="sol-choice ${t==='system'?'on':''}" onclick="SolucionaFeatures.setTheme('system')">⚙️ Sistema</button>
    </div>`;
  }

  function referralSummary() {
    const r = extra.referrals;
    if (!r) return `<div class="metric">Cargando programa de referidos…</div>`;
    return `<div class="sol-code">${escapeHtml(r.code || '—')}</div>
      <div class="sol-kpi">
        <div><b>${Number(r.invited||0)}</b><span class="metric">Invitados</span></div>
        <div><b>${Number(r.qualified||0)}</b><span class="metric">Válidos</span></div>
        <div><b>${Number(r.benefitsAvailable||0)}</b><span class="metric">Beneficios</span></div>
      </div>`;
  }

  function workZonesCard() {
    if (role() !== 'PROFESSIONAL') return '';
    const zs = extra.zones || [];
    const selected = new Set(extra.bootstrap.zones || []);
    const primary = extra.bootstrap.primaryZone || [...selected][0] || '';
    if (!zs.length) {
      return `<div class="card"><b>Zonas de trabajo</b><div class="metric">Todavía no hay zonas cargadas en Firestore. Ejecutá el seed de zonas.</div></div>`;
    }
    return `<div class="card">
      <b>Zonas de trabajo</b>
      <div class="metric">Elegí una principal y todas las localidades donde aceptás pedidos.</div>
      <label class="lbl">Zona principal</label>
      <select id="solPrimaryZone">${zs.map(z=>`<option value="${escapeHtml(z.id)}" ${z.id===primary?'selected':''}>${escapeHtml(z.name)}</option>`).join('')}</select>
      <div class="sol-zone-list">${zs.map(z=>`<label class="sol-zone"><input type="checkbox" class="sol-zone-check" value="${escapeHtml(z.id)}" ${selected.has(z.id)||z.id===primary?'checked':''}><span>${escapeHtml(z.name)} <small class="metric">${escapeHtml(z.province||'')}</small></span></label>`).join('')}</div>
      <button class="btn secondary" onclick="window.solSaveZones()">Guardar zonas</button>
    </div>`;
  }

  window.solSaveZones = () => {
    const primary = document.getElementById('solPrimaryZone')?.value || '';
    const zones = [...document.querySelectorAll('.sol-zone-check:checked')].map(x=>x.value);
    if (!zones.includes(primary)) zones.unshift(primary);
    F.saveWorkZones(JSON.stringify({primaryZone: primary, zones}));
  };

  function enhancedAccountView() {
    const p = currentProfile();
    return `<h2>Mi cuenta</h2>
      <div class="card">
        <b>${escapeHtml(p.displayName||'')}</b>
        <div class="metric">${escapeHtml(p.email||'')}</div>
        <div class="metric">📞 ${escapeHtml(p.phone||'')}</div>
        <div class="metric">📍 ${escapeHtml(typeof addressText==='function'?addressText(p.address):'')}</div>
        <div style="margin-top:8px"><span class="badge ${p.emailVerified?'ok':'warn'}">${p.emailVerified?'✓ Correo verificado':'Correo pendiente'}</span></div>
      </div>

      <div class="card">
        <b>Seguridad</b>
        <div class="metric">La huella es opcional y protege el acceso a la sesión guardada en este dispositivo.</div>
        <div class="sol-switch" style="margin-top:12px">
          <span>${extra.settings.biometricAvailable ? 'Ingreso con huella / biometría' : 'Biometría no disponible en este dispositivo'}</span>
          <input type="checkbox" ${extra.settings.biometricEnabled?'checked':''} ${extra.settings.biometricAvailable?'':'disabled'}
            onchange="SolucionaFeatures.setBiometricEnabled(this.checked)">
        </div>
      </div>

      <div class="card"><b>Apariencia</b><div class="metric" style="margin-bottom:10px">Elegí cómo querés ver Soluciona.</div>${themeButtons()}</div>

      ${workZonesCard()}

      <div class="card">
        <b>Referidos</b>
        <div class="metric" style="margin-bottom:10px">Invitá personas. Los premios se activan solo cuando el referido genera actividad válida.</div>
        ${referralSummary()}
        <button class="btn secondary" onclick="window.solOpenReferrals()">Ver beneficios y compartir</button>
      </div>

      ${role()==='PROFESSIONAL' ? `<div class="card">
        <b>Cobros con Mercado Pago</b>
        <div class="metric">${extra.mp?.connected ? '✓ Cuenta vinculada para cobrar y dividir la comisión automáticamente.' : 'Vinculá tu cuenta para generar QR de cobro.'}</div>
        <div style="height:10px"></div>
        <button class="btn ${extra.mp?.connected?'secondary':'primary'}" onclick="window.solOpenMp()">${extra.mp?.connected?'Administrar Mercado Pago':'Conectar Mercado Pago'}</button>
      </div>` : ''}

      ${p.privacyOptionsRequired?`<button class="btn secondary" onclick="N.showPrivacyOptions()">Opciones de privacidad de anuncios</button><div style="height:8px"></div>`:''}
      <button class="btn dangerBtn" onclick="logout()">Cerrar sesión</button>
      <div class="footerlinks"><button onclick="setScreen('privacy')">Política de privacidad</button> · <button onclick="setScreen('terms')">Términos</button><br>
      <a href="mailto:infosoluciona2026@gmail.com" style="color:var(--blue);text-decoration:none">infosoluciona2026@gmail.com</a></div>`;
  }

  try {
    if (typeof views !== 'undefined') views.account = () => enhancedAccountView();
  } catch (_) {}

  function escapeHtml(v) {
    return String(v ?? '').replace(/[&<>"']/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
  }

  function closeModal() {
    document.querySelector('.sol-modal-backdrop')?.remove();
  }
  window.solCloseFeatureModal = closeModal;

  window.solOpenReferrals = () => {
    openReferralModal();
    F.getReferralDashboard();
  };

  function openReferralModal() {
    closeModal();
    const wrap = document.createElement('div');
    wrap.className = 'sol-modal-backdrop';
    wrap.id = 'sol-ref-modal';
    wrap.onclick = e => { if (e.target === wrap) closeModal(); };
    wrap.innerHTML = `<div class="sol-modal"><div class="sol-modal-head"><h2>Referidos</h2><button class="sol-x" onclick="solCloseFeatureModal()">×</button></div><div id="sol-ref-body"></div></div>`;
    document.body.appendChild(wrap);
    renderReferralModal();
  }

  function renderReferralModal() {
    const body = document.getElementById('sol-ref-body');
    if (!body) return;
    const r = extra.referrals;
    if (!r) {
      body.innerHTML = `<div class="loader"></div><div class="sub" style="text-align:center">Cargando…</div>`;
      return;
    }
    const isPro = role() === 'PROFESSIONAL';
    body.innerHTML = `${referralSummary()}
      <div class="card">
        <b>${isPro?'Beneficios para profesionales':'Beneficios para clientes'}</b>
        ${isPro
          ? `<p class="sub">Profesional referido: comisión promocional del 5% en sus primeros 3 trabajos pagados. Quien invita recibe 2 trabajos al 5% cuando el referido completa su primer cobro válido.</p>`
          : `<p class="sub">Cliente referido y quien invita reciben una Solicitud Prioritaria cuando el referido completa su primer servicio pagado. El beneficio no es dinero ni es transferible.</p>`}
        <p class="metric">Los beneficios se validan en servidor para evitar cuentas o trabajos ficticios.</p>
      </div>
      <button class="btn primary" onclick="navigator.share?navigator.share({title:'Soluciona',text:'Mi código de Soluciona es ${escapeHtml(r.code||'')}' }):navigator.clipboard?.writeText('${escapeHtml(r.code||'')}')">Compartir mi código</button>
      ${r.referredBy ? `<div class="notice">Ingresaste referido por <b>${escapeHtml(r.referredBy)}</b>.</div>` :
      `<label class="lbl">¿Te invitó alguien?</label><div class="row"><input class="input grow" id="solRefCode" placeholder="SOL-XXXXXXXX"><button class="btn secondary" style="width:auto" onclick="SolucionaFeatures.applyReferralCode(document.getElementById('solRefCode').value)">Aplicar</button></div>`}`;
  }

  window.solOpenMp = openMpModal;
  function openMpModal() {
    closeModal();
    const wrap = document.createElement('div');
    wrap.className = 'sol-modal-backdrop';
    wrap.id = 'sol-mp-modal';
    wrap.onclick = e => { if (e.target === wrap) closeModal(); };
    wrap.innerHTML = `<div class="sol-modal"><div class="sol-modal-head"><h2>Mercado Pago</h2><button class="sol-x" onclick="solCloseFeatureModal()">×</button></div><div id="sol-mp-body"></div></div>`;
    document.body.appendChild(wrap);
    renderMpModal();
    if (extra.settings.marketplaceConfigured) F.getMarketplaceStatus();
  }

  window.solConfirmDisconnectMercadoPago = function() {
    document.getElementById('sol-mp-disconnect-confirm')?.remove();

    const wrap = document.createElement('div');
    wrap.id = 'sol-mp-disconnect-confirm';
    wrap.className = 'sol-confirm-backdrop';
    wrap.onclick = e => {
      if (e.target === wrap) window.solCancelDisconnectMercadoPago();
    };
    wrap.innerHTML = `<div class="sol-confirm-card" role="dialog" aria-modal="true" aria-labelledby="sol-mp-disconnect-title">
      <div class="sol-confirm-icon">↔</div>
      <h3 id="sol-mp-disconnect-title">Desvincular Mercado Pago</h3>
      <p>
        Soluciona dejará de usar esta cuenta para nuevos cobros.
        Para volver a cobrar vas a tener que autorizar Mercado Pago nuevamente.
      </p>
      <div class="notice warn" style="margin-top:14px">
        Los pagos ya confirmados no se modifican.
      </div>
      <div id="sol-mp-disconnect-error" class="sol-confirm-error"></div>
      <div class="sol-confirm-actions">
        <button id="sol-mp-disconnect-cancel-btn" class="btn secondary" onclick="solCancelDisconnectMercadoPago()">Cancelar</button>
        <button id="sol-mp-disconnect-confirm-btn" class="btn dangerBtn" onclick="solExecuteDisconnectMercadoPago()">Sí, desvincular</button>
      </div>
    </div>`;
    document.body.appendChild(wrap);
  };

  window.solCancelDisconnectMercadoPago = function() {
    document.getElementById('sol-mp-disconnect-confirm')?.remove();
  };

  window.solExecuteDisconnectMercadoPago = function() {
    const btn = document.getElementById('sol-mp-disconnect-confirm-btn');
    const cancelBtn = document.getElementById('sol-mp-disconnect-cancel-btn');
    const errorBox = document.getElementById('sol-mp-disconnect-error');

    if (errorBox) {
      errorBox.textContent = '';
      errorBox.style.display = 'none';
    }
    if (btn) {
      btn.disabled = true;
      btn.textContent = 'Desvinculando…';
    }
    if (cancelBtn) cancelBtn.disabled = true;

    try {
      F.disconnectMercadoPago();
    } catch (_) {
      if (btn) {
        btn.disabled = false;
        btn.textContent = 'Sí, desvincular';
      }
      if (cancelBtn) cancelBtn.disabled = false;
      if (errorBox) {
        errorBox.textContent = 'No pudimos iniciar la desvinculación. Cerrá y volvé a abrir Soluciona.';
        errorBox.style.display = 'block';
      }
    }
  };

  function renderMpModal() {
    const body = document.getElementById('sol-mp-body');
    if (!body) return;
    if (!extra.settings.marketplaceConfigured) {
      body.innerHTML = `<div class="notice warn">Falta configurar MARKETPLACE_API_URL. La app está lista, pero el backend de pagos aún no fue desplegado.</div>`;
      return;
    }
    const mp = extra.mp;
    if (!mp) {
      body.innerHTML = `<div class="loader"></div><div class="sub" style="text-align:center">Consultando Mercado Pago…</div>`;
      return;
    }
    body.innerHTML = mp.connected
      ? `<div class="card">
           <div class="row">
             <div class="grow">
               <b>✓ Mercado Pago conectado</b>
               <div class="metric">Cuenta: ${escapeHtml(mp.mpUserId||'')}</div>
               <div class="metric">QR/POS: ${mp.posReady?'Configurado':'Se configurará en el primer cobro con tu ubicación actual'}</div>
             </div>
             <span class="badge ok">Activo</span>
           </div>
         </div>
         <div class="notice">
           La conexión se usa para recibir pagos de tus servicios. Soluciona nunca muestra tus credenciales de Mercado Pago en la app.
         </div>
         <button
           id="sol-mp-disconnect-btn"
           class="btn dangerBtn"
           onclick="solConfirmDisconnectMercadoPago()">
           Desvincular Mercado Pago
         </button>`
      : `<div class="card"><b>Conectá tu cuenta</b><p class="sub">Mercado Pago te pedirá autorización. Soluciona nunca recibe tu contraseña.</p><button class="btn primary" onclick="SolucionaFeatures.connectMercadoPago()">Conectar Mercado Pago</button></div>`;
  }

  function renderPaymentModal() {
    closeModal();
    const p = extra.payment || {};
    const wrap = document.createElement('div');
    wrap.className = 'sol-modal-backdrop';
    wrap.id = 'sol-pay-modal';
    wrap.onclick = e => { if (e.target === wrap) closeModal(); };
    wrap.innerHTML = `<div class="sol-modal"><div class="sol-modal-head"><h2>Cobro del servicio</h2><button class="sol-x" onclick="solCloseFeatureModal()">×</button></div>
      <div class="card">
        <div class="row"><div class="grow"><b>Total</b><div class="metric">Comisión Soluciona: ${escapeHtml(p.marketplaceFeeFormatted||'—')}</div></div><b>${escapeHtml(p.amountFormatted||'—')}</b></div>
      </div>
      <div class="sol-qr">${p.qrSvg || ''}</div>
      <div class="notice"><b>QR híbrido de Mercado Pago.</b><br>
        El cliente puede pagar con este QR dinámico o con el QR estático asociado a la caja del profesional.
        Ambos corresponden a la misma order y, cuando uno se paga, el otro deja de ser utilizable.
        Soluciona confirma el pago únicamente mediante el webhook de Mercado Pago.
      </div>
      <div class="metric">Estado: ${escapeHtml(p.status||'PENDING')}</div>
    </div>`;
    document.body.appendChild(wrap);
  }

  function fillRegistrationZone() {
    const sel = document.getElementById('proZone');
    if (!sel) return;
    const zones = registrationZones();
    const current = sel.value;
    if (!zones.length) {
      // Important: do not trigger another Firestore request from DOM mutations.
      // An empty collection used to create a load -> render -> mutation -> load loop.
      sel.innerHTML = '<option value="">No hay zonas cargadas</option>';
      sel.disabled = true;
      return;
    }
    sel.disabled = false;
    sel.innerHTML = zones.map(z => `<option value="${escapeHtml(z.id)}">${escapeHtml(z.name)}</option>`).join('');
    if (current && zones.some(z => z.id === current)) sel.value = current;
  }

  const observer = new MutationObserver(() => {
    const sel = document.getElementById('proZone');
    if (sel) {
      const zones = registrationZones();
      if (zones.length && (!sel.options || sel.options.length === 0 || sel.disabled)) {
        fillRegistrationZone();
      } else if (!zones.length && !zonesLoadedOnce && !zonesLoadInFlight) {
        loadZonesOnce();
      } else if (!zones.length && zonesLoadedOnce && (!sel.options || sel.options.length === 0)) {
        fillRegistrationZone();
      }
    }
    // Keep bottom nav hidden during email verification.
    try {
      const nav = document.getElementById('nav');
      if (nav && typeof state !== 'undefined' && state.screen === 'verify') nav.className = 'nav';
    } catch (_) {}
  });
  observer.observe(document.body, {subtree:true, childList:true});

  applyTheme(extra.settings.theme);
  // Only unauthenticated-safe calls run immediately. Authenticated modules are
  // loaded by the native-event wrapper after session/signIn succeeds.
  F.getSettings();
  loadZonesOnce();
  setTimeout(requestRerender, 0);
})();