package org.example;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.api.client.http.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;

/** The drive.file scope limits visibility to files created by or individually granted to this application. */
final class GoogleDriveApi implements DriveRemote {
    static final long MAX_FILE=200L*1024*1024;
    private static final String API="https://www.googleapis.com/drive/v3/";
    private static final String FIELDS="id,name,mimeType,parents,md5Checksum,version,trashed,appProperties,ownedByMe";
    private static final String V2="https://www.googleapis.com/drive/v2/";
    private static final String V2_FIELDS="id,title,mimeType,parents(id),md5Checksum,version,etag,labels(trashed)";
    private final HttpRequestFactory requests;
    private final ObjectMapper mapper=new ObjectMapper();
    GoogleDriveApi(HttpRequestFactory requests){this.requests=requests;}
    private record Reply(JsonNode json,String etag){}
    @Override public String root(String known)throws IOException {
        if(!known.isBlank())id(known);
        if(!known.isBlank())try{var reply=json("GET",API+"files/"+known,Map.of("fields",FIELDS),null,null);var file=file(reply.json(),reply.etag());
            if(file.folder()&&!file.trashed()&&reply.json().path("ownedByMe").asBoolean()&&reply.json().path("appProperties").path("academicOs").asText().equals("documents-v1"))return file.id();
            throw new IOException("Seçilen klasör bu hesabın Academic OS klasörü değil.");
        }catch(HttpResponseException error){if(error.getStatusCode()!=404)throw error;}
        var page=json("GET",API+"files",Map.of("q","trashed=false and 'me' in owners and mimeType='"+FOLDER+"' and appProperties has { key='academicOs' and value='documents-v1' }","fields","files(id),nextPageToken","pageSize",100),null,null).json();
        if(page.path("files").size()>1)throw new IOException("Birden fazla Academic OS klasörü bulundu; eski klasör bağlantısını seçerek tekrar bağlan.");
        if(!page.path("files").isEmpty())return page.path("files").get(0).path("id").asText();
        return json("POST",API+"files",Map.of("fields",FIELDS),Map.of("id",newId(),"name","Academic OS","mimeType",FOLDER,
                "appProperties",Map.of("academicOs","documents-v1")),null).json().path("id").asText();
    }
    @Override public List<File> children(String parent)throws IOException {
        id(parent);List<File> result=new ArrayList<>();String token="";Set<String> seen=new HashSet<>();
        do{Map<String,Object> parameters=new HashMap<>(Map.of("q","'"+parent+"' in parents and trashed=false","fields","files("+FIELDS+"),nextPageToken","pageSize",1000));
            if(!token.isBlank())parameters.put("pageToken",token);var page=json("GET",API+"files",parameters,null,null).json();
            for(var entry:page.path("files"))result.add(file(entry,""));
            if(result.size()>10000)throw new IOException("Drive klasöründe çok fazla dosya var.");
            token=page.path("nextPageToken").asText("");if(!token.isBlank()&&!seen.add(token))throw new IOException("Drive sayfalaması tekrarlıyor.");
        }while(!token.isBlank());return result;
    }
    @Override public File get(String id)throws IOException {id(id);var reply=json("GET",V2+"files/"+id,Map.of("fields",V2_FIELDS),null,null);return fileV2(reply.json());}
    @Override public String newId()throws IOException {return json("GET",API+"files/generateIds",Map.of("count",1,"space","drive","type","files"),null,null).json().path("ids").get(0).asText();}
    @Override public File createFolder(String name,String parent)throws IOException {id(parent);return file(json("POST",API+"files",Map.of("fields",FIELDS),
            Map.of("id",newId(),"name",name,"parents",List.of(parent),"mimeType",FOLDER),null).json(),"");}
    @Override public File create(String id,String name,String parent,Path source)throws IOException {
        id(id);id(parent);var body=new MultipartContent().setMediaType(new HttpMediaType("multipart/related").setParameter("boundary","academic_os_"+UUID.randomUUID()))
                .addPart(new MultipartContent.Part(ByteArrayContent.fromString("application/json; charset=UTF-8",mapper.writeValueAsString(Map.of("id",id,"name",name,"parents",List.of(parent))))))
                .addPart(new MultipartContent.Part(new FileContent("application/octet-stream",source.toFile())));
        HttpRequest request=requests.buildPostRequest(url("https://www.googleapis.com/upload/drive/v3/files",Map.of("uploadType","multipart","fields",FIELDS)),body);
        try{execute(request);return get(id);}catch(HttpResponseException error){if(error.getStatusCode()!=409)throw error;return get(id);}
    }
    @Override public File update(File expected,Path source)throws IOException {
        File latest=verify(expected);HttpRequest request=requests.buildPutRequest(url("https://www.googleapis.com/upload/drive/v2/files/"+latest.id(),Map.of("uploadType","media","fields",V2_FIELDS)),new FileContent("application/octet-stream",source.toFile()));
        conditional(request,latest);HttpResponse response=request.execute();try{return fileV2(read(response));}finally{response.disconnect();}
    }
    @Override public void download(File expected,Path destination)throws IOException {
        File latest=verify(expected); HttpResponse response=null;long total=0;
        try{response=requests.buildGetRequest(url(API+"files/"+latest.id(),Map.of("alt","media"))).execute();
            try(var input=response.getContent();var output=Files.newOutputStream(destination)){byte[] bytes=new byte[64*1024];int count;
                while((count=input.read(bytes))!=-1){total+=count;if(total>MAX_FILE)throw new IOException("Drive dosyası 200 MB sınırını aşıyor.");output.write(bytes,0,count);}}
        }finally{if(response!=null)response.disconnect();}
        verify(expected);
    }
    @Override public void trash(File expected)throws IOException {var latest=verify(expected);if(latest.etag().isBlank())throw new IOException("Drive sürüm koruması alınamadı; dosya silinmedi.");json("POST",V2+"files/"+latest.id()+"/trash",Map.of("fields",V2_FIELDS),Map.of(),latest.etag());}
    private File verify(File expected)throws IOException {
        File actual=get(expected.id());if(actual.trashed()||!actual.parent().equals(expected.parent())||!actual.version().equals(expected.version())||!actual.hash().equals(expected.hash()))
            throw new IOException("Drive dosyası başka yerde değişti; senkronizasyonu yenile.");
        return actual;
    }
    private void conditional(HttpRequest request,File expected)throws IOException {
        if(expected.etag()==null||expected.etag().isBlank())throw new IOException("Drive sürüm koruması alınamadı; mevcut dosyanın üzerine yazılmadı.");
        request.getHeaders().setIfMatch(expected.etag());
    }
    private File execute(HttpRequest request)throws IOException {
        HttpResponse response=null;try{response=request.execute();return file(read(response),Objects.toString(response.getHeaders().getETag(),""));}
        finally{if(response!=null)response.disconnect();}
    }
    private Reply json(String method,String address,Map<String,?> parameters,Object body,String etag)throws IOException {
        HttpContent content=body==null?null:ByteArrayContent.fromString("application/json; charset=UTF-8",mapper.writeValueAsString(body));
        var request=requests.buildRequest(method,url(address,parameters),content);if(etag!=null&&!etag.isBlank())request.getHeaders().setIfMatch(etag);
        HttpResponse response=null;try{response=request.execute();return new Reply(read(response),Objects.toString(response.getHeaders().getETag(),""));}
        finally{if(response!=null)response.disconnect();}
    }
    private JsonNode read(HttpResponse response)throws IOException {byte[] data=response.getContent().readNBytes(FinanceStorageService.MAX_BYTES+1);
        if(data.length>FinanceStorageService.MAX_BYTES)throw new IOException("Drive yanıtı çok büyük.");return mapper.readTree(data);}
    private static GenericUrl url(String address,Map<String,?> parameters){var url=new GenericUrl(address);parameters.forEach(url::set);return url;}
    private File file(JsonNode node,String etag)throws IOException {
        String id=node.path("id").asText();id(id);String parent=node.path("parents").isEmpty()?"":node.path("parents").get(0).asText();
        return new File(id,node.path("name").asText(),node.path("mimeType").asText(),parent,node.path("md5Checksum").asText(),node.path("version").asText(),etag,node.path("trashed").asBoolean());
    }
    private File fileV2(JsonNode node)throws IOException {
        String id=node.path("id").asText();id(id);String parent=node.path("parents").isEmpty()?"":node.path("parents").get(0).path("id").asText();
        return new File(id,node.path("title").asText(),node.path("mimeType").asText(),parent,node.path("md5Checksum").asText(),node.path("version").asText(),node.path("etag").asText(),node.path("labels").path("trashed").asBoolean());
    }
    private static void id(String id)throws IOException {if(id==null||!id.matches("[a-zA-Z0-9_-]{1,200}"))throw new IOException("Drive dosya kimliği geçersiz.");}
}
