package org.example;

import java.io.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;

/** A second entry point: no JavaFX toolkit, OAuth initialization or network calls. */
public final class AgentCli {
    private AgentCli(){}
    public static void main(String[] args){System.exit(run(args,System.out));}
    public static int run(String[] arguments,PrintStream out){
        return run(arguments, out, Clock.systemUTC());
    }
    static int run(String[] arguments, PrintStream out, Clock clock){
        try{
            Args args=new Args(arguments);Path root=args.option("profile")==null?AppPaths.root():Path.of(args.option("profile")).toAbsolutePath().normalize();
            String command=args.command();
            args.validateUsage();
            if(args.flag("help")||command.equals("help")){print(out,help(command));return 0;}
            var planner=new PlannerService(root);var proposals=new ProposalService(root,clock);Object result;
            switch(command){
                case "snapshot"->result=planner.snapshot(args.week());
                case "pool"->{var snap=planner.snapshot(args.weekOrNow());String until=args.option("until");if(until!=null)LocalDate.parse(until);result=Map.of("timezone",snap.timezone(),"fingerprint",snap.fingerprint(),"items",snap.pool().stream().filter(p->until==null||p.date().isBlank()||!LocalDate.parse(p.date()).isAfter(LocalDate.parse(until))).toList());}
                case "free-slots"->{var snap=planner.snapshot(args.week());int min=args.integer("min",snap.preferences().minMinutes());if(min<1||min>1440)throw new IllegalArgumentException("--min 1–1440 olmalı");result=Map.of("timezone",snap.timezone(),"fingerprint",snap.fingerprint(),"slots",snap.freeSlots().stream().filter(s->s.minutes()>=min).toList());}
                case "schema"->result=schema();
                case "validate"->{var validation=proposals.validate(proposals.read(Path.of(args.positional(1))));print(out,validation);return validation.valid()?0:2;}
                case "propose"->result=proposals.propose(proposals.read(Path.of(args.positional(1))));
                case "proposals"->result=Map.of("proposals",proposals.list(args.option("status")));
                case "proposal-status"->result=proposals.status(args.positional(1));
                case "suggest"->{var snapshot=planner.snapshot(args.week());result=new AutomaticPlanner().suggest(snapshot,clock.withZone(ZoneId.of(snapshot.timezone())));}
                case "task","block","undo","redo"->{
                    if(!args.flag("apply"))throw new CliError("APPLY_REQUIRED","Doğrudan yazım için --apply gerekli; varsayılan akış propose");
                    if(!PlanningPreferences.load(root).agentWrites())throw new CliError("DIRECT_WRITES_DISABLED","Ayarlar → Ajanların doğrudan değişiklik yapmasına izin ver kapalı");
                    if(command.equals("undo")){result=planner.undo();break;}if(command.equals("redo")){result=planner.redo();break;}
                    String operation=args.positional(1);PlannerCommand action;
                    if(command.equals("task")){
                        if(!operation.equals("add"))throw new IllegalArgumentException("task add kullan");
                        var task=new TaskItem(args.required("title"),args.option("due")==null?null:LocalDate.parse(args.option("due")),"","Bekliyor");task.setEstimatedMinutes(args.integer("minutes",0));action=new PlannerCommand.CreateTask(task);
                    }else action=switch(operation){
                        case "add"->new PlannerCommand.ScheduleBlock(Objects.toString(args.option("kind"),""),Objects.toString(args.option("item"),""),args.required("date"),args.required("start"),args.integer("minutes",60),args.option("title"),PlannerCommand.ConflictPolicy.REJECT);
                        case "move"->new PlannerCommand.MoveBlock(args.required("id"),args.required("date"),args.required("start"),PlannerCommand.ConflictPolicy.REJECT);
                        case "resize"->new PlannerCommand.ResizeBlock(args.required("id"),args.integer("minutes",60),PlannerCommand.ConflictPolicy.REJECT);
                        case "delete"->new PlannerCommand.DeleteBlock(args.required("id"));
                        default->throw new IllegalArgumentException("block add|move|resize|delete kullan");
                    };
                    result=planner.execute(List.of(action),"agent-cli",command+" "+operation);
                }
                default->throw new CliError("UNKNOWN_COMMAND","Bilinmeyen komut; --help kullan");
            }
            print(out,result);return 0;
        }catch(Exception error){
            String message=Objects.toString(error.getMessage(),"İşlem tamamlanamadı");String code=error instanceof CliError e?e.code:errorCode(message);
            try{print(out,Map.of("ok",false,"error",Map.of("code",code,"message",message)));}catch(Exception ignored){out.println("{\"ok\":false,\"error\":{\"code\":\"INTERNAL\"}}");}return 2;
        }
    }
    static String errorCode(String message){for(String code:List.of("STALE_SNAPSHOT","BUSY","AFTER_DEADLINE","CONFLICT","NOT_FOUND","INVALID_PROPOSAL","INVALID_COMMAND","DUPLICATE_ID","DAILY_LIMIT","PREFERENCES","OUTSIDE_WEEK","PAST_DATE","TIME_GRID"))if(message.startsWith(code+":"))return code;return "INVALID_REQUEST";}
    private static void print(PrintStream out,Object value)throws IOException{out.println(PlanningFiles.JSON.writeValueAsString(value));}
    private static Map<String,Object> help(String command){return Map.of("command",command,"usage","academic-os [--profile DIR] <command> [options]","examples",List.of(
            "academic-os snapshot --week 2026-11-09","academic-os pool --until 2026-12-31","academic-os free-slots --week 2026-11-09 --min 30",
            "academic-os suggest --week 2026-11-09","academic-os schema","academic-os validate proposal.json","academic-os propose proposal.json",
            "academic-os proposals --status pending","academic-os proposal-status <id>","academic-os task add --title Rapor --due 2026-11-13 --minutes 120 --apply",
            "academic-os block add --date 2026-11-10 --start 10:00 --minutes 60 --title Okuma --apply","academic-os block move --id ID --date 2026-11-11 --start 11:00 --apply",
            "academic-os block resize --id ID --minutes 90 --apply","academic-os block delete --id ID --apply","academic-os undo --apply","academic-os redo --apply"));}
    public static Map<String,Object> schema(){
        Map<String,Object> action=new LinkedHashMap<>();action.put("type","object");action.put("additionalProperties",false);action.put("required",List.of("type","reason"));
        Map<String,Object> props=new LinkedHashMap<>();props.put("type",Map.of("enum",List.of("schedule","move","resize","delete","complete-block","complete-item","estimate","create-task")));
        for(String field:List.of("id","kind","itemId","title","date","start","reason"))props.put(field,Map.of("type",List.of("string","null")));
        props.put("reason",Map.of("type","string","minLength",1,"maxLength",1000));
        props.put("kind",Map.of("enum",Arrays.asList("task","work","classroom","application","",null)));
        List<Map<String,Object>> variants=new ArrayList<>();
        Map<String,List<String>> requirements=new LinkedHashMap<>();requirements.put("schedule",List.of("date","start","minutes"));requirements.put("move",List.of("id","date","start"));requirements.put("resize",List.of("id","minutes"));requirements.put("delete",List.of("id"));requirements.put("complete-block",List.of("id","minutes"));requirements.put("complete-item",List.of("kind","id"));requirements.put("estimate",List.of("kind","id","minutes"));requirements.put("create-task",List.of("title"));
        requirements.forEach((type,required)->variants.add(Map.of("properties",Map.of("type",Map.of("const",type)),"required",required)));action.put("oneOf",variants);
        props.put("minutes",Map.of("type",List.of("integer","null"),"minimum",0,"maximum",100000));props.put("details",Map.of("type",List.of("object","null")));action.put("properties",props);
        Map<String,Object> proposal=new LinkedHashMap<>();proposal.put("$schema","https://json-schema.org/draft/2020-12/schema");proposal.put("title","Academic OS önerisi v1");proposal.put("type","object");proposal.put("additionalProperties",false);
        proposal.put("required",List.of("schemaVersion","agent","note","week","fingerprint","commands"));
        Map<String,Object> fields=new LinkedHashMap<>();fields.put("schemaVersion",Map.of("const",1));for(String field:List.of("id","agent","note","createdAt","week","fingerprint","status","userNote","processedAt"))fields.put(field,Map.of("type",List.of("string","null")));
        fields.put("agent",Map.of("type","string","minLength",1,"maxLength",200));fields.put("note",Map.of("type","string","maxLength",4000));fields.put("fingerprint",Map.of("type","string","pattern","^[a-f0-9]{64}$"));fields.put("week",Map.of("type","string","format","date"));
        fields.put("commands",Map.of("type","array","minItems",1,"maxItems",500,"items",action));fields.put("decisions",Map.of("type","array"));proposal.put("properties",fields);
        return Map.of("schemaVersion",1,"proposal",proposal,"commandReference",help("all"),"dateFormat","ISO-8601","timeFormat","HH:mm","directWritesDefault",false);
    }
    private static final class CliError extends IOException{final String code;CliError(String code,String message){super(message);this.code=code;}}
    private static final class Args{
        final Map<String,String> options=new LinkedHashMap<>();final List<String> positions=new ArrayList<>();final Set<String> used=new HashSet<>();
        Args(String[] args){for(int i=0;i<args.length;i++){String a=args[i];if(a.startsWith("--")){String key=a.substring(2);if(options.containsKey(key))throw new IllegalArgumentException("Tekrarlanan seçenek: "+a);if(Set.of("help","apply").contains(key))options.put(key,"true");else{if(i+1>=args.length||args[i+1].startsWith("--"))throw new IllegalArgumentException(a+" değeri eksik");options.put(key,args[++i]);}}else positions.add(a);}checkKnown();}
        void validateUsage(){
            String cmd=command();Set<String> allowed=new HashSet<>(Set.of("profile","help"));
            allowed.addAll(switch(cmd){case "snapshot","suggest"->Set.of("week");case "pool"->Set.of("week","until");case "free-slots"->Set.of("week","min");case "proposals"->Set.of("status");case "undo","redo"->Set.of("apply");case "task"->Set.of("title","due","minutes","apply");case "block"->Set.of("kind","item","date","start","minutes","title","id","apply");default->Set.of();});
            for(String key:options.keySet())if(!allowed.contains(key))throw new IllegalArgumentException("Bu komutta geçersiz seçenek: --"+key);
            int count=Set.of("task","block","validate","propose","proposal-status").contains(cmd)?2:1;
            if(!options.containsKey("help")&&positions.size()>count)throw new IllegalArgumentException("Fazla komut argümanı");
        }
        void checkKnown(){for(String key:options.keySet())if(!Set.of("profile","week","until","min","status","apply","help","title","due","minutes","kind","item","date","start","id").contains(key))throw new IllegalArgumentException("Bilinmeyen seçenek: --"+key);}
        String command(){return positions.isEmpty()?"help":positions.getFirst();}
        String option(String name){used.add(name);return options.get(name);}
        boolean flag(String name){return "true".equals(option(name));}
        String required(String name){String value=option(name);if(value==null)throw new IllegalArgumentException("--"+name+" gerekli");return value;}
        int integer(String name,int fallback){String value=option(name);return value==null?fallback:Integer.parseInt(value);}
        String positional(int index){if(index>=positions.size())throw new IllegalArgumentException("Dosya/kimlik/alt komut gerekli");return positions.get(index);}
        LocalDate week(){return LocalDate.parse(required("week"));}
        LocalDate weekOrNow(){String value=option("week");return value==null?LocalDate.now(ZoneId.of("Europe/Istanbul")):LocalDate.parse(value);}
        void check(){for(String key:options.keySet())if(!used.contains(key))throw new IllegalArgumentException("Bu komutta geçersiz seçenek: --"+key);}
    }
}
