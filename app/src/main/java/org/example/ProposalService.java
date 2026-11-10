package org.example;

import java.io.IOException;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import static org.example.PlannerCommand.*;

/** Agents only submit suggestions. The UI owns acceptance and records feedback atomically with changes. */
public final class ProposalService {
    public record Issue(int index,String code,String message) {}
    public record Validation(boolean valid,boolean stale,List<Issue> issues) {}
    private final Path root;
    private final Clock clock;
    public ProposalService(Path root){this(root, Clock.systemUTC());}
    ProposalService(Path root, Clock clock){this.root=root;this.clock=clock;}
    public PlanProposal read(Path file)throws IOException{
        if(Files.size(file)>FinanceStorageService.MAX_BYTES)throw new IOException("Öneri dosyası 5 MB sınırını aşıyor");
        var value=PlanningFiles.JSON.readValue(FinanceStorageService.readLimited(file),PlanProposal.class);check(value);return value;
    }
    static void check(PlanProposal p)throws IOException{
        if(p != null && p.commands().isEmpty())
            throw new IOException("INVALID_PROPOSAL: commands en az bir komut içermeli");
        try{
            if(p.schemaVersion()!=1||!p.id().matches("[a-zA-Z0-9_-]{1,80}")||p.agent()==null||p.agent().isBlank()||p.agent().length()>200
                    ||p.note()==null||p.note().length()>4000||p.commands().isEmpty()||p.commands().size()>500||p.fingerprint()==null||!p.fingerprint().matches("[a-f0-9]{64}")
                    ||!Set.of("pending","accepted","partially_accepted","rejected").contains(p.status())||p.userNote().length()>4000)throw new IllegalArgumentException();
            Instant.parse(p.createdAt());LocalDate.parse(p.week());
            if(p.processedAt()!=null)Instant.parse(p.processedAt());
            for(var a:p.commands())if(a==null||a.reason()==null||a.reason().isBlank()||a.reason().length()>1000)throw new IllegalArgumentException("Her komutun gerekçesi olmalı");
            Set<Integer> indices=new HashSet<>();for(var d:p.decisions())if(d.index()<0||d.index()>=p.commands().size()||!indices.add(d.index())||!Set.of("accepted","modified","rejected").contains(d.status()))throw new IllegalArgumentException();
        }catch(RuntimeException e){throw new IOException("INVALID_PROPOSAL: Öneri şeması veya alanları geçersiz",e);}
    }
    public Validation validate(PlanProposal proposal)throws Exception{
        check(proposal);var planner=new PlannerService(root);var snapshot=planner.snapshot(LocalDate.parse(proposal.week()));
        var beforeBlocks=new StudyStorageService(root.resolve("data/study/sessions.json")).load();
        List<Issue> issues=new ArrayList<>();List<PlannerCommand> prefix=new ArrayList<>();
        if(!snapshot.errors().isEmpty())return new Validation(false,!snapshot.fingerprint().equals(proposal.fingerprint()),List.of(new Issue(-1,"SOURCE_ERROR",String.join("; ",snapshot.errors()))));
        PlanningSnapshot context=snapshot;
        for(int i=0;i<proposal.commands().size();i++)try{
            var action=proposal.commands().get(i);var command=command(action);checkPlacement(action,context);checkDeadline(action,context);
            List<PlannerCommand> attempt=new ArrayList<>(prefix);attempt.add(command);
            var dry=planner.transact(attempt,proposal.agent(),proposal.note(),false,Map.of());checkLimits(dry.blocks(),snapshot,beforeBlocks);prefix.add(command);context=withBlocks(withCreatedTask(context,action),dry.blocks());
        }catch(Exception error){issues.add(new Issue(i,AgentCli.errorCode(Objects.toString(error.getMessage(),"")),error.getMessage()));}
        return new Validation(issues.isEmpty(),!snapshot.fingerprint().equals(proposal.fingerprint()),List.copyOf(issues));
    }
    public PlanProposal propose(PlanProposal input)throws Exception{
        check(input);
        if(!input.status().equals("pending")||!input.decisions().isEmpty()||input.processedAt()!=null)throw new IOException("Yeni öneri pending olmalı");
        var validation=validate(input);if(!validation.valid())throw new IOException("INVALID_PROPOSAL: "+validation.issues());
        String name=path(input.id(),false);var before=PlanningFiles.capture(root,List.of(name));
        if(before.get(name)!=null||Files.exists(PlanningFiles.safe(root,path(input.id(),true))))throw new IOException("DUPLICATE_ID: Bu öneri zaten var");
        PlanningFiles.commit(root,before,Map.of(name,PlanningFiles.JSON.writerWithDefaultPrettyPrinter().writeValueAsBytes(input)));return input;
    }
    public PlanProposal status(String id)throws IOException{
        String name=path(id,false);if(!Files.exists(PlanningFiles.safe(root,name)))name=path(id,true);
        var value=PlanningFiles.read(root,name,PlanProposal.class,null);if(value==null)throw new IOException("NOT_FOUND: Öneri bulunamadı");check(value);return value;
    }
    public List<PlanProposal> list(String status)throws IOException{
        List<PlanProposal> result=new ArrayList<>();
        for(String dir:List.of("data/agent/proposals","data/agent/archive")){
            Path directory=PlanningFiles.safe(root,dir);if(!Files.exists(directory))continue;
            try(var files=Files.list(directory)){for(var file:files.sorted().toList()){
                if(!file.getFileName().toString().endsWith(".json"))continue;
                var p=read(PlanningFiles.safe(root,dir+"/"+file.getFileName()));if(status==null||p.status().equals(status))result.add(p);
                if(result.size()>1000)throw new IOException("Çok fazla öneri; eski önerileri arşivden dışa taşı");
            }}
        }
        result.sort(Comparator.comparing(PlanProposal::createdAt).thenComparing(PlanProposal::id));return List.copyOf(result);
    }
    public PlanProposal decide(String id,Map<Integer,PlanProposal.Action> accepted,String note)throws Exception{
        String pendingName=path(id,false);var pendingBefore=PlanningFiles.capture(root,List.of(pendingName));
        if(pendingBefore.get(pendingName)==null)throw new IOException("NOT_FOUND: Bekleyen öneri bulunamadı");
        var p=PlanningFiles.JSON.readValue(pendingBefore.get(pendingName),PlanProposal.class);check(p);if(!p.status().equals("pending"))throw new IOException("Bu öneri zaten işlendi");
        if(note==null||note.length()>4000)throw new IOException("Kullanıcı notu geçersiz");
        for(int i:accepted.keySet())if(i<0||i>=p.commands().size())throw new IOException("Komut indeksi geçersiz");
        var planner=new PlannerService(root);var snapshot=planner.snapshot(LocalDate.parse(p.week()));
        var beforeBlocks=new StudyStorageService(root.resolve("data/study/sessions.json")).load();
        List<PlanProposal.Decision> decisions=new ArrayList<>();List<PlannerCommand> commands=new ArrayList<>();
        PlanningSnapshot context=snapshot;
        for(int i=0;i<p.commands().size();i++){
            var action=accepted.get(i);
            if(action==null)decisions.add(new PlanProposal.Decision(i,"rejected",null));
            else{
                commands.add(command(action));checkPlacement(action,context);checkDeadline(action,context);
                var dry=planner.transact(commands,p.agent(),p.note(),false,Map.of());checkLimits(dry.blocks(),snapshot,beforeBlocks);
                context=withBlocks(withCreatedTask(context,action),dry.blocks());
                decisions.add(new PlanProposal.Decision(i,action.equals(p.commands().get(i))?"accepted":"modified",action));
            }
        }
        String state=commands.isEmpty()?"rejected":commands.size()==p.commands().size()?"accepted":"partially_accepted";
        var result=new PlanProposal(1,p.id(),p.agent(),p.note(),p.createdAt(),p.week(),p.fingerprint(),p.commands(),state,note,List.copyOf(decisions),Instant.now().toString());
        String name=path(id,false);byte[] bytes=PlanningFiles.JSON.writerWithDefaultPrettyPrinter().writeValueAsBytes(result);
        // The pending result is guarded as well, so two open windows cannot accept twice.
        Map<String,byte[]> before=pendingBefore;
        if(commands.isEmpty())PlanningFiles.commit(root,before,Map.of(name,bytes));
        else {
            planner.transact(commands,"proposal:"+p.agent(),"Öneri "+p.id()+" · "+note,true,Map.of(name,bytes),before);
        }
        return result;
    }
    public void archive()throws IOException{
        for(var p:list(null))if(!p.status().equals("pending")&&p.processedAt()!=null&&Instant.parse(p.processedAt()).isBefore(Instant.now().minus(java.time.Duration.ofDays(30)))){
            String old=path(p.id(),false),next=path(p.id(),true);if(!Files.exists(PlanningFiles.safe(root,old)))continue;
            var before=PlanningFiles.capture(root,List.of(old,next));if(before.get(next)!=null)throw new IOException("Arşivde aynı öneri var");
            var after=new LinkedHashMap<String,byte[]>();after.put(old,null);after.put(next,before.get(old));PlanningFiles.commit(root,before,after);
        }
    }
    private static String path(String id,boolean archive)throws IOException{if(id==null||!id.matches("[a-zA-Z0-9_-]{1,80}"))throw new IOException("Öneri kimliği geçersiz");return "data/agent/"+(archive?"archive":"proposals")+"/"+id+".json";}
    static PlannerCommand command(PlanProposal.Action a)throws IOException{
        try{return switch(a.type()){
            case "schedule"->new ScheduleBlock(Objects.toString(a.kind(),""),Objects.toString(a.itemId(),""),a.date(),a.start(),Objects.requireNonNull(a.minutes()),a.title(),ConflictPolicy.REJECT);
            case "move"->new MoveBlock(a.id(),a.date(),a.start(),ConflictPolicy.REJECT);
            case "resize"->new ResizeBlock(a.id(),Objects.requireNonNull(a.minutes()),ConflictPolicy.REJECT);
            case "delete"->new DeleteBlock(a.id());
            case "complete-block"->new CompleteBlock(a.id(),Objects.requireNonNull(a.minutes()),true);
            case "complete-item"->new CompleteItem(a.kind(),a.id(),true);
            case "estimate"->new SetEstimate(a.kind(),a.id(),Objects.requireNonNull(a.minutes()));
            case "create-task"->{var t=new TaskItem(a.id()==null?UUID.randomUUID().toString():a.id(),a.title(),a.date()==null||a.date().isBlank()?null:LocalDate.parse(a.date()),a.details()==null?"":a.details().path("note").asText(""),"Bekliyor");t.setEstimatedMinutes(a.minutes()==null?0:a.minutes());yield new CreateTask(t);}
            default->throw new IOException("INVALID_COMMAND: Bilinmeyen komut: "+a.type());
        };}catch(RuntimeException e){throw new IOException("INVALID_COMMAND: Komut alanları eksik veya geçersiz",e);}
    }
    private static PlanningSnapshot withCreatedTask(PlanningSnapshot snapshot,PlanProposal.Action action){
        if(!action.type().equals("create-task")||action.id()==null)return snapshot;
        var pool=new ArrayList<>(snapshot.pool());pool.add(new PlanningSnapshot.PoolItem("task",action.id(),action.title(),Objects.toString(action.date(),""),"","","Normal",action.minutes()==null?0:action.minutes(),0,action.minutes()==null?0:action.minutes(),0,"",""));
        return new PlanningSnapshot(snapshot.schemaVersion(),snapshot.week(),snapshot.until(),snapshot.timezone(),snapshot.fingerprint(),snapshot.fixedBlocks(),snapshot.blocks(),List.copyOf(pool),snapshot.freeSlots(),snapshot.preferences(),snapshot.errors());
    }
    private static PlanningSnapshot withBlocks(PlanningSnapshot snapshot, List<StudySession> blocks) {
        // Follow earlier commands without exposing unrelated blocks from outside the source week.
        Set<String> ids = new HashSet<>();
        snapshot.blocks().forEach(block -> ids.add(block.id()));
        return new PlanningSnapshot(snapshot.schemaVersion(), snapshot.week(), snapshot.until(), snapshot.timezone(),
                snapshot.fingerprint(), snapshot.fixedBlocks(), blocks.stream().filter(block -> ids.contains(block.id())).toList(),
                snapshot.pool(), snapshot.freeSlots(), snapshot.preferences(), snapshot.errors());
    }
    private void checkPlacement(PlanProposal.Action action, PlanningSnapshot snapshot) throws IOException {
        if (!Set.of("schedule", "move", "resize").contains(action.type())) return;
        String date = action.date(), start = action.start();
        int minutes = action.minutes() == null ? 0 : action.minutes();
        if (!action.type().equals("schedule")) {
            var block = snapshot.blocks().stream().filter(b -> b.id().equals(action.id())).findFirst()
                    .orElseThrow(() -> new IOException("NOT_FOUND: Bu haftada blok bulunamadı"));
            if (action.type().equals("resize")) {
                date = block.date();
                start = block.start();
            } else {
                minutes = PlanningPreferences.minute(block.end()) - PlanningPreferences.minute(block.start());
            }
        }
        LocalDate target = LocalDate.parse(date);
        // A missed block belongs to its source week's proposal, even when moved to a later week.
        if (action.type().equals("schedule") && (target.isBefore(LocalDate.parse(snapshot.week()))
                || target.isAfter(LocalDate.parse(snapshot.until()))))
            throw new IOException("OUTSIDE_WEEK: date önerinin haftası içinde olmalı (" + snapshot.week() + "–" + snapshot.until() + ")");
        if (target.isBefore(LocalDate.now(clock.withZone(ZoneId.of(snapshot.timezone())))))
            throw new IOException("PAST_DATE: date profilin saat diliminde geçmiş bir gün olamaz");
        LocalTime time = LocalTime.parse(start);
        if (!start.matches("[0-9]{2}:[0-9]{2}") || time.getMinute() % 15 != 0 || minutes % 15 != 0)
            throw new IOException("TIME_GRID: start ve minutes 15 dakikalık ızgaraya uymalı");
    }
    private static void checkLimits(List<StudySession> blocks,PlanningSnapshot snapshot,List<StudySession> before)throws IOException{
        var prefs=snapshot.preferences();Set<String> affected=new HashSet<>();
        for(var block:blocks){
            var old=before.stream().filter(b->b.id().equals(block.id())).findFirst().orElse(null);
            if(old!=null && old.date().equals(block.date())&&old.start().equals(block.start())&&old.end().equals(block.end()))continue;
            int start=PlanningPreferences.minute(block.start()),end=PlanningPreferences.minute(block.end());
            if(start<PlanningPreferences.minute(prefs.start())||end>PlanningPreferences.minute(prefs.end())||end-start<prefs.minMinutes()||end-start>prefs.maxMinutes())throw new IOException("PREFERENCES: Çalışma saati veya blok süresi tercihe uymuyor");
            affected.add(block.date());
        }
        for(String date:affected){int total=blocks.stream().filter(b->b.date().equals(date)).mapToInt(b->PlanningPreferences.minute(b.end())-PlanningPreferences.minute(b.start())).sum();if(total>prefs.dailyMinutes())throw new IOException("DAILY_LIMIT: Günlük çalışma sınırı aşılıyor");}
    }
    private static void checkDeadline(PlanProposal.Action a,PlanningSnapshot snapshot)throws IOException{
        if(!Set.of("schedule","move","resize").contains(a.type()))return;
        String kind=a.kind(),id=a.itemId(),date=a.date(),start=a.start();int duration=a.minutes()==null?0:a.minutes();
        if(!a.type().equals("schedule")){
            var block=snapshot.blocks().stream().filter(b->b.id().equals(a.id())).findFirst().orElseThrow(()->new IOException("NOT_FOUND: Bu haftada blok bulunamadı"));
            kind=block.taskId().isBlank()?"work":"task";id=block.taskId().isBlank()?block.workId():block.taskId();
            if(a.type().equals("resize")){date=block.date();start=block.start();}else duration=PlanningPreferences.minute(block.end())-PlanningPreferences.minute(block.start());
        }
        if(id==null||id.isBlank())return;String target=id;
        var item=snapshot.pool().stream().filter(p->p.id().equals(target)).findFirst().orElseThrow(()->new IOException("NOT_FOUND: Bağlı açık iş bulunamadı"));
        if(item.date().isBlank())return;
        LocalDateTime deadline=LocalDate.parse(item.date()).atTime(item.dueTime().isBlank()?LocalTime.of(23,59):LocalTime.parse(item.dueTime()));
        LocalDateTime end=LocalDate.parse(date).atTime(LocalTime.parse(start)).plusMinutes(duration);
        if(end.isAfter(deadline))throw new IOException("AFTER_DEADLINE: Teslimden sonra çalışma planlanamaz");
    }
}
