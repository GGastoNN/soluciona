(function(root){
  'use strict';
  function key(node){return node.nodeType===1 ? node.id || node.getAttribute('data-key') || node.getAttribute('data-id') || '' : '';}
  function compatible(a,b){return a.nodeType===b.nodeType && a.nodeName===b.nodeName && key(a)===key(b);}
  function patchChildren(target,source){
    const desired=Array.from(source.childNodes);
    desired.forEach((next,i)=>{
      let current=target.childNodes[i];
      if(current && !compatible(current,next) && key(next)){
        const matching=Array.from(target.childNodes).slice(i+1).find(n=>compatible(n,next));
        if(matching){target.insertBefore(matching,current);current=matching;}
      }
      if(!current){target.appendChild(next.cloneNode(true));return;}
      if(!compatible(current,next)){current.replaceWith(next.cloneNode(true));return;}
      if(current.nodeType===3){if(current.data!==next.data)current.data=next.data;return;}
      if(current.nodeType!==1)return;
      const field=current.matches('input,textarea,select');
      const selectedChip=current.matches('.chip') && current.classList.contains('on');
      Array.from(current.attributes).forEach(a=>{
        if(!next.hasAttribute(a.name) && !(field && ['value','checked'].includes(a.name)))current.removeAttribute(a.name);
      });
      Array.from(next.attributes).forEach(a=>{
        if(!(field && ['value','checked'].includes(a.name)) && current.getAttribute(a.name)!==a.value)current.setAttribute(a.name,a.value);
      });
      if(selectedChip){current.classList.add('on');current.setAttribute('aria-pressed','true');}
      if(current.matches('input,textarea'))return;
      if(current.matches('select')){
        const value=current.value;
        patchChildren(current,next);
        if(Array.from(current.options).some(o=>o.value===value))current.value=value;
        return;
      }
      patchChildren(current,next);
    });
    while(target.childNodes.length>desired.length)target.lastChild.remove();
  }
  function mergeById(existing,incoming){
    const items=new Map(existing.map(item=>[item.id,item]));
    incoming.forEach(item=>items.set(item.id,item));
    return Array.from(items.values());
  }
  function mergeMessages(existing,incoming){
    return mergeById(existing,incoming).sort((a,b)=>(a.createdAt||0)-(b.createdAt||0) || String(a.id).localeCompare(String(b.id)));
  }
  const api={patchChildren,mergeById,mergeMessages};
  if(typeof module==='object' && module.exports)module.exports=api;
  else root.SolucionaUI=api;
})(typeof window==='object'?window:globalThis);
