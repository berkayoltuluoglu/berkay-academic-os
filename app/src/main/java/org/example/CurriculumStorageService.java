package org.example;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.*;
import java.io.*;import java.nio.channels.FileChannel;import java.nio.file.*;import java.util.*;
import static org.example.CurriculumData.*;

public final class CurriculumStorageService {
 private final Path file;private final ObjectMapper mapper=new ObjectMapper().enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION).enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);private byte[] baseline;private boolean loaded;
 public CurriculumStorageService(Path file){this.file=file;}
 public synchronized CurriculumData load()throws IOException {loaded=false;byte[] bytes=Files.exists(file)?FinanceStorageService.readLimited(file):null;try{var data=bytes==null?CurriculumData.empty():mapper.readValue(bytes,CurriculumData.class);validate(data);baseline=bytes;loaded=true;return data;}catch(RuntimeException e){throw new IOException("Müfredat kayıtları geçersiz; mevcut dosya korunuyor.",e);}}
 public synchronized void save(CurriculumData data)throws IOException {if(!loaded)throw new IOException("Önce müfredatı yükle.");validate(data);byte[] bytes=mapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(data);if(bytes.length>FinanceStorageService.MAX_BYTES)throw new IOException("Müfredat kaydı en fazla 5 MB olabilir.");
  AtomicFileWriter.exclusive(()->{Files.createDirectories(file.getParent());try(var channel=FileChannel.open(file.resolveSibling(file.getFileName()+".lock"),StandardOpenOption.CREATE,StandardOpenOption.WRITE);var lock=channel.tryLock()){
   if(lock==null)throw new IOException("Müfredat başka yerde kaydediliyor.");byte[] current=Files.exists(file)?FinanceStorageService.readLimited(file):null;if(!Arrays.equals(current,baseline))throw new IOException("Müfredat değişti. Önce yenile.");if(current!=null)AtomicFileWriter.write(file.resolveSibling(file.getFileName()+".bak"),current);AtomicFileWriter.write(file,bytes);baseline=bytes;return null;
  }catch(java.nio.channels.OverlappingFileLockException e){throw new IOException("Müfredat başka yerde kaydediliyor.",e);}});
 }
 public static void validate(CurriculumData data)throws IOException {
  try{if(data==null||data.schemaVersion()!=1||data.degrees().size()>2||data.preferences()==null)throw new IOException("Müfredat kaydı geçersiz.");Set<String> ids=new HashSet<>();
   for(var d:data.degrees()){
    if(!List.of("A","B").contains(d.id())||!ids.add(d.id())||d.name()==null||d.name().isBlank()||d.name().length()>200||d.source()==null||d.source().length()>500)throw new IOException("Bölüm bilgileri geçersiz.");CurriculumParser.validate(d.curriculum());
    if(d.curriculum().semesters().stream().noneMatch(s->s.number()==d.semester()))throw new IOException("Planlanacak yarıyılı seç.");Set<String> keys=new HashSet<>(),completed=new HashSet<>();
    for(var s:d.curriculum().semesters())for(var r:s.rows()){
     String key=key(s,r);keys.add(key);var decision=d.decision(s,r);var members=d.curriculum().members(r);Set<String> allowed=new HashSet<>();members.forEach(m->allowed.add(m.code()));
     if(!List.of("prefer","required","done","skip").contains(decision.status())||(!decision.choice().equals("auto")&&!allowed.contains(decision.choice()))||decision.grade().length()>80||decision.note().length()>3000||decision.preferred().size()>200||decision.excluded().size()>200||!allowed.containsAll(decision.preferred())||!allowed.containsAll(decision.excluded()))throw new IOException("Ders geçmişi / seçmeli tercihi geçersiz.");
     if(decision.status().equals("done")){String actual=decision.choice().equals("auto")?(d.curriculum().groups().containsKey(r.code())?"":r.code()):decision.choice();if(actual.isEmpty()||!completed.add(actual))throw new IOException("Tamamlanan seçmelinin gerçek kodunu seç; bir ders iki gereksinimde sayılamaz.");}
    }
    if(!keys.containsAll(d.decisions().keySet()))throw new IOException("Geçmişte müfredat dışında satır var.");
   }
   if(ids.contains("B")&&!ids.contains("A"))throw new IOException("Önce anadal müfredatını ekle.");
   Set<String> a=new HashSet<>(),b=new HashSet<>();for(var pair:data.equivalences()){
    Degree da=data.degrees().stream().filter(d->d.id().equals("A")).findFirst().orElseThrow();Degree db=data.degrees().stream().filter(d->d.id().equals("B")).findFirst().orElseThrow();
    if(!da.curriculum().catalog().containsKey(pair.a())||!db.curriculum().catalog().containsKey(pair.b())||!a.add(pair.a())||!b.add(pair.b()))throw new IOException("Eşdeğerlikte her bölümden bir ders seç; dersler yalnızca bir eşleştirmede kullanılabilir.");
   }
   AdvancedCoursePlanner.validate(data.preferences());
  }catch(RuntimeException e){throw new IOException("Müfredat / tercih kaydı eksik veya geçersiz.",e);}
 }
}
