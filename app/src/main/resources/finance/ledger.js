/* Adapted from the user-provided takip-paneli.html.
 * Offline UI contract: financeHost.validate/save/importFile/exportFile return JSON envelopes.
 * Rates are a dated editable snapshot, not a live feed:
 * https://www.tcmb.gov.tr/wps/wcm/connect/TR/TCMB+TR/Main+Menu/Istatistikler/Bankacilik+Verileri/Kredi_Karti_Islemlerinde_Uygulanacak_Azami_Faiz_Oranlari
 */

(function(){
"use strict";

/* =========================================================
   1. VERİ MODELİ
   ========================================================= */
const clone = value => JSON.parse(JSON.stringify(value));
let committed = null;

const DEFAULTS = {
  // TCMB azami aylık oranlar (1 Eylül 2026 itibarıyla), dönem borcuna göre
  ccTiers: [
    { max: 30000,   akdi: 3.25, gecikme: 3.55 },
    { max: 180000,  akdi: 3.75, gecikme: 4.05 },
    { max: null,    akdi: 4.25, gecikme: 4.55 }
  ],
  nakitAvans: { akdi: 4.25, gecikme: 4.55 },
  kurumsal:   { akdi: 4.25, gecikme: 4.55 },
  // BDDK asgari ödeme oranları (Karar 10970)
  asgariEsik: 50000,
  asgariAlt: 20,
  asgariUst: 40,
  // Faiz üzerinden alınan KKDF (%15) + BSMV (%15)
  vergi: 30,
  // Kendi belirlediğin günlük yaşam bütçesi (0 = belirlenmedi)
  gunlukButce: 0
};

const BANKALAR = [
  "Ziraat Bankası","VakıfBank","Halkbank","İş Bankası","Garanti BBVA","Yapı Kredi",
  "Akbank","QNB","DenizBank","TEB","ING","Şekerbank","Alternatif Bank","Fibabanka",
  "Anadolubank","Burgan Bank","Odeabank","HSBC","Enpara","Kuveyt Türk","Albaraka Türk",
  "Türkiye Finans","Ziraat Katılım","Vakıf Katılım","Emlak Katılım","Diğer"
];

const TURLER = {
  kredikarti: "Kredi kartı",
  kredi:      "Kredi / taksit",
  elden:      "Elden borç",
  avans:      "Nakit avans / KMH",
  diger:      "Diğer"
};

let state = { debts: [], flows: [], accounts: [], subs: [], incomes: [], goals: [],
              settings: clone(DEFAULTS) };
let activeTab = "panel";
let openCards = new Set();
let editingDebt = null;
let editSub = null, editAcc = null, editInc = null, editGoal = null, editFlow = null;
let odemeSecim = null;
let formType = "kredikarti";

/* =========================================================
   2. YARDIMCILAR
   ========================================================= */
const uid = () => Math.random().toString(36).slice(2,10);
const num = v => {
  if(typeof v === "number") return Number.isFinite(v) ? v : NaN;
  let text = String(v ?? "").trim().replace(/\s/g, "");
  if(!text) return 0;
  if(text.includes(",")) text = text.replace(/\./g, "").replace(",", ".");
  return /^[+-]?(?:\d+(?:\.\d*)?|\.\d+)$/.test(text) ? Number(text) : NaN;
};
const money = value => Math.round((num(value) + Number.EPSILON) * 100) / 100;
const alacakMi = d => d.type === "elden" && d.yon === "alacak";
const esc = s => String(s ?? "").replace(/[&<>"']/g, c => ({"&":"&amp;","<":"&lt;",">":"&gt;",'"':"&quot;","'":"&#39;"}[c]));

const TRY = new Intl.NumberFormat("tr-TR",{minimumFractionDigits:2,maximumFractionDigits:2});
const fmt  = n => TRY.format(isFinite(n)?n:0) + " ₺";
const fmt0 = n => new Intl.NumberFormat("tr-TR",{maximumFractionDigits:0}).format(isFinite(n)?n:0) + " ₺";
const pct  = n => TRY.format(isFinite(n)?n:0).replace(",00","") + "%";

const today = () => { const d = new Date(); d.setHours(0,0,0,0); return d; };
const parseDate = s => { if(!s) return null; const d = new Date(s+"T00:00:00"); return isNaN(d) ? null : d; };
const isoOf = d => `${d.getFullYear()}-${String(d.getMonth()+1).padStart(2,"0")}-${String(d.getDate()).padStart(2,"0")}`;
const daysUntil = s => { const d = parseDate(s); if(!d) return null; return Math.round((d - today())/86400000); };
const fmtDate = s => { const d = parseDate(s); return d ? d.toLocaleDateString("tr-TR",{day:"2-digit",month:"short",year:"numeric"}) : "—"; };
function addMonth(iso, n=1){
  const d = parseDate(iso); if(!d) return iso;
  const day = d.getDate();
  d.setDate(1); d.setMonth(d.getMonth()+n);
  const last = new Date(d.getFullYear(), d.getMonth()+1, 0).getDate();
  d.setDate(Math.min(day,last));
  return isoOf(d);
}
const monthKey = s => (s||"").slice(0,7);
const thisMonth = () => isoOf(today()).slice(0,7);

/* =========================================================
   3. HESAP MOTORU
   ========================================================= */

// Dönem borcuna göre TCMB kademesi
function ccTier(donemBorcu){
  const t = state.settings.ccTiers;
  for(let i=0; i<t.length; i++){ const tier=t[i]; if(tier.max === null || (i===0 ? donemBorcu < tier.max : donemBorcu <= tier.max)) return tier; }
  return t[t.length-1];
}

// BDDK asgari ödeme oranı — belirleyici olan LİMİT, borç değil
function asgariOrani(limit){
  const s = state.settings;
  return limit > s.asgariEsik ? s.asgariUst : s.asgariAlt;
}

// Bir kredi kartı için tüm türev değerler
function hesaplaKart(d){
  const borc  = num(d.balance);
  const limit = num(d.limit);
  const avans = num(d.avans);

  const oran  = d.asgariOverride != null && d.asgariOverride !== "" ? num(d.asgariOverride) : asgariOrani(limit);
  const asgari = money(borc * oran / 100);

  const tier = d.type === "avans" ? state.settings.nakitAvans : d.kurumsal ? state.settings.kurumsal : ccTier(borc);
  const akdiAzami = tier.akdi;
  const akdi = d.rateOverride != null && d.rateOverride !== "" ? num(d.rateOverride) : akdiAzami;
  const gecikme = d.gecikmeOverride != null && d.gecikmeOverride !== "" ? num(d.gecikmeOverride) : tier.gecikme;

  const vergi = state.settings.vergi / 100;
  const avansOran = state.settings.nakitAvans.akdi;

  // Sadece asgari ödenirse: kalan bakiyeye akdi faiz + vergi
  const kalan = Math.max(0, borc - asgari);
  const faizAlisveris = Math.max(0, kalan - avans) * akdi/100;
  const faizAvans     = Math.min(kalan, avans) * avansOran/100;
  const faizHam  = faizAlisveris + faizAvans;
  const faizVergi = faizHam * vergi;
  const faizToplam = faizHam + faizVergi;
  const sonrakiBorc = kalan + faizToplam;

  // Hiç ödenmezse: tüm bakiyeye gecikme faizi
  const gecikmeFaiz = borc * gecikme/100 * (1+vergi);

  const kalanLimit = Math.max(0, limit - borc);
  const doluluk = limit > 0 ? Math.min(100, borc/limit*100) : 0;

  return { borc, limit, avans, oran, asgari, akdi, akdiAzami, gecikme, kalan,
           faizHam, faizVergi, faizToplam, sonrakiBorc, gecikmeFaiz, kalanLimit, doluluk };
}

// "Hep asgari ödersem" projeksiyonu (yeni harcama yok)
function projeksiyon(d, ay = 60){
  const s = state.settings, vergi = s.vergi/100;
  const limit = num(d.limit);
  const oran = (d.asgariOverride != null && d.asgariOverride !== "" ? num(d.asgariOverride) : asgariOrani(limit))/100;
  let b = num(d.balance), odenen = 0, faiz = 0;
  const seri = [b];
  let i = 0;
  for(; i < ay && b > 1; i++){
    const tier = d.type === "avans" ? s.nakitAvans : d.kurumsal ? s.kurumsal : ccTier(b);
    const r = (d.rateOverride != null && d.rateOverride !== "" ? num(d.rateOverride) : tier.akdi)/100;
    const asg = Math.min(b, Math.max(b*oran, 1));
    odenen += asg;
    const kalan = b - asg;
    const f = kalan * r * (1+vergi);
    faiz += f;
    b = kalan + f;
    seri.push(b);
  }
  return { ay:i, odenen, faiz, bitti: b <= 1, seri, kalanBorc:b };
}

// Kredi: eşit taksitli (annüite) ödeme
function taksitHesapla(anapara, aylikOran, vade){
  const r = aylikOran/100;
  if(vade <= 0) return 0;
  if(r === 0) return anapara/vade;
  return anapara * r * Math.pow(1+r,vade) / (Math.pow(1+r,vade)-1);
}

function hesaplaKredi(d){
  const anapara = num(d.principal);
  const vade    = Math.round(num(d.vade));
  const oran    = num(d.rate);
  const kalanTaksit = d.kalanTaksit !== "" && d.kalanTaksit != null ? Math.round(num(d.kalanTaksit)) : vade;
  const taksit = money(d.taksit != null && d.taksit !== "" ? num(d.taksit) : taksitHesapla(anapara, oran, vade));
  const kismi = d.donemAnahtari === (d.due || ("acik:" + d.id)) ? num(d.donemOdenen) : 0;
  const kalanOdeme = money(Math.max(0, taksit * kalanTaksit - kismi));
  const toplamOdeme = taksit * vade;
  return { anapara, vade, oran, kalanTaksit, taksit, kalanOdeme, toplamOdeme,
           toplamFaiz: Math.max(0, toplamOdeme - anapara), odenenTaksit: vade - kalanTaksit };
}

// Bu dönemin ham yükümlülüğü (hiç ödeme yapılmamış gibi)
function temelDonemTutari(d){
  if(d.type === "kredikarti" || d.type === "avans") return hesaplaKart(d).asgari;
  if(d.type === "kredi"){ const k = hesaplaKredi(d); return k.kalanTaksit > 0 ? k.taksit : 0; }
  return num(d.balance);
}

/* Dönem durumu: kısmi ödeme yapıldıysa hedef dondurulur, kalan aynı vadede kalır.
   donemAnahtari, snapshot'ın hangi son ödeme tarihine ait olduğunu tutar;
   vade değişince (dönem devredince) takip kendiliğinden sıfırlanır. */
function donemDurumu(d){
  const ayniDonem = d.donemAnahtari && d.donemAnahtari === (d.due || ("acik:" + d.id));
  const hedef  = ayniDonem && d.donemHedef != null ? num(d.donemHedef) : temelDonemTutari(d);
  const odenen = ayniDonem ? num(d.donemOdenen) : 0;
  return { hedef, odenen, kalan: money(Math.max(0, hedef - odenen)), kismi: ayniDonem && odenen > 0 };
}

// Bir borcun bu dönem ödenmesi KALAN tutarı
function donemTutari(d){ return alacakMi(d) ? 0 : donemDurumu(d).kalan; }

// Vadesi her ay tekrar eden borç türleri
const tekrarliBorc = d => d.type === "kredikarti" || d.type === "avans" || d.type === "kredi";
function toplamBakiye(d){
  if(d.type === "kredi") return hesaplaKredi(d).kalanOdeme;
  return num(d.balance);
}

/* =========================================================
   4. DEPOLAMA
   ========================================================= */
function hostResult(raw){
  const result = JSON.parse(String(raw));
  if(!result.ok) throw new Error(result.error || "İşlem tamamlanamadı");
  return result;
}
function setSync(text){ document.getElementById("bt-sync").textContent = text; }
function resetEditors(){
  editingDebt = editSub = editAcc = editInc = editGoal = editFlow = null;
  odemeSecim = null;
}
function save(){
  try {
    const checked = hostResult(window.financeHost.validate(JSON.stringify(state))).data;
    hostResult(window.financeHost.save(JSON.stringify(checked)));
    state = checked;
    committed = clone(state);
    setSync("bu bilgisayara kaydedildi");
  } catch(error) {
    if(committed) state = clone(committed);
    resetEditors();
    render();
    setSync("kaydedilemedi — son kayıt korundu");
    toast("Kaydedilemedi: " + error.message);
    throw error; // Stops the event handler before a success toast can be displayed.
  }
}

/* ---- veriyi state'e uygula: replace = üzerine yaz, merge = birleştir ---- */
const KOLEKSIYONLAR = ["debts","flows","accounts","subs","incomes","goals"];
function hydrate(p, mode){
  for(const key of ["__proto__", "constructor", "prototype"]){
    if(Object.prototype.hasOwnProperty.call(p, key) || (p.settings && Object.prototype.hasOwnProperty.call(p.settings, key))) throw new Error("Geçersiz alan adı");
  }
  KOLEKSIYONLAR.forEach(k => {
    if(p[k] != null && !Array.isArray(p[k])) throw new Error("Geçersiz kayıt grubu: " + k);
    const ids = new Set();
    (p[k] || []).forEach(record => {
      if(!record || !record.id || ids.has(record.id)) throw new Error("Eksik veya tekrarlanan kayıt kimliği");
      ids.add(record.id);
    });
  });
  const kaynak = {};
  KOLEKSIYONLAR.forEach(k => { kaynak[k] = Array.isArray(p[k]) ? p[k] : []; });
  KOLEKSIYONLAR.forEach(k => {
    if(mode === "replace"){ state[k] = kaynak[k]; return; }
    if(!Array.isArray(state[k])) state[k] = [];
    const idx = new Map(state[k].map(x=>[x.id,x]));
    kaynak[k].forEach(x => {
      if(idx.has(x.id)) Object.assign(idx.get(x.id), x);
      else state[k].push(x);
    });
  });
  if(mode === "replace") state.settings = Object.assign(clone(DEFAULTS), p.settings || {});
  else if(p.settings) state.settings = Object.assign(state.settings, p.settings);
  return kaynak;
}

/* =========================================================
   4b. DOSYA: CSV / JSON
   ========================================================= */
const CSV_COLS = ["kayit_tipi","id","tur","ad","banka","limit","bakiye","anapara",
  "aylik_faiz","vade","kalan_taksit","taksit","nakit_avans","asgari_oran_ozel",
  "akdi_oran_ozel","gecikme_oran_ozel","kurumsal","son_odeme","hesap_kesim","kisi","yon","alinma_tarihi",
  "akis_yonu","tutar","tarih","kategori","borc_id","hesap_id","hesap_turu","bagli_hesap","odeme_gunu",
  "tekrar","aktif","oncelik","kisitli_tutar","donem_hedef","donem_odenen","donem_anahtari","aciklama"];

const csvOut = v => {
  if(v === null || v === undefined) return "";
  if(typeof v === "number") return String(v).replace(".",",");
  const s = String(v);
  return /[";\n\r]/.test(s) ? '"' + s.replace(/"/g,'""') + '"' : s;
};
function csvNum(v){
  if(String(v ?? "").trim() === "") return "";
  const value = num(v);
  if(!Number.isFinite(value)) throw new Error("CSV içinde geçersiz sayı: " + v);
  return value;
}

function buildCSV(){
  const line = o => CSV_COLS.map(c => csvOut(o[c])).join(";");
  const rows = [CSV_COLS.join(";")];

  state.debts.forEach(d => rows.push(line({
    kayit_tipi:"borc", id:d.id, tur:d.type, ad:d.name, banka:d.bank,
    limit:d.limit, bakiye:d.balance, anapara:d.principal, aylik_faiz:d.rate,
    vade:d.vade, kalan_taksit:d.kalanTaksit, taksit:d.taksit, nakit_avans:d.avans,
    asgari_oran_ozel:d.asgariOverride, akdi_oran_ozel:d.rateOverride, gecikme_oran_ozel:d.gecikmeOverride,
    kurumsal: d.kurumsal ? "evet" : "hayir",
    son_odeme:d.due, hesap_kesim:d.kesim, kisi:d.kisi, yon:d.yon,
    alinma_tarihi:d.alindi, donem_hedef:d.donemHedef, donem_odenen:d.donemOdenen,
    donem_anahtari:d.donemAnahtari, aciklama:d.note
  })));

  state.flows.forEach(f => rows.push(line({
    kayit_tipi:"hareket", id:f.id,
    akis_yonu: f.type === "in" ? "girdi" : "cikti",
    tutar:f.amount, tarih:f.date, kategori:f.category, aciklama:f.note, borc_id:f.debtId, hesap_id:f.accountId
  })));

  state.accounts.forEach(a => rows.push(line({
    kayit_tipi:"hesap", id:a.id, ad:a.name, banka:a.bank,
    hesap_turu:a.kind, bakiye:a.balance, kisitli_tutar:a.kisitli, aciklama:a.note
  })));

  state.subs.forEach(x => rows.push(line({
    kayit_tipi:"abonelik", id:x.id, ad:x.name, tutar:x.amount,
    odeme_gunu:x.day, bagli_hesap:x.link, kategori:x.category,
    aktif: x.active === false ? "hayir" : "evet", aciklama:x.note
  })));

  state.incomes.forEach(g => rows.push(line({
    kayit_tipi:"gelir", id:g.id, ad:g.name, tutar:g.amount,
    tarih:g.date, tekrar:g.repeat, tur:g.kind, aciklama:g.note
  })));

  state.goals.forEach(h => rows.push(line({
    kayit_tipi:"hedef", id:h.id, ad:h.name, tutar:h.amount, bakiye:h.saved,
    tarih:h.due, oncelik:h.priority, aciklama:h.note
  })));

  rows.push(line({ kayit_tipi:"ayar", aciklama: JSON.stringify(state.settings) }));
  return "\uFEFF" + rows.join("\r\n");
}

function parseCSV(text){
  if(text.charCodeAt(0) === 0xFEFF) text = text.slice(1);
  const rows = []; let row = [], cur = "", q = false;
  for(let i = 0; i < text.length; i++){
    const c = text[i];
    if(q){
      if(c === '"'){ if(text[i+1] === '"'){ cur += '"'; i++; } else q = false; }
      else cur += c;
    }else{
      if(c === '"') q = true;
      else if(c === ";") { row.push(cur); cur = ""; }
      else if(c === "\n"){ row.push(cur); rows.push(row); row = []; cur = ""; }
      else if(c === "\r"){ /* atla */ }
      else cur += c;
    }
  }
  if(q) throw new Error("CSV tırnakları kapatılmamış");
  if(cur !== "" || row.length){ row.push(cur); rows.push(row); }
  return rows.filter(r => r.some(c => String(c).trim() !== ""));
}

function readCSV(text){
  const rows = parseCSV(text);
  if(!rows.length) throw new Error("Dosya boş");
  const head = rows[0].map(h => h.trim().toLowerCase());
  if(!head.includes("kayit_tipi")) throw new Error("Bu CSV bu uygulamadan çıkmamış");
  const at = (r,c) => { const i = head.indexOf(c); return i < 0 ? "" : String(r[i] ?? "").trim(); };

  const out = { debts: [], flows: [], accounts: [], subs: [], incomes: [], goals: [], settings: null };
  for(let i = 1; i < rows.length; i++){
    const r = rows[i], tip = at(r,"kayit_tipi").toLowerCase();

    if(tip === "borc"){
      const d = {
        id: at(r,"id") || uid(),
        type: at(r,"tur") || "diger",
        name: at(r,"ad") || "Borç",
        bank: at(r,"banka"),
        due: at(r,"son_odeme"),
        note: at(r,"aciklama")
      };
      if(!TURLER[d.type]) d.type = "diger";
      const set = (k,v) => { if(v !== "") d[k] = v; };
      set("limit",    csvNum(at(r,"limit")));
      set("balance",  csvNum(at(r,"bakiye")));
      set("principal",csvNum(at(r,"anapara")));
      set("rate",     csvNum(at(r,"aylik_faiz")));
      set("vade",     csvNum(at(r,"vade")));
      set("avans",    csvNum(at(r,"nakit_avans")));
      d.kalanTaksit     = at(r,"kalan_taksit");
      d.taksit          = at(r,"taksit");
      d.asgariOverride  = at(r,"asgari_oran_ozel");
      d.rateOverride    = at(r,"akdi_oran_ozel");
      d.gecikmeOverride = at(r,"gecikme_oran_ozel");
      d.kurumsal        = at(r,"kurumsal").toLowerCase() === "evet";
      const dh = at(r,"donem_hedef");
      d.donemHedef    = dh === "" ? null : csvNum(dh);
      d.donemOdenen   = csvNum(at(r,"donem_odenen")) || 0;
      d.donemAnahtari = at(r,"donem_anahtari") || null;
      d.kesim  = at(r,"hesap_kesim");
      d.kisi   = at(r,"kisi");
      d.yon    = at(r,"yon");
      d.alindi = at(r,"alinma_tarihi");
      out.debts.push(d);

    }else if(tip === "hareket"){
      out.flows.push({
        id: at(r,"id") || uid(),
        type: at(r,"akis_yonu").toLowerCase() === "girdi" ? "in" : "out",
        amount: csvNum(at(r,"tutar")) || 0,
        date: at(r,"tarih") || isoOf(today()),
        category: at(r,"kategori"),
        debtId: at(r,"borc_id") || undefined, accountId: at(r,"hesap_id") || undefined,
        note: at(r,"aciklama")
      });

    }else if(tip === "hesap"){
      out.accounts.push({
        id: at(r,"id") || uid(), name: at(r,"ad") || "Hesap", bank: at(r,"banka"),
        kind: at(r,"hesap_turu") || "banka", balance: csvNum(at(r,"bakiye")) || 0,
        kisitli: csvNum(at(r,"kisitli_tutar")) || 0, note: at(r,"aciklama")
      });

    }else if(tip === "abonelik"){
      out.subs.push({
        id: at(r,"id") || uid(), name: at(r,"ad") || "Abonelik",
        amount: csvNum(at(r,"tutar")) || 0, day: csvNum(at(r,"odeme_gunu")) || 1,
        link: at(r,"bagli_hesap"), category: at(r,"kategori"),
        active: at(r,"aktif").toLowerCase() !== "hayir", note: at(r,"aciklama")
      });

    }else if(tip === "gelir"){
      out.incomes.push({
        id: at(r,"id") || uid(), name: at(r,"ad") || "Gelir",
        amount: csvNum(at(r,"tutar")) || 0, date: at(r,"tarih"),
        repeat: at(r,"tekrar") === "aylik" ? "aylik" : "tek",
        kind: at(r,"tur") === "harclik" ? "harclik" : "serbest", note: at(r,"aciklama")
      });

    }else if(tip === "hedef"){
      out.goals.push({
        id: at(r,"id") || uid(), name: at(r,"ad") || "Hedef",
        amount: csvNum(at(r,"tutar")) || 0, saved: csvNum(at(r,"bakiye")) || 0,
        due: at(r,"tarih"), priority: csvNum(at(r,"oncelik")) || 2, note: at(r,"aciklama")
      });

    }else if(tip === "ayar"){
      try{ out.settings = JSON.parse(at(r,"aciklama")); }catch(e){ /* yok say */ }
    }
  }
  return out;
}

function download(name, text, mime){
  const result = hostResult(window.financeHost.exportFile(name, text));
  if(result.cancelled) return false;
  toast("Yedek kaydedildi");
  return true;
}

function importText(text, mode){
  try {
    const data = /^\s*[\[{]/.test(text) ? JSON.parse(text.replace(/^\uFEFF/, ""), (key, value) => {
      if(["__proto__", "constructor", "prototype"].includes(key)) throw new Error("Geçersiz alan adı");
      return value;
    }) : readCSV(text);
    if(!data || typeof data !== "object" || !KOLEKSIYONLAR.some(k => Array.isArray(data[k]))) {
      throw new Error("Bu dosya bir Borç Defteri yedeği değil");
    }
    if(data.schemaVersion != null && data.schemaVersion !== 1) throw new Error("Desteklenmeyen defter sürümü");
    const old = clone(state);
    try {
      hydrate(data, mode);
      state = hostResult(window.financeHost.validate(JSON.stringify(state))).data;
    } catch(error) { state = old; throw error; }
    const candidate = clone(state);
    state = old;
    const count = KOLEKSIYONLAR.reduce((total,k) => total + candidate[k].length, 0);
    const question = mode === "replace"
      ? `${count} kayıt içeren defter mevcut kayıtların yerine yüklensin mi? Önceki kayıt yedeklenecek.`
      : "Dosyadaki kayıtlar birleştirilsin mi? Aynı kimliğe sahip kayıtlar güncellenecek.";
    if(!confirm(question)) return false;
    state = candidate;
    save(); resetEditors(); render();
    toast("Defter içe aktarıldı");
    return true;
  } catch(error) {
    toast("İçe aktarılamadı: " + error.message);
    return false;
  }
}
function pickFile(mode){
  try {
    const result = hostResult(window.financeHost.importFile());
    if(!result.cancelled) importText(result.text, mode);
  } catch(error) { toast(error.message); }
}

let toastTimer = null;
function toast(msg){
  document.querySelectorAll(".bt-toast").forEach(n=>n.remove());
  const el = document.createElement("div");
  el.className = "bt-toast"; el.textContent = msg;
  document.getElementById("bt-app").appendChild(el);
  clearTimeout(toastTimer);
  toastTimer = setTimeout(()=>el.remove(), 2400);
}

/* =========================================================
   4c. ABONELİK · KASA · GELİR · PLAN MOTORU
   ========================================================= */
const HESAP_TURLERI = { banka:"Banka hesabı", nakit:"Nakit", diger:"Diğer (cüzdan, altın, döviz)" };
const ABONE_KAT = ["Dijital abonelik","Telefon / internet","Sigorta","Spor","Bulut / yazılım","Ulaşım","Diğer"];

/* Aboneliğin ay içindeki ödeme gününü takvime oturt */
function abonelikGunu(sub, yil, ay){
  const g = Math.min(31, Math.max(1, Math.round(num(sub.day)) || 1));
  const son = new Date(yil, ay+1, 0).getDate();
  return new Date(yil, ay, Math.min(g, son));
}
function aboneSonraki(sub){
  const t = today();
  for(let i = 0; i < 3; i++){
    const d = abonelikGunu(sub, t.getFullYear(), t.getMonth()+i);
    if(d >= t) return isoOf(d);
  }
  return null;
}
/* [bas, bit] aralığına düşen tüm abonelik ödemeleri */
function aboneOdemeleri(bas, bit){
  const out = [];
  state.subs.filter(x => x.active !== false).forEach(x => {
    for(let i = 0; i < 15; i++){
      const d = abonelikGunu(x, bas.getFullYear(), bas.getMonth()+i);
      if(d > bit) break;
      if(d >= bas) out.push({ sub:x, date:isoOf(d), amount:num(x.amount) });
    }
  });
  return out.sort((a,b) => a.date.localeCompare(b.date));
}
const aylikAbonelik = () =>
  state.subs.filter(x => x.active !== false).reduce((t,x) => t + num(x.amount), 0);

/* Bağlı kart / hesap seçimi */
function baglantiSecenekleri(sec){
  const o = [`<option value="">— bağlanmadı —</option>`];
  state.debts.filter(d => d.type === "kredikarti" || d.type === "avans")
    .forEach(d => o.push(`<option value="d:${d.id}" ${sec==="d:"+d.id?"selected":""}>${esc(d.name)} — kart</option>`));
  state.accounts.forEach(a =>
    o.push(`<option value="a:${a.id}" ${sec==="a:"+a.id?"selected":""}>${esc(a.name)} — hesap</option>`));
  return o.join("");
}
function baglantiAdi(v){
  if(!v) return "—";
  const [k, id] = String(v).split(":");
  const o = k === "d" ? state.debts.find(x => x.id === id) : state.accounts.find(x => x.id === id);
  return o ? o.name : "—";
}

/* Eldeki gerçek para (kredi kartı limiti buraya dahil değil) */
const eldekiPara = () => state.accounts.reduce((t,a) => t + num(a.balance), 0);
/* Harçlık cebi: borç ödemesinde kullanılamayan para */
const kisitliPara = () => state.accounts.reduce((t,a) => t + Math.min(num(a.balance), num(a.kisitli)), 0);
/* Serbest cep: borç dahil her şeye harcanabilen para */
const serbestPara = () => state.accounts.reduce((t,a) => t + Math.max(0, num(a.balance) - num(a.kisitli)), 0);
/* Kartlardan kullanılabilir limit — para değil, borçlanma kapasitesi */
const kullanilabilirLimit = () => state.debts
  .filter(d => d.type === "kredikarti")
  .reduce((t,d) => t + hesaplaKart(d).kalanLimit, 0);

/* Gelirleri takvime aç (aylık tekrar edenler dahil) */
function gelirTakvimi(gun){
  const t = today();
  const son = new Date(t); son.setDate(son.getDate() + gun);
  const out = [];
  state.incomes.forEach(g => {
    if(!g.date) return;
    if(g.repeat === "aylik"){
      let iso = g.date, kilit = 0;
      while(parseDate(iso) < t && kilit++ < 400) iso = addMonth(iso, 1);
      while(parseDate(iso) <= son && kilit++ < 420){
        out.push({ id:g.id, name:g.name, amount:num(g.amount), date:iso, repeat:"aylik", kind:g.kind || "serbest" });
        iso = addMonth(iso, 1);
      }
    }else if(parseDate(g.date) && parseDate(g.date) <= son){
      out.push({ id:g.id, name:g.name, amount:num(g.amount), date:g.date, repeat:"tek", kind:g.kind || "serbest" });
    }
  });
  return out.sort((a,b) => a.date.localeCompare(b.date));
}

/* Gelecek N gündeki tüm ödeme olayları — borç + abonelik, tek liste */
function yaklasanlar(gun){
  const t = today();
  const son = new Date(t); son.setDate(son.getDate() + gun);
  const out = [];
  state.debts.filter(d => d.due && !alacakMi(d) && donemTutari(d) > 0).forEach(d => {
    const g = daysUntil(d.due);
    if(g !== null && g <= gun) out.push({
      kind:"borc", id:d.id, ad:d.name, alt:d.bank || TURLER[d.type],
      date:d.due, amount:donemTutari(d), ref:d
    });
  });
  aboneOdemeleri(t, son).forEach(o => out.push({
    kind:"abonelik", id:o.sub.id, ad:o.sub.name, alt:baglantiAdi(o.sub.link),
    date:o.date, amount:o.amount, ref:o.sub
  }));
  return out.sort((a,b) => a.date.localeCompare(b.date));
}

/* ---- ASIL PLAN: bir sonraki gelire kadar günlük harcama payı ---- */
function planHesapla(){
  const t = today();
  const takvim = gelirTakvimi(180);
  const hedef = takvim.find(g => daysUntil(g.date) >= 0) || null;

  let bitis, etiket;
  if(hedef){
    bitis = parseDate(hedef.date);
    etiket = hedef.name;
  }else{
    bitis = new Date(t.getFullYear(), t.getMonth()+1, 0);   // gelir yoksa ay sonu
    etiket = null;
  }
  const gun = Math.max(1, Math.round((bitis - t) / 86400000));

  // Aralığa düşen borç ödemeleri (gecikmişler de dahil — hâlâ ödenecekler)
  const borclar = state.debts.filter(d => {
    const dd = parseDate(d.due);
    return dd && dd <= bitis && !alacakMi(d) && donemTutari(d) > 0;
  }).map(d => ({ ad:d.name, date:d.due, amount:donemTutari(d), gecikmis: daysUntil(d.due) < 0 }));

  const abone = aboneOdemeleri(t, bitis)
    .map(o => ({ ad:o.sub.name, date:o.date, amount:o.amount, gecikmis:false }));

  const borcTutar = borclar.reduce((x,o) => x + o.amount, 0);
  const aboTutar  = abone.reduce((x,o) => x + o.amount, 0);
  const zorunlu   = borcTutar + aboTutar;

  const nakit    = eldekiPara();
  const cepH     = kisitliPara();          // harçlık — borca gitmez
  const cepS     = serbestPara();          // serbest — her şeye gider
  const borcOdenen = Math.min(cepS, borcTutar);
  const borcAcik   = borcTutar - borcOdenen;
  const kalanS     = cepS - borcOdenen;
  const serbest = cepH + kalanS - aboTutar;   // günlük yaşama kalan
  const gunluk  = serbest / gun;

  return {
    hedef, etiket, bitis:isoOf(bitis), gun, nakit, cepH, cepS, borcAcik, kalanS,
    limit:kullanilabilirLimit(),
    borclar, abone, borcTutar, aboTutar, zorunlu, serbest, gunluk,
    gunlukBorcPayi: zorunlu / gun,
    kalemler: [...borclar, ...abone].sort((a,b) => (a.date||"").localeCompare(b.date||""))
  };
}

/* =========================================================
   4d. HEDEFLER VE KARAR MOTORU
   ========================================================= */
const ONCELIK = { 1:"Yüksek", 2:"Orta", 3:"Düşük" };

/* Bir kartın vergi dahil aylık gerçek maliyeti (%) */
function kartAylikMaliyet(d){
  const c = hesaplaKart(d);
  return c.akdi * (1 + state.settings.vergi/100);
}
/* En pahalı borç hangisi — kartopu değil, çığ yöntemi */
function enPahaliBorc(){
  const k = state.debts.filter(d => (d.type==="kredikarti"||d.type==="avans") && num(d.balance) > 0);
  if(!k.length) return null;
  return k.map(d => ({ d, oran: kartAylikMaliyet(d) })).sort((a,b) => b.oran - a.oran)[0];
}

/* Belirli bir aralığa düşen borç ödemeleri (aylık tekrar edenler ileri sarılır) */
function borcOlaylari(bas, bit, gecmisiDahilEt){
  const out = [];
  state.debts.forEach(d => {
    if(!d.due || alacakMi(d) || toplamBakiye(d) <= 0) return;
    const first = parseDate(d.due);
    if(!first) return;
    const recurring = tekrarliBorc(d);
    const periods = d.type === "kredi" ? hesaplaKredi(d).kalanTaksit : recurring ? 120 : 1;
    for(let index = 0; index < periods; index++){
      const date = addMonth(d.due, index), parsed = parseDate(date);
      if(parsed > bit) break;
      if(parsed < bas && !(gecmisiDahilEt && index === 0)) continue;
      const amount = index === 0 ? donemTutari(d) : temelDonemTutari(d);
      if(amount > 0) out.push({ad:d.name, date, amount, gecikmis:parsed < bas, ref:d});
    }
  });
  return out.sort((a,b) => a.date.localeCompare(b.date));
}

/* ---- KART KULLANIM PLANI ----
   Hangi kartla harcamalı, hangi kartı önce ödemeli */
function kartPlani(){
  const t = today();
  const bilgi = state.debts.filter(d => d.type === "kredikarti").map(d => {
    const c = hesaplaKart(d);
    let kesim = null, faizsizGun = null;
    if(d.kesim){
      let iso = d.kesim, kilit = 0;
      while(parseDate(iso) < t && kilit++ < 80) iso = addMonth(iso, 1);
      kesim = iso;
      // Bugün yapılan harcama bu kesimden sonraki ekstreye düşer
      faizsizGun = daysUntil(iso) + (d.due ? Math.max(0, Math.round((parseDate(d.due) - parseDate(d.kesim))/86400000)) : 10);
    }
    return { d, c, kesim, faizsizGun, maliyet: kartAylikMaliyet(d) };
  });

  // Önce nefes alanı: %40 altı > %70 altı > %90 altı. Sonra en uzun faizsiz süre.
  const kademe = b => b.c.doluluk < 40 ? 0 : b.c.doluluk < 70 ? 1 : 2;
  const uygun = bilgi.filter(b => b.c.kalanLimit > 0 && b.c.doluluk < 90);
  const harcama = uygun.slice().sort((a,b) =>
    kademe(a) - kademe(b) ||
    (b.faizsizGun ?? -1) - (a.faizsizGun ?? -1) ||
    b.c.kalanLimit - a.c.kalanLimit)[0] || null;
  const odeme = bilgi.filter(b => b.c.borc > 0)
    .sort((a,b) => b.maliyet - a.maliyet || b.c.borc - a.c.borc);
  const dolu = bilgi.filter(b => b.c.doluluk >= 90);

  return { bilgi, harcama, odeme, dolu };
}

/* Bir hedefin son tarihine kadar kaç gelir dönemi kaldı */
function hedefDonemSayisi(h, takvim){
  if(!h.due) return null;                    // tarih yoksa zorunluluk yok
  const son = parseDate(h.due);
  if(!son) return null;
  const n = takvim.filter(g => parseDate(g.date) <= son).length;
  return Math.max(1, n);
}

/* İki tarih arasındaki bir pencerenin planı */
function pencerePlani(bas, bit, cepS, cepH, gecmisiDahilEt){
  const gun    = Math.max(1, Math.round((bit - bas) / 86400000));
  const borc   = borcOlaylari(bas, bit, !!gecmisiDahilEt);
  const abone  = aboneOdemeleri(bas, bit).map(o => ({ ad:o.sub.name, date:o.date, amount:o.amount }));
  const borcT  = borc.reduce((t,o) => t + o.amount, 0);
  const aboT   = abone.reduce((t,o) => t + o.amount, 0);
  const zorunlu = borcT + aboT;
  // Borç yalnızca serbest cepten ödenebilir
  const borcOdenen = Math.min(cepS, borcT);
  const borcAcik   = borcT - borcOdenen;
  const kalanS     = cepS - borcOdenen;
  return { bas:isoOf(bas), bit:isoOf(bit), gun, borc, abone, borcT, aboT, zorunlu,
           cepS, cepH, nakit: cepS + cepH, borcOdenen, borcAcik, kalanS,
           kalan: cepH + kalanS - aboT };
}

/* ---- KARAR MOTORU ---- */
function hedefPlani(){
  const t = today();
  const takvim  = gelirTakvimi(1095);
  const g1 = takvim.find(g => daysUntil(g.date) >= 0) || null;
  const butce = num(state.settings.gunlukButce);

  // 1. pencere: bugün → ilk gelir (gelir yoksa ay sonu)
  const bit1 = g1 ? parseDate(g1.date) : new Date(t.getFullYear(), t.getMonth()+1, 0);
  const w1 = pencerePlani(t, bit1, serbestPara(), kisitliPara(), true);

  // hedeflerin bu dönem için minimumu
  const hedefler = [...state.goals].sort((a,b) =>
    (num(a.priority)||2) - (num(b.priority)||2) ||
    (parseDate(a.due) ? parseDate(a.due) : 9e15) - (parseDate(b.due) ? parseDate(b.due) : 9e15));

  const ihtiyaclar = hedefler.map(h => {
    const kalanTutar = Math.max(0, num(h.amount) - num(h.saved));
    const donem = hedefDonemSayisi(h, takvim);
    const min = donem ? kalanTutar / donem : 0;
    return { h, kalanTutar, donem, min, tamam: kalanTutar <= 0 };
  });
  const hedefMin = ihtiyaclar.reduce((x,i) => x + i.min, 0);

  // 1. pencere şelalesi: abonelik ve yaşam önce harçlık cebinden, sonra serbestten
  const yasam1 = butce > 0 ? butce * w1.gun : 0;
  let cepH = w1.cepH, cepS = w1.kalanS;
  const harca = tutar => {
    const h = Math.min(Math.max(0, cepH), tutar);
    cepH -= h;
    cepS -= (tutar - h);
  };
  harca(w1.aboT);
  harca(yasam1);
  const dagitim = ihtiyaclar.map(i => {
    const bosluk = Math.max(0, cepH) + Math.max(0, cepS);
    const ver = i.min > 0 ? Math.max(0, Math.min(bosluk, i.min)) : 0;
    harca(ver);
    return { ...i, ver, eksik: Math.max(0, i.min - ver) };
  });
  const artanH = Math.max(0, cepH);
  const artanS = cepS;                                 // eksiye düşebilir = açık
  const artan  = Math.max(0, artanH + Math.max(0, artanS));
  const azamiGunluk1 = (w1.kalan - hedefMin) / w1.gun; // hedefler karşılanırsa günlük tavan

  // 2. pencere: gelir günü sonrası → bir sonraki gelir. Gelir kendi cebine düşer.
  let w2 = null, gunluk2 = null, g2 = null;
  if(g1){
    const bas2 = new Date(bit1); bas2.setDate(bas2.getDate()+1);
    g2 = takvim.find(g => parseDate(g.date) > bit1) || null;
    const bit2 = g2 ? parseDate(g2.date) : (function(){ const d = new Date(bas2); d.setDate(d.getDate()+29); return d; })();
    const harclikMi = g1.kind === "harclik";
    const yeniH = artanH + (harclikMi ? g1.amount : 0);
    const yeniS = Math.max(0, artanS) + (harclikMi ? 0 : g1.amount);
    w2 = pencerePlani(bas2, bit2, yeniS, yeniH, false);
    gunluk2 = (w2.kalan - hedefMin) / w2.gun;
  }

  return { g1, g2, w1, w2, butce, yasam1, hedefMin, dagitim, artan, artanH, artanS,
           azamiGunluk1, gunluk2, ihtiyaclar, takvim, kart: kartPlani() };
}

/* ---- ÖNERİLER ---- */
function oneriler(P){
  const o = [];
  const vergi = state.settings.vergi/100;

  const gecikmis = P.w1.borc.filter(b => b.gecikmis);
  if(gecikmis.length){
    const tut = gecikmis.reduce((t,b)=>t+b.amount,0);
    o.push({ ton:"red", baslik:"Önce gecikmiş ödemeler",
      metin:`${gecikmis.map(b=>esc(b.ad)).join(", ")} vadesi geçmiş durumda, toplam ${fmt(tut)}. `+
            `Gecikme faizi akdi faizden yüksek ve kredi notunu da yakıyor; sıralamada her şeyin önünde.` });
  }

  if(!state.accounts.length){
    o.push({ ton:"amber", baslik:"Bakiye girilmemiş",
      metin:"Karar motoru elindeki paradan çalışıyor. Bakiyeler sekmesine banka ve nakit tutarlarını gir, yoksa bütün hesaplar 0 üzerinden yapılıyor." });
  }

  if(P.w1.kalan < 0){
    o.push({ ton:"red", baslik:"Bu dönemde açık var",
      metin:`Zorunlu ödemelerin elindeki parayı ${fmt(-P.w1.kalan)} aşıyor. Sırayla bak: `+
            `dondurulabilecek abonelik var mı (aylık toplam ${fmt(aylikAbonelik())}), `+
            `kredilerde taksit erteleme mümkün mü, kartlarda asgariye düşmek zorunda mısın. `+
            `Asgariye düşmek en pahalı çözüm, son sıraya koy.` });
  } else if(P.butce <= 0){
    o.push({ ton:"pine", baslik:"Günlük bütçe belirle",
      metin:`Aşağıdaki kutuya bir rakam gir. Hedeflerini de karşılamak istiyorsan tavanın `+
            `günde ${fmt(P.azamiGunluk1)}; güvenlik payı için bunun %10–20 altını seç.` });
  } else if(P.butce > P.azamiGunluk1 && P.hedefMin > 0){
    o.push({ ton:"amber", baslik:"Bütçe hedefleri yiyor",
      metin:`Günde ${fmt(P.butce)} harcarsan hedeflerine bu dönem ${fmt(P.dagitim.reduce((t,d)=>t+d.eksik,0))} eksik ayırmış olacaksın. `+
            `Hedefleri aksatmadan harcayabileceğin tavan günde ${fmt(P.azamiGunluk1)}.` });
  }

  if(P.w1.borcAcik > 0.5){
    o.push({ ton:"red", baslik:"Borç için serbest paran yetmiyor",
      metin:`Bu dönemin borç ödemeleri ${fmt(P.w1.borcT)}, ama borca kullanabileceğin serbest paran ${fmt(P.w1.cepS)}. `+
            `${fmt(P.w1.borcAcik)} açık var. Harçlık cebindeki ${fmt(P.w1.cepH)} buraya sayılmıyor — `+
            `bu açığı serbest bir gelirle ya da harcamalarını kısarak kapatman gerekiyor.` });
  }
  if(P.w1.cepH > 0){
    o.push({ ton:"pine", baslik:"Harçlık cebini doğru yerde kullan",
      metin:`Harçlık cebinde ${fmt(P.w1.cepH)} var; bu para yalnızca günlük gider ve aboneliklere gidiyor. `+
            `Dikkat edilecek nokta: bu harcamaları kredi kartına yıkarsan harçlığı borca çevirmiş olursun, `+
            `çünkü o kart borcunu harçlıkla kapatamıyorsun. Günlük gideri banka kartı veya nakitle öde; `+
            `krediyi sadece serbest parayla kapatacağın harcamalar için kullan.` });
  }

  const KP = P.kart;
  if(KP.harcama){
    const h = KP.harcama;
    o.push({ ton:"pine", baslik:`Kartla harcayacaksan: ${esc(h.d.name)}`,
      metin:`Kullanılabilir limiti ${fmt(h.c.kalanLimit)}, doluluk %${h.c.doluluk.toFixed(0)} — kartların içinde en çok nefes alanı olan bu. `+
            (h.kesim ? `Hesap kesimi ${fmtDate(h.kesim)}, yani bugünkü bir harcama o ekstreye düşer ve yaklaşık ${h.faizsizGun} gün faizsiz kalır. `
                     : `Hesap kesim tarihi girilmemiş; girersen hangi kartın en uzun faizsiz süreyi verdiğini de hesaplarım. `)+
            `İnternet alışverişleri dahil kart harcamalarını burada topla.` });
  }
  if(KP.odeme.length){
    const ilk = KP.odeme[0];
    const siralama = KP.odeme.map((b,i) => `${i+1}. ${esc(b.d.name)} (%${b.maliyet.toFixed(2)}/ay, ${fmt(b.c.borc)})`).join(" · ");
    o.push({ ton:"amber", baslik:`Önce şu kartı öde: ${esc(ilk.d.name)}`,
      metin:`Ödeme sırası maliyete göre — ${siralama}. Elindeki serbest parayla önce listenin başındakine yüklen; `+
            `hepsinin asgarisini ödemeyi aksatma, fazlasını en pahalıya yatır.` });
  }
  if(KP.dolu.length){
    o.push({ ton:"amber", baslik:"Limit dolmak üzere",
      metin:`${KP.dolu.map(b=>`${esc(b.d.name)} (%${b.c.doluluk.toFixed(0)})`).join(", ")} kartlarında doluluk %90'ı geçti. `+
            `Bu oran kredi notunu düşürür ve bir aksilikte manevra alanın kalmaz; bu kartlarda yeni harcama yapma.` });
  }

  const pahali = enPahaliBorc();
  if(pahali && P.artan > 0){
    const aylik = pahali.oran/100;
    const yillik = (Math.pow(1+aylik, 12) - 1) * 100;
    const kazanc = P.artan * aylik;
    o.push({ ton:"amber", baslik:"Artan parayı hedefe değil karta yatır",
      metin:`${esc(pahali.d.name)} kartın vergi dahil ayda %${pahali.oran.toFixed(2)} işliyor — yıllık bileşik karşılığı yaklaşık %${yillik.toFixed(0)}. `+
            `Elindeki ${fmt(P.artan)} artanı bu karta yatırırsan ayda ${fmt(kazanc)} faizden kurtulursun. `+
            `Hiçbir birikim hesabı bu getiriyi vermez; acil bir hedef tarihin yoksa önce kartı kapat.` });
  } else if(P.artan > 0){
    o.push({ ton:"pine", baslik:"Artan var",
      metin:`Zorunlular, yaşam gideri ve hedef minimumları çıktıktan sonra ${fmt(P.artan)} artıyor. `+
            `Faizli borcun kalmadığı için bunu en yakın tarihli hedefe yazabilirsin.` });
  }

  P.dagitim.filter(d => d.eksik > 0.5).forEach(d => {
    const ek = d.donem ? d.eksik : 0;
    o.push({ ton:"amber", baslik:`"${esc(d.h.name)}" hedefi yetişmiyor`,
      metin:`Bu dönem ${fmt(d.min)} ayırman gerekiyordu, ancak ${fmt(d.ver)} ayrılabiliyor — ${fmt(d.eksik)} eksik. `+
            `Ya hedef tarihini ileri al, ya tutarı düşür, ya da dönem başına ${fmt(ek)} ek gelir/tasarruf bul.` });
  });

  const tarihsiz = state.goals.filter(h => !h.due && num(h.amount) > num(h.saved));
  if(tarihsiz.length){
    o.push({ ton:"pine", baslik:"Tarihsiz hedefler",
      metin:`${tarihsiz.map(h=>esc(h.name)).join(", ")} için tarih girilmemiş. Tarihsiz hedefe zorunlu pay ayrılmıyor, `+
            `sadece artandan besleniyor. Gerçekten yetişmesini istiyorsan bir tarih koy.` });
  }

  if(!o.length){
    o.push({ ton:"pine", baslik:"Plan oturmuş",
      metin:"Zorunlu ödemeler, günlük bütçe ve hedef payları elindeki parayla karşılanıyor. Tek yapman gereken bütçeye sadık kalmak." });
  }
  return o;
}

/* =========================================================
   5. PANEL
   ========================================================= */
function vadeChip(iso){
  const g = daysUntil(iso);
  if(g === null) return '<span class="bt-chip grey">vade yok</span>';
  if(g < 0)  return `<span class="bt-chip red">${-g} gün gecikti</span>`;
  if(g === 0) return '<span class="bt-chip red">bugün son gün</span>';
  if(g <= 7) return `<span class="bt-chip amber">${g} gün kaldı</span>`;
  return `<span class="bt-chip grey">${g} gün kaldı</span>`;
}
function vadeRenk(iso){
  const g = daysUntil(iso);
  if(g === null) return "var(--ink-3)";
  if(g <= 0) return "var(--red)";
  if(g <= 7) return "var(--amber)";
  return "var(--pine)";
}

function renderPanel(){
  const el = document.getElementById("bt-panel");
  const debts = state.debts;
  const toplam = debts.filter(d=>!alacakMi(d)).reduce((s,d)=>s+toplamBakiye(d),0);
  const alacak = debts.filter(alacakMi).reduce((s,d)=>s+toplamBakiye(d),0);
  const abonelik = aylikAbonelik();
  const buAy   = debts.reduce((s,d)=>s+donemTutari(d),0) + abonelik;
  const faiz   = debts.filter(d=>d.type==="kredikarti"||d.type==="avans")
                      .reduce((s,d)=>s+hesaplaKart(d).faizToplam,0);

  const mk = thisMonth();
  const gelir = state.flows.filter(f=>f.type==="in"  && monthKey(f.date)===mk).reduce((s,f)=>s+num(f.amount),0);
  const gider = state.flows.filter(f=>f.type==="out" && monthKey(f.date)===mk).reduce((s,f)=>s+num(f.amount),0);

  const plan = planHesapla();
  const yakin = yaklasanlar(45);
  const bosMu = !debts.length && !state.subs.length;

  let hero;
  if(bosMu){
    hero = `
      <div class="bt-hero">
        <p class="bt-hero-lead">Henüz kayıt yok</p>
        <p class="bt-hero-num">0,00 ₺</p>
        <p class="bt-hero-sub">Borçlar sekmesinden kartını, Abonelikler sekmesinden düzenli ödemelerini ekleyerek başla.</p>
      </div>`;
  } else {
    const ilk = yakin[0];
    let alt = "Kayıtlarda vade tarihi girilmemiş.";
    if(ilk){
      const g = daysUntil(ilk.date);
      const ne = g < 0 ? `${-g} gün önce geçti` : g === 0 ? "bugün" : `${g} gün sonra`;
      alt = `En yakın ödeme: <b>${esc(ilk.ad)}</b> — ${fmtDate(ilk.date)} (${ne}), ${fmt(ilk.amount)}`;
    }
    hero = `
      <div class="bt-hero">
        <p class="bt-hero-lead">Bu dönem ödemen gereken en az tutar (asgariler + abonelikler)</p>
        <p class="bt-hero-num">${fmt(buAy)}</p>
        <p class="bt-hero-sub">${alt}</p>
        <dl class="bt-hero-grid">
          <div class="bt-stat"><dt>Toplam borç</dt><dd>${fmt0(toplam)}</dd></div>
          <div class="bt-stat"><dt>Toplam alacak</dt><dd>${fmt0(alacak)}</dd></div>
          <div class="bt-stat"><dt>Aylık abonelik</dt><dd>${fmt0(abonelik)}</dd></div>
          <div class="bt-stat"><dt>Eldeki para</dt><dd style="color:var(--green)">${fmt0(plan.nakit)}</dd></div>
          <div class="bt-stat"><dt>Günlük harcama payı</dt><dd style="color:${plan.gunluk<0?"var(--red)":"var(--pine)"}">${fmt0(plan.gunluk)}</dd></div>
          <div class="bt-stat"><dt>Tahmini faiz</dt><dd style="color:var(--red)">${fmt0(faiz)}</dd></div>
          <div class="bt-stat"><dt>Bu ay net akış</dt><dd style="color:${gelir-gider<0?"var(--red)":"var(--green)"}">${fmt0(gelir-gider)}</dd></div>
        </dl>
      </div>`;
  }

  let ozet = "";
  if(!state.accounts.length){
    ozet = `<div class="bt-callout">Günlük harcama planı için <b>Bakiyeler</b> sekmesinden hesaplarındaki parayı gir. Şu an elde 0 ₺ varsayılıyor.</div>`;
  }else{
    ozet = `<div class="bt-callout ${plan.gunluk<0?"":"pine"}">
      ${plan.hedef ? `<b>${esc(plan.etiket)}</b> gelirine ${plan.gun} gün var.` : `Ay sonuna ${plan.gun} gün var (kayıtlı gelecek gelir yok).`}
      O güne kadar ${fmt(plan.zorunlu)} zorunlu ödemen düşülünce günlük yaşama ${fmt(plan.serbest)} kalıyor →
      günde <b>${fmt(plan.gunluk)}</b> harcayabilirsin.
      ${plan.cepH > 0 ? `<br>Bunun ${fmt(plan.cepH)} kadarı harçlık cebinden; borç ödemesine sayılmıyor.` : ""}
      ${plan.borcAcik > 0.5 ? `<br><b>Borç tarafında ${fmt(plan.borcAcik)} açık var</b> — serbest paran yetmiyor.` : ""}
    </div>`;
  }

  // 45 günlük vade şeridi
  let track;
  if(!yakin.length){
    track = `<div class="bt-track"><div class="bt-empty-track">Önümüzdeki 45 günde vadesi gelen ödeme yok.</div></div>`;
  }else{
    const span = 52, geri = 7;
    const marks = yakin.map(o=>{
      const g = daysUntil(o.date);
      const x = Math.max(1.5, Math.min(98.5, ((g+geri)/span)*100));
      const renk = o.kind === "abonelik" ? "var(--ink-3)" : vadeRenk(o.date);
      return `<div class="bt-mark" style="left:${x}%">
                <b style="color:${renk}">${fmt0(o.amount)}</b>
                <i style="background:${renk}"></i>
              </div>`;
    }).join("");
    const t = today();
    const son = new Date(t); son.setDate(son.getDate()+45);
    track = `<div class="bt-track">
      <div class="bt-track-base"></div>
      <div class="bt-today" style="left:${(geri/span)*100}%"></div>
      ${marks}
      <div class="bt-scale"><span>bugün · ${t.toLocaleDateString("tr-TR",{day:"numeric",month:"short"})}</span><span>${son.toLocaleDateString("tr-TR",{day:"numeric",month:"short"})}</span></div>
    </div>`;
  }

  const liste = yakin.length ? `
    <div class="bt-scroll"><table class="bt-table">
      <thead><tr><th>Ödeme</th><th>Tür</th><th>Tarih</th><th class="num">Tutar</th><th></th></tr></thead>
      <tbody>${yakin.map(o=>`
        <tr>
          <td><b>${esc(o.ad)}</b>${o.alt?`<br><span style="color:var(--ink-3);font-size:12px">${esc(o.alt)}</span>`:""}</td>
          <td>${o.kind==="abonelik"?`<span class="bt-chip grey">abonelik</span>`:TURLER[o.ref.type]}</td>
          <td>${fmtDate(o.date)} ${vadeChip(o.date)}</td>
          <td class="num"><b>${fmt(o.amount)}</b></td>
          <td class="num">${o.kind==="borc"?`<button class="bt-btn ghost sm" data-pay="${o.id}">Ödedim</button>`:""}</td>
        </tr>`).join("")}
      </tbody></table></div>` : "";

  el.innerHTML = hero + ozet + `
    <div class="bt-timeline">
      <h2>Önümüzdeki 45 gün</h2>
      ${track}
    </div>
    <div class="bt-section">
      <div class="bt-section-head"><h2>Yaklaşan ödemeler</h2></div>
      ${liste || `<div class="bt-empty"><b>Sıradaki ödeme görünmüyor</b>Borçlarına son ödeme tarihi ekle ya da aboneliklerini gir.</div>`}
    </div>`;
}

/* =========================================================
   6. BORÇLAR
   ========================================================= */
function formHTML(){
  const d = editingDebt || {};
  const t = formType;
  const v = (k, def="") => d[k] != null ? esc(d[k]) : def;
  const bankOpts = BANKALAR.map(b=>`<option value="${b}" ${d.bank===b?"selected":""}>${b}</option>`).join("");

  let alan = "";
  if(t === "kredikarti" || t === "avans"){
    alan = `
      <div class="bt-field"><label for="f-bank">Banka</label>
        <select id="f-bank"><option value="">Seç…</option>${bankOpts}</select>
        <div class="hint">Oran, kayıtlı varsayımlardan gelir; ekstre oranıyla değiştirebilirsin.</div></div>
      <div class="bt-field"><label for="f-limit">Kart limiti (₺)</label>
        <input id="f-limit" type="text" inputmode="decimal" value="${v("limit")}" placeholder="15000">
        <div class="hint">Asgari oranı limite göre belirlenir.</div></div>
      <div class="bt-field"><label for="f-balance">Dönem borcu (₺)</label>
        <input id="f-balance" type="text" inputmode="decimal" value="${v("balance")}" placeholder="13238">
        <div class="hint">Ekstredeki toplam borç.</div></div>
      <div class="bt-field"><label for="f-avans">Bunun nakit avans kısmı (₺)</label>
        <input id="f-avans" type="text" inputmode="decimal" value="${v("avans")}" placeholder="0">
        <div class="hint">Nakit çekime %${state.settings.nakitAvans.akdi} işler.</div></div>
      <div class="bt-field"><label for="f-due">Son ödeme tarihi</label>
        <input id="f-due" type="date" value="${v("due")}"></div>
      <div class="bt-field"><label for="f-kesim">Hesap kesim tarihi</label>
        <input id="f-kesim" type="date" value="${v("kesim")}"></div>
      <div class="bt-field"><label for="f-rate">Akdi faiz — banka farklıysa (%)</label>
        <input id="f-rate" type="text" inputmode="decimal" value="${v("rateOverride")}" placeholder="otomatik">
        <div class="hint">Boş bırak: ayarlardaki kayıtlı oran kullanılır.</div></div>
      <div class="bt-field"><label for="f-asgari">Asgari oran — farklıysa (%)</label>
        <input id="f-asgari" type="text" inputmode="decimal" value="${v("asgariOverride")}" placeholder="otomatik">
        <div class="hint">Kartın 1 yıldan yeniyse bankan %40 uygulayabilir.</div></div>
      <div class="bt-field"><label for="f-kurumsal">Kart tipi</label>
        <select id="f-kurumsal">
          <option value="0" ${!d.kurumsal?"selected":""}>Bireysel</option>
          <option value="1" ${d.kurumsal?"selected":""}>Ticari / kurumsal</option>
        </select></div>`;
  } else if(t === "kredi"){
    alan = `
      <div class="bt-field"><label for="f-bank">Banka / kurum</label>
        <select id="f-bank"><option value="">Seç…</option>${bankOpts}</select></div>
      <div class="bt-field"><label for="f-principal">Çekilen anapara (₺)</label>
        <input id="f-principal" type="text" inputmode="decimal" value="${v("principal")}" placeholder="50000"></div>
      <div class="bt-field"><label for="f-rate2">Aylık faiz oranı (%)</label>
        <input id="f-rate2" type="text" inputmode="decimal" value="${v("rate")}" placeholder="3,29">
        <div class="hint">Sözleşmedeki aylık oran.</div></div>
      <div class="bt-field"><label for="f-vade">Toplam vade (ay)</label>
        <input id="f-vade" type="text" inputmode="numeric" value="${v("vade")}" placeholder="12"></div>
      <div class="bt-field"><label for="f-kalan">Kalan taksit sayısı</label>
        <input id="f-kalan" type="text" inputmode="numeric" value="${v("kalanTaksit")}" placeholder="12"></div>
      <div class="bt-field"><label for="f-taksit">Aylık taksit (₺)</label>
        <input id="f-taksit" type="text" inputmode="decimal" value="${v("taksit")}" placeholder="otomatik hesaplanır">
        <div class="hint">Ekstrende yazan tutarı gir; boşsa annüiteyle hesaplarım.</div></div>
      <div class="bt-field"><label for="f-due">Sonraki taksit tarihi</label>
        <input id="f-due" type="date" value="${v("due")}"></div>`;
  } else if(t === "elden"){
    alan = `
      <div class="bt-field"><label for="f-yon">Yön</label>
        <select id="f-yon">
          <option value="borc" ${d.yon!=="alacak"?"selected":""}>Ben borçluyum</option>
          <option value="alacak" ${d.yon==="alacak"?"selected":""}>Bana borçlu (alacak)</option>
        </select></div>
      <div class="bt-field"><label for="f-kisi">Kişi</label>
        <input id="f-kisi" type="text" value="${v("kisi")}" placeholder="Ad soyad"></div>
      <div class="bt-field"><label for="f-balance">Tutar (₺)</label>
        <input id="f-balance" type="text" inputmode="decimal" value="${v("balance")}" placeholder="5000"></div>
      <div class="bt-field"><label for="f-alindi">Alınma tarihi</label>
        <input id="f-alindi" type="date" value="${v("alindi")}"></div>
      <div class="bt-field"><label for="f-due">Geri ödeme sözü</label>
        <input id="f-due" type="date" value="${v("due")}"></div>`;
  } else {
    alan = `
      <div class="bt-field"><label for="f-balance">Tutar (₺)</label>
        <input id="f-balance" type="text" inputmode="decimal" value="${v("balance")}"></div>
      <div class="bt-field"><label for="f-due">Son ödeme tarihi</label>
        <input id="f-due" type="date" value="${v("due")}"></div>`;
  }

  const turBtns = Object.entries(TURLER).map(([k,l])=>
    `<option value="${k}" ${formType===k?"selected":""}>${l}</option>`).join("");

  return `
  <div class="bt-form">
    <div class="bt-grid">
      <div class="bt-field"><label for="f-type">Borç türü</label>
        <select id="f-type">${turBtns}</select></div>
      <div class="bt-field"><label for="f-name">Ad / etiket</label>
        <input id="f-name" type="text" value="${v("name")}" placeholder="${t==="kredikarti"?"Ziraat Bankkart Combo":"Kısa bir isim"}"></div>
      ${alan}
      <div class="bt-field" style="grid-column:1/-1"><label for="f-note">Not</label>
        <input id="f-note" type="text" value="${v("note")}" placeholder="isteğe bağlı"></div>
    </div>
    <div id="bt-live"></div>
    <div class="bt-form-actions">
      <button class="bt-btn" id="bt-save">${editingDebt?"Değişiklikleri kaydet":"Borcu ekle"}</button>
      <button class="bt-btn quiet" id="bt-cancel">Vazgeç</button>
    </div>
  </div>`;
}

function readForm(){
  const g = id => { const e = document.getElementById(id); return e ? e.value.trim() : ""; };
  const t = formType;
  const d = { id: editingDebt ? editingDebt.id : uid(), type: t, name: g("f-name"), note: g("f-note"), due: g("f-due") };
  if(t === "kredikarti" || t === "avans"){
    Object.assign(d, {
      bank: g("f-bank"), limit: num(g("f-limit")), balance: num(g("f-balance")),
      avans: num(g("f-avans")), kesim: g("f-kesim"),
      rateOverride: g("f-rate"), asgariOverride: g("f-asgari"),
      kurumsal: g("f-kurumsal") === "1"
    });
    if(!d.name) d.name = (d.bank || "Kredi kartı") + " kartı";
  } else if(t === "kredi"){
    Object.assign(d, {
      bank: g("f-bank"), principal: num(g("f-principal")), rate: num(g("f-rate2")),
      vade: num(g("f-vade")), kalanTaksit: g("f-kalan"), taksit: g("f-taksit")
    });
    if(!d.name) d.name = (d.bank || "Kredi") + " kredisi";
  } else if(t === "elden"){
    Object.assign(d, { yon: g("f-yon"), kisi: g("f-kisi"), balance: num(g("f-balance")), alindi: g("f-alindi") });
    if(!d.name) d.name = d.kisi || "Elden borç";
  } else {
    d.balance = num(g("f-balance"));
    if(!d.name) d.name = "Borç";
  }
  if(editingDebt && editingDebt.type === d.type && editingDebt.due === d.due){
    ["donemAnahtari", "donemHedef", "donemOdenen"].forEach(key => { if(editingDebt[key] != null) d[key] = editingDebt[key]; });
  }
  return d;
}

function renderLive(){
  const box = document.getElementById("bt-live");
  if(!box) return;
  const d = readForm();
  if(d.type === "kredikarti" || d.type === "avans"){
    if(!num(d.balance)){ box.innerHTML = ""; return; }
    const c = hesaplaKart(d);
    box.innerHTML = `<div class="bt-live">
      Limit ${fmt0(c.limit)} → asgari oran <b>${pct(c.oran)}</b>. Dönem borcu ${fmt(c.borc)} üzerinden
      <b>asgari ödeme ${fmt(c.asgari)}</b>.<br>
      Sadece asgariyi ödersen kalan ${fmt(c.kalan)} bakiyeye aylık ${pct(c.akdi)} akdi faiz + %${state.settings.vergi} vergi işler:
      <b>${fmt(c.faizToplam)}</b>. Gelecek ay ekstren <b>${fmt(c.sonrakiBorc)}</b> olarak açılır.
    </div>`;
  } else if(d.type === "kredi"){
    if(!num(d.principal) || !num(d.vade)){ box.innerHTML = ""; return; }
    const k = hesaplaKredi(d);
    box.innerHTML = `<div class="bt-live">
      Aylık taksit <b>${fmt(k.taksit)}</b>, ${k.vade} ay boyunca toplam <b>${fmt(k.toplamOdeme)}</b>
      (${fmt(k.toplamFaiz)} faiz). Kalan ${k.kalanTaksit} taksit için ödenecek: <b>${fmt(k.kalanOdeme)}</b>.
    </div>`;
  } else { box.innerHTML = ""; }
}

function sparkline(seri){
  if(seri.length < 2) return "";
  const w = 600, h = 78, max = Math.max(...seri), min = 0;
  const pts = seri.map((v,i)=>{
    const x = (i/(seri.length-1))*w;
    const y = h - 6 - ((v-min)/(max-min||1))*(h-14);
    return `${x.toFixed(1)},${y.toFixed(1)}`;
  }).join(" ");
  return `<svg class="bt-spark" viewBox="0 0 ${w} ${h}" preserveAspectRatio="none" role="img" aria-label="Borç bakiyesinin ay ay seyri">
    <polyline points="${pts}" fill="none" stroke="var(--pine)" stroke-width="2" stroke-linejoin="round"/>
    <polyline points="0,${h-6} ${pts} ${w},${h-6}" fill="var(--pine)" opacity="0.09" stroke="none"/>
  </svg>`;
}

function debtCard(d){
  const acik = openCards.has(d.id);
  let sag = "", chips = "", govde = "";

  const st = donemDurumu(d);
  const kismiChip = st.kismi
    ? `<span class="bt-chip amber">bu dönem ${fmt(st.odenen)} ödendi · ${fmt(st.kalan)} kaldı</span>` : "";

  if(d.type === "kredikarti" || d.type === "avans"){
    const c = hesaplaKart(d);
    const p = projeksiyon(d);
    sag = st.kismi
      ? `<span class="big">${fmt(st.kalan)}</span><span class="lbl">bu dönem kalan</span>`
      : `<span class="big">${fmt(c.asgari)}</span><span class="lbl">bu ay asgari</span>`;
    chips = kismiChip + `<span class="bt-chip pine">asgari ${pct(c.oran)}</span><span class="bt-chip grey">akdi ${pct(c.akdi)}</span>` +
            (c.doluluk >= 90 ? `<span class="bt-chip red">limit %${c.doluluk.toFixed(0)} dolu</span>` : "") +
            (d.kurumsal ? `<span class="bt-chip grey">ticari</span>` : "");
    govde = `
      <div class="bt-kv">
        <div><span>Dönem borcu</span><b>${fmt(c.borc)}</b></div>
        <div><span>Limit / kalan</span><b>${fmt0(c.limit)} · ${fmt0(c.kalanLimit)}</b></div>
        <div><span>Asgari ödeme (${pct(c.oran)})</span><b>${fmt(c.asgari)}</b></div>
        ${st.kismi?`<div><span>Bu dönem ödenen / kalan</span><b>${fmt(st.odenen)} / ${fmt(st.kalan)}</b></div>`:""}
        <div><span>Asgari sonrası kalan</span><b>${fmt(c.kalan)}</b></div>
        <div><span>Akdi faiz (${pct(c.akdi)})</span><b>${fmt(c.faizHam)}</b></div>
        <div><span>KKDF + BSMV (%${state.settings.vergi})</span><b>${fmt(c.faizVergi)}</b></div>
        <div><span>Toplam faiz yükü</span><b style="color:var(--red)">${fmt(c.faizToplam)}</b></div>
        <div><span>Tahmini gelecek ay borç</span><b>${fmt(c.sonrakiBorc)}</b></div>
        <div><span>Hesap kesim</span><b>${fmtDate(d.kesim)}</b></div>
        <div><span>Gecikme faizi senaryosu (yaklaşık) (${pct(c.gecikme)})</span><b style="color:var(--red)">${fmt(c.gecikmeFaiz)}</b></div>
      </div>
      <div class="bt-callout">
        Kayıtlı oranlarla, yalnızca asgari ödeme ve yeni harcama olmaması varsayımıyla bu borç
        <b>${p.bitti ? p.ay + " ayda" : "60 aydan uzun sürede"}</b> kapanır;
        toplam <b>${fmt(p.odenen)}</b> ödersin, bunun <b>${fmt(p.faiz)}</b>'si faiz.
        ${sparkline(p.seri)}
      </div>`;
  } else if(d.type === "kredi"){
    const k = hesaplaKredi(d);
    sag = st.kismi
      ? `<span class="big">${fmt(st.kalan)}</span><span class="lbl">bu dönem kalan</span>`
      : `<span class="big">${fmt(k.taksit)}</span><span class="lbl">aylık taksit</span>`;
    chips = kismiChip + `<span class="bt-chip pine">${k.kalanTaksit}/${k.vade} taksit kaldı</span><span class="bt-chip grey">aylık ${pct(k.oran)}</span>`;
    govde = `<div class="bt-kv">
        <div><span>Anapara</span><b>${fmt(k.anapara)}</b></div>
        <div><span>Aylık taksit</span><b>${fmt(k.taksit)}</b></div>
        <div><span>Kalan ödeme</span><b>${fmt(k.kalanOdeme)}</b></div>
        <div><span>Toplam geri ödeme</span><b>${fmt(k.toplamOdeme)}</b></div>
        <div><span>Toplam faiz</span><b style="color:var(--red)">${fmt(k.toplamFaiz)}</b></div>
        <div><span>Ödenen taksit</span><b>${k.odenenTaksit}</b></div>
      </div>`;
  } else if(d.type === "elden"){
    const alacak = d.yon === "alacak";
    sag = `<span class="big" style="color:${alacak?"var(--green)":"var(--ink)"}">${fmt(num(d.balance))}</span><span class="lbl">${alacak?"alacak":"borç"}</span>`;
    chips = `<span class="bt-chip ${alacak?"green":"grey"}">${alacak?"Bana borçlu":"Ben borçluyum"}</span>` +
            (d.kisi ? `<span class="bt-chip grey">${esc(d.kisi)}</span>` : "");
    govde = `<div class="bt-kv">
        <div><span>Tutar</span><b>${fmt(num(d.balance))}</b></div>
        <div><span>Alınma tarihi</span><b>${fmtDate(d.alindi)}</b></div>
        <div><span>Geri ödeme sözü</span><b>${fmtDate(d.due)}</b></div>
      </div>`;
  } else {
    sag = `<span class="big">${fmt(num(d.balance))}</span><span class="lbl">bakiye</span>`;
    govde = `<div class="bt-kv"><div><span>Tutar</span><b>${fmt(num(d.balance))}</b></div></div>`;
  }

  return `<div class="bt-card">
    <div class="bt-card-head">
      <div class="bt-card-id">
        <h3>${esc(d.name)}</h3>
        <p>${TURLER[d.type]}${d.bank?" · "+esc(d.bank):""}${d.due?" · son ödeme "+fmtDate(d.due):""}</p>
        <div style="margin-top:7px">${chips}${d.due?vadeChip(d.due):""}</div>
        ${d.note?`<p style="margin-top:6px">${esc(d.note)}</p>`:""}
      </div>
      <div class="bt-card-amt">${sag}</div>
    </div>
    ${acik ? `<div class="bt-card-body">${govde}</div>` : ""}
    <div class="bt-card-actions">
      <button class="bt-btn quiet sm" data-toggle="${d.id}">${acik?"Detayı kapat":"Detay"}</button>
      <button class="bt-btn ghost sm" data-pay="${d.id}">${alacakMi(d) ? "Tahsilat kaydet" : "Ödeme kaydet"}</button>
      <button class="bt-btn quiet sm" data-edit="${d.id}">Düzenle</button>
      <button class="bt-btn danger sm" data-del="${d.id}">Sil</button>
    </div>
  </div>`;
}

/* Ayrı ödeme giriş formu — kısmi ödemeler buradan girilir */
function odemeFormHTML(){
  const borclar = state.debts.filter(d => toplamBakiye(d) > 0 || donemTutari(d) > 0);
  if(!borclar.length){
    return `<div class="bt-empty"><b>Ödenecek borç yok</b>Önce bir borç kaydı ekle.</div>`;
  }
  const secili = odemeSecim && borclar.some(d=>d.id===odemeSecim) ? odemeSecim : borclar[0].id;
  const d  = borclar.find(x=>x.id===secili);
  const st = donemDurumu(d);
  return `
  <div class="bt-form">
    <div class="bt-grid">
      <div class="bt-field"><label for="od-borc">Hangi borç</label>
        <select id="od-borc">${borclar.map(x=>`<option value="${x.id}" ${x.id===secili?"selected":""}>${esc(x.name)} — ${TURLER[x.type]}</option>`).join("")}</select></div>
      <div class="bt-field"><label for="od-tutar">${alacakMi(d) ? "Tahsil edilen" : "Ödenen"} tutar (₺)</label>
        <input id="od-tutar" type="text" inputmode="decimal" value="${st.kalan > 0 ? st.kalan.toFixed(2).replace(".",",") : ""}" placeholder="0">
        <div class="hint">Kısmi ödeyebilirsin; kalan aynı vadede durur.</div></div>
      <div class="bt-field"><label for="od-tarih">Ödeme tarihi</label>
        <input id="od-tarih" type="date" value="${isoOf(today())}"></div>
      <div class="bt-field"><label for="od-hesap">${alacakMi(d) ? "Tahsilat hesabı" : "Ödeme hesabı"}</label>
        <select id="od-hesap"><option value="">Hesap bakiyesini değiştirme</option>
          ${state.accounts.map(a=>`<option value="${a.id}">${esc(a.name)} · ${fmt(a.balance)}</option>`).join("")}</select>
        <div class="hint">Hesap seçersen bakiye işlemle birlikte güncellenir.</div></div>
      <div class="bt-field"><label for="od-not">Açıklama</label>
        <input id="od-not" type="text" placeholder="isteğe bağlı"></div>
    </div>
    <div class="bt-live">
      <b>${esc(d.name)}</b> — son ödeme ${fmtDate(d.due)} ${d.due?`(${daysUntil(d.due)} gün)`:""}<br>
      Bu dönem gereken <b>${fmt(st.hedef)}</b> · ödenen <b>${fmt(st.odenen)}</b> · kalan <b>${fmt(st.kalan)}</b><br>
      Kalanı bugün ödemezsen vade ileri kaymaz; aynı gün için borç olarak durmaya devam eder.
    </div>
    <div class="bt-form-actions">
      <button class="bt-btn" id="bt-odeme-kaydet">${alacakMi(d) ? "Tahsilatı işle" : "Ödemeyi işle"}</button>
      <button class="bt-btn quiet" id="bt-odeme-kapat">Vazgeç</button>
    </div>
  </div>`;
}

function renderBorclar(){
  const el = document.getElementById("bt-borclar");
  const showForm = el.dataset.form === "1";
  const showPay  = el.dataset.pay === "1";
  const gruplar = Object.keys(TURLER).map(t => [t, state.debts.filter(d=>d.type===t)]).filter(([,a])=>a.length);

  el.innerHTML = `
    <div class="bt-section" style="margin-top:22px">
      <div class="bt-section-head">
        <h2>Borç kayıtları</h2>
        <div style="display:flex;gap:7px;flex-wrap:wrap">
          <button class="bt-btn ghost" id="bt-odeme-ac">${showPay?"Ödeme formunu kapat":"Ödeme gir"}</button>
          <button class="bt-btn" id="bt-new">${showForm?"Formu kapat":"Borç ekle"}</button>
        </div>
      </div>
      ${showPay ? odemeFormHTML() : ""}
      ${showForm ? formHTML() : ""}
      ${gruplar.length ? gruplar.map(([t,arr])=>`
        <div style="margin-top:18px">
          <div class="bt-section-head" style="margin-bottom:8px">
            <h2 style="font-size:13px;color:var(--ink-2);font-weight:600">${TURLER[t]} · ${arr.length}</h2>
            <span class="bt-note">${t==="elden" ? "Borç: " + fmt(arr.filter(d=>!alacakMi(d)).reduce((s,d)=>s+toplamBakiye(d),0)) + " · Alacak: " + fmt(arr.filter(alacakMi).reduce((s,d)=>s+toplamBakiye(d),0)) : "toplam " + fmt(arr.reduce((s,d)=>s+toplamBakiye(d),0))}</span>
          </div>
          ${arr.map(debtCard).join("")}
        </div>`).join("")
      : (showForm ? "" : `<div class="bt-empty"><b>Defter boş</b>Kredi kartı, kredi, elden borç ya da nakit avans ekleyerek başla.</div>`)}
    </div>`;

  if(showForm) renderLive();
}

/* =========================================================
   7. NAKİT AKIŞI
   ========================================================= */
const KAT_IN  = ["Maaş","Serbest iş / fatura","Burs","Satış","Borç tahsilatı","Diğer"];
const KAT_OUT = ["Borç ödemesi","Kira","Fatura","Market","Ulaşım","Malzeme / parça","Vergi","Diğer"];
/* Kayıtta listede olmayan bir kategori varsa onu da koru */
function katOpsiyon(tip, sec){
  const liste = tip === "in" ? KAT_IN : KAT_OUT;
  const hepsi = (sec && !liste.includes(sec)) ? [sec, ...liste] : liste;
  return hepsi.map(k => `<option ${k === sec ? "selected" : ""}>${esc(k)}</option>`).join("");
}

function renderAkis(){
  const el = document.getElementById("bt-akis");
  const mk = el.dataset.month || thisMonth();
  const aylar = [...new Set(state.flows.map(f=>monthKey(f.date)).concat([thisMonth()]))].sort().reverse();

  const kayit = state.flows.filter(f=>monthKey(f.date)===mk).sort((a,b)=>b.date.localeCompare(a.date));
  const gelir = kayit.filter(f=>f.type==="in").reduce((s,f)=>s+num(f.amount),0);
  const gider = kayit.filter(f=>f.type==="out").reduce((s,f)=>s+num(f.amount),0);

  const kat = {};
  kayit.filter(f=>f.type==="out").forEach(f=>{ kat[f.category||"Diğer"] = (kat[f.category||"Diğer"]||0)+num(f.amount); });
  const katSirali = Object.entries(kat).sort((a,b)=>b[1]-a[1]);

  el.innerHTML = `
    <div class="bt-section" style="margin-top:22px">
      <div class="bt-section-head">
        <h2>${editFlow ? "Hareketi düzenle" : "Girdi ve çıktı ekle"}</h2>
        ${editFlow ? `<button class="bt-btn quiet sm" id="bt-flow-cancel">Vazgeç</button>` : ""}
      </div>
      <div class="bt-form">
        <div class="bt-grid">
          <div class="bt-field"><label for="fl-type">Yön</label>
            <select id="fl-type">
              <option value="out" ${editFlow && editFlow.type === "in" ? "" : "selected"}>Çıktı (para gitti)</option>
              <option value="in" ${editFlow && editFlow.type === "in" ? "selected" : ""}>Girdi (para geldi)</option>
            </select></div>
          <div class="bt-field"><label for="fl-amount">Tutar (₺)</label>
            <input id="fl-amount" type="text" inputmode="decimal" value="${editFlow ? esc(editFlow.amount) : ""}" placeholder="0"></div>
          <div class="bt-field"><label for="fl-date">Tarih</label>
            <input id="fl-date" type="date" value="${editFlow ? esc(editFlow.date) : isoOf(today())}"></div>
          <div class="bt-field"><label for="fl-cat">Kategori</label>
            <select id="fl-cat">${katOpsiyon(editFlow ? editFlow.type : "out", editFlow ? editFlow.category : null)}</select></div>
          <div class="bt-field" style="grid-column:1/-1"><label for="fl-note">Açıklama</label>
            <input id="fl-note" type="text" value="${editFlow ? esc(editFlow.note || "") : ""}" placeholder="isteğe bağlı"></div>
        </div>
        <div class="bt-form-actions">
          <button class="bt-btn" id="bt-flow-add">${editFlow ? "Değişikliği kaydet" : "Kaydet"}</button>
        </div>
      </div>
    </div>

    <div class="bt-section">
      <div class="bt-section-head">
        <h2>Ay özeti</h2>
        <select id="bt-month" style="padding:6px 10px;border:1px solid var(--line);border-radius:8px;background:#fff;font-size:13.5px">
          ${aylar.map(m=>`<option value="${m}" ${m===mk?"selected":""}>${new Date(m+"-01T00:00:00").toLocaleDateString("tr-TR",{month:"long",year:"numeric"})}</option>`).join("")}
        </select>
      </div>
      <dl class="bt-hero-grid" style="margin-top:0">
        <div class="bt-stat"><dt>Giren</dt><dd style="color:var(--green)">${fmt(gelir)}</dd></div>
        <div class="bt-stat"><dt>Çıkan</dt><dd style="color:var(--red)">${fmt(gider)}</dd></div>
        <div class="bt-stat"><dt>Net</dt><dd style="color:${gelir-gider<0?"var(--red)":"var(--green)"}">${fmt(gelir-gider)}</dd></div>
        <div class="bt-stat"><dt>Kayıt sayısı</dt><dd>${kayit.length}</dd></div>
      </dl>
      ${katSirali.length ? `<div class="bt-callout pine">En çok harcama: ${katSirali.slice(0,3).map(([k,v])=>`<b>${esc(k)}</b> ${fmt0(v)}`).join(" · ")}</div>` : ""}
    </div>

    <div class="bt-section">
      <div class="bt-section-head"><h2>Hareketler</h2></div>
      ${kayit.length ? `<div class="bt-scroll"><table class="bt-table">
        <thead><tr><th>Tarih</th><th>Kategori</th><th>Açıklama</th><th class="num">Tutar</th><th></th></tr></thead>
        <tbody>${kayit.map(f=>`<tr>
          <td>${fmtDate(f.date)}</td>
          <td>${esc(f.category||"—")}</td>
          <td>${esc(f.note||"")}</td>
          <td class="num ${f.type}">${f.type==="in"?"+":"−"}${fmt(num(f.amount))}</td>
          <td class="num">
            <button class="bt-btn quiet sm" data-editflow="${f.id}">Düzenle</button>
            <button class="bt-btn danger sm" data-delflow="${f.id}">Sil</button></td>
        </tr>`).join("")}</tbody></table></div>`
      : `<div class="bt-empty"><b>Bu ay kayıt yok</b>Yukarıdaki formdan ilk girdini ekle.</div>`}
    </div>`;
}

/* =========================================================
   7b. ABONELİKLER
   ========================================================= */
function renderAbonelik(){
  const el = document.getElementById("bt-abonelik");
  const e = editSub || {};
  const v = (k,d="") => e[k] != null ? esc(e[k]) : d;
  const liste = [...state.subs].sort((a,b)=>(num(a.day)||1)-(num(b.day)||1));
  const toplam = aylikAbonelik();
  const yillik = toplam * 12;

  el.innerHTML = `
    <div class="bt-section" style="margin-top:22px">
      <div class="bt-section-head">
        <h2>${editSub ? "Aboneliği düzenle" : "Yeni abonelik"}</h2>
        ${editSub ? `<button class="bt-btn quiet sm" id="bt-sub-cancel">Vazgeç</button>` : ""}
      </div>
      <div class="bt-form">
        <div class="bt-grid">
          <div class="bt-field"><label for="sb-name">Abonelik adı</label>
            <input id="sb-name" type="text" value="${v("name")}" placeholder="Spotify, Turkcell, spor salonu…"></div>
          <div class="bt-field"><label for="sb-amount">Aylık tutar (₺)</label>
            <input id="sb-amount" type="text" inputmode="decimal" value="${v("amount")}" placeholder="0"></div>
          <div class="bt-field"><label for="sb-day">Ayın kaçında çekiliyor</label>
            <input id="sb-day" type="number" min="1" max="31" value="${v("day","1")}">
            <div class="hint">31'i olmayan aylarda ayın son gününe kayar.</div></div>
          <div class="bt-field"><label for="sb-link">Bağlı kart / hesap</label>
            <select id="sb-link">${baglantiSecenekleri(e.link)}</select>
            <div class="hint">Kartlar Borçlar, hesaplar Bakiyeler sekmesinden gelir.</div></div>
          <div class="bt-field"><label for="sb-cat">Kategori</label>
            <select id="sb-cat">${ABONE_KAT.map(k=>`<option ${e.category===k?"selected":""}>${k}</option>`).join("")}</select></div>
          <div class="bt-field"><label for="sb-active">Durum</label>
            <select id="sb-active">
              <option value="1" ${e.active===false?"":"selected"}>Aktif</option>
              <option value="0" ${e.active===false?"selected":""}>Dondurulmuş (hesaba katma)</option>
            </select></div>
          <div class="bt-field" style="grid-column:1/-1"><label for="sb-note">Not</label>
            <input id="sb-note" type="text" value="${v("note")}" placeholder="isteğe bağlı"></div>
        </div>
        <div class="bt-form-actions">
          <button class="bt-btn" id="bt-sub-save">${editSub?"Kaydet":"Abonelik ekle"}</button>
        </div>
      </div>
    </div>

    <div class="bt-section">
      <div class="bt-section-head">
        <h2>Kayıtlı abonelikler</h2>
        <span class="bt-note">aylık ${fmt(toplam)} · yıllık ${fmt0(yillik)}</span>
      </div>
      ${liste.length ? `<div class="bt-scroll"><table class="bt-table">
        <thead><tr><th>Abonelik</th><th>Gün</th><th>Bağlı</th><th>Sonraki ödeme</th><th class="num">Tutar</th><th></th></tr></thead>
        <tbody>${liste.map(x=>{
          const snk = aboneSonraki(x);
          const pasif = x.active === false;
          return `<tr style="${pasif?"opacity:.5":""}">
            <td><b>${esc(x.name)}</b>${x.category?`<br><span style="color:var(--ink-3);font-size:12px">${esc(x.category)}</span>`:""}</td>
            <td>her ayın ${Math.min(31,Math.max(1,Math.round(num(x.day))||1))}'i</td>
            <td>${esc(baglantiAdi(x.link))}</td>
            <td>${pasif?`<span class="bt-chip grey">dondurulmuş</span>`:`${fmtDate(snk)} ${vadeChip(snk)}`}</td>
            <td class="num"><b>${fmt(num(x.amount))}</b></td>
            <td class="num">
              <button class="bt-btn quiet sm" data-subedit="${x.id}">Düzenle</button>
              <button class="bt-btn danger sm" data-subdel="${x.id}">Sil</button></td>
          </tr>`;}).join("")}
        </tbody></table></div>
        <div class="bt-callout pine">Bu abonelikler panel toplamına, 45 günlük şeride ve günlük harcama planına otomatik giriyor.</div>`
      : `<div class="bt-empty"><b>Abonelik yok</b>Her ay sabit çekilen ödemelerini ekle: dijital abonelikler, hat faturası, sigorta, spor salonu.</div>`}
    </div>`;
}

/* =========================================================
   7c. BAKİYELER
   ========================================================= */
function renderKasa(){
  const el = document.getElementById("bt-kasa");
  const e = editAcc || {};
  const v = (k,d="") => e[k] != null ? esc(e[k]) : d;
  const nakit = eldekiPara();
  const kartlar = state.debts.filter(d=>d.type==="kredikarti");
  const limit = kullanilabilirLimit();

  el.innerHTML = `
    <div class="bt-section" style="margin-top:22px">
      <div class="bt-section-head">
        <h2>${editAcc ? "Hesabı düzenle" : "Hesap / bakiye ekle"}</h2>
        ${editAcc ? `<button class="bt-btn quiet sm" id="bt-acc-cancel">Vazgeç</button>` : ""}
      </div>
      <div class="bt-form">
        <div class="bt-grid">
          <div class="bt-field"><label for="ac-name">Ad</label>
            <input id="ac-name" type="text" value="${v("name")}" placeholder="Ziraat vadesiz, cüzdan…"></div>
          <div class="bt-field"><label for="ac-kind">Tür</label>
            <select id="ac-kind">${Object.entries(HESAP_TURLERI).map(([k,l])=>`<option value="${k}" ${e.kind===k?"selected":""}>${l}</option>`).join("")}</select></div>
          <div class="bt-field"><label for="ac-bank">Banka</label>
            <select id="ac-bank"><option value="">—</option>${BANKALAR.map(b=>`<option ${e.bank===b?"selected":""}>${b}</option>`).join("")}</select></div>
          <div class="bt-field"><label for="ac-balance">Güncel bakiye (₺)</label>
            <input id="ac-balance" type="text" inputmode="decimal" value="${v("balance")}" placeholder="0"></div>
          <div class="bt-field"><label for="ac-kisitli">Bunun harçlık / kısıtlı kısmı (₺)</label>
            <input id="ac-kisitli" type="text" inputmode="decimal" value="${v("kisitli")}" placeholder="0">
            <div class="hint">Borç ödemesinde kullanmayacağın tutar. Tamamı harçlıksa bakiyenin aynısını yaz.</div></div>
          <div class="bt-field" style="grid-column:1/-1"><label for="ac-note">Not</label>
            <input id="ac-note" type="text" value="${v("note")}" placeholder="isteğe bağlı"></div>
        </div>
        <div class="bt-form-actions">
          <button class="bt-btn" id="bt-acc-save">${editAcc?"Kaydet":"Hesap ekle"}</button>
        </div>
      </div>
    </div>

    <div class="bt-section">
      <div class="bt-section-head"><h2>Eldeki para</h2>
        <span class="bt-note">toplam ${fmt(nakit)} · serbest ${fmt(serbestPara())} · harçlık ${fmt(kisitliPara())}</span></div>
      ${state.accounts.length ? `<div class="bt-scroll"><table class="bt-table">
        <thead><tr><th>Hesap</th><th>Tür</th><th>Banka</th><th class="num">Bakiye</th><th class="num">Harçlık</th><th class="num">Serbest</th><th></th></tr></thead>
        <tbody>${state.accounts.map(a=>`<tr>
          <td><b>${esc(a.name)}</b>${a.note?`<br><span style="color:var(--ink-3);font-size:12px">${esc(a.note)}</span>`:""}</td>
          <td>${HESAP_TURLERI[a.kind]||"—"}</td>
          <td>${esc(a.bank||"—")}</td>
          <td class="num"><b>${fmt(num(a.balance))}</b></td>
          <td class="num">${num(a.kisitli) ? fmt(Math.min(num(a.balance),num(a.kisitli))) : "—"}</td>
          <td class="num">${fmt(Math.max(0, num(a.balance)-num(a.kisitli)))}</td>
          <td class="num">
            <button class="bt-btn quiet sm" data-accedit="${a.id}">Düzenle</button>
            <button class="bt-btn danger sm" data-accdel="${a.id}">Sil</button></td>
        </tr>`).join("")}</tbody></table></div>`
      : `<div class="bt-empty"><b>Hesap yok</b>Banka hesabındaki ve cebindeki parayı gir; günlük harcama planı bu rakamdan çıkıyor.</div>`}
    </div>

    <div class="bt-section">
      <div class="bt-section-head"><h2>Kartlarda kullanılabilir limit</h2><span class="bt-note">toplam ${fmt(limit)}</span></div>
      ${kartlar.length ? `<div class="bt-scroll"><table class="bt-table">
        <thead><tr><th>Kart</th><th>Banka</th><th class="num">Limit</th><th class="num">Dönem borcu</th><th class="num">Kullanılabilir</th></tr></thead>
        <tbody>${kartlar.map(d=>{const c=hesaplaKart(d);return `<tr>
          <td><b>${esc(d.name)}</b></td><td>${esc(d.bank||"—")}</td>
          <td class="num">${fmt(c.limit)}</td><td class="num">${fmt(c.borc)}</td>
          <td class="num"><b>${fmt(c.kalanLimit)}</b></td>
        </tr>`;}).join("")}</tbody></table></div>
        <div class="bt-callout">Bu rakam <b>para değil, borçlanma kapasitesi</b>. Günlük harcama planına dahil edilmiyor; harcarsan gelecek ayın asgari ödemesi ve faizi büyür.</div>`
      : `<div class="bt-empty"><b>Kayıtlı kart yok</b>Borçlar sekmesinden kredi kartı eklersen kullanılabilir limitleri burada toplanır.</div>`}
    </div>`;
}

/* =========================================================
   7d. GELİR VE GÜNLÜK PLAN
   ========================================================= */
function renderPlan(){
  const el = document.getElementById("bt-plan");
  const e = editInc || {};
  const v = (k,d="") => e[k] != null ? esc(e[k]) : d;
  const plan = planHesapla();
  const takvim = gelirTakvimi(120);

  const planKutu = !state.accounts.length
    ? `<div class="bt-empty"><b>Önce bakiyeni gir</b>Plan, Bakiyeler sekmesindeki toplam paradan hesaplanıyor.</div>`
    : `
    <div class="bt-hero" style="border-bottom:0;padding-top:0">
      <p class="bt-hero-lead">${plan.hedef
        ? `${esc(plan.etiket)} gelirine kadar günde harcayabileceğin`
        : `Ay sonuna kadar günde harcayabileceğin`}</p>
      <p class="bt-hero-num" style="color:${plan.gunluk<0?"var(--red)":"var(--pine)"}">${fmt(plan.gunluk)}</p>
      <p class="bt-hero-sub">${plan.gun} gün · hedef tarih ${fmtDate(plan.bitis)}${plan.hedef?` · ${fmt(plan.hedef.amount)} gelecek`:""}</p>
      <dl class="bt-hero-grid">
        <div class="bt-stat"><dt>Eldeki para</dt><dd>${fmt0(plan.nakit)}</dd></div>
        <div class="bt-stat"><dt>Borçlara ayır</dt><dd style="color:var(--red)">${fmt0(plan.borcTutar)}</dd></div>
        <div class="bt-stat"><dt>Aboneliklere ayır</dt><dd style="color:var(--red)">${fmt0(plan.aboTutar)}</dd></div>
        <div class="bt-stat"><dt>Serbest kalan</dt><dd style="color:${plan.serbest<0?"var(--red)":"var(--green)"}">${fmt0(plan.serbest)}</dd></div>
        <div class="bt-stat"><dt>Günlük borç payı</dt><dd>${fmt0(plan.gunlukBorcPayi)}</dd></div>
      </dl>
    </div>
    ${plan.serbest < 0 || plan.borcAcik > 0 ? `<div class="bt-callout">
        <b>Açık var:</b> bu dönemin zorunlu ödemeleri elindeki parayı ${fmt(Math.max(0,-plan.serbest) + plan.borcAcik)} aşıyor.
        Gelir tarihini öne çekemiyorsan asgari ödemeye düşmek, dondurulabilecek abonelik aramak
        ya da ${fmt(Math.max(0,-plan.serbest) + plan.borcAcik)} ek gelir bulmak gerekiyor.
      </div>` : `<div class="bt-callout pine">
        Girilen kayıtlara göre günlük pay ${fmt(plan.gunluk)}. Bu senaryo ${fmtDate(plan.bitis)} tarihine kadar listelenen ödemeleri kapsar.
        Güvenlik payı istiyorsan bu rakamın %10–20 altında kal.
      </div>`}
    ${plan.kalemler.length ? `<div class="bt-section">
      <div class="bt-section-head"><h2>Bu dönem çıkacaklar</h2><span class="bt-note">toplam ${fmt(plan.zorunlu)}</span></div>
      <div class="bt-scroll"><table class="bt-table">
        <thead><tr><th>Kalem</th><th>Tarih</th><th class="num">Tutar</th></tr></thead>
        <tbody>${plan.kalemler.map(k=>`<tr>
          <td>${esc(k.ad)} ${k.gecikmis?`<span class="bt-chip red">gecikmiş</span>`:""}</td>
          <td>${fmtDate(k.date)}</td>
          <td class="num"><b>${fmt(k.amount)}</b></td>
        </tr>`).join("")}</tbody></table></div>
    </div>` : ""}`;

  el.innerHTML = `
    <div class="bt-section" style="margin-top:22px">
      <div class="bt-section-head">
        <h2>${editInc ? "Geliri düzenle" : "Gelecek gelir ekle"}</h2>
        ${editInc ? `<button class="bt-btn quiet sm" id="bt-inc-cancel">Vazgeç</button>` : ""}
      </div>
      <div class="bt-form">
        <div class="bt-grid">
          <div class="bt-field"><label for="in-name">Ne geliyor</label>
            <input id="in-name" type="text" value="${v("name")}" placeholder="Maaş, fatura tahsilatı, burs…"></div>
          <div class="bt-field"><label for="in-amount">Tutar (₺)</label>
            <input id="in-amount" type="text" inputmode="decimal" value="${v("amount")}" placeholder="0"></div>
          <div class="bt-field"><label for="in-date">Geleceği tarih</label>
            <input id="in-date" type="date" value="${v("date")}"></div>
          <div class="bt-field"><label for="in-rep">Tekrar</label>
            <select id="in-rep">
              <option value="tek" ${e.repeat==="aylik"?"":"selected"}>Tek seferlik</option>
              <option value="aylik" ${e.repeat==="aylik"?"selected":""}>Her ay aynı gün</option>
            </select></div>
          <div class="bt-field"><label for="in-kind">Gelir türü</label>
            <select id="in-kind">
              <option value="serbest" ${e.kind==="harclik"?"":"selected"}>Serbest — borç dahil her şeye</option>
              <option value="harclik" ${e.kind==="harclik"?"selected":""}>Harçlık avans — borca kullanılamaz</option>
            </select>
            <div class="hint">Harçlık avans sadece günlük gider ve aboneliklere sayılır; borç ödemesine dahil edilmez.</div></div>
          <div class="bt-field" style="grid-column:1/-1"><label for="in-note">Not</label>
            <input id="in-note" type="text" value="${v("note")}" placeholder="isteğe bağlı"></div>
        </div>
        <div class="bt-form-actions">
          <button class="bt-btn" id="bt-inc-save">${editInc?"Kaydet":"Gelir ekle"}</button>
        </div>
      </div>
    </div>

    <div class="bt-section">
      <div class="bt-section-head"><h2>Beklenen gelirler</h2></div>
      ${takvim.length ? `<div class="bt-scroll"><table class="bt-table">
        <thead><tr><th>Gelir</th><th>Tarih</th><th>Tekrar</th><th class="num">Tutar</th><th></th></tr></thead>
        <tbody>${takvim.slice(0,12).map(g=>`<tr>
          <td><b>${esc(g.name)}</b></td>
          <td>${fmtDate(g.date)} ${vadeChip(g.date)}</td>
          <td>${g.repeat==="aylik"?"her ay":"tek seferlik"}${g.kind==="harclik"?` <span class="bt-chip amber">harçlık</span>`:""}</td>
          <td class="num in">+${fmt(g.amount)}</td>
          <td class="num">
            <button class="bt-btn quiet sm" data-incedit="${g.id}">Düzenle</button>
            <button class="bt-btn danger sm" data-incdel="${g.id}">Sil</button></td>
        </tr>`).join("")}</tbody></table></div>`
      : `<div class="bt-empty"><b>Kayıtlı gelir yok</b>Maaşını ya da bekleyen tahsilatını ekle; plan o tarihe göre kurulur. Girmezsen ay sonu esas alınır.</div>`}
    </div>

    <div class="bt-section">
      <div class="bt-section-head"><h2>Günlük harcama planı</h2></div>
      ${planKutu}
    </div>`;
}

/* =========================================================
   7e. HEDEFLER
   ========================================================= */
function renderHedef(){
  const el = document.getElementById("bt-hedef");
  const e = editGoal || {};
  const v = (k,d="") => e[k] != null ? esc(e[k]) : d;
  const P = hedefPlani();
  const O = oneriler(P);

  const adim = (no, baslik, icerik) => `
    <div class="bt-card">
      <div class="bt-card-head">
        <div class="bt-card-id">
          <h3>${no}. ${baslik}</h3>
        </div>
      </div>
      <div class="bt-card-body">${icerik}</div>
    </div>`;

  const kalemTablo = (liste) => liste.length ? `
    <div class="bt-scroll"><table class="bt-table">
      <tbody>${liste.map(k=>`<tr>
        <td>${esc(k.ad)} ${k.gecikmis?`<span class="bt-chip red">gecikmiş</span>`:""}</td>
        <td>${fmtDate(k.date)}</td>
        <td class="num"><b>${fmt(k.amount)}</b></td>
      </tr>`).join("")}</tbody></table></div>` : `<p class="bt-note">Bu pencerede ödeme yok.</p>`;

  // ---- 1. pencere ----
  const adim1 = adim(1,
    P.g1 ? `Bugünden ${fmtDate(P.g1.date)} tarihine kadar (${P.w1.gun} gün)`
         : `Bugünden ay sonuna kadar (${P.w1.gun} gün)`,
    `<div class="bt-kv">
       <div><span>Serbest cep (borca gider)</span><b>${fmt(P.w1.cepS)}</b></div>
       <div><span>Harçlık cebi (borca gitmez)</span><b>${fmt(P.w1.cepH)}</b></div>
       <div><span>Borçlara ayır</span><b style="color:var(--red)">${fmt(P.w1.borcT)}</b></div>
       <div><span>Aboneliklere ayır</span><b style="color:var(--red)">${fmt(P.w1.aboT)}</b></div>
       <div><span>Hedeflere ayır</span><b>${fmt(P.dagitim.reduce((t,d)=>t+d.ver,0))}</b></div>
       <div><span>Günlük harcama</span><b style="color:${(P.butce||P.azamiGunluk1)<0?"var(--red)":"var(--pine)"}">${fmt(P.butce > 0 ? P.butce : P.azamiGunluk1)}</b></div>
       <div><span>Dönem sonunda artan</span><b>${fmt(P.artan)}</b></div>
     </div>
     <div style="margin-top:14px"><b style="font-size:13px">Ödenecekler</b>${kalemTablo([...P.w1.borc, ...P.w1.abone].sort((a,b)=>a.date.localeCompare(b.date)))}</div>`);

  // ---- 2. pencere ----
  const adim2 = P.w2 ? adim(2,
    `${fmtDate(P.g1.date)} tarihinde ${fmt(P.g1.amount)} geldikten sonra${P.g2?` — ${fmtDate(P.g2.date)} tarihine kadar`:""} (${P.w2.gun} gün)`,
    `<div class="bt-kv">
       <div><span>Serbest cep</span><b style="color:var(--green)">${fmt(P.w2.cepS)}</b></div>
       <div><span>Harçlık cebi</span><b style="color:var(--green)">${fmt(P.w2.cepH)}</b></div>
       <div><span>Borçlara ayır</span><b style="color:var(--red)">${fmt(P.w2.borcT)}</b></div>
       <div><span>Aboneliklere ayır</span><b style="color:var(--red)">${fmt(P.w2.aboT)}</b></div>
       <div><span>Hedeflere ayır (min)</span><b>${fmt(P.hedefMin)}</b></div>
       <div><span>Günde harcayabilirsin</span><b style="color:${P.gunluk2<0?"var(--red)":"var(--pine)"}">${fmt(P.gunluk2)}</b></div>
     </div>
     ${!P.g2?`<p class="bt-note" style="margin-top:10px">Sonraki gelir kayıtlı olmadığı için 30 günlük pencere varsayıldı.</p>`:""}
     <div style="margin-top:14px"><b style="font-size:13px">Ödenecekler</b>${kalemTablo([...P.w2.borc, ...P.w2.abone].sort((a,b)=>a.date.localeCompare(b.date)))}</div>`)
    : `<div class="bt-empty"><b>İkinci adım çizilemedi</b>Gelir ve plan sekmesinden en az bir gelecek gelir gir; motor o tarihten sonrasını da planlasın.</div>`;

  // ---- hedef payları ----
  const paylar = P.dagitim.length ? `
    <div class="bt-scroll"><table class="bt-table">
      <thead><tr><th>Hedef</th><th>Kalan</th><th>Dönem</th><th class="num">Dönem başına min</th><th class="num">Bu dönem ayrılabilen</th></tr></thead>
      <tbody>${P.dagitim.map(d=>`<tr>
        <td><b>${esc(d.h.name)}</b> <span class="bt-chip grey">${ONCELIK[num(d.h.priority)||2]}</span>${d.tamam?` <span class="bt-chip green">tamamlandı</span>`:""}</td>
        <td>${fmt(d.kalanTutar)}</td>
        <td>${d.donem ? d.donem + " gelir dönemi" : "tarih yok"}</td>
        <td class="num">${d.min > 0 ? fmt(d.min) : "—"}</td>
        <td class="num"><b style="color:${d.eksik>0.5?"var(--red)":"var(--green)"}">${fmt(d.ver)}</b></td>
      </tr>`).join("")}</tbody></table></div>` : "";

  el.innerHTML = `
    <div class="bt-section" style="margin-top:22px">
      <div class="bt-section-head"><h2>Planlama senaryosu</h2>
        <span class="bt-note">${P.g1 ? `hedef gelir: ${esc(P.g1.name)}, ${fmtDate(P.g1.date)}` : "kayıtlı gelir yok"}</span>
      </div>
      <div class="bt-form">
        <div class="bt-grid">
          <div class="bt-field"><label for="hd-butce">Günlük yaşam bütçen (₺)</label>
            <input id="hd-butce" type="text" inputmode="decimal" value="${state.settings.gunlukButce || ""}" placeholder="0 = belirlenmedi">
            <div class="hint">Hedefleri aksatmadan tavanın: ${fmt(P.azamiGunluk1)}/gün</div></div>
        </div>
        <div class="bt-form-actions"><button class="bt-btn quiet" id="bt-butce-kaydet">Bütçeyi kaydet</button></div>
      </div>
      ${O.map(x=>`<div class="bt-callout ${x.ton==="pine"?"pine":""}" ${x.ton==="red"?'style="border-left-color:var(--red);background:var(--red-soft)"':""}>
          <b>${x.baslik}</b><br>${x.metin}
        </div>`).join("")}
    </div>

    <div class="bt-section">
      <div class="bt-section-head"><h2>Adım adım plan</h2></div>
      ${adim1}
      ${adim2}
      ${paylar ? `<div class="bt-section-head" style="margin:18px 0 8px"><h2 style="font-size:13px;color:var(--ink-2)">Hedef payları</h2></div>${paylar}` : ""}
    </div>

    <div class="bt-section">
      <div class="bt-section-head"><h2>Kart kullanım planı</h2></div>
      ${P.kart.bilgi.length ? `<div class="bt-scroll"><table class="bt-table">
        <thead><tr><th>Kart</th><th class="num">Kullanılabilir</th><th class="num">Doluluk</th>
          <th>Sonraki kesim</th><th class="num">Faizsiz gün</th><th class="num">Aylık maliyet</th><th>Rol</th></tr></thead>
        <tbody>${P.kart.bilgi.map(b=>{
          const rol = P.kart.harcama && P.kart.harcama.d.id === b.d.id
            ? `<span class="bt-chip green">buradan harca</span>`
            : (P.kart.odeme[0] && P.kart.odeme[0].d.id === b.d.id
               ? `<span class="bt-chip red">önce bunu öde</span>`
               : (b.c.doluluk >= 90 ? `<span class="bt-chip amber">dokunma</span>` : `<span class="bt-chip grey">—</span>`));
          return `<tr>
            <td><b>${esc(b.d.name)}</b><br><span style="color:var(--ink-3);font-size:12px">${esc(b.d.bank||"")}</span></td>
            <td class="num">${fmt(b.c.kalanLimit)}</td>
            <td class="num" style="color:${b.c.doluluk>=90?"var(--red)":b.c.doluluk>=70?"var(--amber)":"var(--ink)"}">%${b.c.doluluk.toFixed(0)}</td>
            <td>${b.kesim ? fmtDate(b.kesim) : "girilmemiş"}</td>
            <td class="num">${b.faizsizGun != null ? b.faizsizGun + " gün" : "—"}</td>
            <td class="num">%${b.maliyet.toFixed(2)}</td>
            <td>${rol}</td>
          </tr>`;}).join("")}
        </tbody></table></div>
        <p class="bt-note" style="margin-top:8px">Faizsiz gün, bugün yapılan bir harcamanın hangi ekstreye düşüp ne zaman ödeneceğine göre hesaplanıyor. Doğru çıkması için kartlara hesap kesim tarihini girmen gerekiyor.</p>`
      : `<div class="bt-empty"><b>Kayıtlı kredi kartı yok</b>Borçlar sekmesinden kart ekle; hangisinden harcayıp hangisini önce ödeyeceğini burada planlayayım.</div>`}
    </div>

    <div class="bt-section">
      <div class="bt-section-head">
        <h2>${editGoal ? "Hedefi düzenle" : "Yeni hedef"}</h2>
        ${editGoal ? `<button class="bt-btn quiet sm" id="bt-goal-cancel">Vazgeç</button>` : ""}
      </div>
      <div class="bt-form">
        <div class="bt-grid">
          <div class="bt-field"><label for="hd-name">Hedef</label>
            <input id="hd-name" type="text" value="${v("name")}" placeholder="Osiloskop, ehliyet, acil durum fonu…"></div>
          <div class="bt-field"><label for="hd-amount">Hedef tutar (₺)</label>
            <input id="hd-amount" type="text" inputmode="decimal" value="${v("amount")}" placeholder="0"></div>
          <div class="bt-field"><label for="hd-saved">Şu ana kadar biriken (₺)</label>
            <input id="hd-saved" type="text" inputmode="decimal" value="${v("saved")}" placeholder="0"></div>
          <div class="bt-field"><label for="hd-due">Hedef tarih</label>
            <input id="hd-due" type="date" value="${v("due")}">
            <div class="hint">Boş bırakırsan zorunlu pay ayrılmaz, sadece artandan beslenir.</div></div>
          <div class="bt-field"><label for="hd-pri">Öncelik</label>
            <select id="hd-pri">${Object.entries(ONCELIK).map(([k,l])=>`<option value="${k}" ${String(num(e.priority)||2)===k?"selected":""}>${l}</option>`).join("")}</select>
            <div class="hint">Para yetmezse yüksek öncelikliden başlanır.</div></div>
          <div class="bt-field" style="grid-column:1/-1"><label for="hd-note">Not</label>
            <input id="hd-note" type="text" value="${v("note")}" placeholder="isteğe bağlı"></div>
        </div>
        <div class="bt-form-actions"><button class="bt-btn" id="bt-goal-save">${editGoal?"Kaydet":"Hedef ekle"}</button></div>
      </div>
    </div>

    <div class="bt-section">
      <div class="bt-section-head"><h2>Hedeflerin</h2></div>
      ${state.goals.length ? state.goals.map(h=>{
        const kalan = Math.max(0, num(h.amount) - num(h.saved));
        const yuzde = num(h.amount) > 0 ? Math.min(100, num(h.saved)/num(h.amount)*100) : 0;
        const gk = daysUntil(h.due);
        return `<div class="bt-card">
          <div class="bt-card-head">
            <div class="bt-card-id">
              <h3>${esc(h.name)}</h3>
              <p>${fmt(num(h.saved))} / ${fmt(num(h.amount))} · %${yuzde.toFixed(0)} tamam</p>
              <div style="margin-top:7px">
                <span class="bt-chip ${kalan<=0?"green":"pine"}">${kalan<=0?"tamamlandı":"kalan "+fmt(kalan)}</span>
                <span class="bt-chip grey">${ONCELIK[num(h.priority)||2]} öncelik</span>
                ${h.due?`<span class="bt-chip ${gk<0?"red":gk<30?"amber":"grey"}">${fmtDate(h.due)}</span>`:`<span class="bt-chip grey">tarihsiz</span>`}
              </div>
              ${h.note?`<p style="margin-top:6px">${esc(h.note)}</p>`:""}
            </div>
            <div class="bt-card-amt"><span class="big">${fmt(kalan)}</span><span class="lbl">daha gerekiyor</span></div>
          </div>
          <div class="bt-card-actions">
            <button class="bt-btn ghost sm" data-goaladd="${h.id}">Birikim ekle</button>
            <button class="bt-btn quiet sm" data-goaledit="${h.id}">Düzenle</button>
            <button class="bt-btn danger sm" data-goaldel="${h.id}">Sil</button>
          </div>
        </div>`;}).join("")
      : `<div class="bt-empty"><b>Hedef yok</b>Biriktirmek istediğin şeyi tutarı ve tarihiyle ekle; motor her gelir döneminde ne kadar ayırman gerektiğini söylesin.</div>`}
    </div>`;
}

/* =========================================================
   8. AYARLAR
   ========================================================= */
function renderAyarlar(){
  const s = state.settings;
  const el = document.getElementById("bt-ayarlar");
  el.innerHTML = `
    <div class="bt-section" style="margin-top:22px">
      <div class="bt-section-head"><h2>Faiz varsayımları (aylık)</h2></div>
      <p class="bt-note" style="margin-bottom:12px">
        Başlangıç oranları TCMB’nin 1 Eylül 2026 tablosuna göre hazırlanmıştır (kontrol: 30 Eylül 2026).
        Oranlar otomatik güncellenmez. Bankanın ekstre oranlarını ilgili borç kaydına veya bu ayarlara girebilirsin.
        Faiz ve ödeme planları yaklaşık senaryolardır; gerçekleşen faiz bakiyeye otomatik eklenmez.
      </p>
      <div class="bt-scroll"><table class="bt-table">
        <thead><tr><th>Dönem borcu</th><th class="num">Akdi faiz</th><th class="num">Gecikme faizi</th></tr></thead>
        <tbody>
          ${s.ccTiers.map((t,i)=>`<tr>
            <td>${i===0 ? `${fmt0(t.max)} altı` : t.max===null ? `${fmt0(s.ccTiers[i-1].max)} üzeri` : `${fmt0(s.ccTiers[i-1].max)} – ${fmt0(t.max)}`}</td>
            <td class="num"><input data-tier="${i}" data-f="akdi" value="${t.akdi}" style="width:74px;text-align:right;padding:4px 7px;border:1px solid var(--line);border-radius:6px"></td>
            <td class="num"><input data-tier="${i}" data-f="gecikme" value="${t.gecikme}" style="width:74px;text-align:right;padding:4px 7px;border:1px solid var(--line);border-radius:6px"></td>
          </tr>`).join("")}
          <tr><td>Nakit avans / KMH</td>
            <td class="num"><input data-set="nakitAvans.akdi" value="${s.nakitAvans.akdi}" style="width:74px;text-align:right;padding:4px 7px;border:1px solid var(--line);border-radius:6px"></td>
            <td class="num"><input data-set="nakitAvans.gecikme" value="${s.nakitAvans.gecikme}" style="width:74px;text-align:right;padding:4px 7px;border:1px solid var(--line);border-radius:6px"></td></tr>
          <tr><td>Ticari kart (borç tutarından bağımsız)</td>
            <td class="num"><input data-set="kurumsal.akdi" value="${s.kurumsal.akdi}" style="width:74px;text-align:right;padding:4px 7px;border:1px solid var(--line);border-radius:6px"></td>
            <td class="num"><input data-set="kurumsal.gecikme" value="${s.kurumsal.gecikme}" style="width:74px;text-align:right;padding:4px 7px;border:1px solid var(--line);border-radius:6px"></td></tr>
        </tbody></table></div>
    </div>

    <div class="bt-section">
      <div class="bt-section-head"><h2>Asgari ödeme ve vergi</h2></div>
      <div class="bt-form">
        <div class="bt-grid">
          <div class="bt-field"><label for="s-esik">Limit eşiği (₺)</label>
            <input id="s-esik" data-set="asgariEsik" value="${s.asgariEsik}">
            <div class="hint">Bu limitin altı/üstü farklı oran.</div></div>
          <div class="bt-field"><label for="s-alt">Eşik altı asgari oran (%)</label>
            <input id="s-alt" data-set="asgariAlt" value="${s.asgariAlt}"></div>
          <div class="bt-field"><label for="s-ust">Eşik üstü asgari oran (%)</label>
            <input id="s-ust" data-set="asgariUst" value="${s.asgariUst}"></div>
          <div class="bt-field"><label for="s-vergi">Faiz üzerinden vergi (%)</label>
            <input id="s-vergi" data-set="vergi" value="${s.vergi}">
            <div class="hint">Başlangıç varsayımı toplam %30; kendi işlemine göre düzenle.</div></div>
        </div>
        <div class="bt-form-actions">
          <button class="bt-btn quiet" id="bt-reset-rates">Varsayılan oranlara dön</button>
        </div>
      </div>
      <p class="bt-note">
        Asgari oranı belirleyen şey kartın <b>limiti</b>, borcun büyüklüğü değil. Akdi faiz kademesini
        belirleyen ise <b>dönem borcu</b>. İki farklı kural, ikisi de otomatik uygulanıyor.
      </p>
    </div>

    <div class="bt-section">
      <div class="bt-section-head"><h2>Veri</h2></div>
      <div class="bt-form">
        <p class="bt-note" style="margin-bottom:6px">
          <b>Kayıtlar bu bilgisayarda:</b> Her başarılı işlem otomatik kaydedilir.
          Önceki başarılı kayıt ayrıca yedeklenir.
        </p>
        <p class="bt-note" style="margin-bottom:14px">
          Başka bilgisayara taşımak veya ayrı bir yedek saklamak için JSON / CSV dışa aktar.
          Bu panelden veya örnek HTML dosyasından alınmış yedekleri içe aktarabilirsin.
        </p>
        <div class="bt-form-actions" style="margin-top:0">
          <button class="bt-btn" id="bt-export-csv">CSV indir</button>
          <button class="bt-btn quiet" id="bt-export">JSON indir</button>
        </div>
        <div class="bt-form-actions">
          <button class="bt-btn ghost" id="bt-import">Dosyadan yükle — üzerine yaz</button>
          <button class="bt-btn quiet" id="bt-import-merge">Dosyadan yükle — birleştir</button>
        </div>
        <p class="bt-note" style="margin-top:4px">
          <b>Üzerine yaz:</b> defteri siler, dosyadakini koyar. <b>Birleştir:</b> dosyadaki kayıtları ekler,
          aynı kaydın eski hâlini yenisiyle değiştirir. Yeni ekstre girip tekrar indirmek istiyorsan birleştir yeterli.
        </p>
        <input type="file" id="bt-file" accept=".csv,.json,text/csv,application/json" class="bt-hidden">
        <div class="bt-form-actions" style="border-top:1px solid var(--line-2);padding-top:14px;margin-top:16px">
          <button class="bt-btn danger" id="bt-wipe">Tüm kayıtları sil</button>
        </div>
      </div>
    </div>`;
}

/* =========================================================
   9. RENDER + OLAYLAR
   ========================================================= */
function render(){
  document.querySelectorAll(".bt-view").forEach(v=>v.classList.add("bt-hidden"));
  document.getElementById("bt-"+activeTab).classList.remove("bt-hidden");
  document.querySelectorAll(".bt-tab").forEach(t=>t.setAttribute("aria-selected", String(t.dataset.tab===activeTab)));
  if(activeTab==="panel") renderPanel();
  if(activeTab==="borclar") renderBorclar();
  if(activeTab==="abonelik") renderAbonelik();
  if(activeTab==="kasa") renderKasa();
  if(activeTab==="plan") renderPlan();
  if(activeTab==="hedef") renderHedef();
  if(activeTab==="akis") renderAkis();
  if(activeTab==="ayarlar") renderAyarlar();
}

/* Ödemeyi işle. Kısmi ödemede vade DEVRETMEZ; kalan aynı gün için borç olarak durur. */
function odemeIsle(d, tutar, tarih, aciklama, accountId = ""){
  tutar = money(tutar);
  if(!d || !Number.isFinite(tutar) || tutar <= 0) { toast("Pozitif bir tutar gir"); return null; }
  if(tutar > money(toplamBakiye(d))) { toast("Tutar kalan borcu veya alacağı aşamaz"); return null; }
  const account = accountId ? state.accounts.find(a => a.id === accountId) : null;
  if(accountId && !account) { toast("Hesap bulunamadı"); return null; }
  if(account && !alacakMi(d) && tutar > money(num(account.balance) - num(account.kisitli))){
    toast("Seçili hesabın serbest bakiyesi yetersiz"); return null;
  }
  let tamamlandi = false;
  if(d.type === "kredi"){
    let kalanOdeme = tutar;
    while(kalanOdeme > 0 && hesaplaKredi(d).kalanTaksit > 0){
      const st = donemDurumu(d), k = hesaplaKredi(d);
      if(st.kalan <= 0) { toast("Taksit bilgilerini kontrol et"); return null; }
      const pay = Math.min(st.kalan, kalanOdeme);
      d.donemAnahtari = d.due || ("acik:" + d.id);
      d.donemHedef = st.hedef;
      d.donemOdenen = money(st.odenen + pay);
      kalanOdeme = money(kalanOdeme - pay);
      tamamlandi = d.donemOdenen >= money(st.hedef);
      if(tamamlandi){
        d.kalanTaksit = String(k.kalanTaksit - 1);
        if(d.due && k.kalanTaksit > 1) d.due = addMonth(d.due, 1);
        d.donemAnahtari = null; d.donemHedef = null; d.donemOdenen = 0;
      }
    }
  } else {
    const st = donemDurumu(d);
    d.donemAnahtari = d.due || ("acik:" + d.id);
    d.donemHedef = st.hedef;
    d.donemOdenen = money(st.odenen + tutar);
    d.balance = money(Math.max(0, num(d.balance) - tutar));
    if(d.avans != null) d.avans = Math.min(num(d.avans), d.balance);
    tamamlandi = d.donemOdenen >= money(d.donemHedef) || d.balance === 0;
    if(tamamlandi){
      if(d.due && tekrarliBorc(d) && d.balance > 0) d.due = addMonth(d.due, 1);
      d.donemAnahtari = null; d.donemHedef = null; d.donemOdenen = 0;
    }
  }
  if(account) account.balance = money(num(account.balance) + (alacakMi(d) ? tutar : -tutar));
  state.flows.push({id:uid(), type:alacakMi(d) ? "in" : "out", amount:tutar,
    date:tarih || isoOf(today()), category:alacakMi(d) ? "Borç tahsilatı" : "Borç ödemesi",
    note:aciklama ? `${d.name} — ${aciklama}` : d.name, debtId:d.id,
    ...(account ? {accountId:account.id} : {})});
  return {tamamlandi, kalan:donemDurumu(d).kalan, faizEklendi:0};
}

document.getElementById("bt-app").addEventListener("click", e => {
  const t = e.target.closest("button");
  if(!t) return;

  if(t.classList.contains("bt-tab")){ activeTab = t.dataset.tab; render(); return; }

  if(t.id === "bt-odeme-ac"){
    const el = document.getElementById("bt-borclar");
    el.dataset.pay = el.dataset.pay === "1" ? "0" : "1";
    renderBorclar();
    document.getElementById("od-tutar")?.focus();
    return;
  }
  if(t.id === "bt-odeme-kapat"){
    document.getElementById("bt-borclar").dataset.pay = "0";
    odemeSecim = null; renderBorclar(); return;
  }
  if(t.id === "bt-odeme-kaydet"){
    const g = id => document.getElementById(id).value.trim();
    const d = state.debts.find(x => x.id === g("od-borc"));
    const tutar = num(g("od-tutar"));
    if(!d){ toast("Borç seç"); return; }
    if(tutar <= 0){ toast("Tutar gir"); return; }
    const r = odemeIsle(d, tutar, g("od-tarih") || isoOf(today()), g("od-not"), g("od-hesap"));
    if(!r) return;
    odemeSecim = d.id;
    save(); renderBorclar();
    toast(r.tamamlandi
      ? `${fmt(tutar)} ödendi — kayıt güncellendi`
      : `${fmt(tutar)} ödendi — ${fmt(r.kalan)} kaldı, vade değişmedi`);
    return;
  }

  if(t.id === "bt-new"){
    const el = document.getElementById("bt-borclar");
    if(el.dataset.form === "1"){ el.dataset.form="0"; editingDebt=null; }
    else { el.dataset.form="1"; editingDebt=null; formType="kredikarti"; }
    renderBorclar(); return;
  }
  if(t.id === "bt-cancel"){
    const el = document.getElementById("bt-borclar");
    el.dataset.form="0"; editingDebt=null; renderBorclar(); return;
  }
  if(t.id === "bt-save"){
    const d = readForm();
    if(!num(d.balance) && !num(d.principal)){ toast("Tutar girmen gerekiyor"); return; }
    const i = state.debts.findIndex(x=>x.id===d.id);
    if(i >= 0) state.debts[i] = d; else state.debts.push(d);
    document.getElementById("bt-borclar").dataset.form="0";
    editingDebt = null;
    save(); renderBorclar();
    toast(i>=0 ? "Güncellendi" : "Borç eklendi");
    return;
  }
  if(t.dataset.toggle){
    const id = t.dataset.toggle;
    openCards.has(id) ? openCards.delete(id) : openCards.add(id);
    renderBorclar(); return;
  }
  if(t.dataset.edit){
    editingDebt = state.debts.find(x=>x.id===t.dataset.edit);
    formType = editingDebt.type;
    document.getElementById("bt-borclar").dataset.form="1";
    renderBorclar();
    document.getElementById("f-name")?.focus();
    return;
  }
  if(t.dataset.del){
    const d = state.debts.find(x=>x.id===t.dataset.del);
    if(d && confirm(`"${d.name}" kaydı silinsin mi?`)){
      state.debts = state.debts.filter(x=>x.id!==d.id);
      if(odemeSecim === d.id) odemeSecim = null;
      save(); render(); toast("Kayıt silindi");
    }
    return;
  }
  if(t.dataset.pay){
    activeTab = "borclar";
    odemeSecim = t.dataset.pay;
    document.getElementById("bt-borclar").dataset.pay = "1";
    render(); document.getElementById("od-tutar")?.focus(); return;
  }

  /* ---- abonelikler ---- */
  if(t.id === "bt-sub-save"){
    const g = id => document.getElementById(id).value.trim();
    const amount = num(g("sb-amount"));
    if(!g("sb-name") || !Number.isFinite(amount) || amount <= 0){ toast("Ad ve tutar gerekiyor"); return; }
    const rec = {
      id: editSub ? editSub.id : uid(),
      name: g("sb-name") || "Abonelik",
      amount,
      day: Math.min(31, Math.max(1, Math.round(num(g("sb-day"))) || 1)),
      link: g("sb-link"), category: g("sb-cat"),
      active: g("sb-active") === "1", note: g("sb-note")
    };
    const i = state.subs.findIndex(x=>x.id===rec.id);
    if(i >= 0) state.subs[i] = rec; else state.subs.push(rec);
    editSub = null; save(); renderAbonelik();
    toast(i>=0 ? "Abonelik güncellendi" : "Abonelik eklendi");
    return;
  }
  if(t.id === "bt-sub-cancel"){ editSub = null; renderAbonelik(); return; }
  if(t.dataset.subedit){
    editSub = state.subs.find(x=>x.id===t.dataset.subedit);
    renderAbonelik(); document.getElementById("sb-name")?.focus(); return;
  }
  if(t.dataset.subdel){
    const x = state.subs.find(y=>y.id===t.dataset.subdel);
    if(x && confirm(`"${x.name}" aboneliği silinsin mi?`)){
      state.subs = state.subs.filter(y=>y.id!==x.id);
      if(editSub && editSub.id===x.id) editSub = null;
      save(); renderAbonelik(); toast("Silindi");
    }
    return;
  }

  /* ---- hesap bakiyeleri ---- */
  if(t.id === "bt-acc-save"){
    const g = id => document.getElementById(id).value.trim();
    if(!g("ac-name")){ toast("Hesaba bir ad ver"); return; }
    const rec = {
      id: editAcc ? editAcc.id : uid(),
      name: g("ac-name"), kind: g("ac-kind"), bank: g("ac-bank"),
      balance: num(g("ac-balance")), kisitli: Math.min(num(g("ac-kisitli")), num(g("ac-balance"))),
      note: g("ac-note")
    };
    const i = state.accounts.findIndex(x=>x.id===rec.id);
    if(i >= 0) state.accounts[i] = rec; else state.accounts.push(rec);
    editAcc = null; save(); renderKasa();
    toast(i>=0 ? "Bakiye güncellendi" : "Hesap eklendi");
    return;
  }
  if(t.id === "bt-acc-cancel"){ editAcc = null; renderKasa(); return; }
  if(t.dataset.accedit){
    editAcc = state.accounts.find(x=>x.id===t.dataset.accedit);
    renderKasa(); document.getElementById("ac-balance")?.focus(); return;
  }
  if(t.dataset.accdel){
    const x = state.accounts.find(y=>y.id===t.dataset.accdel);
    if(x && confirm(`"${x.name}" hesabı silinsin mi?`)){
      state.accounts = state.accounts.filter(y=>y.id!==x.id);
      if(editAcc && editAcc.id===x.id) editAcc = null;
      save(); renderKasa(); toast("Silindi");
    }
    return;
  }

  /* ---- gelecek gelirler ---- */
  if(t.id === "bt-inc-save"){
    const g = id => document.getElementById(id).value.trim();
    const amount = num(g("in-amount"));
    if(amount <= 0 || !g("in-date")){ toast("Tutar ve tarih gerekiyor"); return; }
    const rec = {
      id: editInc ? editInc.id : uid(),
      name: g("in-name") || "Gelir", amount, date: g("in-date"),
      repeat: g("in-rep"), kind: g("in-kind"), note: g("in-note")
    };
    const i = state.incomes.findIndex(x=>x.id===rec.id);
    if(i >= 0) state.incomes[i] = rec; else state.incomes.push(rec);
    editInc = null; save(); renderPlan();
    toast(i>=0 ? "Gelir güncellendi" : "Gelir eklendi");
    return;
  }
  if(t.id === "bt-inc-cancel"){ editInc = null; renderPlan(); return; }
  if(t.dataset.incedit){
    editInc = state.incomes.find(x=>x.id===t.dataset.incedit);
    renderPlan(); document.getElementById("in-name")?.focus(); return;
  }
  if(t.dataset.incdel){
    const x = state.incomes.find(y=>y.id===t.dataset.incdel);
    if(x && confirm(`"${x.name}" geliri silinsin mi?`)){
      state.incomes = state.incomes.filter(y=>y.id!==x.id);
      if(editInc && editInc.id===x.id) editInc = null;
      save(); renderPlan(); toast("Silindi");
    }
    return;
  }

  /* ---- hedefler ---- */
  if(t.id === "bt-butce-kaydet"){
    state.settings.gunlukButce = num(document.getElementById("hd-butce").value);
    save(); renderHedef(); toast("Günlük bütçe kaydedildi");
    return;
  }
  if(t.id === "bt-goal-save"){
    const g = id => document.getElementById(id).value.trim();
    const amount = num(g("hd-amount"));
    if(!g("hd-name") || amount <= 0){ toast("Ad ve hedef tutar gerekiyor"); return; }
    const rec = {
      id: editGoal ? editGoal.id : uid(),
      name: g("hd-name"), amount, saved: num(g("hd-saved")),
      due: g("hd-due"), priority: num(g("hd-pri")) || 2, note: g("hd-note")
    };
    const i = state.goals.findIndex(x=>x.id===rec.id);
    if(i >= 0) state.goals[i] = rec; else state.goals.push(rec);
    editGoal = null; save(); renderHedef();
    toast(i>=0 ? "Hedef güncellendi" : "Hedef eklendi");
    return;
  }
  if(t.id === "bt-goal-cancel"){ editGoal = null; renderHedef(); return; }
  if(t.dataset.goaledit){
    editGoal = state.goals.find(x=>x.id===t.dataset.goaledit);
    renderHedef(); document.getElementById("hd-name")?.focus(); return;
  }
  if(t.dataset.goaladd){
    const h = state.goals.find(x=>x.id===t.dataset.goaladd);
    if(!h) return;
    const girilen = prompt(`"${h.name}" hedefine eklenecek tutar (₺):`, "");
    if(girilen === null) return;
    const tutar = num(girilen);
    if(tutar === 0) return;
    h.saved = Math.max(0, num(h.saved) + tutar);
    save(); renderHedef();
    toast(`${fmt(tutar)} eklendi — hesap bakiyeni ayrıca güncelle`);
    return;
  }
  if(t.dataset.goaldel){
    const h = state.goals.find(x=>x.id===t.dataset.goaldel);
    if(h && confirm(`"${h.name}" hedefi silinsin mi?`)){
      state.goals = state.goals.filter(x=>x.id!==h.id);
      if(editGoal && editGoal.id===h.id) editGoal = null;
      save(); renderHedef(); toast("Silindi");
    }
    return;
  }

  if(t.id === "bt-flow-add"){
    const g = id => document.getElementById(id).value;
    const amount = num(g("fl-amount"));
    if(amount <= 0){ toast("Tutar gir"); return; }
    if(editFlow){
      Object.assign(editFlow, { type: g("fl-type"), amount, date: g("fl-date"),
                                category: g("fl-cat"), note: g("fl-note").trim() });
    }else{
      state.flows.push({ id: uid(), type: g("fl-type"), amount, date: g("fl-date"),
                         category: g("fl-cat"), note: g("fl-note").trim() });
    }
    const guncellendi = !!editFlow;
    editFlow = null;
    document.getElementById("bt-akis").dataset.month = monthKey(g("fl-date"));
    save(); renderAkis();
    toast(guncellendi ? "Hareket güncellendi" : "Kaydedildi");
    return;
  }
  if(t.dataset.editflow){
    if(state.flows.find(f=>f.id===t.dataset.editflow)?.debtId){ toast("Bu hareket bir borç işlemine bağlı. Düzeltmeyi borç ve hesap kaydından yap."); return; }
    editFlow = state.flows.find(f=>f.id===t.dataset.editflow) || null;
    renderAkis();
    document.getElementById("fl-amount")?.focus();
    return;
  }
  if(t.id === "bt-flow-cancel"){ editFlow = null; renderAkis(); return; }
  if(t.dataset.delflow){
    if(state.flows.find(f=>f.id===t.dataset.delflow)?.debtId){ toast("Borç işlemine bağlı hareketler ayrı silinemez."); return; }
    if(editFlow && editFlow.id === t.dataset.delflow) editFlow = null;
    state.flows = state.flows.filter(f=>f.id!==t.dataset.delflow);
    save(); renderAkis(); toast("Silindi");
    return;
  }

  if(t.id === "bt-reset-rates"){
    const s = state.settings;
    Object.assign(s, {
      ccTiers: clone(DEFAULTS.ccTiers),
      nakitAvans: clone(DEFAULTS.nakitAvans),
      kurumsal: clone(DEFAULTS.kurumsal),
      asgariEsik: DEFAULTS.asgariEsik, asgariAlt: DEFAULTS.asgariAlt,
      asgariUst: DEFAULTS.asgariUst, vergi: DEFAULTS.vergi
    });
    save(); renderAyarlar(); toast("Oranlar sıfırlandı");
    return;
  }
  if(t.id === "bt-export-csv"){
    download("borc-defteri-" + isoOf(today()) + ".csv", buildCSV(), "text/csv;charset=utf-8");
    return;
  }
  if(t.id === "bt-export"){
    download("borc-defteri-" + isoOf(today()) + ".json", JSON.stringify(state,null,2), "application/json");
    return;
  }
  if(t.id === "bt-import"){ pickFile("replace"); return; }
  if(t.id === "bt-import-merge"){ pickFile("merge"); return; }
  if(t.id === "bt-wipe"){
    if(confirm("Bütün borç, abonelik, hesap, gelir, hedef ve nakit akışı kayıtların silinecek. Emin misin?")){
      KOLEKSIYONLAR.forEach(k => { state[k] = []; });
      save(); render(); toast("Defter temizlendi");
    }
    return;
  }
});

document.getElementById("bt-app").addEventListener("input", e => {
  const el = e.target;

  if(el.id === "f-type"){ formType = el.value; renderBorclar(); document.getElementById("f-name")?.focus(); return; }
  if(el.id === "od-borc"){ odemeSecim = el.value; renderBorclar(); return; }
  if(el.closest(".bt-form") && document.getElementById("bt-live")){ renderLive(); }


});

document.getElementById("bt-app").addEventListener("change", e => {
  const el = e.target;
  if(el.dataset.tier != null){
    state.settings.ccTiers[+el.dataset.tier][el.dataset.f] = num(el.value);
    save(); return;
  }
  if(el.dataset.set){
    const path = el.dataset.set.split(".");
    if(path.length === 2) state.settings[path[0]][path[1]] = num(el.value);
    else state.settings[path[0]] = num(el.value);
    save(); return;
  }
  if(e.target.id === "bt-month"){
    document.getElementById("bt-akis").dataset.month = e.target.value;
    renderAkis(); return;
  }
  if(e.target.id === "fl-type"){
    const sel = document.getElementById("fl-cat");
    const onceki = sel.value;
    sel.innerHTML = katOpsiyon(e.target.value, onceki);
    return;
  }
});

/* =========================================================
   10. BAŞLAT
   ========================================================= */
window.financeApp = {
  initialize(json){
    const saved = JSON.parse(json);
    if(saved) hydrate(saved, "replace");
    state = hostResult(window.financeHost.validate(JSON.stringify(state))).data;
    committed = clone(state);
    render(); setSync("bu bilgisayara kayıt açık");
  },
  exportState(){ return JSON.stringify(state); },
  exportCsv(){ return buildCSV(); },
  importText,
  summary(){
    return JSON.stringify({debt:money(state.debts.filter(d=>!alacakMi(d)).reduce((sum,d)=>sum+toplamBakiye(d),0)),
      receivable:money(state.debts.filter(alacakMi).reduce((sum,d)=>sum+toplamBakiye(d),0)),
      period:money(state.debts.reduce((sum,d)=>sum+donemTutari(d),0)), cash:money(eldekiPara()), today:isoOf(today())});
  }
};
window.addEventListener("error", event => { toast(event.message || "İşlem tamamlanamadı"); });
})();
