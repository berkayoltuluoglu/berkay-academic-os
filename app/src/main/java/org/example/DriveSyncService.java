package org.example;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.*;
import java.io.*;
import java.nio.file.*;
import java.security.*;
import java.time.Instant;
import java.util.*;

/** Three-way file synchronization compares both sides with the last common content hash. */
public class DriveSyncService {
    record Tracked(String path,String id,String hash){}
    public record Conflict(String path,String id,String localHash,String remoteHash,String copy,String reason){}
    record State(int schemaVersion,String account,String folder,String localDirectory,boolean automatic,String syncedAt,List<Tracked> files,List<Conflict> conflicts){}
    public record Result(int uploads,int downloads,int archived,int conflicts,List<String> warnings){}
    private record Connection(String account,String folder,DriveRemote client){}
    private record Local(Path file,String hash){}
    private final Path root,stateFile;
    private final GoogleCalendarOAuthService auth;
    private final ObjectMapper mapper=new ObjectMapper();
    private volatile Connection connection;
    private volatile String error;
    public DriveSyncService(Path root){this(root,new GoogleDriveOAuthService());}
    DriveSyncService(Path root,GoogleCalendarOAuthService auth){this.root=root.toAbsolutePath().normalize();this.auth=auth;stateFile=root.resolve("data/sync/drive.json");}
    public boolean isConnected(){return connection!=null;}
    public String getAccountEmail(){return connection==null?null:connection.account();}
    public String getError(){return error;}
    public String folderLink(){return connection==null?"":"https://drive.google.com/drive/folders/"+connection.folder();}
    public void cancelAuthorization(){auth.cancelAuthorization();}
    public synchronized void connect()throws Exception{connect(false);}
    public synchronized boolean connectExisting()throws Exception{return connection!=null||connect(true);}
    private boolean connect(boolean existing)throws Exception{
        connection=null;error=null;
        if(existing&&!Files.isRegularFile(root.resolve("config/google-calendar-tokens/drive-files-v1/StoredCredential")))return false;
        var credentialFile=GoogleCredentials.resolve(root,"google-calendar-credentials.json");
        Credential credential=existing?auth.authorizeExisting(credentialFile,root.resolve("config/google-calendar-tokens")):auth.authorize(credentialFile,root.resolve("config/google-calendar-tokens"));
        if(credential==null)return false;
        HttpRequestFactory requests=createRequests(credential);String account=account(requests);DriveRemote api=createRemote(requests);State old=state();
        String folder=api.root(old.account().equals(account)?old.folder():"");
        if(Thread.currentThread().isInterrupted())throw new IOException("Drive bağlantısı iptal edildi.");
        boolean same=old.account().equals(account)&&old.folder().equals(folder);
        save(new State(1,account,folder,old.localDirectory(),same&&old.automatic(),same?old.syncedAt():"",same?old.files():List.of(),same?old.conflicts():List.of()));
        connection=new Connection(account,folder,api);return true;
    }
    HttpRequestFactory createRequests(Credential credential)throws Exception{return GoogleNetHttpTransport.newTrustedTransport().createRequestFactory(request->{credential.initialize(request);request.setConnectTimeout(15000);request.setReadTimeout(60000);});}
    DriveRemote createRemote(HttpRequestFactory requests){return new GoogleDriveApi(requests);}
    String account(HttpRequestFactory requests)throws IOException{
        var response=requests.buildGetRequest(new GenericUrl("https://www.googleapis.com/oauth2/v2/userinfo")).execute();
        try{String email=mapper.readTree(response.getContent().readNBytes(8192)).path("email").asText();if(email.isBlank())throw new IOException("Drive hesabının e-postası doğrulanamadı.");return email;}finally{response.disconnect();}
    }
    State state()throws IOException{
        if(!Files.exists(stateFile))return new State(1,"","","",false,"",List.of(),List.of());
        State value=mapper.readValue(FinanceStorageService.readLimited(stateFile),State.class);validate(value);return value;
    }
    static void validate(State state)throws IOException{
        if(state==null||state.schemaVersion()!=1||state.account()==null||state.folder()==null||state.localDirectory()==null||state.files()==null||state.conflicts()==null
                ||state.files().size()>10000||state.conflicts().size()>10000)throw new IOException("Drive senkronizasyon kaydı geçersiz.");
        Set<String> names=new HashSet<>();for(var f:state.files()){portable(f.path());if(!names.add(f.path().toLowerCase(Locale.ROOT))||f.id()==null||!f.id().matches("[a-zA-Z0-9_-]{1,200}")||f.hash()==null||!f.hash().matches("[a-f0-9]{0,32}"))throw new IOException("Drive dosya eşleştirmesi geçersiz.");}
        names.clear();for(var c:state.conflicts()){portable(c.path());if(!names.add(c.path())||c.id()==null||c.localHash()==null||c.remoteHash()==null||c.copy()==null||c.reason()==null)throw new IOException("Drive çakışması geçersiz.");
            if(!c.copy().isBlank()){portable(c.copy());if(!c.copy().startsWith("data/sync/conflicts/"))throw new IOException("Drive çakışma kopyasının yolu geçersiz.");}}
        if(!state.folder().matches("[a-zA-Z0-9_-]{0,200}"))throw new IOException("Drive klasör kimliği geçersiz.");
    }
    private void save(State state)throws IOException{validate(state);byte[] bytes=mapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(state);
        if(bytes.length>FinanceStorageService.MAX_BYTES)throw new IOException("Drive kayıtları çok büyük.");AtomicFileWriter.write(stateFile,bytes);}
    public Path localDirectory()throws IOException{String path=state().localDirectory();return path.isBlank()?root.resolve("data/documents"):Path.of(path).toAbsolutePath().normalize();}
    public boolean automatic()throws IOException{return state().automatic();}
    public synchronized void automatic(boolean enabled)throws IOException{var s=state();save(new State(1,s.account(),s.folder(),s.localDirectory(),enabled,s.syncedAt(),s.files(),s.conflicts()));}
    public synchronized void chooseDirectory(Path selected)throws IOException{
        selected=selected.toAbsolutePath().normalize();if(!Files.isDirectory(selected)||Files.isSymbolicLink(selected))throw new IOException("Var olan bir belge klasörü seç.");
        if(root.startsWith(selected)||selected.startsWith(root.resolve("config"))||selected.startsWith(root.resolve("data"))&&!selected.startsWith(root.resolve("data/documents")))throw new IOException("Uygulama kayıtları yerine belge klasörünü seç.");
        var s=state();save(new State(1,s.account(),s.folder(),selected.toString(),false,"",List.of(),List.of()));
    }
    public synchronized void disconnect()throws IOException{connection=null;automatic(false);auth.forget(root.resolve("config/google-calendar-tokens"));}
    public List<Conflict> conflicts()throws IOException{return state().conflicts();}
    public synchronized Result synchronize()throws Exception{
        error=null;if(connection==null&&!connectExisting())throw new IOException("Önce Drive hesabına bağlan.");
        State initial=state();Path directory=localDirectory();
        if(initial.localDirectory().isBlank())Files.createDirectories(directory);else if(!Files.isDirectory(directory))throw new IOException("Seçilen yerel belge klasörü bulunamadı.");
        safe(directory,"");Map<String,Local> local=scanLocal(directory);Map<String,DriveRemote.File> remote=new TreeMap<>();Map<String,String> folders=new HashMap<>();folders.put("",connection.folder());
        scanRemote(connection.folder(),"",remote,folders,new HashSet<>(),0);
        Map<String,Tracked> tracked=new LinkedHashMap<>();initial.files().forEach(f->tracked.put(f.path(),f));
        Map<String,Conflict> conflicts=new LinkedHashMap<>();initial.conflicts().forEach(c->conflicts.put(c.path(),c));
        Set<String> paths=new TreeSet<>(tracked.keySet());paths.addAll(local.keySet());paths.addAll(remote.keySet());
        int uploads=0,downloads=0,archived=0;List<String>warnings=new ArrayList<>();
        for(String path:paths){
            if(Thread.currentThread().isInterrupted())throw new IOException("Drive senkronizasyonu durduruldu.");
            var l=local.get(path);var r=remote.get(path);var base=tracked.get(path);
            if(r!=null&&(r.hash().isBlank()||r.mime().startsWith("application/vnd.google-apps."))){warnings.add(path+": Google Dokümanı / ikili dosya olmayan içerik otomatik aktarılmadı.");continue;}
            if(l==null&&r==null){tracked.remove(path);conflicts.remove(path);continue;}
            if(l!=null&&r!=null&&l.hash().equals(r.hash())){tracked.put(path,new Tracked(path,r.id(),r.hash()));conflicts.remove(path);continue;}
            boolean localChanged=base==null||l==null||!l.hash().equals(base.hash());
            boolean remoteChanged=base==null||r==null||!r.hash().equals(base.hash());
            try{
                if(l!=null&&r==null&&base==null){
                    String parent=parent(path);ensureFolder(parent,folders);String id=connection.client().newId();
                    tracked.put(path,new Tracked(path,id,""));checkpoint(initial,tracked,conflicts);
                    var created=upload(l,null,id,leaf(path),folders.get(parent));
                    if(!created.hash().equals(l.hash())){conflicts.put(path,conflict(directory,path,l,created,conflicts.get(path),"Drive oluşturma sonucu farklı içerik taşıyor."));}
                    else{tracked.put(path,new Tracked(path,created.id(),created.hash()));conflicts.remove(path);uploads++;}
                }else if(l==null&&r!=null&&base==null){download(directory,path,r,null);tracked.put(path,new Tracked(path,r.id(),r.hash()));conflicts.remove(path);downloads++;}
                else if(l!=null&&r!=null&&localChanged&&!remoteChanged){var saved=upload(l,r,r.id(),r.name(),r.parent());tracked.put(path,new Tracked(path,saved.id(),l.hash()));conflicts.remove(path);uploads++;}
                else if(l!=null&&r!=null&&!localChanged&&remoteChanged){download(directory,path,r,l.hash());tracked.put(path,new Tracked(path,r.id(),r.hash()));conflicts.remove(path);downloads++;}
                else if(l==null&&r!=null&&base!=null&&!remoteChanged){connection.client().trash(r);tracked.remove(path);conflicts.remove(path);archived++;}
                else if(l!=null&&r==null&&base!=null&&!localChanged){archive(directory,path,l.hash());tracked.remove(path);conflicts.remove(path);archived++;}
                else if(l!=null&&r==null&&base!=null&&base.hash().isBlank()){
                    ensureFolder(parent(path),folders);var created=upload(l,null,base.id(),leaf(path),folders.get(parent(path)));
                    if(created.hash().equals(l.hash())){tracked.put(path,new Tracked(path,created.id(),created.hash()));conflicts.remove(path);uploads++;}
                    else conflicts.put(path,conflict(directory,path,l,created,conflicts.get(path),"Belirsiz oluşturma sonucunda farklı içerik bulundu."));
                }else conflicts.put(path,conflict(directory,path,l,r,conflicts.get(path),l==null?"Yerelde silindi, Drive'da değişti.":r==null?"Drive'da silindi, yerelde değişti.":"İki tarafta da değişti."));
                checkpoint(initial,tracked,conflicts);
            }catch(IOException failure){warnings.add(path+": "+failure.getMessage());checkpoint(initial,tracked,conflicts);}
        }
        for(var folder:folders.keySet())if(!folder.isBlank())Files.createDirectories(safe(directory,folder));
        save(new State(1,connection.account(),connection.folder(),initial.localDirectory(),initial.automatic(),Instant.now().toString(),List.copyOf(tracked.values()),List.copyOf(conflicts.values())));
        if(!warnings.isEmpty())error=String.join(" · ",warnings);return new Result(uploads,downloads,archived,conflicts.size(),List.copyOf(warnings));
    }
    private void checkpoint(State settings,Map<String,Tracked> files,Map<String,Conflict> conflicts)throws IOException{save(new State(1,connection.account(),connection.folder(),settings.localDirectory(),settings.automatic(),settings.syncedAt(),List.copyOf(files.values()),List.copyOf(conflicts.values())));}
    private Map<String,Local> scanLocal(Path directory)throws IOException{
        Map<String,Local> files=new TreeMap<>();Set<String>folded=new HashSet<>();
        try(var paths=Files.walk(directory)){for(var path:paths.toList()){
            if(Files.isSymbolicLink(path))throw new IOException("Senkronizasyon klasöründe sembolik bağlantı var: "+path.getFileName());
            if(!Files.isRegularFile(path))continue;String relative=directory.relativize(path).toString().replace('\\','/');if(relative.contains(".academic-os-"))continue;
            portable(relative);if(!folded.add(relative.toLowerCase(Locale.ROOT)))throw new IOException("Windows'ta çakışacak dosya adları var: "+relative);
            files.put(relative,new Local(path,hash(path)));if(files.size()>10000)throw new IOException("En fazla 10.000 dosya senkronize edilebilir.");
        }}return files;
    }
    private void scanRemote(String folder,String prefix,Map<String,DriveRemote.File> files,Map<String,String>folders,Set<String>visited,int depth)throws IOException{
        if(depth>50||!visited.add(folder))throw new IOException("Drive klasörleri döngü oluşturuyor veya çok derin.");
        Set<String> names=new HashSet<>();for(var f:connection.client().children(folder)){
            if(!f.parent().equals(folder))throw new IOException("Drive dosyası beklenen klasörün dışında.");
            portable(f.name());if(f.name().contains("/")||!names.add(f.name().toLowerCase(Locale.ROOT)))throw new IOException("Drive'da aynı yerde çakışan ad var: "+f.name());
            String path=prefix.isBlank()?f.name():prefix+"/"+f.name();
            if(f.folder()){folders.put(path,f.id());scanRemote(f.id(),path,files,folders,visited,depth+1);}else files.put(path,f);
            if(files.size()+folders.size()>10000)throw new IOException("Drive klasöründe çok fazla kayıt var.");
        }
    }
    private void ensureFolder(String path,Map<String,String>folders)throws IOException{
        if(folders.containsKey(path))return;String parent=parent(path);ensureFolder(parent,folders);var folder=connection.client().createFolder(leaf(path),folders.get(parent));folders.put(path,folder.id());
    }
    private DriveRemote.File upload(Local local,DriveRemote.File remote,String id,String name,String parent)throws IOException{
        Path temporary=temporary();try{Files.copy(local.file(),temporary,StandardCopyOption.REPLACE_EXISTING);
            if(!hash(temporary).equals(local.hash()))throw new IOException("Yerel dosya okunurken değişti; sonraki senkronizasyonda tekrar denenecek.");
            var saved=remote==null?connection.client().create(id,name,parent,temporary):connection.client().update(remote,temporary);
            if(!saved.parent().equals(parent))throw new IOException("Drive dosyasının klasörü değişti.");return saved;
        }finally{Files.deleteIfExists(temporary);}
    }
    private void download(Path directory,String path,DriveRemote.File remote,String expected)throws IOException{
        Path target=safe(directory,path);Path temporary=temporary();try{connection.client().download(remote,temporary);
            if(!hash(temporary).equals(remote.hash()))throw new IOException("Drive indirme sağlama toplamı uyuşmuyor.");
            String actual=Files.exists(target)?hash(target):null;if(!Objects.equals(actual,expected))throw new IOException("Yerel dosya indirme sırasında değişti; üzerine yazılmadı.");
            if(actual!=null)backup(target);AtomicFileWriter.copy(temporary,target);
        }finally{Files.deleteIfExists(temporary);}
    }
    private Conflict conflict(Path directory,String path,Local local,DriveRemote.File remote,Conflict previous,String reason)throws IOException{
        String localHash=local==null?"":local.hash(),remoteHash=remote==null?"":remote.hash();
        if(previous!=null&&previous.localHash().equals(localHash)&&previous.remoteHash().equals(remoteHash))return previous;
        String copy="";if(remote!=null){Path target=root.resolve("data/sync/conflicts/"+UUID.randomUUID()+"/"+path);Files.createDirectories(target.getParent());connection.client().download(remote,target);
            if(!hash(target).equals(remote.hash())){Files.deleteIfExists(target);throw new IOException("Çakışma kopyası doğrulanamadı.");}copy=root.relativize(target).toString().replace('\\','/');}
        return new Conflict(path,remote==null?"":remote.id(),localHash,remoteHash,copy,reason);
    }
    public synchronized void resolve(String path,boolean keepLocal)throws Exception{
        if(connection==null)throw new IOException("Önce Drive'a bağlan.");var s=state();var conflict=s.conflicts().stream().filter(c->c.path().equals(path)).findFirst().orElseThrow(()->new IOException("Çakışma artık yok."));
        Path directory=localDirectory(),file=safe(directory,path);String local=Files.exists(file)?hash(file):"";
        if(!local.equals(conflict.localHash()))throw new IOException("Yerel dosya son görüntülemeden sonra değişti. Önce yenile.");
        Map<String,DriveRemote.File> remote=new HashMap<>();Map<String,String>folders=new HashMap<>();folders.put("",connection.folder());scanRemote(connection.folder(),"",remote,folders,new HashSet<>(),0);var current=remote.get(path);
        if(!Objects.equals(current==null?"":current.hash(),conflict.remoteHash()))throw new IOException("Drive dosyası son görüntülemeden sonra değişti. Önce yenile.");
        var files=new LinkedHashMap<String,Tracked>();s.files().forEach(f->files.put(f.path(),f));
        if(keepLocal){if(local.isBlank()){if(current!=null)connection.client().trash(current);files.remove(path);}
            else{ensureFolder(parent(path),folders);var prior=files.get(path);String id=current!=null?current.id():prior!=null&&prior.hash().isBlank()?prior.id():connection.client().newId();
                if(current==null){files.put(path,new Tracked(path,id,""));save(new State(1,s.account(),s.folder(),s.localDirectory(),s.automatic(),s.syncedAt(),List.copyOf(files.values()),s.conflicts()));}
                var saved=upload(new Local(file,local),current,id,leaf(path),folders.get(parent(path)));files.put(path,new Tracked(path,saved.id(),local));}}
        else if(current==null){if(!local.isBlank())archive(directory,path,local);files.remove(path);}
        else{download(directory,path,current,local.isBlank()?null:local);files.put(path,new Tracked(path,current.id(),current.hash()));}
        save(new State(1,s.account(),s.folder(),s.localDirectory(),s.automatic(),s.syncedAt(),List.copyOf(files.values()),s.conflicts().stream().filter(c->!c.path().equals(path)).toList()));
    }
    private Path backup(Path source)throws IOException{Path target=root.resolve("data/sync/trash/"+UUID.randomUUID()+"/"+source.getFileName());AtomicFileWriter.copy(source,target);return target;}
    private void archive(Path directory,String path,String expected)throws IOException{Path source=safe(directory,path);if(!hash(source).equals(expected))throw new IOException("Yerel dosya değişti.");backup(source);if(!hash(source).equals(expected))throw new IOException("Yerel dosya yedekleme sırasında değişti; silinmedi.");Files.delete(source);}
    private Path temporary()throws IOException{Path folder=root.resolve("data/cache/drive");Files.createDirectories(folder);return Files.createTempFile(folder,".academic-os-",".part");}
    private static Path safe(Path directory,String relative)throws IOException{if(!relative.isBlank())portable(relative);Path path=directory.resolve(relative).normalize();if(!path.startsWith(directory))throw new IOException("Dosya klasörün dışında.");
        for(Path p=path;p!=null&&p.startsWith(directory);p=p.getParent())if(Files.isSymbolicLink(p))throw new IOException("Senkronizasyon yolunda sembolik bağlantı var.");return path;}
    static void portable(String path)throws IOException{if(path==null||path.isBlank()||path.startsWith("/")||path.contains("\\"))throw new IOException("Geçersiz dosya yolu.");
        for(String part:path.split("/",-1))if(part.isBlank()||part.equals(".")||part.equals("..")||part.matches(".*[\\p{Cntrl}:*?\"<>|].*")||part.endsWith(".")||part.endsWith(" ")
                ||part.matches("(?i)(CON|PRN|AUX|NUL|COM[1-9]|LPT[1-9])(\\..*)?"))throw new IOException("Windows ile uyumsuz dosya adı: "+part);}
    static String hash(Path path)throws IOException{if(Files.size(path)>GoogleDriveApi.MAX_FILE)throw new IOException("Bir dosya en fazla 200 MB olabilir.");
        try{MessageDigest digest=MessageDigest.getInstance("MD5");try(var input=Files.newInputStream(path)){byte[] bytes=new byte[64*1024];int count;long total=0;while((count=input.read(bytes))!=-1){total+=count;if(total>GoogleDriveApi.MAX_FILE)throw new IOException("Dosya çok büyük.");digest.update(bytes,0,count);}}
            return HexFormat.of().formatHex(digest.digest());}catch(NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}}
    private static String parent(String path){int i=path.lastIndexOf('/');return i<0?"":path.substring(0,i);}
    private static String leaf(String path){return path.substring(path.lastIndexOf('/')+1);}
}
