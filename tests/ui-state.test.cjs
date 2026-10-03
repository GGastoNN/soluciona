const {test}=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const vm=require('node:vm');
const path=require('node:path');
const UI=require('../app/src/main/assets/ui-runtime.js');
function app(){
  const nativeCalls=[],elements={chatText:{value:''}};
  const native=new Proxy({}, {get:(_,name)=>(...args)=>{nativeCalls.push([name,...args]);return true;}});
  const context=vm.createContext({SolucionaUI:UI,window:{SolucionaNative:native},document:{querySelector:s=>elements[s.slice(1)]},console});
  const html=fs.readFileSync(path.join(__dirname,'../app/src/main/assets/index.html'),'utf8');
  const inline=[...html.matchAll(/<script[^>]*>([\s\S]*?)<\/script>/g)].map(m=>m[1]).find(s=>s.includes('const state='));
  vm.runInContext(inline.slice(0,inline.indexOf('if(!N||')),context);
  vm.runInContext("render=()=>{};toast=()=>{};state.profile={uid:'client',role:'CLIENT'};state.screen='chat';state.chatId='service';",context);
  return {context,nativeCalls,elements,run:s=>vm.runInContext(s,context)};
}
test('live-window updates keep older messages, deduplicate and apply corrected timestamps',()=>{
  const a=[{id:'old',createdAt:1,text:'old'},{id:'new',createdAt:0,text:'pending'}];
  const b=[{id:'new',createdAt:3,text:'sent'},{id:'mid',createdAt:2,text:'middle'}];
  const merged=UI.mergeMessages(a,b);
  assert.deepEqual(merged.map(x=>x.id),['old','mid','new']);assert.equal(merged[2].text,'sent');
  assert.equal(a[1].text,'pending');
});
test('older page and subsequent realtime event do not drop history',()=>{
  const a=app();
  a.run("window.solucionaNativeEvent('messages',true,{requestId:'service',ownerUid:'client',messages:[{id:'latest',createdAt:5}],hasMore:true})");
  a.run("window.solucionaNativeEvent('messages',true,{requestId:'service',ownerUid:'client',messages:[{id:'old',createdAt:1}],older:true,hasMore:false})");
  a.run("window.solucionaNativeEvent('messages',true,{requestId:'service',ownerUid:'client',messages:[{id:'new',createdAt:6}],hasMore:false})");
  assert.equal(a.run('state.messages.map(x=>x.id).join(",")'),'old,latest,new');
});
test('messages for previous chat or previous account are ignored',()=>{
  const a=app();
  a.run("window.solucionaNativeEvent('messages',true,{requestId:'previous',ownerUid:'client',messages:[{id:'wrong'}]})");
  a.run("window.solucionaNativeEvent('messages',true,{requestId:'service',ownerUid:'previous-user',messages:[{id:'wrong'}]})");
  assert.equal(a.run('state.messages.length'),0);
});
test('history appends in query order and ignores a previous account',()=>{
  const a=app();
  a.run("applyHistory('clientRequests',{ownerUid:'client',requests:[{id:'a'},{id:'b'}],hasMore:true})");
  a.run("applyHistory('clientRequests',{ownerUid:'client',requests:[{id:'b',status:'COMPLETED'},{id:'c'}],append:true,hasMore:false})");
  a.run("applyHistory('clientRequests',{ownerUid:'other',requests:[{id:'wrong'}]})");
  assert.equal(a.run('state.requests.map(x=>x.id).join(",")'),'a,b,c');
  assert.equal(a.run('state.requests[1].status'),'COMPLETED');assert.equal(a.run('historyPages.clientRequests.hasMore'),false);
});
test('load more disables double requests; failure clears loading for retry',()=>{
  const a=app();a.run("loadMoreHistory('clientRequests');loadMoreHistory('clientRequests')");
  assert.equal(a.nativeCalls.filter(x=>x[0]==='loadMoreClientRequests').length,1);
  a.run("window.solucionaNativeEvent('clientRequests',false,{message:'Sin conexión'})");
  assert.equal(a.run('historyPages.clientRequests.loading'),false);
  assert.equal(a.run('historyPages.clientRequests.error'),'Sin conexión');
});
test('send confirmation keeps a newer draft and prevents double sends',()=>{
  const a=app();a.elements.chatText.value='Primer mensaje';a.run('sendChat();sendChat()');
  assert.equal(a.nativeCalls.filter(x=>x[0]==='sendMessage').length,1);
  a.elements.chatText.value='Siguiente borrador';
  a.run("window.solucionaNativeEvent('messageSent',true,{requestId:'service',ownerUid:'client'})");
  assert.equal(a.elements.chatText.value,'Siguiente borrador');assert.equal(a.run('pendingChatSend'),null);
});
test('send failure keeps draft and makes retry possible',()=>{
  const a=app();a.elements.chatText.value='Sin enviar';a.run('sendChat()');
  a.run("window.solucionaNativeEvent('messageSent',false,{requestId:'service',ownerUid:'client',message:'Sin conexión'})");
  assert.equal(a.elements.chatText.value,'Sin enviar');a.run('sendChat()');
  assert.equal(a.nativeCalls.filter(x=>x[0]==='sendMessage').length,2);
});
test('dynamic request IDs remain data within inline actions',()=>{
  const a=app();const id="x');globalThis.pwned=true;//\"<";
  const encoded=a.context.jsAction('openChat',id);
  const decoded=encoded.replace(/&quot;/g,'"').replace(/&#39;/g,"'").replace(/&lt;/g,'<').replace(/&gt;/g,'>').replace(/&amp;/g,'&');
  let received;vm.runInNewContext(decoded,{openChat:value=>received=value});assert.equal(received,id);
  assert.ok(!encoded.includes('<'));assert.ok(!encoded.includes('"'));
});
test('empty compatible block still offers the next page of open requests',()=>{
  const a=app();
  a.run("state.profile.role='PROFESSIONAL';state.profile.verificationStatus='APPROVED';applyHistory('openRequests',{ownerUid:'client',requests:[],hasMore:true})");
  const html=a.run('proHome()');assert.ok(html.includes('Cargar más'));assert.ok(html.includes('este bloque'));
  a.run("loadMoreHistory('openRequests')");assert.equal(a.nativeCalls.at(-1)[0],'loadMoreOpenRequests');
});
