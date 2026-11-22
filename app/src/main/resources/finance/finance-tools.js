/* Pure calculations shared by the offline finance UI. No account or network access. */
(function(){
"use strict";
const copy = value => JSON.parse(JSON.stringify(value));
const numeric = value => {
  let text=String(value == null ? "0" : value).trim().replace(/\s/g,"");
  if(text.includes(",")) text=text.replace(/\./g,"").replace(",", ".");
  return Number(text);
};
const cents = value => Math.round(numeric(value) * 100);
const cash = value => Math.round(value) / 100;
const noteKey = value => String(value || "").trim().toLocaleLowerCase("tr-TR").replace(/\s+/g, " ");

function date(value){
  let iso = String(value || "").trim();
  const local = /^(\d{1,2})[./](\d{1,2})[./](\d{4})$/.exec(iso);
  if(local) iso = `${local[3]}-${local[2].padStart(2,"0")}-${local[1].padStart(2,"0")}`;
  if(!/^\d{4}-\d{2}-\d{2}$/.test(iso)) throw new Error("Tarih YYYY-MM-DD veya GG.AA.YYYY biçiminde olmalı");
  const parsed = new Date(iso + "T12:00:00");
  if(!Number.isFinite(parsed.getTime()) || parsed.getFullYear() !== Number(iso.slice(0,4))
      || parsed.getMonth()+1 !== Number(iso.slice(5,7)) || parsed.getDate() !== Number(iso.slice(8)))
    throw new Error("Geçersiz tarih: " + iso);
  return iso;
}
function monthAfter(iso, offset){
  const checked = date(iso), value = new Date(checked + "T12:00:00"), day = value.getDate();
  value.setDate(1); value.setMonth(value.getMonth() + offset);
  value.setDate(Math.min(day, new Date(value.getFullYear(), value.getMonth()+1, 0).getDate()));
  if(value.getFullYear() > 9999) throw new Error("Taksit tarihleri desteklenen aralığı aşıyor");
  return `${String(value.getFullYear()).padStart(4,"0")}-${String(value.getMonth()+1).padStart(2,"0")}-${String(value.getDate()).padStart(2,"0")}`;
}
function installments(plan){
  const total = cents(plan.amount), count = numeric(plan.count), paid = numeric(plan.paidCount);
  if(!Number.isSafeInteger(total) || total <= 0 || !Number.isInteger(count) || count < 1 || count > 1200
      || !Number.isInteger(paid) || paid < 0 || paid > count || total < count)
    throw new Error("Taksit sayısını, ödenen adedi ve toplam tutarı kontrol et");
  const base = Math.floor(total / count);
  return Array.from({length:count}, (_, index) => ({
    date:monthAfter(plan.firstDue, index), amount:cash(index === count-1 ? total-base*(count-1) : base),
    number:index+1, paid:index < paid, planId:plan.id, cardId:plan.cardId, name:plan.name
  }));
}

function applyFlow(accounts, original, replacement){
  const candidate = copy(accounts);
  const account = id => {
    const found = candidate.find(item => item.id === id);
    if(!found) throw new Error("Hareketin bağlı hesabı bulunamadı");
    return found;
  };
  function change(item, direction){
    if(!item || !item.accountId) return;
    const from = account(item.accountId), amount = cents(item.amount);
    if(!Number.isSafeInteger(amount) || amount <= 0) throw new Error("Geçerli bir hareket tutarı gerekiyor");
    if(item.type === "transfer"){
      if(item.accountId === item.targetAccountId) throw new Error("Kaynak ve hedef hesap farklı olmalı");
      const to = account(item.targetAccountId), reserved = cents(item.restrictedAmount || 0);
      if(!Number.isSafeInteger(reserved) || reserved < 0 || reserved > amount)
        throw new Error("Aktarılan harçlık toplam tutarı aşamaz");
      from.balance = cash(cents(from.balance)-direction*amount);
      from.kisitli = cash(cents(from.kisitli || 0)-direction*reserved);
      to.balance = cash(cents(to.balance)+direction*amount);
      to.kisitli = cash(cents(to.kisitli || 0)+direction*reserved);
    } else {
      if(!["in","out"].includes(item.type)) throw new Error("Geçersiz hareket yönü");
      from.balance = cash(cents(from.balance)+direction*(item.type === "in" ? amount : -amount));
      if(direction < 0) from.kisitli = cash(cents(from.kisitli || 0)+cents(item.restrictedUsed || 0));
      else {
        item.restrictedUsed = cash(Math.max(0, cents(from.kisitli || 0)-cents(from.balance)));
        from.kisitli = cash(cents(from.kisitli || 0)-cents(item.restrictedUsed));
      }
    }
  }
  change(original, -1); change(replacement, 1);
  for(const item of candidate){
    if(!Number.isFinite(numeric(item.balance)) || numeric(item.balance) < 0 || numeric(item.kisitli || 0) < 0
        || numeric(item.kisitli || 0) > numeric(item.balance))
      throw new Error("Hesap bakiyesi veya harçlık tutarı bu işleme yetmiyor");
  }
  return candidate;
}
function accountRows(flows, accountId, from, to){
  return flows.filter(item => item.date >= from && item.date <= to)
    .filter(item => item.accountId === accountId || (item.type === "transfer" && item.targetAccountId === accountId))
    .map(item => ({id:item.id, date:item.date, amount:numeric(item.amount), note:item.note || "",
      type:item.type === "transfer" ? (item.accountId === accountId ? "out" : "in") : item.type,
      transfer:item.type === "transfer"}));
}
function compare(rows, flows, accountId, from, to){
  date(from); date(to);
  if(from > to) throw new Error("İlk gün son günden sonra olamaz");
  const local = accountRows(flows, accountId, from, to), available = new Set(local.map((item,index) => index));
  const selected = rows.filter(item => item.date >= from && item.date <= to);
  const result = selected.map(item => ({...item, status:"missing", local:null}));
  const exactKey = item => JSON.stringify([item.date,item.type,cents(item.amount),noteKey(item.note)]);
  const amountKey = item => JSON.stringify([item.date,item.type,cents(item.amount)]);
  const descriptionKey = item => JSON.stringify([item.date,item.type,noteKey(item.note)]);
  function match(key, status){
    const buckets = new Map();
    for(const index of available){const value=key(local[index]); if(!buckets.has(value)) buckets.set(value,{items:[],cursor:0});buckets.get(value).items.push(index);}
    for(const item of result.filter(entry => entry.status === "missing")){
      if(status === "amount" && !noteKey(item.note)) continue;
      const bucket=buckets.get(key(item));
      if(!bucket) continue;
      const candidates=status === "matched" ? bucket.items.slice(bucket.cursor,bucket.cursor+1) : bucket.items.filter(index=>available.has(index));
      if(candidates.length && (status === "matched" || candidates.length === 1)){
        const index=candidates[0];bucket.cursor++;available.delete(index);item.local=local[index];item.status=status;
      }
    }
  }
  match(exactKey,"matched");match(amountKey,"description");match(descriptionKey,"amount");
  const net = items => cash(items.reduce((sum,item) => sum+(item.type === "in" ? 1 : -1)*cents(item.amount),0));
  return {rows:result, onlyLocal:[...available].map(index => local[index]), bankNet:net(selected), localNet:net(local),
    difference:cash(cents(net(selected))-cents(net(local))), excluded:rows.length-selected.length,
    matched:result.filter(item => item.status === "matched").length};
}
window.financeTools = {date, monthAfter, installments, applyFlow, accountRows, compare};
})();
