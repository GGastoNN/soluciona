const {test}=require('node:test'),assert=require('node:assert/strict'),vm=require('node:vm'),fs=require('node:fs');
function server(){const docs=new Map(),devices=new Map(),sent=[],writes=[];const ref=path=>({path,collection:name=>({doc:id=>ref(path+'/'+name+'/'+id)}),get:async()=>({exists:docs.has(path),data:()=>docs.get(path)}),set:async v=>docs.set(path,v),delete:async()=>docs.delete(path)});const db={
 doc:ref,
 collection:path=>({
  doc:id=>ref(path+'/'+id),
  where:()=>({limit:()=>({get:async()=>({docs:(devices.get(path)||[]).map((v,i)=>({data:()=>v,ref:ref(path+'/'+i)}))})})})
 }),
 runTransaction:async fn=>fn({
  get:async r=>r.get(),
  update:(r,v)=>{docs.set(r.path,{...docs.get(r.path),...v});writes.push(r.path);},
  set:(r,v)=>{docs.set(r.path,v);writes.push(r.path);}
 })
};
 class HttpsError extends Error{constructor(code,message){super(message);this.code=code;}}
 const context={exports:{},require:name=>{if(name==='firebase-admin/app')return {initializeApp:()=>{}};if(name==='firebase-admin/firestore')return {getFirestore:()=>db,FieldValue:{serverTimestamp:()=>new Date()}};if(name==='firebase-admin/auth')return {getAuth:()=>({getUser:async uid=>({disabled:docs.get('users/'+uid)?.accountStatus==='SUSPENDED',tokensValidAfterTime:'1970-01-01T00:00:00Z'})})};if(name==='firebase-admin/messaging')return {getMessaging:()=>({sendEachForMulticast:async m=>{sent.push(m);return {responses:m.tokens.map(()=>({success:true}))};}})};if(name==='firebase-functions/v2/https')return {onCall:fn=>fn,HttpsError};if(name==='firebase-functions/v2/firestore')return {onDocumentCreated:(o,fn)=>fn,onDocumentUpdated:(o,fn)=>fn};if(name==='firebase-functions/v2')return {setGlobalOptions:()=>{}};if(name==='./budget.cjs')return require('../budget.cjs');return require(name);}};
 vm.createContext(context);vm.runInContext(fs.readFileSync(require.resolve('../index.cjs'),'utf8'),context);return {api:context.exports,docs,devices,sent,writes};
}
test('chat notifications go to recipient and never include chat contents',async()=>{const s=server();s.docs.set('chats/order',{members:['client','professional']});s.docs.set('users/professional',{accountStatus:'ACTIVE'});s.devices.set('users/professional/notification_devices',[{token:'long-registration-token'}]);await s.api.chatNotification({id:'event',params:{requestId:'order'},data:{data:()=>({senderUid:'client',text:'Sensitive secret'})}});assert.equal(s.sent.length,1);assert.equal(s.sent[0].data.recipientUid,'professional');assert.ok(!JSON.stringify(s.sent[0]).includes('Sensitive secret'));});
test('disabled profiles receive no notifications; completed delivery is deduplicated',async()=>{const s=server();s.docs.set('chats/order',{members:['client','professional']});s.docs.set('users/professional',{accountStatus:'SUSPENDED'});s.devices.set('users/professional/notification_devices',[{token:'long-registration-token'}]);const e={id:'event',params:{requestId:'order'},data:{data:()=>({senderUid:'client'})}};await s.api.chatNotification(e);assert.equal(s.sent.length,0);s.docs.set('users/professional',{accountStatus:'ACTIVE'});await s.api.chatNotification(e);await s.api.chatNotification(e);assert.equal(s.sent.length,1);});
test('budget callable writes current proposal and immutable revision together',async()=>{const s=server();s.docs.set('users/pro',{accountStatus:'ACTIVE'});s.docs.set('professionals/pro',{verificationStatus:'APPROVED'});s.docs.set('service_requests/order',{clientUid:'client',professionalUid:'pro',status:'ACCEPTED'});await s.api.offerBudget({auth:{uid:'pro',token:{email_verified:true,auth_time:1000}},data:{requestId:'order',visit:'0',labor:'100',materials:'0',note:'Reparación incluida'}});assert.deepEqual(s.writes,['service_requests/order','service_requests/order/budgets/1']);assert.equal(s.docs.get('service_requests/order').budget.status,'PENDING');assert.equal(s.docs.get('service_requests/order').approvedTotalCents,undefined);});
