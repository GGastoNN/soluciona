'use strict';
function money(value) {
  const s=String(value??'').trim().replace(',','.');
  if(!/^\d{1,8}(\.\d{1,2})?$/.test(s))throw Error('Ingresá importes positivos con hasta dos decimales.');
  const [whole,decimal='']=s.split('.');const cents=Number(whole)*100+Number(decimal.padEnd(2,'0'));
  if(!Number.isSafeInteger(cents)||cents>100000000)throw Error('El importe supera el máximo permitido.');
  return cents;
}
function proposal(req,uid,input) {
  if(req.professionalUid!==uid)throw Error('No estás asignado al servicio.');
  if(!['ACCEPTED','ON_THE_WAY','IN_PROGRESS'].includes(req.status))throw Error('No se puede presupuestar en este estado.');
  if(req.budget?.status==='PENDING')throw Error('El cliente debe responder el presupuesto pendiente.');
  const additional=req.status==='IN_PROGRESS';
  if(additional&&!(req.approvedTotalCents>0))throw Error('Primero se necesita un presupuesto aprobado.');
  const items={visitCents:money(input.visit),laborCents:money(input.labor),materialsCents:money(input.materials)};
  const amount=Object.values(items).reduce((a,b)=>a+b,0),note=String(input.note||'').trim();
  if(amount<=0||amount+(additional?req.approvedTotalCents:0)>100000000)throw Error('El total debe ser mayor a cero y no superar $1.000.000.');
  if(note.length<5||note.length>1000)throw Error('Describí el alcance en 5 a 1000 caracteres.');
  return {...items,amountCents:amount,totalCents:amount+(additional?req.approvedTotalCents:0),additional,note,status:'PENDING',revision:(req.budget?.revision||0)+1};
}
function decision(req,uid,input) {
  if(req.clientUid!==uid)throw Error('Solo el cliente puede responder.');
  if(!['ACCEPTED','ON_THE_WAY','IN_PROGRESS'].includes(req.status))throw Error('El servicio ya no admite cambios de presupuesto.');
  if(req.budget?.status!=='PENDING'||req.budget.revision!==input.revision)throw Error('El presupuesto cambió. Actualizá antes de responder.');
  if(typeof input.accept!=='boolean')throw Error('Respuesta inválida.');
  return {status:input.accept?'APPROVED':'REJECTED',approvedTotalCents:input.accept?req.budget.totalCents:(req.approvedTotalCents||0)};
}
module.exports={money,proposal,decision};
