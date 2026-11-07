package org.example;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import java.io.*;
import java.nio.file.*;
import java.math.BigDecimal;
import java.util.*;
import java.util.regex.*;
import static org.example.CurriculumData.*;

/** Deliberately validates table totals instead of accepting partially extracted curricula. */
public final class CurriculumParser {
 private static final Pattern SEM=Pattern.compile("^(\\d+)\\s*\\.\\s*YARIYIL$",Pattern.CASE_INSENSITIVE|Pattern.UNICODE_CASE);
 private static final Pattern ROW=Pattern.compile("^(\\d+)\\s+([A-Za-z][A-Za-z0-9,.-]*)\\s+(.*?)\\s*(\\d+)\\s+(\\d+)\\s+(\\d+(?:[,.]\\d+)?)\\s+(\\d+(?:[,.]\\d+)?)$");
 private static final Pattern TOTAL=Pattern.compile("^Toplam\\s+\\d+\\s+\\d+\\s+([\\d,.]+)\\s+([\\d,.]+)$",Pattern.CASE_INSENSITIVE);
 private static final class Table {int number;String code;List<Row> rows=new ArrayList<>();BigDecimal credit,ects;}
 public Model read(Path file)throws IOException {
  if(Files.size(file)>50L*1024*1024)throw new IOException("Müfredat dosyası en fazla 50 MB olabilir.");
  String text;
  if(file.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".pdf")){
   try(var pdf=Loader.loadPDF(file.toFile())){if(pdf.isEncrypted()||pdf.getNumberOfPages()>100)throw new IOException("Şifresiz, en fazla 100 sayfalık bir PDF seç.");
    var stripper=new PDFTextStripper();stripper.setSortByPosition(true);StringWriter writer=new StringWriter(){@Override public void write(String s,int offset,int length){if(getBuffer().length()+length>2_000_000)throw new IllegalArgumentException("PDF metni çok büyük.");super.write(s,offset,length);}};
    try{stripper.writeText(pdf,writer);text=writer.toString();}catch(IllegalArgumentException e){throw new IOException(e.getMessage(),e);}}
  }else{if(Files.size(file)>2_000_000)throw new IOException("Müfredat metni çok büyük.");text=Files.readString(file);}
  return parse(text);
 }
 public Model parse(String text)throws IOException {
  if(text==null||text.length()>2_000_000)throw new IOException("Müfredat metni eksik veya çok büyük.");
  List<Table> semesters=new ArrayList<>();Map<String,Table> pools=new LinkedHashMap<>();Table current=null;String pending="";List<String> title=new ArrayList<>();Integer expected=null;
  for(String raw:text.split("\\R")){
   String line=raw.replaceAll("\\s+"," ").trim();if(line.isEmpty())continue;
   var general=Pattern.compile("Genel Toplam Ders Adedi\\s*:\\s*(\\d+)",Pattern.CASE_INSENSITIVE).matcher(line);if(general.find()){expected=Integer.parseInt(general.group(1));continue;}
   var sem=SEM.matcher(line);if(sem.matches()){current=new Table();current.number=Integer.parseInt(sem.group(1));semesters.add(current);pending="";continue;}
   if(line.matches("(?i)^No\\s+Ders Kodu.*"))continue;
   var total=TOTAL.matcher(line);if(total.matches()&&current!=null){current.credit=number(total.group(1));current.ects=number(total.group(2));pending="";continue;}
   if(line.matches("[A-Za-z][A-Za-z0-9,.-]*")&&!semesters.isEmpty()){
    current=new Table();current.code=code(line);if(pools.putIfAbsent(current.code,current)!=null)throw new IOException("Tekrarlı seçmeli havuzu: "+line);pending="";continue;}
   var row=ROW.matcher(line);if(row.matches()&&current!=null){String name=row.group(3).replaceAll("(?i)\\(Bu ders.*?\\)","").trim();if(name.isEmpty())name=pending;
    int order=Integer.parseInt(row.group(1));if(order!=current.rows.size()+1||name.isEmpty())throw new IOException("Eksik / okunamayan müfredat satırı: "+line);
    current.rows.add(new Row(order,code(row.group(2)),name,number(row.group(6)),number(row.group(7)),row.group(3).toLowerCase(Locale.ROOT).contains("ders grubundan")));pending="";continue;}
   if(line.toLowerCase(Locale.ROOT).contains("ders grubundan alınacaktır")){if(current!=null&&!current.rows.isEmpty()){Row r=current.rows.removeLast();current.rows.add(new Row(r.order(),r.code(),r.name(),r.credit(),r.ects(),true));}continue;}
   if(current!=null&&line.matches("^\\d+\\s+\\S+.*"))throw new IOException("Müfredat satırı okunamadı: "+line);
   if(current==null)title.add(line);else pending=line;
  }
  Map<String,List<Row>> groups=new LinkedHashMap<>();pools.forEach((k,v)->groups.put(k,v.rows));
  Model model=new Model(String.join(" · ",title),semesters.stream().map(s->new Semester(s.number,s.rows,s.credit,s.ects)).toList(),groups);validate(model);
  if(expected!=null&&expected!=model.semesters().stream().mapToInt(s->s.rows().size()).sum())throw new IOException("Genel ders sayısı okunan tabloyla uyuşmuyor.");return model;
 }
 static void validate(Model m)throws IOException {
  if(m==null||m.title()==null||m.title().length()>2000||m.semesters().isEmpty()||m.semesters().size()>20||m.groups().size()>200)throw new IOException("Müfredat tabloları bulunamadı. Metin içeren yarıyıl / Kredi / ECTS tablosu gerekli; taranmış PDF için OCR desteklenmiyor.");
  Set<Integer> numbers=new HashSet<>();for(var s:m.semesters()){
   if(s.number()<1||s.number()>20||!numbers.add(s.number()))throw new IOException("Yarıyıl numarası geçersiz veya tekrarlı.");rows(s.rows());
   for(var r:s.rows())if(r.group()&&!m.groups().containsKey(r.code()))throw new IOException("Seçmeli havuzu eksik: "+r.code());
   if(s.credit()==null||s.ects()==null||s.rows().stream().map(Row::credit).reduce(BigDecimal.ZERO,BigDecimal::add).subtract(s.credit()).abs().compareTo(new BigDecimal("0.01"))>=0
     ||s.rows().stream().map(Row::ects).reduce(BigDecimal.ZERO,BigDecimal::add).subtract(s.ects()).abs().compareTo(new BigDecimal("0.01"))>=0)throw new IOException("Yarıyıl kredi / AKTS toplamları tutarsız veya eksik.");
  }
  for(var e:m.groups().entrySet()){if(!e.getKey().matches("[A-Z][A-Z0-9,.-]{0,79}"))throw new IOException("Seçmeli havuzu kodu geçersiz.");rows(e.getValue());Set<String> codes=new HashSet<>();for(var r:e.getValue())if(!codes.add(r.code()))throw new IOException("Seçmeli havuzunda tekrarlı ders.");}
 }
 private static void rows(List<Row> rows)throws IOException {if(rows.isEmpty()||rows.size()>200)throw new IOException("Müfredat tablosu boş veya çok büyük.");for(int i=0;i<rows.size();i++){var r=rows.get(i);if(r.order()!=i+1||r.code()==null||!r.code().matches("[A-Z][A-Z0-9,.-]{0,79}")||r.name()==null||r.name().isBlank()||r.name().length()>500||r.credit()==null||r.ects()==null||r.credit().signum()<0||r.ects().signum()<0||r.credit().compareTo(new BigDecimal("100"))>0||r.ects().compareTo(new BigDecimal("100"))>0)throw new IOException("Müfredat satırı geçersiz.");}}
 private static BigDecimal number(String text){return new BigDecimal(text.replace(',','.'));}
}
