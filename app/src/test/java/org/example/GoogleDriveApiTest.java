package org.example;
import com.google.api.client.testing.http.*;import com.google.api.client.http.*;import org.junit.jupiter.api.*;import org.junit.jupiter.api.io.TempDir;import java.nio.file.*;import java.util.*;import static org.junit.jupiter.api.Assertions.*;
class GoogleDriveApiTest {
 @TempDir Path root;
 @Test void multipartHasABoundaryAndUpdatesAndTrashUseConditionalRequests()throws Exception{
  List<String> writes=new ArrayList<>();var transport=new MockHttpTransport(){@Override public LowLevelHttpRequest buildRequest(String method,String url){return new MockLowLevelHttpRequest(url){@Override public MockLowLevelHttpResponse execute()throws java.io.IOException{
   if(url.contains("upload/drive/v3")){assertTrue(getContentType().contains("boundary="));assertTrue(getContentAsString().contains("payload"));writes.add("create");return response(200,"{\"id\":\"file1\"}");}
   if(!method.equals("GET")){assertEquals("\"e1\"",getFirstHeaderValue("If-Match"));writes.add(method);}
   return response(200,"{\"id\":\"file1\",\"title\":\"a.txt\",\"mimeType\":\"text/plain\",\"parents\":[{\"id\":\"root\"}],\"md5Checksum\":\"hash\",\"version\":\"1\",\"etag\":\"\\\"e1\\\"\",\"labels\":{\"trashed\":false}}");
  }};}};var api=new GoogleDriveApi(transport.createRequestFactory());Path file=root.resolve("a.txt");Files.writeString(file,"payload");var saved=api.create("file1","a.txt","root",file);api.update(saved,file);api.trash(saved);assertEquals(List.of("create","PUT","POST"),writes);
 }
 @Test void changedMetadataNeverTriggersAnOverwrite()throws Exception{
  int[] writes={0};var transport=new MockHttpTransport(){@Override public LowLevelHttpRequest buildRequest(String method,String url){if(!method.equals("GET"))writes[0]++;return new MockLowLevelHttpRequest(url).setResponse(response(200,"{\"id\":\"file1\",\"parents\":[{\"id\":\"root\"}],\"version\":\"2\",\"md5Checksum\":\"changed\",\"etag\":\"e2\"}"));}};
  var api=new GoogleDriveApi(transport.createRequestFactory());var old=new DriveRemote.File("file1","a.txt","text/plain","root","original","1","e1",false);assertThrows(java.io.IOException.class,()->api.update(old,root.resolve("a.txt")));assertThrows(java.io.IOException.class,()->api.trash(old));assertEquals(0,writes[0]);
 }
 private static MockLowLevelHttpResponse response(int status,String json){return new MockLowLevelHttpResponse().setStatusCode(status).setContentType("application/json").setContent(json);}
}
