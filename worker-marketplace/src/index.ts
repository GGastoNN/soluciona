import { createRemoteJWKSet, jwtVerify } from "jose";
import QRCode from "qrcode";

interface Env {
  DB: D1Database;
  FIREBASE_PROJECT_ID: string;
  FIREBASE_CLIENT_EMAIL: string;
  FIREBASE_PRIVATE_KEY: string;
  MP_CLIENT_ID: string;
  MP_CLIENT_SECRET: string;
  MP_REDIRECT_URI: string;
  MP_WEBHOOK_SECRET: string;
  TOKEN_ENCRYPTION_KEY: string;
  COMMISSION_BPS?: string;
  REFERRAL_PRO_BPS?: string;
}

type Identity = { uid: string; email?: string };
type SellerRow = {
  uid: string;
  mp_user_id: string;
  access_token_enc: string;
  refresh_token_enc: string | null;
  expires_at: number | null;
  store_id: string | null;
  pos_id: string | null;
  external_pos_id: string | null;
  setup_error: string | null;
};

const jwksByProject = new Map<string, ReturnType<typeof createRemoteJWKSet>>();
let googleTokenCache: { token: string; expiresAt: number } | null = null;

export default {
  async fetch(request: Request, env: Env): Promise<Response> {
    try {
      const url = new URL(request.url);

      if (request.method === "GET" && url.pathname === "/health") {
        return json({ ok: true, service: "soluciona-marketplace", version: "0.9.1" });
      }

      if (url.pathname === "/v1/mp/callback" && request.method === "GET") {
        return handleMpCallback(url, env);
      }

      if (url.pathname === "/v1/mp/webhook" && request.method === "POST") {
        return handleMpWebhook(request, url, env);
      }

      const identity = await authenticate(request, env);

      if (url.pathname === "/v1/referrals/dashboard" && request.method === "GET") {
        return referralDashboard(identity, env);
      }
      if (url.pathname === "/v1/referrals/apply" && request.method === "POST") {
        return referralApply(identity, request, env);
      }

      if (url.pathname === "/v1/mp/connect" && request.method === "POST") {
        return marketplaceConnect(identity, env);
      }
      if (url.pathname === "/v1/mp/status" && request.method === "GET") {
        return marketplaceStatus(identity, env);
      }

      if (url.pathname === "/v1/payment-requests" && request.method === "POST") {
        return createPlatformPaymentRequest(identity, request, env);
      }
      if (url.pathname.startsWith("/v1/payment-requests/by-service/") && request.method === "GET") {
        const requestId = decodeURIComponent(url.pathname.slice("/v1/payment-requests/by-service/".length));
        return getPlatformPaymentRequest(identity, requestId, env);
      }
      if (url.pathname.startsWith("/v1/payment-requests/") && url.pathname.endsWith("/card-session") && request.method === "POST") {
        const paymentRequestId = decodeURIComponent(
          url.pathname.slice("/v1/payment-requests/".length, -"/card-session".length)
        );
        return createCardSession(identity, paymentRequestId, env);
      }
      if (url.pathname.startsWith("/v1/payment-requests/") && url.pathname.endsWith("/qr") && request.method === "POST") {
        const paymentRequestId = decodeURIComponent(
          url.pathname.slice("/v1/payment-requests/".length, -"/qr".length)
        );
        return createPaymentRequestQr(identity, paymentRequestId, request, env);
      }

      // Legacy 0.8.x routes remain temporarily for backward compatibility.
      if (url.pathname === "/v1/payments/qr" && request.method === "POST") {
        return createPaymentQr(identity, request, env);
      }
      if (url.pathname.startsWith("/v1/payments/by-request/") && request.method === "GET") {
        const requestId = decodeURIComponent(url.pathname.slice("/v1/payments/by-request/".length));
        return paymentByRequest(identity, requestId, env);
      }

      return json({ error: "NOT_FOUND", message: "Ruta no encontrada." }, 404);
    } catch (error: any) {
      console.error(error);
      const status = Number(error?.status || 500);
      return json({
        error: error?.code || "INTERNAL_ERROR",
        message: status >= 500 ? "Error interno del servidor." : String(error?.message || "Error.")
      }, status);
    }
  }
};

async function authenticate(request: Request, env: Env): Promise<Identity> {
  const header = request.headers.get("authorization") || "";
  if (!header.startsWith("Bearer ")) throw httpError(401, "UNAUTHORIZED", "Falta la sesión.");
  const token = header.slice(7).trim();
  let jwks = jwksByProject.get(env.FIREBASE_PROJECT_ID);
  if (!jwks) {
    jwks = createRemoteJWKSet(new URL("https://www.googleapis.com/service_accounts/v1/jwk/securetoken@system.gserviceaccount.com"));
    jwksByProject.set(env.FIREBASE_PROJECT_ID, jwks);
  }
  const result = await jwtVerify(token, jwks, {
    issuer: `https://securetoken.google.com/${env.FIREBASE_PROJECT_ID}`,
    audience: env.FIREBASE_PROJECT_ID
  });
  const uid = String(result.payload.sub || "");
  if (!uid) throw httpError(401, "UNAUTHORIZED", "Sesión inválida.");
  return { uid, email: typeof result.payload.email === "string" ? result.payload.email : undefined };
}

async function marketplaceConnect(identity: Identity, env: Env): Promise<Response> {
  const role = await getUserRole(identity.uid, env);
  if (role !== "PROFESSIONAL") {
    throw httpError(403, "PROFESSIONAL_ONLY", "Solo los profesionales pueden vincular Mercado Pago.");
  }

  const state = crypto.randomUUID().replaceAll("-", "") + crypto.randomUUID().replaceAll("-", "");
  const expiresAt = Date.now() + 10 * 60 * 1000;
  await env.DB.prepare(
    "INSERT INTO oauth_states(state,uid,expires_at) VALUES(?,?,?)"
  ).bind(state, identity.uid, expiresAt).run();

  const authUrl = new URL("https://auth.mercadopago.com.ar/authorization");
  authUrl.searchParams.set("client_id", env.MP_CLIENT_ID);
  authUrl.searchParams.set("response_type", "code");
  authUrl.searchParams.set("platform_id", "mp");
  authUrl.searchParams.set("redirect_uri", env.MP_REDIRECT_URI);
  authUrl.searchParams.set("state", state);

  return json({ authUrl: authUrl.toString(), expiresAt });
}

async function handleMpCallback(url: URL, env: Env): Promise<Response> {
  const code = url.searchParams.get("code") || "";
  const state = url.searchParams.get("state") || "";
  if (!code || !state) return callbackHtml(false, "Faltan parámetros de autorización.");

  const row = await env.DB.prepare(
    "SELECT uid,expires_at FROM oauth_states WHERE state=?"
  ).bind(state).first<{ uid: string; expires_at: number }>();

  if (!row || row.expires_at < Date.now()) {
    return callbackHtml(false, "La autorización venció. Volvé a Soluciona e intentá nuevamente.");
  }
  await env.DB.prepare("DELETE FROM oauth_states WHERE state=?").bind(state).run();

  try {
    const tokenData = await mpOAuthExchange(env, {
      grant_type: "authorization_code",
      code,
      redirect_uri: env.MP_REDIRECT_URI
    });

    const accessEnc = await encryptSecret(String(tokenData.access_token), env);
    const refreshToken = tokenData.refresh_token ? String(tokenData.refresh_token) : "";
    const refreshEnc = refreshToken ? await encryptSecret(refreshToken, env) : null;
    const expiresAt = tokenData.expires_in ? Date.now() + Number(tokenData.expires_in) * 1000 : null;
    const mpUserId = String(tokenData.user_id || "");

    if (!mpUserId) throw new Error("Mercado Pago no devolvió user_id.");

    await env.DB.prepare(`
      INSERT INTO sellers(uid,mp_user_id,access_token_enc,refresh_token_enc,expires_at,connected_at,updated_at)
      VALUES(?,?,?,?,?,?,?)
      ON CONFLICT(uid) DO UPDATE SET
        mp_user_id=excluded.mp_user_id,
        access_token_enc=excluded.access_token_enc,
        refresh_token_enc=excluded.refresh_token_enc,
        expires_at=excluded.expires_at,
        updated_at=excluded.updated_at
    `).bind(row.uid, mpUserId, accessEnc, refreshEnc, expiresAt, Date.now(), Date.now()).run();

    // Mobile professionals do not have a fixed point of sale. Store/POS setup
    // is intentionally deferred until the first QR, when the app can provide the
    // real current service location.
    await env.DB.prepare("UPDATE sellers SET setup_error=NULL,updated_at=? WHERE uid=?")
      .bind(Date.now(), row.uid).run();
    return callbackHtml(true, "Mercado Pago quedó conectado con Soluciona.");
  } catch (e: any) {
    console.error("OAuth callback failed", e);
    return callbackHtml(false, "No se pudo completar la conexión con Mercado Pago.");
  }
}

async function marketplaceStatus(identity: Identity, env: Env): Promise<Response> {
  const seller = await getSeller(identity.uid, env);
  return json({
    connected: !!seller,
    mpUserId: seller?.mp_user_id || "",
    posReady: !!seller?.external_pos_id,
    setupError: seller?.setup_error || ""
  });
}

async function syncStoreAndPosForService(
  uid: string,
  serviceAddress: any,
  latitude: number,
  longitude: number,
  env: Env
): Promise<void> {
  let seller = await getSeller(uid, env);
  if (!seller) throw new Error("Seller no encontrado.");
  const accessToken = await validSellerAccessToken(seller, env);

  try {
    const streetName = String(serviceAddress?.street || "").trim();
    const streetNumber = String(serviceAddress?.number || "").trim();
    const cityName = String(serviceAddress?.city || "").trim();
    const stateName = String(serviceAddress?.province || "").trim();

    if (!streetName || !streetNumber || !cityName || !stateName) {
      throw new Error("La dirección del servicio está incompleta. Revisá calle, número, ciudad y provincia.");
    }
    if (!Number.isFinite(latitude) || latitude < -90 || latitude > 90
        || !Number.isFinite(longitude) || longitude < -180 || longitude > 180) {
      throw new Error("La ubicación actual del servicio no es válida.");
    }

    const externalStoreId = `SOLSTORE${shortId(uid)}`;
    const location = {
      street_name: streetName,
      street_number: streetNumber,
      city_name: cityName,
      state_name: stateName,
      latitude,
      longitude,
      reference: "Servicio a domicilio Soluciona"
    };

    let storeId = seller.store_id;
    if (!storeId) {
      const storeResp = await mpFetch(
        `https://api.mercadopago.com/users/${encodeURIComponent(seller.mp_user_id)}/stores`,
        accessToken,
        {
          method: "POST",
          body: {
            name: "Soluciona Movil",
            external_id: externalStoreId,
            location
          }
        }
      );
      storeId = String(storeResp.id || "");
      if (!storeId) throw new Error("No se pudo crear la sucursal móvil de Mercado Pago.");
      await env.DB.prepare("UPDATE sellers SET store_id=?,updated_at=? WHERE uid=?")
        .bind(storeId, Date.now(), uid).run();
      seller = (await getSeller(uid, env))!;
    } else {
      // A professional may be working at a different client's address on every
      // service. Keep the same Store/POS identity but synchronize the Store with
      // the real current point of service before creating the QR.
      await mpFetch(
        `https://api.mercadopago.com/users/${encodeURIComponent(seller.mp_user_id)}/stores/${encodeURIComponent(storeId)}`,
        accessToken,
        {
          method: "PUT",
          body: {
            name: "Soluciona Movil",
            external_id: externalStoreId,
            location
          }
        }
      );
    }

    seller = (await getSeller(uid, env))!;
    if (!seller.external_pos_id) {
      const externalPosId = `SOLPOS${shortId(uid)}`;
      const posResp = await mpFetch(
        "https://api.mercadopago.com/v2/pos",
        accessToken,
        {
          method: "POST",
          idempotencyKey: `pos-${uid}`,
          body: {
            name: `Soluciona ${shortId(uid)}`,
            store_id: storeId,
            external_id: externalPosId,
            config: { qr: { operating_mode: "pdv" } }
          }
        }
      );
      await env.DB.prepare(
        "UPDATE sellers SET pos_id=?,external_pos_id=?,setup_error=NULL,updated_at=? WHERE uid=?"
      ).bind(String(posResp.id || ""), externalPosId, Date.now(), uid).run();
    } else {
      await env.DB.prepare("UPDATE sellers SET setup_error=NULL,updated_at=? WHERE uid=?")
        .bind(Date.now(), uid).run();
    }
  } catch (e: any) {
    const message = String(e?.message || "Error configurando sucursal/caja.");
    await env.DB.prepare("UPDATE sellers SET setup_error=?,updated_at=? WHERE uid=?")
      .bind(message.slice(0, 500), Date.now(), uid).run();
    console.error("Store/POS setup error", message);
  }
}


type PaymentRequestRow = {
  id: string;
  request_id: string;
  client_uid: string;
  professional_uid: string;
  amount_cents: number;
  note: string;
  status: string;
  created_at: number;
  updated_at: number;
};

type PaymentAttemptRow = {
  id: string;
  payment_request_id: string;
  request_id: string;
  client_uid: string;
  professional_uid: string;
  method: string;
  mp_order_id: string;
  amount_cents: number;
  marketplace_fee_cents: number;
  commission_bps: number;
  status: string;
  qr_data: string | null;
  idempotency_key: string;
  created_at: number;
  updated_at: number;
};

async function createPlatformPaymentRequest(
  identity: Identity,
  request: Request,
  env: Env
): Promise<Response> {
  const body = await parseJson(request);
  const requestId = String(body.requestId || "").trim();
  const amount = parseMoney(String(body.amount || ""));
  const note = String(body.note || "").trim().slice(0, 500);

  if (!requestId || amount <= 0) {
    throw httpError(400, "INVALID_PAYMENT_REQUEST", "Ingresá un importe válido.");
  }
  const role = await getUserRole(identity.uid, env);
  if (role !== "PROFESSIONAL") {
    throw httpError(403, "PROFESSIONAL_ONLY", "Solo el profesional puede enviar el cobro.");
  }

  const service = await firestoreGet(`service_requests/${requestId}`, env);
  if (!service) throw httpError(404, "REQUEST_NOT_FOUND", "No encontramos el servicio.");
  if (String(service.professionalUid || "") !== identity.uid) {
    throw httpError(403, "NOT_ASSIGNED", "No estás asignado a este servicio.");
  }
  const serviceStatus = String(service.status || "");
  if (!["IN_PROGRESS", "AWAITING_PAYMENT"].includes(serviceStatus)) {
    throw httpError(409, "INVALID_STATUS", "El trabajo debe estar en curso para enviar el cobro.");
  }

  const seller = await getSeller(identity.uid, env);
  if (!seller) {
    throw httpError(409, "MP_NOT_CONNECTED", "Conectá Mercado Pago antes de enviar el cobro.");
  }

  const existing = await env.DB.prepare(
    "SELECT * FROM payment_requests WHERE request_id=? LIMIT 1"
  ).bind(requestId).first<PaymentRequestRow>();

  if (existing?.status === "PAID") {
    throw httpError(409, "ALREADY_PAID", "Este servicio ya está pagado.");
  }

  if (existing) {
    const active = await env.DB.prepare(
      "SELECT id FROM payment_attempts WHERE payment_request_id=? AND status='CREATED' LIMIT 1"
    ).bind(existing.id).first();
    if (active) {
      throw httpError(
        409,
        "PAYMENT_IN_PROGRESS",
        "Ya hay un intento de pago activo. Esperá a que termine o venza antes de cambiar el importe."
      );
    }
  }

  const now = Date.now();
  const id = existing?.id || crypto.randomUUID();
  const clientUid = String(service.clientUid || "");
  if (!clientUid) throw httpError(409, "CLIENT_NOT_FOUND", "El servicio no tiene un cliente válido.");

  await env.DB.prepare(`
    INSERT INTO payment_requests(
      id,request_id,client_uid,professional_uid,amount_cents,note,status,created_at,updated_at
    ) VALUES(?,?,?,?,?,?,?,?,?)
    ON CONFLICT(request_id) DO UPDATE SET
      amount_cents=excluded.amount_cents,
      note=excluded.note,
      status='PENDING',
      updated_at=excluded.updated_at
  `).bind(
    id, requestId, clientUid, identity.uid, amount, note, "PENDING",
    existing?.created_at || now, now
  ).run();

  const commissionBps = await commissionForProfessional(identity.uid, env);
  const feeCents = Math.round(amount * commissionBps / 10000);

  await firestorePatch(`service_requests/${requestId}`, {
    status: "AWAITING_PAYMENT",
    paymentStatus: "PENDING",
    paymentRequestId: id,
    amountCents: amount,
    marketplaceFeeCents: feeCents,
    updatedAt: new Date()
  }, env);

  const saved = await getPaymentRequestRow(id, env);
  return platformPaymentRequestResponse(saved!, null, identity.uid);
}

async function getPlatformPaymentRequest(
  identity: Identity,
  requestId: string,
  env: Env
): Promise<Response> {
  const paymentRequest = await env.DB.prepare(
    "SELECT * FROM payment_requests WHERE request_id=? LIMIT 1"
  ).bind(requestId).first<PaymentRequestRow>();

  if (!paymentRequest) {
    throw httpError(404, "PAYMENT_REQUEST_NOT_FOUND", "Todavía no hay un cobro enviado para este servicio.");
  }
  ensurePaymentRequestMember(identity, paymentRequest);

  let attempt = await getLatestAttempt(paymentRequest.id, env);
  if (attempt && attempt.status === "CREATED") {
    attempt = await syncPlatformAttempt(attempt, paymentRequest, env);
  }
  const freshRequest = await getPaymentRequestRow(paymentRequest.id, env);
  return platformPaymentRequestResponseAsync(freshRequest!, attempt, identity.uid);
}

async function createCardSession(
  identity: Identity,
  paymentRequestId: string,
  env: Env
): Promise<Response> {
  const paymentRequest = await getPaymentRequestRow(paymentRequestId, env);
  if (!paymentRequest) {
    throw httpError(404, "PAYMENT_REQUEST_NOT_FOUND", "No encontramos el cobro.");
  }
  if (identity.uid !== paymentRequest.client_uid) {
    throw httpError(403, "CLIENT_ONLY", "Solo el cliente del servicio puede pagar con tarjeta.");
  }
  if (paymentRequest.status !== "PENDING") {
    throw httpError(409, "PAYMENT_NOT_PENDING", "Este cobro ya no está pendiente.");
  }
  if (!identity.email) {
    throw httpError(409, "EMAIL_REQUIRED", "Tu cuenta necesita un correo válido para pagar.");
  }

  const existing = await getLatestAttempt(paymentRequest.id, env);
  if (existing?.status === "CREATED") {
    if (existing.method !== "CARD") {
      throw httpError(
        409,
        "PAYMENT_METHOD_ACTIVE",
        "Hay un QR activo para este cobro. Esperá a que venza antes de cambiar de método."
      );
    }
    const seller = await getSeller(paymentRequest.professional_uid, env);
    if (!seller) throw httpError(409, "MP_NOT_CONNECTED", "El profesional debe reconectar Mercado Pago.");
    const accessToken = await validSellerAccessToken(seller, env);
    const current = await mpFetch(
      `https://api.mercadopago.com/v1/orders/${encodeURIComponent(existing.mp_order_id)}`,
      accessToken,
      { method: "GET" }
    );
    const currentStatus = String(current.status || "").toLowerCase();
    if (currentStatus === "processed") {
      await markPlatformAttemptProcessed(existing, paymentRequest, env);
      throw httpError(409, "ALREADY_PAID", "El pago ya fue confirmado.");
    }
    const clientToken = String(current.client_token || "");
    if (currentStatus === "created" && clientToken) {
      return json({
        paymentRequestId: paymentRequest.id,
        requestId: paymentRequest.request_id,
        orderId: existing.mp_order_id,
        clientToken,
        amountFormatted: formatArs(paymentRequest.amount_cents)
      });
    }
  }

  const seller = await getSeller(paymentRequest.professional_uid, env);
  if (!seller) throw httpError(409, "MP_NOT_CONNECTED", "El profesional debe reconectar Mercado Pago.");
  const accessToken = await validSellerAccessToken(seller, env);
  const commissionBps = await commissionForProfessional(paymentRequest.professional_uid, env);
  const feeCents = Math.round(paymentRequest.amount_cents * commissionBps / 10000);
  const amountDecimal = centsToDecimal(paymentRequest.amount_cents);
  const feeDecimal = centsToDecimal(feeCents);
  const idempotencyKey = crypto.randomUUID();

  const order = await mpFetch("https://api.mercadopago.com/v1/orders", accessToken, {
    method: "POST",
    idempotencyKey,
    body: {
      type: "online",
      processing_mode: "manual",
      total_amount: amountDecimal,
      external_reference: paymentRequest.request_id,
      description: "Servicio Soluciona",
      marketplace_fee: feeDecimal,
      expiration_time: "P1D",
      payer: { email: identity.email },
      items: [{
        title: "Servicio Soluciona",
        quantity: 1,
        unit_price: amountDecimal
      }]
    }
  });

  const orderId = String(order.id || "");
  const clientToken = String(order.client_token || "");
  if (!orderId || !clientToken) {
    throw httpError(502, "MP_CARD_SESSION_MISSING", "Mercado Pago no devolvió la sesión de tarjeta esperada.");
  }

  await insertPlatformAttempt({
    paymentRequest,
    method: "CARD",
    orderId,
    amountCents: paymentRequest.amount_cents,
    feeCents,
    commissionBps,
    qrData: null,
    idempotencyKey
  }, env);

  return json({
    paymentRequestId: paymentRequest.id,
    requestId: paymentRequest.request_id,
    orderId,
    clientToken,
    amountFormatted: formatArs(paymentRequest.amount_cents)
  });
}

async function createPaymentRequestQr(
  identity: Identity,
  paymentRequestId: string,
  request: Request,
  env: Env
): Promise<Response> {
  const paymentRequest = await getPaymentRequestRow(paymentRequestId, env);
  if (!paymentRequest) {
    throw httpError(404, "PAYMENT_REQUEST_NOT_FOUND", "No encontramos el cobro.");
  }
  if (identity.uid !== paymentRequest.professional_uid) {
    throw httpError(403, "PROFESSIONAL_ONLY", "Solo el profesional puede mostrar el QR del servicio.");
  }
  if (paymentRequest.status !== "PENDING") {
    throw httpError(409, "PAYMENT_NOT_PENDING", "Este cobro ya no está pendiente.");
  }

  const body = await parseJson(request);
  const latitude = Number(body.latitude);
  const longitude = Number(body.longitude);
  if (!Number.isFinite(latitude) || latitude < -90 || latitude > 90
      || !Number.isFinite(longitude) || longitude < -180 || longitude > 180) {
    throw httpError(400, "LOCATION_REQUIRED", "Necesitamos la ubicación actual del servicio para generar el QR.");
  }

  const existing = await getLatestAttempt(paymentRequest.id, env);
  if (existing?.status === "CREATED") {
    if (existing.method !== "QR") {
      throw httpError(
        409,
        "PAYMENT_METHOD_ACTIVE",
        "Hay un pago con tarjeta en curso. Esperá a que finalice antes de generar QR."
      );
    }
    let current = await syncPlatformAttempt(existing, paymentRequest, env);
    if (current.status === "PROCESSED") {
      const paidRequest = await getPaymentRequestRow(paymentRequest.id, env);
      return platformPaymentRequestResponseAsync(paidRequest!, current, identity.uid);
    }
    if (current.status === "CREATED" && current.qr_data) {
      return platformPaymentRequestResponseAsync(paymentRequest, current, identity.uid);
    }
  }

  const service = await firestoreGet(`service_requests/${paymentRequest.request_id}`, env);
  if (!service) throw httpError(404, "REQUEST_NOT_FOUND", "No encontramos el servicio.");
  const privateService = await firestoreGet(`service_request_private/${paymentRequest.request_id}`, env);
  const serviceAddress: any = privateService?.address || {};

  await syncStoreAndPosForService(
    paymentRequest.professional_uid,
    serviceAddress,
    latitude,
    longitude,
    env
  );

  const seller = await getSeller(paymentRequest.professional_uid, env);
  if (!seller?.external_pos_id) {
    throw httpError(
      409,
      "MP_POS_NOT_READY",
      seller?.setup_error || "La caja de Mercado Pago todavía no está configurada."
    );
  }

  const commissionBps = await commissionForProfessional(paymentRequest.professional_uid, env);
  const feeCents = Math.round(paymentRequest.amount_cents * commissionBps / 10000);
  const amountDecimal = centsToDecimal(paymentRequest.amount_cents);
  const feeDecimal = centsToDecimal(feeCents);
  const accessToken = await validSellerAccessToken(seller, env);
  const idempotencyKey = crypto.randomUUID();

  const order = await mpFetchWithTransientRetry("https://api.mercadopago.com/v1/orders", accessToken, {
    method: "POST",
    idempotencyKey,
    body: {
      type: "qr",
      processing_mode: "automatic",
      total_amount: amountDecimal,
      description: "Servicio Soluciona",
      external_reference: paymentRequest.request_id,
      expiration_time: "PT15M",
      marketplace_fee: feeDecimal,
      config: {
        qr: {
          external_pos_id: seller.external_pos_id,
          mode: "hybrid"
        }
      },
      transactions: {
        payments: [{ amount: amountDecimal }]
      },
      items: [{
        title: "Servicio Soluciona",
        unit_price: amountDecimal,
        quantity: 1,
        unit_measure: "unit"
      }]
    }
  });

  const orderId = String(order.id || "");
  const qrData = String(order?.type_response?.qr_data || "");
  if (!orderId || !qrData) {
    throw httpError(502, "MP_QR_MISSING", "Mercado Pago no devolvió el QR esperado.");
  }

  const attempt = await insertPlatformAttempt({
    paymentRequest,
    method: "QR",
    orderId,
    amountCents: paymentRequest.amount_cents,
    feeCents,
    commissionBps,
    qrData,
    idempotencyKey
  }, env);

  return platformPaymentRequestResponseAsync(paymentRequest, attempt, identity.uid);
}

async function getPaymentRequestRow(
  id: string,
  env: Env
): Promise<PaymentRequestRow | null> {
  return env.DB.prepare("SELECT * FROM payment_requests WHERE id=?")
    .bind(id).first<PaymentRequestRow>();
}

async function getLatestAttempt(
  paymentRequestId: string,
  env: Env
): Promise<PaymentAttemptRow | null> {
  return env.DB.prepare(
    "SELECT * FROM payment_attempts WHERE payment_request_id=? ORDER BY created_at DESC LIMIT 1"
  ).bind(paymentRequestId).first<PaymentAttemptRow>();
}

async function insertPlatformAttempt(input: {
  paymentRequest: PaymentRequestRow;
  method: "CARD" | "QR";
  orderId: string;
  amountCents: number;
  feeCents: number;
  commissionBps: number;
  qrData: string | null;
  idempotencyKey: string;
}, env: Env): Promise<PaymentAttemptRow> {
  const id = crypto.randomUUID();
  const now = Date.now();
  await env.DB.prepare(`
    INSERT INTO payment_attempts(
      id,payment_request_id,request_id,client_uid,professional_uid,method,mp_order_id,
      amount_cents,marketplace_fee_cents,commission_bps,status,qr_data,idempotency_key,
      created_at,updated_at
    ) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
  `).bind(
    id,
    input.paymentRequest.id,
    input.paymentRequest.request_id,
    input.paymentRequest.client_uid,
    input.paymentRequest.professional_uid,
    input.method,
    input.orderId,
    input.amountCents,
    input.feeCents,
    input.commissionBps,
    "CREATED",
    input.qrData,
    input.idempotencyKey,
    now,
    now
  ).run();
  return (await env.DB.prepare("SELECT * FROM payment_attempts WHERE id=?")
    .bind(id).first<PaymentAttemptRow>())!;
}

function ensurePaymentRequestMember(identity: Identity, paymentRequest: PaymentRequestRow) {
  if (identity.uid !== paymentRequest.client_uid && identity.uid !== paymentRequest.professional_uid) {
    throw httpError(403, "FORBIDDEN", "No tenés acceso a este cobro.");
  }
}

async function syncPlatformAttempt(
  attempt: PaymentAttemptRow,
  paymentRequest: PaymentRequestRow,
  env: Env
): Promise<PaymentAttemptRow> {
  if (attempt.status !== "CREATED") return attempt;
  const seller = await getSeller(attempt.professional_uid, env);
  if (!seller) return attempt;
  const accessToken = await validSellerAccessToken(seller, env);
  const order = await mpFetch(
    `https://api.mercadopago.com/v1/orders/${encodeURIComponent(attempt.mp_order_id)}`,
    accessToken,
    { method: "GET" }
  );
  const status = String(order.status || "").toLowerCase();
  if (status === "processed") {
    await markPlatformAttemptProcessed(attempt, paymentRequest, env);
  } else if (status === "canceled") {
    await markPlatformAttemptTerminal(attempt, paymentRequest, "CANCELED", env);
  } else if (status === "refunded") {
    await markPlatformAttemptTerminal(attempt, paymentRequest, "REFUNDED", env);
  } else if (status === "expired") {
    await markPlatformAttemptTerminal(attempt, paymentRequest, "EXPIRED", env);
  }
  return (await env.DB.prepare("SELECT * FROM payment_attempts WHERE id=?")
    .bind(attempt.id).first<PaymentAttemptRow>())!;
}

async function markPlatformAttemptProcessed(
  attempt: PaymentAttemptRow,
  paymentRequest: PaymentRequestRow,
  env: Env
): Promise<void> {
  if (attempt.status === "PROCESSED" || paymentRequest.status === "PAID") return;
  const now = Date.now();
  await env.DB.batch([
    env.DB.prepare("UPDATE payment_attempts SET status='PROCESSED',updated_at=? WHERE id=?")
      .bind(now, attempt.id),
    env.DB.prepare("UPDATE payment_requests SET status='PAID',updated_at=? WHERE id=?")
      .bind(now, paymentRequest.id)
  ]);

  await firestorePatch(`service_requests/${paymentRequest.request_id}`, {
    status: "COMPLETED",
    paymentStatus: "PAID",
    paymentOrderId: attempt.mp_order_id,
    amountCents: Number(paymentRequest.amount_cents),
    marketplaceFeeCents: Number(attempt.marketplace_fee_cents),
    paidAt: new Date(),
    completedAt: new Date(),
    updatedAt: new Date()
  }, env);

  if (Number(attempt.commission_bps) < standardCommissionBps(env)) {
    await consumeProfessionalDiscount(paymentRequest.professional_uid, env);
  }
  await qualifyReferralIfFirstActivity(paymentRequest.client_uid, "CLIENT", attempt.id, env);
  await qualifyReferralIfFirstActivity(paymentRequest.professional_uid, "PROFESSIONAL", attempt.id, env);
}

async function markPlatformAttemptTerminal(
  attempt: PaymentAttemptRow,
  paymentRequest: PaymentRequestRow,
  status: string,
  env: Env
): Promise<void> {
  const now = Date.now();
  await env.DB.prepare("UPDATE payment_attempts SET status=?,updated_at=? WHERE id=?")
    .bind(status, now, attempt.id).run();

  if (status === "REFUNDED") {
    await env.DB.prepare("UPDATE payment_requests SET status='REFUNDED',updated_at=? WHERE id=?")
      .bind(now, paymentRequest.id).run();
    await firestorePatch(`service_requests/${paymentRequest.request_id}`, {
      paymentStatus: "REFUNDED",
      status: "PAYMENT_REVIEW",
      updatedAt: new Date()
    }, env);
  }
}

function platformPaymentRequestResponse(
  paymentRequest: PaymentRequestRow,
  attempt: PaymentAttemptRow | null,
  viewerUid: string
): Response {
  let qrSvg = "";
  if (attempt?.method === "QR" && attempt.qr_data && attempt.status === "CREATED") {
    // Generated below because QRCode.toString is async; callers that need QR use
    // platformPaymentRequestResponseAsync.
  }
  return json({
    id: paymentRequest.id,
    requestId: paymentRequest.request_id,
    status: paymentRequest.status,
    note: paymentRequest.note || "",
    amountCents: Number(paymentRequest.amount_cents || 0),
    amountFormatted: formatArs(Number(paymentRequest.amount_cents || 0)),
    clientUid: paymentRequest.client_uid,
    professionalUid: paymentRequest.professional_uid,
    viewerRole: viewerUid === paymentRequest.client_uid ? "CLIENT" : "PROFESSIONAL",
    attempt: attempt ? {
      id: attempt.id,
      method: attempt.method,
      status: attempt.status,
      orderId: attempt.mp_order_id,
      marketplaceFeeCents: Number(attempt.marketplace_fee_cents || 0),
      marketplaceFeeFormatted: formatArs(Number(attempt.marketplace_fee_cents || 0))
    } : null,
    qrSvg
  });
}

async function platformPaymentRequestResponseAsync(
  paymentRequest: PaymentRequestRow,
  attempt: PaymentAttemptRow | null,
  viewerUid: string
): Promise<Response> {
  let qrSvg = "";
  if (attempt?.method === "QR" && attempt.qr_data && attempt.status === "CREATED") {
    qrSvg = await QRCode.toString(String(attempt.qr_data), { type: "svg", margin: 1, width: 360 });
  }
  return json({
    id: paymentRequest.id,
    requestId: paymentRequest.request_id,
    status: paymentRequest.status,
    note: paymentRequest.note || "",
    amountCents: Number(paymentRequest.amount_cents || 0),
    amountFormatted: formatArs(Number(paymentRequest.amount_cents || 0)),
    clientUid: paymentRequest.client_uid,
    professionalUid: paymentRequest.professional_uid,
    viewerRole: viewerUid === paymentRequest.client_uid ? "CLIENT" : "PROFESSIONAL",
    attempt: attempt ? {
      id: attempt.id,
      method: attempt.method,
      status: attempt.status,
      orderId: attempt.mp_order_id,
      marketplaceFeeCents: Number(attempt.marketplace_fee_cents || 0),
      marketplaceFeeFormatted: formatArs(Number(attempt.marketplace_fee_cents || 0))
    } : null,
    qrSvg
  });
}

async function createPaymentQr(identity: Identity, request: Request, env: Env): Promise<Response> {
  const body = await parseJson(request);
  const requestId = String(body.requestId || "").trim();
  const amount = parseMoney(String(body.amount || ""));
  const latitude = Number(body.latitude);
  const longitude = Number(body.longitude);
  if (!requestId || amount <= 0) throw httpError(400, "INVALID_PAYMENT", "Pedido o importe inválido.");
  if (!Number.isFinite(latitude) || latitude < -90 || latitude > 90
      || !Number.isFinite(longitude) || longitude < -180 || longitude > 180) {
    throw httpError(400, "LOCATION_REQUIRED", "Necesitamos la ubicación actual del servicio para generar el QR.");
  }

  const role = await getUserRole(identity.uid, env);
  if (role !== "PROFESSIONAL") throw httpError(403, "PROFESSIONAL_ONLY", "Solo el profesional asignado puede generar el cobro.");

  const service = await firestoreGet(`service_requests/${requestId}`, env);
  if (!service) throw httpError(404, "REQUEST_NOT_FOUND", "No encontramos el servicio.");
  if (String(service.professionalUid || "") !== identity.uid) {
    throw httpError(403, "NOT_ASSIGNED", "No estás asignado a este servicio.");
  }
  if (String(service.status || "") !== "IN_PROGRESS") {
    throw httpError(409, "INVALID_STATUS", "El servicio debe estar en curso antes de generar el cobro.");
  }

  const seller = await getSeller(identity.uid, env);
  if (!seller) throw httpError(409, "MP_NOT_CONNECTED", "Conectá Mercado Pago antes de cobrar.");

  const privateService = await firestoreGet(`service_request_private/${requestId}`, env);
  const serviceAddress: any = privateService?.address || {};
  await syncStoreAndPosForService(identity.uid, serviceAddress, latitude, longitude, env);

  const freshSeller = await getSeller(identity.uid, env);
  if (!freshSeller?.external_pos_id) {
    throw httpError(
      409,
      "MP_POS_NOT_READY",
      freshSeller?.setup_error || "La caja de Mercado Pago todavía no está configurada."
    );
  }

  const previous = await env.DB.prepare(
    "SELECT * FROM payments WHERE request_id=? AND status IN ('CREATED','PROCESSED') ORDER BY created_at DESC LIMIT 1"
  ).bind(requestId).first<any>();
  if (previous?.status === "PROCESSED") {
    return paymentResponse(previous, "");
  }

  const commissionBps = await commissionForProfessional(identity.uid, env);
  const feeCents = Math.round(amount * commissionBps / 10000);
  const amountDecimal = centsToDecimal(amount);
  const feeDecimal = centsToDecimal(feeCents);
  const accessToken = await validSellerAccessToken(freshSeller, env);
  const idempotencyKey = previous?.idempotency_key || crypto.randomUUID();

  const orderPayload: any = {
    type: "qr",
    total_amount: amountDecimal,
    description: "Servicio Soluciona",
    external_reference: requestId,
    expiration_time: "PT15M",
    marketplace_fee: feeDecimal,
    config: {
      qr: {
        external_pos_id: freshSeller.external_pos_id,
        mode: "hybrid"
      }
    },
    transactions: {
      payments: [{ amount: amountDecimal }]
    },
    items: [{
      title: "Servicio Soluciona",
      unit_price: amountDecimal,
      quantity: 1,
      unit_measure: "unit"
    }]
  };

  const order = await mpFetch("https://api.mercadopago.com/v1/orders", accessToken, {
    method: "POST",
    idempotencyKey,
    body: orderPayload
  });

  const orderId = String(order.id || "");
  const qrData = String(order?.type_response?.qr_data || "");
  if (!orderId || !qrData) throw httpError(502, "MP_QR_MISSING", "Mercado Pago no devolvió el QR esperado.");

  const paymentId = crypto.randomUUID();
  const clientUid = String(service.clientUid || "");
  await env.DB.prepare(`
    INSERT INTO payments(
      id,request_id,client_uid,professional_uid,mp_order_id,amount_cents,marketplace_fee_cents,
      commission_bps,status,qr_data,idempotency_key,created_at,updated_at
    ) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?)
    ON CONFLICT(request_id) DO UPDATE SET
      mp_order_id=excluded.mp_order_id,
      amount_cents=excluded.amount_cents,
      marketplace_fee_cents=excluded.marketplace_fee_cents,
      commission_bps=excluded.commission_bps,
      status=excluded.status,
      qr_data=excluded.qr_data,
      idempotency_key=excluded.idempotency_key,
      updated_at=excluded.updated_at
  `).bind(
    paymentId, requestId, clientUid, identity.uid, orderId, amount, feeCents,
    commissionBps, "CREATED", qrData, idempotencyKey, Date.now(), Date.now()
  ).run();

  await firestorePatch(`service_requests/${requestId}`, {
    status: "AWAITING_PAYMENT",
    paymentStatus: "PENDING",
    paymentOrderId: orderId,
    amountCents: amount,
    marketplaceFeeCents: feeCents,
    updatedAt: new Date()
  }, env);

  const svg = await QRCode.toString(qrData, { type: "svg", margin: 1, width: 360 });
  const saved = await env.DB.prepare("SELECT * FROM payments WHERE request_id=?").bind(requestId).first<any>();
  return paymentResponse(saved, svg);
}

async function paymentByRequest(identity: Identity, requestId: string, env: Env): Promise<Response> {
  const row = await env.DB.prepare(
    "SELECT * FROM payments WHERE request_id=? ORDER BY created_at DESC LIMIT 1"
  ).bind(requestId).first<any>();
  if (!row) throw httpError(404, "PAYMENT_NOT_FOUND", "Todavía no hay un cobro para este servicio.");
  if (row.client_uid !== identity.uid && row.professional_uid !== identity.uid) {
    throw httpError(403, "FORBIDDEN", "No tenés acceso a este cobro.");
  }
  let svg = "";
  if (row.qr_data && row.status === "CREATED") {
    svg = await QRCode.toString(String(row.qr_data), { type: "svg", margin: 1, width: 360 });
  }
  return paymentResponse(row, svg);
}

function paymentResponse(row: any, qrSvg: string): Response {
  return json({
    requestId: row?.request_id || "",
    orderId: row?.mp_order_id || "",
    status: row?.status || "",
    amountCents: Number(row?.amount_cents || 0),
    marketplaceFeeCents: Number(row?.marketplace_fee_cents || 0),
    commissionBps: Number(row?.commission_bps || 0),
    amountFormatted: formatArs(Number(row?.amount_cents || 0)),
    marketplaceFeeFormatted: formatArs(Number(row?.marketplace_fee_cents || 0)),
    qrSvg
  });
}

async function handleMpWebhook(request: Request, url: URL, env: Env): Promise<Response> {
  const dataIdRaw = url.searchParams.get("data.id") || url.searchParams.get("data_id") || "";
  const dataId = dataIdRaw.toLowerCase();
  const xSignature = request.headers.get("x-signature") || "";
  const xRequestId = request.headers.get("x-request-id") || "";

  if (!dataId || !xSignature || !xRequestId) {
    return json({ error: "INVALID_WEBHOOK" }, 401);
  }
  const valid = await verifyWebhookSignature(dataId, xRequestId, xSignature, env.MP_WEBHOOK_SECRET);
  if (!valid) return json({ error: "INVALID_SIGNATURE" }, 401);

  const body = await request.json<any>().catch(() => ({}));
  const action = String(body?.action || "");
  const orderId = String(body?.data?.id || dataIdRaw);

  const attempt = await env.DB.prepare(
    "SELECT * FROM payment_attempts WHERE mp_order_id=? LIMIT 1"
  ).bind(orderId).first<PaymentAttemptRow>();

  if (attempt) {
    const paymentRequest = await getPaymentRequestRow(attempt.payment_request_id, env);
    if (!paymentRequest) return json({ ok: true, ignored: true });

    const seller = await getSeller(attempt.professional_uid, env);
    if (!seller) return json({ ok: true, ignored: true });
    const accessToken = await validSellerAccessToken(seller, env);
    const order = await mpFetch(
      `https://api.mercadopago.com/v1/orders/${encodeURIComponent(orderId)}`,
      accessToken,
      { method: "GET" }
    );
    const status = String(order.status || "").toLowerCase();

    if (action === "order.processed" || status === "processed") {
      await markPlatformAttemptProcessed(attempt, paymentRequest, env);
    } else if (action === "order.canceled" || status === "canceled") {
      await markPlatformAttemptTerminal(attempt, paymentRequest, "CANCELED", env);
    } else if (action === "order.refunded" || status === "refunded") {
      await markPlatformAttemptTerminal(attempt, paymentRequest, "REFUNDED", env);
    } else if (action === "order.expired" || status === "expired") {
      await markPlatformAttemptTerminal(attempt, paymentRequest, "EXPIRED", env);
    }
    return json({ ok: true });
  }

  // Legacy 0.8.x payment rows remain supported during the migration.
  const payment = await env.DB.prepare(
    "SELECT * FROM payments WHERE mp_order_id=? LIMIT 1"
  ).bind(orderId).first<any>();

  if (!payment) return json({ ok: true, ignored: true });

  const seller = await getSeller(payment.professional_uid, env);
  if (!seller) return json({ ok: true, ignored: true });
  const accessToken = await validSellerAccessToken(seller, env);

  const order = await mpFetch(
    `https://api.mercadopago.com/v1/orders/${encodeURIComponent(orderId)}`,
    accessToken,
    { method: "GET" }
  );

  const status = String(order.status || "").toLowerCase();
  if (action === "order.processed" || status === "processed") {
    await markPaymentProcessed(payment, env);
  } else if (action === "order.canceled" || status === "canceled") {
    await markPaymentTerminal(payment, "CANCELED", env);
  } else if (action === "order.refunded" || status === "refunded") {
    await markPaymentTerminal(payment, "REFUNDED", env);
  } else if (action === "order.expired" || status === "expired") {
    await markPaymentTerminal(payment, "EXPIRED", env);
  }

  return json({ ok: true });
}

async function markPaymentProcessed(payment: any, env: Env): Promise<void> {
  if (payment.status === "PROCESSED") return;
  await env.DB.prepare(
    "UPDATE payments SET status='PROCESSED',updated_at=? WHERE id=?"
  ).bind(Date.now(), payment.id).run();

  await firestorePatch(`service_requests/${payment.request_id}`, {
    status: "COMPLETED",
    paymentStatus: "PAID",
    paymentOrderId: payment.mp_order_id,
    amountCents: Number(payment.amount_cents),
    marketplaceFeeCents: Number(payment.marketplace_fee_cents),
    paidAt: new Date(),
    completedAt: new Date(),
    updatedAt: new Date()
  }, env);

  if (Number(payment.commission_bps) < standardCommissionBps(env)) {
    await consumeProfessionalDiscount(payment.professional_uid, env);
  }

  await qualifyReferralIfFirstActivity(payment.client_uid, "CLIENT", payment.id, env);
  await qualifyReferralIfFirstActivity(payment.professional_uid, "PROFESSIONAL", payment.id, env);
}

async function markPaymentTerminal(payment: any, status: string, env: Env): Promise<void> {
  await env.DB.prepare("UPDATE payments SET status=?,updated_at=? WHERE id=?")
    .bind(status, Date.now(), payment.id).run();
  await firestorePatch(`service_requests/${payment.request_id}`, {
    paymentStatus: status,
    status: status === "REFUNDED" ? "PAYMENT_REVIEW" : "IN_PROGRESS",
    updatedAt: new Date()
  }, env);
}

async function referralDashboard(identity: Identity, env: Env): Promise<Response> {
  const role = await getUserRole(identity.uid, env);
  const code = await ensureReferralCode(identity.uid, role, env);

  const stats = await env.DB.prepare(`
    SELECT
      COUNT(*) invited,
      SUM(CASE WHEN status IN ('QUALIFIED','REWARDED') THEN 1 ELSE 0 END) qualified
    FROM referrals WHERE referrer_uid=?
  `).bind(identity.uid).first<any>();

  const referral = await env.DB.prepare(
    "SELECT rc.code AS referred_by FROM referrals r JOIN referral_codes rc ON rc.uid=r.referrer_uid WHERE r.referred_uid=? LIMIT 1"
  ).bind(identity.uid).first<any>();

  const userBenefits = await env.DB.prepare(
    "SELECT priority_tokens FROM user_benefits WHERE uid=?"
  ).bind(identity.uid).first<any>();

  const proBenefits = await env.DB.prepare(
    "SELECT discounted_jobs_remaining FROM professional_benefits WHERE uid=?"
  ).bind(identity.uid).first<any>();

  const available = role === "PROFESSIONAL"
    ? Number(proBenefits?.discounted_jobs_remaining || 0)
    : Number(userBenefits?.priority_tokens || 0);

  return json({
    code,
    role,
    invited: Number(stats?.invited || 0),
    qualified: Number(stats?.qualified || 0),
    benefitsAvailable: available,
    referredBy: String(referral?.referred_by || "")
  });
}

async function referralApply(identity: Identity, request: Request, env: Env): Promise<Response> {
  const body = await parseJson(request);
  const code = String(body.code || "").trim().toUpperCase();
  if (!code) throw httpError(400, "INVALID_CODE", "Ingresá un código.");

  const role = await getUserRole(identity.uid, env);
  const ownCode = await ensureReferralCode(identity.uid, role, env);
  if (code === ownCode) throw httpError(400, "SELF_REFERRAL", "No podés usar tu propio código.");

  const existing = await env.DB.prepare(
    "SELECT id FROM referrals WHERE referred_uid=? LIMIT 1"
  ).bind(identity.uid).first();
  if (existing) throw httpError(409, "ALREADY_REFERRED", "Ya tenés un referido asociado.");

  const referrer = await env.DB.prepare(
    "SELECT uid,role FROM referral_codes WHERE code=? LIMIT 1"
  ).bind(code).first<{ uid: string; role: string }>();
  if (!referrer) throw httpError(404, "CODE_NOT_FOUND", "Código de referido inexistente.");

  await env.DB.prepare(`
    INSERT INTO referrals(id,referrer_uid,referred_uid,referrer_role,referred_role,status,created_at)
    VALUES(?,?,?,?,?,'PENDING',?)
  `).bind(
    crypto.randomUUID(), referrer.uid, identity.uid, referrer.role, role, Date.now()
  ).run();

  // Profesional referido: beneficio de lanzamiento inmediato y acotado.
  if (role === "PROFESSIONAL") {
    await addProfessionalDiscount(identity.uid, 3, env);
  }

  return json({ ok: true, referrerCode: code });
}

async function ensureReferralCode(uid: string, role: string, env: Env): Promise<string> {
  const existing = await env.DB.prepare(
    "SELECT code FROM referral_codes WHERE uid=?"
  ).bind(uid).first<{ code: string }>();
  if (existing?.code) return existing.code;

  for (let i = 0; i < 5; i++) {
    const code = "SOL-" + randomCode(8);
    try {
      await env.DB.prepare(
        "INSERT INTO referral_codes(uid,code,role,created_at) VALUES(?,?,?,?)"
      ).bind(uid, code, role, Date.now()).run();
      return code;
    } catch (_) {
      // collision: retry
    }
  }
  throw new Error("No se pudo generar el código de referido.");
}

async function qualifyReferralIfFirstActivity(uid: string, role: string, currentPaymentId: string, env: Env) {
  if (!uid) return;
  const prior = role === "CLIENT"
    ? await env.DB.prepare(
        "SELECT COUNT(*) c FROM payments WHERE client_uid=? AND status='PROCESSED' AND id<>?"
      ).bind(uid, currentPaymentId).first<any>()
    : await env.DB.prepare(
        "SELECT COUNT(*) c FROM payments WHERE professional_uid=? AND status='PROCESSED' AND id<>?"
      ).bind(uid, currentPaymentId).first<any>();
  if (Number(prior?.c || 0) > 0) return;

  const referral = await env.DB.prepare(
    "SELECT * FROM referrals WHERE referred_uid=? AND status='PENDING' LIMIT 1"
  ).bind(uid).first<any>();
  if (!referral) return;

  await env.DB.prepare(
    "UPDATE referrals SET status='QUALIFIED',qualified_at=? WHERE id=?"
  ).bind(Date.now(), referral.id).run();

  if (role === "PROFESSIONAL") {
    await addProfessionalDiscount(referral.referrer_uid, 2, env);
  } else {
    await addPriorityToken(uid, 1, env);
    await addPriorityToken(referral.referrer_uid, 1, env);
  }

  await env.DB.prepare(
    "UPDATE referrals SET status='REWARDED',rewarded_at=? WHERE id=?"
  ).bind(Date.now(), referral.id).run();
}

async function addPriorityToken(uid: string, count: number, env: Env) {
  await env.DB.prepare(`
    INSERT INTO user_benefits(uid,priority_tokens,updated_at) VALUES(?,?,?)
    ON CONFLICT(uid) DO UPDATE SET priority_tokens=priority_tokens+excluded.priority_tokens,updated_at=excluded.updated_at
  `).bind(uid, count, Date.now()).run();
}

async function addProfessionalDiscount(uid: string, count: number, env: Env) {
  await env.DB.prepare(`
    INSERT INTO professional_benefits(uid,discounted_jobs_remaining,updated_at) VALUES(?,?,?)
    ON CONFLICT(uid) DO UPDATE SET discounted_jobs_remaining=discounted_jobs_remaining+excluded.discounted_jobs_remaining,updated_at=excluded.updated_at
  `).bind(uid, count, Date.now()).run();
}

async function consumeProfessionalDiscount(uid: string, env: Env) {
  await env.DB.prepare(`
    UPDATE professional_benefits
    SET discounted_jobs_remaining=CASE WHEN discounted_jobs_remaining>0 THEN discounted_jobs_remaining-1 ELSE 0 END,
        updated_at=?
    WHERE uid=?
  `).bind(Date.now(), uid).run();
}

async function commissionForProfessional(uid: string, env: Env): Promise<number> {
  const benefit = await env.DB.prepare(
    "SELECT discounted_jobs_remaining FROM professional_benefits WHERE uid=?"
  ).bind(uid).first<any>();
  return Number(benefit?.discounted_jobs_remaining || 0) > 0
    ? referralCommissionBps(env)
    : standardCommissionBps(env);
}

function standardCommissionBps(env: Env) {
  return clampBps(Number(env.COMMISSION_BPS || 1000));
}
function referralCommissionBps(env: Env) {
  return clampBps(Number(env.REFERRAL_PRO_BPS || 500));
}
function clampBps(v: number) {
  return Number.isFinite(v) ? Math.max(0, Math.min(5000, Math.round(v))) : 1000;
}

async function getUserRole(uid: string, env: Env): Promise<string> {
  const doc = await firestoreGet(`users/${uid}`, env);
  const role = String(doc?.role || "");
  if (!role) throw httpError(409, "PROFILE_NOT_FOUND", "No encontramos el perfil de Soluciona.");
  return role;
}

async function getSeller(uid: string, env: Env): Promise<SellerRow | null> {
  return env.DB.prepare("SELECT * FROM sellers WHERE uid=?")
    .bind(uid).first<SellerRow>();
}

async function validSellerAccessToken(seller: SellerRow, env: Env): Promise<string> {
  const now = Date.now();
  if (!seller.expires_at || seller.expires_at > now + 60_000) {
    return decryptSecret(seller.access_token_enc, env);
  }
  if (!seller.refresh_token_enc) return decryptSecret(seller.access_token_enc, env);

  const refreshToken = await decryptSecret(seller.refresh_token_enc, env);
  const tokenData = await mpOAuthExchange(env, {
    grant_type: "refresh_token",
    refresh_token: refreshToken
  });

  const access = String(tokenData.access_token || "");
  if (!access) throw new Error("Mercado Pago no devolvió access_token al refrescar.");
  const accessEnc = await encryptSecret(access, env);
  const newRefresh = tokenData.refresh_token ? String(tokenData.refresh_token) : refreshToken;
  const refreshEnc = await encryptSecret(newRefresh, env);
  const expiresAt = tokenData.expires_in ? Date.now() + Number(tokenData.expires_in) * 1000 : null;

  await env.DB.prepare(
    "UPDATE sellers SET access_token_enc=?,refresh_token_enc=?,expires_at=?,updated_at=? WHERE uid=?"
  ).bind(accessEnc, refreshEnc, expiresAt, Date.now(), seller.uid).run();

  return access;
}

async function mpOAuthExchange(env: Env, values: Record<string, string>) {
  const params = new URLSearchParams({
    client_id: env.MP_CLIENT_ID,
    client_secret: env.MP_CLIENT_SECRET,
    ...values
  });
  const response = await fetch("https://api.mercadopago.com/oauth/token", {
    method: "POST",
    headers: {
      "accept": "application/json",
      "content-type": "application/x-www-form-urlencoded"
    },
    body: params.toString()
  });
  const data = await response.json<any>();
  if (!response.ok) throw new Error(data?.message || data?.error || `OAuth Mercado Pago ${response.status}`);
  return data;
}

async function mpFetchWithTransientRetry(url: string, token: string, options: {
  method: string;
  body?: any;
  idempotencyKey?: string;
}) {
  try {
    return await mpFetch(url, token, options);
  } catch (error: any) {
    // Orders QR can return transient 5xx errors. Retrying with the SAME
    // idempotency key is safe and avoids creating a duplicate order.
    if (Number(error?.upstreamStatus || 0) < 500) throw error;
    await new Promise(resolve => setTimeout(resolve, 350));
    return mpFetch(url, token, options);
  }
}

async function mpFetch(url: string, token: string, options: {
  method: string;
  body?: any;
  idempotencyKey?: string;
}) {
  const headers = new Headers({
    "Authorization": `Bearer ${token}`,
    "Accept": "application/json"
  });
  if (options.body !== undefined) headers.set("Content-Type", "application/json");
  if (options.idempotencyKey) headers.set("X-Idempotency-Key", options.idempotencyKey);

  const response = await fetch(url, {
    method: options.method,
    headers,
    body: options.body !== undefined ? JSON.stringify(options.body) : undefined
  });
  const data = await response.json<any>().catch(() => ({}));
  if (!response.ok) {
    const upstreamMessage = String(
      data?.message || data?.error || `Mercado Pago respondió ${response.status}.`
    );
    console.error("Mercado Pago API error", {
      method: options.method,
      url,
      status: response.status,
      error: data?.error || null,
      message: upstreamMessage,
      cause: data?.cause || null
    });
    const error: any = httpError(
      response.status >= 500 ? 502 : response.status,
      "MERCADO_PAGO_ERROR",
      upstreamMessage
    );
    error.upstreamStatus = response.status;
    error.upstreamCode = data?.error || null;
    throw error;
  }
  return data;
}

async function verifyWebhookSignature(dataId: string, requestId: string, signature: string, secret: string) {
  const parts = Object.fromEntries(signature.split(",").map(part => {
    const i = part.indexOf("=");
    return i > 0 ? [part.slice(0, i).trim(), part.slice(i + 1).trim()] : ["", ""];
  }));
  const ts = parts.ts || "";
  const expected = parts.v1 || "";
  if (!ts || !expected || !secret) return false;

  const manifest = `id:${dataId};request-id:${requestId};ts:${ts};`;
  const key = await crypto.subtle.importKey(
    "raw",
    new TextEncoder().encode(secret),
    { name: "HMAC", hash: "SHA-256" },
    false,
    ["sign"]
  );
  const sig = await crypto.subtle.sign("HMAC", key, new TextEncoder().encode(manifest));
  const actual = bytesToHex(new Uint8Array(sig));
  return timingSafeEqual(actual, expected);
}

function timingSafeEqual(a: string, b: string) {
  if (a.length !== b.length) return false;
  let diff = 0;
  for (let i = 0; i < a.length; i++) diff |= a.charCodeAt(i) ^ b.charCodeAt(i);
  return diff === 0;
}

// ---- Firestore Admin REST -------------------------------------------------

async function firestoreGet(path: string, env: Env): Promise<any | null> {
  const token = await getGoogleAccessToken(env);
  const response = await fetch(
    `https://firestore.googleapis.com/v1/projects/${encodeURIComponent(env.FIREBASE_PROJECT_ID)}/databases/(default)/documents/${path}`,
    { headers: { Authorization: `Bearer ${token}` } }
  );
  if (response.status === 404) return null;
  if (!response.ok) throw new Error(`Firestore GET ${response.status}`);
  const doc = await response.json<any>();
  return fromFirestoreFields(doc.fields || {});
}

async function firestorePatch(path: string, values: Record<string, any>, env: Env) {
  const token = await getGoogleAccessToken(env);
  const fields: Record<string, any> = {};
  const params = new URLSearchParams();
  for (const [key, value] of Object.entries(values)) {
    fields[key] = toFirestoreField(value);
    params.append("updateMask.fieldPaths", key);
  }
  const response = await fetch(
    `https://firestore.googleapis.com/v1/projects/${encodeURIComponent(env.FIREBASE_PROJECT_ID)}/databases/(default)/documents/${path}?${params.toString()}`,
    {
      method: "PATCH",
      headers: {
        Authorization: `Bearer ${token}`,
        "Content-Type": "application/json"
      },
      body: JSON.stringify({ fields })
    }
  );
  if (!response.ok) {
    const text = await response.text();
    throw new Error(`Firestore PATCH ${response.status}: ${text.slice(0, 300)}`);
  }
}

function fromFirestoreFields(fields: Record<string, any>) {
  const out: Record<string, any> = {};
  for (const [key, value] of Object.entries(fields)) out[key] = fromFirestoreField(value);
  return out;
}

function fromFirestoreField(v: any): any {
  if (!v) return null;
  if ("stringValue" in v) return v.stringValue;
  if ("booleanValue" in v) return v.booleanValue;
  if ("integerValue" in v) return Number(v.integerValue);
  if ("doubleValue" in v) return Number(v.doubleValue);
  if ("timestampValue" in v) return v.timestampValue;
  if ("nullValue" in v) return null;
  if ("arrayValue" in v) return (v.arrayValue.values || []).map(fromFirestoreField);
  if ("mapValue" in v) return fromFirestoreFields(v.mapValue.fields || {});
  return null;
}

function toFirestoreField(value: any): any {
  if (value === null || value === undefined) return { nullValue: null };
  if (value instanceof Date) return { timestampValue: value.toISOString() };
  if (typeof value === "string") return { stringValue: value };
  if (typeof value === "boolean") return { booleanValue: value };
  if (typeof value === "number") {
    return Number.isInteger(value)
      ? { integerValue: String(value) }
      : { doubleValue: value };
  }
  if (Array.isArray(value)) return { arrayValue: { values: value.map(toFirestoreField) } };
  if (typeof value === "object") {
    const fields: Record<string, any> = {};
    for (const [k, v] of Object.entries(value)) fields[k] = toFirestoreField(v);
    return { mapValue: { fields } };
  }
  return { stringValue: String(value) };
}

async function getGoogleAccessToken(env: Env): Promise<string> {
  if (googleTokenCache && googleTokenCache.expiresAt > Date.now() + 60_000) {
    return googleTokenCache.token;
  }
  const now = Math.floor(Date.now() / 1000);
  const header = base64UrlJson({ alg: "RS256", typ: "JWT" });
  const payload = base64UrlJson({
    iss: env.FIREBASE_CLIENT_EMAIL,
    sub: env.FIREBASE_CLIENT_EMAIL,
    aud: "https://oauth2.googleapis.com/token",
    scope: "https://www.googleapis.com/auth/datastore",
    iat: now,
    exp: now + 3600
  });
  const signingInput = `${header}.${payload}`;
  const privateKey = await importPrivateKey(env.FIREBASE_PRIVATE_KEY);
  const signature = await crypto.subtle.sign(
    "RSASSA-PKCS1-v1_5",
    privateKey,
    new TextEncoder().encode(signingInput)
  );
  const assertion = `${signingInput}.${base64UrlBytes(new Uint8Array(signature))}`;

  const response = await fetch("https://oauth2.googleapis.com/token", {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({
      grant_type: "urn:ietf:params:oauth:grant-type:jwt-bearer",
      assertion
    }).toString()
  });
  const data = await response.json<any>();
  if (!response.ok) throw new Error(data?.error_description || "No se pudo autenticar Firestore Admin.");
  googleTokenCache = {
    token: String(data.access_token),
    expiresAt: Date.now() + Number(data.expires_in || 3600) * 1000
  };
  return googleTokenCache.token;
}

async function importPrivateKey(pem: string) {
  const normalized = pem.replace(/\\n/g, "\n");
  const body = normalized
    .replace("-----BEGIN PRIVATE KEY-----", "")
    .replace("-----END PRIVATE KEY-----", "")
    .replace(/\s/g, "");
  const der = base64ToBytes(body);
  return crypto.subtle.importKey(
    "pkcs8",
    der,
    { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" },
    false,
    ["sign"]
  );
}

// ---- Secret encryption ---------------------------------------------------

async function encryptionKey(env: Env) {
  const raw = base64ToBytes(env.TOKEN_ENCRYPTION_KEY);
  if (raw.length !== 32) throw new Error("TOKEN_ENCRYPTION_KEY debe contener 32 bytes en base64.");
  return crypto.subtle.importKey("raw", raw, "AES-GCM", false, ["encrypt", "decrypt"]);
}

async function encryptSecret(value: string, env: Env) {
  const iv = crypto.getRandomValues(new Uint8Array(12));
  const key = await encryptionKey(env);
  const encrypted = await crypto.subtle.encrypt(
    { name: "AES-GCM", iv },
    key,
    new TextEncoder().encode(value)
  );
  return `${bytesToBase64(iv)}.${bytesToBase64(new Uint8Array(encrypted))}`;
}

async function decryptSecret(value: string, env: Env) {
  const [ivB64, dataB64] = value.split(".");
  if (!ivB64 || !dataB64) throw new Error("Token cifrado inválido.");
  const key = await encryptionKey(env);
  const clear = await crypto.subtle.decrypt(
    { name: "AES-GCM", iv: base64ToBytes(ivB64) },
    key,
    base64ToBytes(dataB64)
  );
  return new TextDecoder().decode(clear);
}

// ---- Helpers -------------------------------------------------------------

async function parseJson(request: Request) {
  try { return await request.json<any>(); }
  catch { throw httpError(400, "INVALID_JSON", "JSON inválido."); }
}

function parseMoney(text: string): number {
  if (!/^\d+([.,]\d{1,2})?$/.test(text.trim())) return 0;
  const n = Number(text.replace(",", "."));
  if (!Number.isFinite(n) || n < 1 || n > 100_000_000) return 0;
  return Math.round(n * 100);
}

function centsToDecimal(cents: number) {
  return (cents / 100).toFixed(2);
}

function formatArs(cents: number) {
  const value = cents / 100;
  return "$ " + value.toLocaleString("es-AR", { minimumFractionDigits: 2, maximumFractionDigits: 2 });
}

function shortId(uid: string) {
  return uid.replace(/[^A-Za-z0-9]/g, "").slice(0, 12).toUpperCase();
}

function randomCode(length: number) {
  const alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
  const bytes = crypto.getRandomValues(new Uint8Array(length));
  return Array.from(bytes, b => alphabet[b % alphabet.length]).join("");
}

function callbackHtml(ok: boolean, message: string) {
  const safe = message.replace(/[<>&"]/g, c => ({ "<":"&lt;", ">":"&gt;", "&":"&amp;", '"':"&quot;" }[c] || c));
  const bg = ok ? "#ecfdf3" : "#fef2f2";
  const fg = ok ? "#166534" : "#991b1b";
  return new Response(`<!doctype html><html lang="es"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>Soluciona</title><body style="font-family:system-ui;background:#f4f7fc;color:#14213d;display:grid;place-items:center;min-height:100vh;margin:0"><main style="max-width:520px;padding:32px;text-align:center"><div style="background:${bg};color:${fg};padding:24px;border-radius:20px"><h1>${ok ? "✓ Mercado Pago conectado" : "No pudimos conectar Mercado Pago"}</h1><p>${safe}</p></div><p>Ya podés volver a Soluciona.</p><a href="soluciona://mp-connected" style="display:inline-block;background:#2563eb;color:white;text-decoration:none;padding:14px 20px;border-radius:14px;font-weight:800">Volver a Soluciona</a></main><script>setTimeout(()=>{location.href='soluciona://mp-connected'},700)</script></body></html>`, {
    status: ok ? 200 : 400,
    headers: { "content-type": "text/html; charset=utf-8" }
  });
}

function json(data: any, status = 200) {
  return new Response(JSON.stringify(data), {
    status,
    headers: {
      "content-type": "application/json; charset=utf-8",
      "cache-control": "no-store"
    }
  });
}

function httpError(status: number, code: string, message: string) {
  const e: any = new Error(message);
  e.status = status;
  e.code = code;
  return e;
}

function base64UrlJson(value: any) {
  return base64UrlBytes(new TextEncoder().encode(JSON.stringify(value)));
}

function base64UrlBytes(bytes: Uint8Array) {
  return bytesToBase64(bytes).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/g, "");
}

function bytesToBase64(bytes: Uint8Array) {
  let binary = "";
  for (const b of bytes) binary += String.fromCharCode(b);
  return btoa(binary);
}

function base64ToBytes(value: string) {
  const binary = atob(value);
  const bytes = new Uint8Array(binary.length);
  for (let i = 0; i < binary.length; i++) bytes[i] = binary.charCodeAt(i);
  return bytes;
}

function bytesToHex(bytes: Uint8Array) {
  return Array.from(bytes, b => b.toString(16).padStart(2, "0")).join("");
}
