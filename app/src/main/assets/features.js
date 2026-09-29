(() => {
  if (window.__soluciona080Loaded) return;
  window.__soluciona080Loaded = true;

  const F = window.SolucionaFeatures;
  if (!F || !F.isAvailable || !F.isAvailable()) return;

  const extra = {
    settings: { theme: 'system', biometricAvailable: false, biometricEnabled: false, marketplaceConfigured: false },
    bootstrap: { role: '', primaryZone: '', zones: [] },
    zones: [],
    referrals: null,
    mp: null,
    payment: null
  };

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

  function requestRerender() {
    try { if (typeof render === 'function') render(); } catch (_) {}
  }

  window.solucionaFeaturesEvent = (event, ok, payload={}) => {
    if (!ok) {
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
      extra.zones = payload.zones || [];
      fillRegistrationZone();
      requestRerender();
      return;
    }
    if (event === 'bootstrap') {
      extra.bootstrap = {...extra.bootstrap, ...payload};
      extra.settings.theme = payload.theme || extra.settings.theme;
      extra.settings.biometricAvailable = !!payload.biometricAvailable;
      extra.settings.biometricEnabled = !!payload.biometricEnabled;
      extra.settings.marketplaceConfigured = !!payload.marketplaceConfigured;
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
    if (event === 'paymentQr') {
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
          `<button class="btn primary" onclick="window.solCobrar('${r.id}')">Finalizar y cobrar con QR</button>`
        );
      }
      if (r.status === 'AWAITING_PAYMENT') {
        html = html.replace('</div></div>', `<div class="notice warn">Pago pendiente por Mercado Pago.</div></div></div>`);
      }
      if (r.status === 'PAID' || r.status === 'COMPLETED') {
        html = html.replace('</div></div>', `<div class="notice">✓ Pago confirmado.</div></div></div>`);
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
    const amount = prompt('Importe final del servicio (ARS)');
    if (!amount) return;
    F.createPaymentQr(requestId, amount);
  };

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
      ? `<div class="card"><b>✓ Mercado Pago conectado</b><div class="metric">Cuenta: ${escapeHtml(mp.mpUserId||'')}</div><div class="metric">QR/POS: ${mp.posReady?'Configurado':'Pendiente de configuración'}</div></div>
         <div class="notice">Los cobros QR se crean con el token OAuth del profesional. La comisión de Soluciona se calcula en el servidor, nunca en el APK.</div>`
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
      <div class="notice">Mostrá este QR al cliente. El servicio se marca como pagado únicamente cuando el webhook firmado de Mercado Pago confirma la order.</div>
      <div class="metric">Estado: ${escapeHtml(p.status||'PENDING')}</div>
    </div>`;
    document.body.appendChild(wrap);
  }

  function fillRegistrationZone() {
    const sel = document.getElementById('proZone');
    if (!sel || !extra.zones.length) return;
    const current = sel.value;
    sel.innerHTML = extra.zones.map(z => `<option value="${escapeHtml(z.id)}">${escapeHtml(z.name)}</option>`).join('');
    if (current && extra.zones.some(z => z.id === current)) sel.value = current;
  }

  const observer = new MutationObserver(() => {
    const sel = document.getElementById('proZone');
    if (sel) {
      if ((!sel.options || sel.options.length === 0) && extra.zones.length) fillRegistrationZone();
      if ((!sel.options || sel.options.length === 0) && !extra.zones.length) F.loadZones();
    }
    // Keep bottom nav hidden during email verification.
    try {
      const nav = document.getElementById('nav');
      if (nav && typeof state !== 'undefined' && state.screen === 'verify') nav.className = 'nav';
    } catch (_) {}
  });
  observer.observe(document.body, {subtree:true, childList:true});

  applyTheme(extra.settings.theme);
  F.getSettings();
  F.getFeatureBootstrap();
  F.loadZones();
  setTimeout(requestRerender, 0);
  setTimeout(() => {
    F.getReferralDashboard();
    if (role() === 'PROFESSIONAL' && extra.settings.marketplaceConfigured) F.getMarketplaceStatus();
  }, 350);
})();