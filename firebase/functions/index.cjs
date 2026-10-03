'use strict';
const {initializeApp}=require('firebase-admin/app');
const {getFirestore,FieldValue}=require('firebase-admin/firestore');
const {getAuth}=require('firebase-admin/auth');
const {getMessaging}=require('firebase-admin/messaging');
const {onCall,HttpsError}=require('firebase-functions/v2/https');
const {onDocumentCreated,onDocumentUpdated}=require('firebase-functions/v2/firestore');
const {setGlobalOptions}=require('firebase-functions/v2');
const {proposal,decision}=require('./budget.cjs');
initializeApp();setGlobalOptions({region:'southamerica-east1',maxInstances:10});
const db=getFirestore(),opts={retry:true,timeoutSeconds:540};
async function caller(request) {
 if(!request.auth?.token.email_verified)throw new HttpsError('unauthenticated','Ingresá con correo verificado.');
 const uid=request.auth.uid,auth=await getAuth().getUser(uid);
 const user=(await db.doc(`users/${uid}`).get()).data();
 if(auth.disabled||!user||['SUSPENDED','DEACTIVATED'].includes(user.accountStatus)||Number(request.auth.token.auth_time)<Number(user?.tokensValidAfter||0)||Number(request.auth.token.auth_time)*1000<Date.parse(auth.tokensValidAfterTime))throw new HttpsError('permission-denied','Cuenta bloqueada o sesión revocada.');
 return uid;
}
function serviceRef(input){if(!/^[A-Za-z0-9_-]{1,128}$/.test(input.requestId||''))throw new HttpsError('invalid-argument','Pedido inválido.');return db.doc(`service_requests/${input.requestId}`);}
async function changeBudget(request,respond) {
 const uid=await caller(request),input=request.data||{},ref=serviceRef(input);
 try {
 return await db.runTransaction(async tx=>{
  const snap=await tx.get(ref);if(!snap.exists)throw Error('No encontramos el servicio.');const req=snap.data();
  const actor=(await tx.get(db.doc(`users/${uid}`))).data();
  if(!actor||['SUSPENDED','DEACTIVATED'].includes(actor.accountStatus))throw Error('Cuenta bloqueada.');
  const now=FieldValue.serverTimestamp();let update,history;
  if(respond){const d=decision(req,uid,input);history={...req.budget,status:d.status,respondedAt:now,respondedBy:uid};update={budget:history,approvedTotalCents:d.approvedTotalCents};}
  else {const p=(await tx.get(db.doc(`professionals/${uid}`))).data();if(p?.verificationStatus!=='APPROVED')throw Error('Tu perfil debe estar aprobado.');history={...proposal(req,uid,input),createdAt:now,proposedBy:uid};update={budget:history,budgetRequired:true};}
  tx.update(ref,{...update,updatedAt:now});
  tx.set(ref.collection('budgets').doc(String(history.revision)),history);
  return {ok:true,revision:history.revision};
 });
 }catch(e){if(e instanceof HttpsError)throw e;throw new HttpsError('failed-precondition',e.message||'No se pudo modificar el presupuesto.');}
}
exports.offerBudget=onCall(r=>changeBudget(r,false));exports.respondBudget=onCall(r=>changeBudget(r,true));
async function notify(uid,title,requestId,kind,eventId){
 if(!uid)return;
 const user=(await db.doc(`users/${uid}`).get()).data();if(!user||['SUSPENDED','DEACTIVATED'].includes(user.accountStatus))return;
 const devices=await db.collection(`users/${uid}/notification_devices`).where('enabled','==',true).limit(20).get();
 const rows=devices.docs.filter(d=>typeof d.data().token==='string'&&d.data().token.length>10);if(!rows.length)return;
 const receipt=db.collection('notification_receipts').doc(require('node:crypto').createHash('sha256').update(`${eventId}:${uid}`).digest('hex'));
 if((await receipt.get()).data()?.sent)return;
 const response=await getMessaging().sendEachForMulticast({tokens:rows.map(d=>d.data().token),notification:{title:'Soluciona',body:title},data:{requestId,kind,eventId,recipientUid:uid},android:{priority:'high',notification:{channelId:'soluciona_services',tag:`${requestId}:${kind}`}}});
 for(let i=0;i<rows.length;i++)if(['messaging/registration-token-not-registered','messaging/invalid-registration-token'].includes(response.responses[i].error?.code))await rows[i].ref.delete();
 if(response.responses.some(r=>r.error&&!['messaging/registration-token-not-registered','messaging/invalid-registration-token'].includes(r.error.code)))throw Error('FCM_TEMPORARY_FAILURE');
 await receipt.set({sent:true,createdAt:FieldValue.serverTimestamp(),expireAt:new Date(Date.now()+14*86400000)});
}
exports.newServiceNotification=onDocumentCreated({...opts,document:'service_requests/{requestId}'},async event=>{
 const req=event.data.data();if(req.status!=='REQUESTED')return;
 if(req.preferredProfessionalUid){const p=(await db.doc(`professionals/${req.preferredProfessionalUid}`).get()).data();if(p?.verificationStatus==='APPROVED'&&p.availability&&p.services?.includes(req.categoryId)&&p.zones?.includes(req.zoneId))await notify(req.preferredProfessionalUid,'Tenés un nuevo pedido compatible.',event.params.requestId,'REQUEST',event.id);return;}
 let cursor=null;
 while(true){let q=db.collection('professionals').where('verificationStatus','==','APPROVED').where('availability','==',true).where('zones','array-contains',req.zoneId).orderBy('__name__').limit(100);if(cursor)q=q.startAfter(cursor);const page=await q.get();if(page.empty)break;
  for(const d of page.docs)if(d.data().services?.includes(req.categoryId))await notify(d.id,'Hay un nuevo pedido en tu zona.',event.params.requestId,'REQUEST',event.id);
  cursor=page.docs.at(-1);if(page.size<100)break;
 }
});
exports.serviceChangeNotification=onDocumentUpdated({...opts,document:'service_requests/{requestId}'},async event=>{
 const before=event.data.before.data(),after=event.data.after.data(),id=event.params.requestId;
 if(before.status!==after.status){const labels={ACCEPTED:'Un profesional aceptó tu pedido.',ON_THE_WAY:'El profesional está en camino.',IN_PROGRESS:'El trabajo comenzó.',AWAITING_PAYMENT:'Tenés un cobro pendiente.',PAID:'El pago fue aprobado.',COMPLETED:'El servicio se completó.',CANCELLED:'El servicio fue cancelado.',PAYMENT_REVIEW:'Hay una actualización del pago.'};const label=after.status==='IN_PROGRESS'&&['AWAITING_PAYMENT','PAYMENT_REVIEW'].includes(before.status)?'El pago no se completó. Revisá el cobro.':labels[after.status];if(label){await notify(after.clientUid,label,id,'STATUS',event.id);await notify(after.professionalUid,label,id,'STATUS',event.id);}}
 if(before.status===after.status&&before.paymentStatus!==after.paymentStatus&&['REJECTED','CANCELED','EXPIRED','REFUNDED'].includes(after.paymentStatus)){await notify(after.clientUid,'Hay una actualización del pago. Revisá el cobro.',id,'PAYMENT',event.id+'payment');await notify(after.professionalUid,'Hay una actualización del pago. Revisá el cobro.',id,'PAYMENT',event.id+'payment');}
 if(after.budget?.revision!==before.budget?.revision)await notify(after.clientUid,'Tenés un presupuesto para revisar.',id,'BUDGET',event.id+'budget');
 else if(after.budget?.status!==before.budget?.status&&after.budget)await notify(after.professionalUid,'El cliente respondió al presupuesto.',id,'BUDGET',event.id+'response');
});
exports.chatNotification=onDocumentCreated({...opts,document:'chats/{requestId}/messages/{messageId}'},async event=>{
 const msg=event.data.data(),chat=(await db.doc(`chats/${event.params.requestId}`).get()).data();
 for(const uid of chat?.members||[])if(uid!==msg.senderUid)await notify(uid,'Tenés un nuevo mensaje.',event.params.requestId,'CHAT',event.id);
});
exports.identityNotification=onDocumentUpdated({...opts,document:'identity_submissions/{uid}'},async event=>{
 const a=event.data.after.data(),b=event.data.before.data();if(a.status!==b.status&&['APPROVED','REJECTED'].includes(a.status))await notify(event.params.uid,a.status==='APPROVED'?'Tu documentación fue revisada.':'Revisá el resultado de tu documentación.','','IDENTITY',event.id);
});
