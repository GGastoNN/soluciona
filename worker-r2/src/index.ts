import { createRemoteJWKSet, jwtVerify } from 'jose';

export interface Env {
  DOCUMENTS: R2Bucket;
  FIREBASE_PROJECT_ID: string;
  MAX_UPLOAD_BYTES?: string;
}

const jwks = createRemoteJWKSet(
  new URL('https://www.googleapis.com/service_accounts/v1/jwk/securetoken@system.gserviceaccount.com')
);

function json(data: unknown, status = 200) {
  return new Response(JSON.stringify(data), {
    status,
    headers: {
      'content-type': 'application/json; charset=utf-8',
      'cache-control': 'no-store'
    }
  });
}

function safeName(value: string) {
  return value.normalize('NFKD').replace(/[^a-zA-Z0-9._-]/g, '-').replace(/-+/g, '-').slice(0, 100);
}

function documentFolder(type: string) {
  switch (type) {
    case 'LICENSE': return 'licenses';
    case 'BACKGROUND_CHECK': return 'background-checks';
    case 'INSURANCE': return 'insurance';
    case 'PROFILE': return 'profile';
    default: return null;
  }
}

async function authenticate(request: Request, env: Env) {
  const header = request.headers.get('authorization') || '';
  const token = header.startsWith('Bearer ') ? header.slice(7) : '';
  if (!token) throw new Error('AUTH_REQUIRED');

  const { payload } = await jwtVerify(token, jwks, {
    issuer: `https://securetoken.google.com/${env.FIREBASE_PROJECT_ID}`,
    audience: env.FIREBASE_PROJECT_ID
  });
  const uid = String(payload.sub || '');
  if (!uid) throw new Error('INVALID_TOKEN');
  return { uid, claims: payload };
}

async function uploadProfessionalDocument(request: Request, env: Env, uid: string) {
  const form = await request.formData();
  const file = form.get('file');
  const type = String(form.get('type') || '').toUpperCase();
  const folder = documentFolder(type);
  if (!folder) return json({ error: 'INVALID_DOCUMENT_TYPE' }, 400);
  if (!(file instanceof File)) return json({ error: 'FILE_REQUIRED' }, 400);

  const max = Number(env.MAX_UPLOAD_BYTES || 10 * 1024 * 1024);
  if (file.size <= 0 || file.size > max) return json({ error: 'FILE_TOO_LARGE', maxBytes: max }, 413);

  const allowed = new Set(['application/pdf', 'image/jpeg', 'image/png', 'image/webp']);
  if (!allowed.has(file.type)) return json({ error: 'UNSUPPORTED_MEDIA_TYPE' }, 415);

  const key = `professionals/${uid}/${folder}/${crypto.randomUUID()}-${safeName(file.name || 'document')}`;
  await env.DOCUMENTS.put(key, file.stream(), {
    httpMetadata: { contentType: file.type },
    customMetadata: { ownerUid: uid, type, originalName: file.name || '' }
  });

  return json({
    ok: true,
    storageKey: key,
    type,
    size: file.size,
    contentType: file.type,
    status: 'PENDING'
  }, 201);
}

async function getOwnDocument(env: Env, uid: string, key: string, isAdmin: boolean) {
  if (!isAdmin && !key.startsWith(`professionals/${uid}/`)) return json({ error: 'FORBIDDEN' }, 403);
  const object = await env.DOCUMENTS.get(key);
  if (!object) return json({ error: 'NOT_FOUND' }, 404);
  const headers = new Headers();
  object.writeHttpMetadata(headers);
  headers.set('etag', object.httpEtag);
  headers.set('cache-control', 'private, no-store');
  return new Response(object.body, { headers });
}

export default {
  async fetch(request: Request, env: Env): Promise<Response> {
    const url = new URL(request.url);

    if (url.pathname === '/health') {
      return json({ ok: true, service: 'soluciona-dev-api' });
    }

    try {
      const { uid, claims } = await authenticate(request, env);
      const isAdmin = claims.admin === true;

      if (request.method === 'POST' && url.pathname === '/v1/professional-documents') {
        return uploadProfessionalDocument(request, env, uid);
      }

      if (request.method === 'GET' && url.pathname === '/v1/document') {
        const key = url.searchParams.get('key') || '';
        if (!key) return json({ error: 'KEY_REQUIRED' }, 400);
        return getOwnDocument(env, uid, key, isAdmin);
      }

      return json({ error: 'NOT_FOUND' }, 404);
    } catch (error) {
      const message = error instanceof Error ? error.message : 'UNAUTHORIZED';
      return json({ error: message }, 401);
    }
  }
};
