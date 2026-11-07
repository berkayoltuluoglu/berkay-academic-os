package org.example;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

interface DriveRemote {
    String FOLDER="application/vnd.google-apps.folder";
    record File(String id,String name,String mime,String parent,String hash,String version,String etag,boolean trashed) {
        boolean folder(){return FOLDER.equals(mime);}
    }
    String root(String knownId)throws IOException;
    List<File> children(String folder)throws IOException;
    File get(String id)throws IOException;
    String newId()throws IOException;
    File createFolder(String name,String parent)throws IOException;
    File create(String id,String name,String parent,Path source)throws IOException;
    File update(File expected,Path source)throws IOException;
    void download(File expected,Path destination)throws IOException;
    void trash(File expected)throws IOException;
}
