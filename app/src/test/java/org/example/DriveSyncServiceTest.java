package org.example;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.auth.oauth2.BearerToken;
import com.google.api.client.http.*;
import com.google.api.client.testing.http.MockHttpTransport;
import java.io.IOException;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class DriveSyncServiceTest {
    @TempDir Path root;
    private final Fake remote=new Fake();
    private DriveSyncService service;
    @BeforeEach void setup()throws Exception{
        Files.createDirectories(root.resolve("config"));Files.writeString(root.resolve("config/google-calendar-credentials.json"),"{}");
        var auth=new GoogleCalendarOAuthService(){@Override public Credential authorize(Path c,Path t){return new Credential(BearerToken.authorizationHeaderAccessMethod());}};
        service=new DriveSyncService(root,auth){
            @Override HttpRequestFactory createRequests(Credential c){return new MockHttpTransport().createRequestFactory();}
            @Override String account(HttpRequestFactory f){return "student@example.com";}
            @Override DriveRemote createRemote(HttpRequestFactory f){return remote;}
        };service.connect();Files.createDirectories(service.localDirectory());
    }
    private Path local(String name,String value)throws Exception{Path file=service.localDirectory().resolve(name);Files.createDirectories(file.getParent());Files.writeString(file,value);return file;}
    @Test void uploadsLocalFilesAndDownloadsFilesCreatedByAnotherAppInstance()throws Exception{
        local("notes/a.txt","Local");remote.add("remote.txt","Remote","root");var result=service.synchronize();
        assertEquals(1,result.uploads());assertEquals(1,result.downloads());assertTrue(result.warnings().isEmpty());
        assertEquals("Remote",Files.readString(service.localDirectory().resolve("remote.txt")));
        assertEquals("Local",remote.content(remote.named("a.txt").id()));
        assertEquals(0,service.synchronize().uploads());
    }
    @Test void localAndRemoteEditsAreComparedWithTheLastCommonContent()throws Exception{
        Path file=local("test.txt","A");service.synchronize();String id=remote.named("test.txt").id();
        Files.writeString(file,"B");assertEquals(1,service.synchronize().uploads());assertEquals("B",remote.content(id));
        remote.change(id,"C");assertEquals(1,service.synchronize().downloads());assertEquals("C",Files.readString(file));
        assertTrue(Files.walk(root.resolve("data/sync/trash")).filter(Files::isRegularFile).anyMatch(p->{try{return Files.readString(p).equals("B");}catch(IOException e){return false;}}));
    }
    @Test void concurrentEditsPreserveBothVersionsUntilAnExplicitResolution()throws Exception{
        Path file=local("test.txt","A");service.synchronize();String id=remote.named("test.txt").id();Files.writeString(file,"Local edit");remote.change(id,"Remote edit");
        assertEquals(1,service.synchronize().conflicts());assertEquals("Local edit",Files.readString(file));assertEquals("Remote edit",remote.content(id));
        var conflict=service.conflicts().getFirst();assertEquals("Remote edit",Files.readString(root.resolve(conflict.copy())));
        service.resolve("test.txt",false);assertEquals("Remote edit",Files.readString(file));assertTrue(service.conflicts().isEmpty());
    }
    @Test void deletingOneUnmodifiedSideArchivesTheOtherRatherThanPermanentlyDeletingIt()throws Exception{
        Path file=local("test.txt","A");service.synchronize();String id=remote.named("test.txt").id();Files.delete(file);service.synchronize();assertTrue(remote.get(id).trashed());
        Path second=local("second.txt","B");service.synchronize();String secondId=remote.named("second.txt").id();remote.remove(secondId);service.synchronize();assertFalse(Files.exists(second));
        assertTrue(Files.walk(root.resolve("data/sync/trash")).filter(Files::isRegularFile).anyMatch(p->{try{return Files.readString(p).equals("B");}catch(IOException e){return false;}}));
    }
    @Test void deleteVersusEditIsAConflictAndNeverRecreatesOrDeletesContentSilently()throws Exception{
        Path file=local("test.txt","A");service.synchronize();String id=remote.named("test.txt").id();remote.remove(id);Files.writeString(file,"Changed");
        assertEquals(1,service.synchronize().conflicts());assertEquals("Changed",Files.readString(file));assertTrue(remote.children("root").isEmpty());
        service.resolve("test.txt",true);assertEquals("Changed",remote.content(remote.named("test.txt").id()));
    }
    @Test void fileChangedDuringDownloadIsNotOverwritten()throws Exception{
        Path file=local("test.txt","A");service.synchronize();String id=remote.named("test.txt").id();remote.change(id,"Remote");
        remote.downloadHook=()->{try{Files.writeString(file,"During download");}catch(IOException e){throw new RuntimeException(e);}};
        var result=service.synchronize();assertFalse(result.warnings().isEmpty());assertEquals("During download",Files.readString(file));
    }
    @Test void uncertainCreateRetriesTheSameGeneratedFileIdAfterRestart()throws Exception{
        local("test.txt","A");remote.failAfterCreate=true;assertFalse(service.synchronize().warnings().isEmpty());
        assertEquals(1,remote.children("root").size());remote.failAfterCreate=false;
        assertTrue(service.synchronize().warnings().isEmpty());assertEquals(1,remote.children("root").size());assertEquals(1,service.state().files().size());
    }
    @Test void unsafeRemoteNamesAndCaseCollisionsAreRejectedBeforeAnyFileTransfer()throws Exception{
        remote.add("../outside","bad","root");assertThrows(IOException.class,service::synchronize);assertFalse(Files.exists(root.resolve("outside")));
        remote.entries.clear();remote.add("A.txt","A","root");remote.add("a.txt","B","root");assertThrows(IOException.class,service::synchronize);
        assertTrue(Files.list(service.localDirectory()).findAny().isEmpty());
    }
    @Test void localFolderSelectionDoesNotAllowUploadingTheAppProfileOrCredentials()throws Exception{
        assertThrows(IOException.class,()->service.chooseDirectory(root));assertThrows(IOException.class,()->service.chooseDirectory(root.resolve("config")));
        Path directory=Files.createDirectories(root.resolve("external-notes"));service.chooseDirectory(directory);assertEquals(directory,service.localDirectory());
    }
    private static final class Fake implements DriveRemote {
        record Entry(File file,byte[] content){}
        final Map<String,Entry> entries=new LinkedHashMap<>();int counter;boolean failAfterCreate;Runnable downloadHook=()->{};
        @Override public String root(String id){return "root";}
        @Override public List<File> children(String folder){return entries.values().stream().map(Entry::file).filter(f->f.parent().equals(folder)&&!f.trashed()).toList();}
        @Override public File get(String id)throws IOException{if(!entries.containsKey(id))throw new IOException("Not found");return entries.get(id).file();}
        @Override public String newId(){return "file"+(++counter);}
        @Override public File createFolder(String name,String parent)throws IOException{return put(newId(),name,parent,new byte[0],FOLDER,1,false);}
        @Override public File create(String id,String name,String parent,Path source)throws IOException{
            if(entries.containsKey(id))return get(id);File file=put(id,name,parent,Files.readAllBytes(source),"text/plain",1,false);if(failAfterCreate)throw new IOException("Response lost");return file;}
        @Override public File update(File expected,Path source)throws IOException{check(expected);return put(expected.id(),expected.name(),expected.parent(),Files.readAllBytes(source),expected.mime(),Integer.parseInt(expected.version())+1,false);}
        @Override public void download(File expected,Path target)throws IOException{check(expected);Files.write(target,entries.get(expected.id()).content());downloadHook.run();check(expected);}
        @Override public void trash(File expected)throws IOException{check(expected);put(expected.id(),expected.name(),expected.parent(),entries.get(expected.id()).content(),expected.mime(),Integer.parseInt(expected.version())+1,true);}
        File add(String name,String value,String parent)throws Exception{return put(newId(),name,parent,value.getBytes(java.nio.charset.StandardCharsets.UTF_8),"text/plain",1,false);}
        void change(String id,String value)throws Exception{File f=get(id);put(id,f.name(),f.parent(),value.getBytes(java.nio.charset.StandardCharsets.UTF_8),f.mime(),Integer.parseInt(f.version())+1,false);}
        void remove(String id)throws Exception{trash(get(id));}
        File named(String name){return entries.values().stream().map(Entry::file).filter(f->!f.trashed()&&f.name().equals(name)).findFirst().orElseThrow();}
        String content(String id){return new String(entries.get(id).content(),java.nio.charset.StandardCharsets.UTF_8);}
        private File put(String id,String name,String parent,byte[] bytes,String mime,int version,boolean trashed)throws IOException{
            try{String hash=mime.equals(FOLDER)?"":HexFormat.of().formatHex(MessageDigest.getInstance("MD5").digest(bytes));var file=new File(id,name,mime,parent,hash,""+version,"etag"+version,trashed);entries.put(id,new Entry(file,bytes));return file;}catch(Exception e){throw new IOException(e);}}
        private void check(File expected)throws IOException{if(!get(expected.id()).equals(expected))throw new IOException("Remote changed");}
    }
}
