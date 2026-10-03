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

async function firestore(request: Request, env: Env, path: string, init: RequestInit = {}) {
  const response = await fetch(`https://firestore.googleapis.com/v1/projects/${env.FIREBASE_PROJECT_ID}/databases/(default)/documents${path === ":commit" ? ":commit" : "/"+path}`, {
    ...init, headers: {authorization: request.headers.get('authorization') || '', 'content-type': 'application/json'}, signal: AbortSignal.timeout(20000)
  });
  if(response.status === 404) return null;
  if(!response.ok) throw new Error(response.status === 409 || response.status === 412 ? 'REGISTRO_MODIFICADO_REINTENTA' : 'ACCESO_O_SINCRONIZACION_DENEGADOS');
  return response.json() as Promise<any>;
}
function str(value: string) { return {stringValue:value}; }
function validId(value: string) { return /^[A-Za-z0-9_-]{1,128}$/.test(value); }
async function limitedForm(request: Request) {
  const limit=10*1024*1024+32768;
  if(Number(request.headers.get('content-length')||0)>limit)throw new Error('ARCHIVO_SUPERA_10_MB');
  if(!request.body)throw new Error('ARCHIVO_REQUERIDO');
  const reader=request.body.getReader(),parts:Uint8Array[]=[];let total=0;
  while(true){const {value,done}=await reader.read();if(done)break;total+=value.byteLength;if(total>limit){await reader.cancel();throw new Error('ARCHIVO_SUPERA_10_MB');}parts.push(value);}
  const bytes=new Uint8Array(total);let offset=0;for(const part of parts){bytes.set(part,offset);offset+=part.length;}
  return new Request(request.url,{method:'POST',headers:request.headers,body:bytes}).formData();
}
async function uploadExtra(request: Request, env: Env, uid: string, identity: boolean) {
  const form=await limitedForm(request),file=form.get('file'),type=String(form.get('type')||''),requestId=String(form.get('requestId')||'');
  if(!(file instanceof File)||file.size<=0||file.size>10*1024*1024)return json({error:'ARCHIVO_INVALIDO_MAX_10_MB'},400);
  if(!['image/jpeg','image/png','image/webp'].includes(file.type))return json({error:'SE_REQUIERE_IMAGEN_JPG_PNG_WEBP'},415);
  const data=new Uint8Array(await file.arrayBuffer());
  const valid=file.type==='image/jpeg'?data[0]===255&&data[1]===216&&data[2]===255:file.type==='image/png'?data[0]===137&&data[1]===80&&data[2]===78&&data[3]===71:String.fromCharCode(...data.slice(0,4))==='RIFF'&&String.fromCharCode(...data.slice(8,12))==='WEBP';
  if(!valid)return json({error:'IMAGEN_INVALIDA'},415);
  let path:string,old:any,slot:string,key:string;
  if(identity){
    if(!['DNI_FRONT','DNI_BACK'].includes(type)||String(form.get('consent'))!=='true')return json({error:'CONSENTIMIENTO_REQUERIDO'},400);
    path=`identity_submissions/${uid}`;old=await firestore(request,env,path);slot=type==='DNI_FRONT'?'front':'back';key=`identities/${uid}/${crypto.randomUUID()}`;
  }else{
    if(!validId(requestId))return json({error:'PEDIDO_INVALIDO'},400);
    path=`service_requests/${requestId}`;old=await firestore(request,env,path);
    if(old?.fields?.clientUid?.stringValue!==uid||old?.fields?.status?.stringValue!=='REQUESTED')return json({error:'SOLO_CLIENTE_ANTES_DE_ACEPTAR'},403);
    if(Number(old.fields.photoCount?.integerValue||0)>=6)return json({error:'MAXIMO_6_FOTOS'},409);
    slot=crypto.randomUUID();key=`request-photos/${requestId}/${uid}/${slot}`;
  }
  await env.DOCUMENTS.put(key,data,{httpMetadata:{contentType:file.type},customMetadata:{ownerUid:uid,type:identity?type:'REQUEST_PHOTO'}});
  const name=`projects/${env.FIREBASE_PROJECT_ID}/databases/(default)/documents${path === ":commit" ? ":commit" : "/"+path}`,now={timestampValue:new Date().toISOString()},fileFields={storageKey:str(key),contentType:str(file.type),size:{integerValue:String(file.size)}};
  let writes:any[];
  if(identity){const fields={...(old?.fields||{}),uid:str(uid),[slot]:{mapValue:{fields:fileFields}},status:str('PENDING'),reviewNote:str(''),consentVersion:str('identity-v1'),consentedAt:now,updatedAt:now};
    writes=[{update:{name,fields},currentDocument:old?{updateTime:old.updateTime}:{exists:false}}];
  }else{writes=[{update:{name,fields:{photoCount:{integerValue:String(Number(old.fields.photoCount?.integerValue||0)+1)}}},updateMask:{fieldPaths:['photoCount']},currentDocument:{updateTime:old.updateTime}},
    {update:{name:`${name}/attachments/${slot}`,fields:{...fileFields,ownerUid:str(uid),createdAt:now}},currentDocument:{exists:false}}];}
  try{await firestore(request,env,':commit',{method:'POST',body:JSON.stringify({writes})});}catch(e){await env.DOCUMENTS.delete(key);throw e;}
  const previous=old?.fields?.[slot]?.mapValue?.fields?.storageKey?.stringValue;
  if(identity&&previous?.startsWith(`identities/${uid}/`))await env.DOCUMENTS.delete(previous).catch(()=>{});
  return json({ok:true,storageKey:key,requestId,status:'PENDING'},201);
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
  if(payload.email_verified !== true) throw new Error('CORREO_NO_VERIFICADO');
  const user = await firestore(request,env,`users/${uid}`);
  if(!user || ['SUSPENDED','DEACTIVATED'].includes(user.fields?.accountStatus?.stringValue)) throw new Error('CUENTA_NO_HABILITADA');
  const validAfter = Number(user.fields?.tokensValidAfter?.integerValue || 0);
  if(Number(payload.auth_time || 0) < validAfter) throw new Error('SESION_REVOCADA');
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

async function getOwnDocument(request: Request, env: Env, uid: string, key: string, isAdmin: boolean) {
  let shared = false;
  if(key.startsWith('request-photos/')) { const parts=key.split('/'); if(parts.length===4&&validId(parts[1])&&validId(parts[3])) { const doc=await firestore(request,env,`service_requests/${parts[1]}/attachments/${parts[3]}`); shared=doc?.fields?.storageKey?.stringValue===key; } }
  if (!shared && !key.startsWith(`identities/${uid}/`) && !key.startsWith(`professionals/${uid}/`)) return json({ error: 'FORBIDDEN' }, 403);
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
      const isAdmin = false;

      if(request.method === 'POST' && url.pathname === '/v1/identity-documents') return await uploadExtra(request,env,uid,true);
      if(request.method === 'POST' && url.pathname === '/v1/request-photos') return await uploadExtra(request,env,uid,false);
      if (request.method === 'POST' && url.pathname === '/v1/professional-documents') {
        return await uploadProfessionalDocument(request, env, uid);
      }

      if (request.method === 'GET' && url.pathname === '/v1/document') {
        const key = url.searchParams.get('key') || '';
        if (!key) return json({ error: 'KEY_REQUIRED' }, 400);
        return await getOwnDocument(request, env, uid, key, isAdmin);
      }

      return json({ error: 'NOT_FOUND' }, 404);
    } catch (error) {
      const message = error instanceof Error ? error.message : 'UNAUTHORIZED';
      const status = message === 'ARCHIVO_SUPERA_10_MB' ? 413 : message === 'REGISTRO_MODIFICADO_REINTENTA' ? 409 : message === 'ACCESO_O_SINCRONIZACION_DENEGADOS' ? 403 : 401;
      return json({ error: message }, status);
    }
  }
};
