const {test}=require('node:test');
const assert=require('node:assert/strict');
const {patchChildren}=require('../app/src/main/assets/ui-runtime.js');
// Minimal DOM adapter tests node identity and field properties; device focus/keyboard
// behavior remains part of the Android manual validation checklist.
class Element {
  constructor(name,attrs={},children=[]){
    this.nodeType=1;this.nodeName=name.toUpperCase();this.attrs={...attrs};this.childNodes=[];this.value=attrs.value||'';
    this.selectionStart=0;this.selectionEnd=0;children.forEach(c=>this.appendChild(c));
    this.classList={contains:x=>(this.attrs.class||'').split(' ').includes(x),add:x=>{if(!this.classList.contains(x))this.attrs.class=((this.attrs.class||'')+' '+x).trim()}};
  }
  get id(){return this.attrs.id||'';}
  get attributes(){return Object.entries(this.attrs).map(([name,value])=>({name,value}));}
  get lastChild(){return this.childNodes.at(-1);}
  get options(){return this.childNodes.filter(c=>c.nodeName==='OPTION');}
  getAttribute(n){return this.attrs[n]??null;}
  hasAttribute(n){return n in this.attrs;}
  setAttribute(n,v){this.attrs[n]=String(v);}
  removeAttribute(n){delete this.attrs[n];}
  matches(selectors){return selectors.split(',').some(s=>s.startsWith('.')?this.classList.contains(s.slice(1)):s.toUpperCase()===this.nodeName);}
  appendChild(n){n.remove();n.parent=this;this.childNodes.push(n);return n;}
  insertBefore(n,c){n.remove();n.parent=this;this.childNodes.splice(this.childNodes.indexOf(c),0,n);}
  replaceWith(n){const p=this.parent;const i=p.childNodes.indexOf(this);this.parent=null;n.parent=p;p.childNodes[i]=n;}
  remove(){if(this.parent){const p=this.parent;p.childNodes.splice(p.childNodes.indexOf(this),1);this.parent=null;}}
  cloneNode(deep){return new Element(this.nodeName,this.attrs,deep?this.childNodes.map(c=>c.cloneNode(true)):[]);}
}
const el=(name,attrs={},children=[])=>new Element(name,attrs,children);
test('message updates preserve composer node, draft and cursor',()=>{
  const field=el('input',{id:'chatText'});field.value='Texto en curso';field.selectionStart=3;field.selectionEnd=7;
  const composer=el('div',{id:'chatComposer'},[field]);
  const root=el('main',{},[el('div',{id:'chatMessages'},[el('div',{'data-key':'a'})]),composer]);
  const desired=el('main',{},[el('div',{id:'chatMessages'},[el('div',{'data-key':'a'}),el('div',{'data-key':'b'})]),el('div',{id:'chatComposer'},[el('input',{id:'chatText',value:''})])]);
  patchChildren(root,desired);
  assert.equal(root.childNodes[1],composer);assert.equal(composer.childNodes[0],field);
  assert.equal(field.value,'Texto en curso');assert.equal(field.selectionStart,3);assert.equal(field.selectionEnd,7);
});
test('professional category selection survives refreshed catalog and adds new categories',()=>{
  const chip=el('button',{'data-id':'electricidad',class:'chip on','aria-pressed':'true'});
  const root=el('div',{id:'proServices'},[chip]);
  const desired=el('div',{id:'proServices'},[el('button',{'data-id':'electricidad',class:'chip','aria-pressed':'false'}),el('button',{'data-id':'plomeria',class:'chip','aria-pressed':'false'})]);
  patchChildren(root,desired);
  assert.equal(root.childNodes[0],chip);assert.equal(chip.classList.contains('on'),true);
  assert.equal(chip.getAttribute('aria-pressed'),'true');assert.equal(root.childNodes.length,2);
});
test('zone options update without discarding a still-valid selected value',()=>{
  const select=el('select',{id:'zone'},[el('option',{value:'north'}),el('option',{value:'south'})]);select.value='south';
  const root=el('div',{},[select]);
  patchChildren(root,el('div',{},[el('select',{id:'zone'},[el('option',{value:'north'}),el('option',{value:'south'}),el('option',{value:'east'})])]));
  assert.equal(root.childNodes[0],select);assert.equal(select.options.length,3);assert.equal(select.value,'south');
});
