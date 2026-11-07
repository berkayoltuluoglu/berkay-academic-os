package org.example;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.*;

public record PlanProposal(int schemaVersion,String id,String agent,String note,String createdAt,String week,String fingerprint,
                           List<Action> commands,String status,String userNote,List<Decision> decisions,String processedAt) {
    public record Action(String type,String id,String kind,String itemId,String title,String date,String start,Integer minutes,String reason,JsonNode details) {}
    public record Decision(int index,String status,Action applied) {}
    public PlanProposal {
        id=id==null||id.isBlank()?UUID.randomUUID().toString():id;
        createdAt=createdAt==null?Instant.now().toString():createdAt;
        commands=commands==null?List.of():List.copyOf(commands);
        status=status==null?"pending":status;userNote=userNote==null?"":userNote;decisions=decisions==null?List.of():List.copyOf(decisions);
    }
}
