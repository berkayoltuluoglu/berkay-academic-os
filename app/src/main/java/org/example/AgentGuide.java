package org.example;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;

/** First-use documentation; existing user guides are preserved. */
final class AgentGuide {
    static void install(Path root)throws IOException{
        for(String name:List.of("AGENTS.md","CLAUDE.md","CLI.md","planner-proposal.json")){
            String target=name.equals("planner-proposal.json")?"examples/"+name:name;
            if(Files.exists(PlanningFiles.safe(root,target)))continue;
            try(var stream=AgentGuide.class.getResourceAsStream("/agent-guide/"+name)){
                if(stream==null)throw new IOException("Ajan kılavuzu pakette bulunamadı");
                byte[] bytes=stream.readNBytes(1_000_000);var before=new LinkedHashMap<String,byte[]>();before.put(target,null);
                PlanningFiles.commit(root,before,Map.of(target,bytes));
            }
        }
    }
}
