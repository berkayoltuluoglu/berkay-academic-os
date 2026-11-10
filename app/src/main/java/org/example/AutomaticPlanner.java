package org.example;

import java.time.*;
import java.util.*;

/** Deterministic greedy placement: deadlines, priority, available minutes and daily load. No model or network. */
public final class AutomaticPlanner {
    public record Unscheduled(String id,String title,int minutes,String reason) {}
    /** No commands means there is no proposal to validate or submit. */
    public record Suggestion(PlanProposal proposal,List<Unscheduled> unscheduled,String message) {}
    public Suggestion suggest(PlanningSnapshot snapshot){return suggest(snapshot,Clock.system(ZoneId.of(snapshot.timezone())));}
    Suggestion suggest(PlanningSnapshot snapshot,Clock clock){
        var prefs=snapshot.preferences();List<PlanningSnapshot.Slot> slots=new ArrayList<>(snapshot.freeSlots());
        Map<String,Integer> loads=new TreeMap<>();snapshot.blocks().forEach(b->loads.merge(b.date(),PlanningPreferences.minute(b.end())-PlanningPreferences.minute(b.start()),Integer::sum));
        List<PlanProposal.Action> commands=new ArrayList<>();List<Unscheduled> missing=new ArrayList<>();
        var items=snapshot.pool().stream().sorted(Comparator.comparing((PlanningSnapshot.PoolItem p)->p.date().isBlank()?"9999-12-31":p.date()).thenComparingInt(p->PlanningSnapshot.priority(p.priority())).thenComparing(PlanningSnapshot.PoolItem::id)).toList();
        LocalDateTime now=LocalDateTime.now(clock.withZone(ZoneId.of(snapshot.timezone())));
        for(var item:items){
            if(!Set.of("task","work","classroom","application").contains(item.kind()))continue;
            int remaining=item.unplannedMinutes();
            LocalDateTime deadline=item.date().isBlank()?LocalDate.parse(snapshot.until()).atTime(23,59):LocalDate.parse(item.date()).atTime(item.dueTime().isBlank()?LocalTime.of(23,59):LocalTime.parse(item.dueTime()));
            if (remaining > 0 && !item.date().isBlank() && deadline.isBefore(now)) {
                missing.add(new Unscheduled(item.id(), item.title(), remaining, "Teslim tarihi geçmiş"));
                continue;
            }
            while(remaining>0){
                PlanningSnapshot.Slot best=null;int bestStart=0,bestLength=0,bestLoad=Integer.MAX_VALUE;
                for(var slot:slots){
                    LocalDate date=LocalDate.parse(slot.date());if(date.isBefore(now.toLocalDate()))continue;
                    int start=PlanningPreferences.minute(slot.start()),end=PlanningPreferences.minute(slot.end());
                    if(date.equals(now.toLocalDate()))start=Math.max(start,((now.getHour()*60+now.getMinute()+14)/15)*15);
                    if(date.isAfter(deadline.toLocalDate()))continue;
                    if(date.equals(deadline.toLocalDate()))end=Math.min(end,deadline.getHour()*60+deadline.getMinute());
                    start=((start+14)/15)*15;int load=loads.getOrDefault(slot.date(),0);
                    int length=Math.min(Math.min(end-start,prefs.maxMinutes()),Math.min(Math.max(remaining,prefs.minMinutes()),prefs.dailyMinutes()-load));length=length/15*15;
                    if(length<prefs.minMinutes())continue;
                    if(load<bestLoad){best=slot;bestStart=start;bestLength=length;bestLoad=load;}
                }
                if(best==null)break;
                String reason="Son teslim "+(item.date().isBlank()?"tarihsiz":item.date())+" · "+item.priority()+" · günlük yük dengesi";
                commands.add(new PlanProposal.Action("schedule",null,item.kind(),item.id(),item.title(),best.date(),PlanningPreferences.time(bestStart),bestLength,reason,null));
                slots.remove(best);int end=bestStart+bestLength;
                if(bestStart>PlanningPreferences.minute(best.start()))slots.add(new PlanningSnapshot.Slot(best.date(),best.start(),PlanningPreferences.time(bestStart),bestStart-PlanningPreferences.minute(best.start())));
                if(end<PlanningPreferences.minute(best.end()))slots.add(new PlanningSnapshot.Slot(best.date(),PlanningPreferences.time(end),best.end(),PlanningPreferences.minute(best.end())-end));
                slots.sort(Comparator.comparing(PlanningSnapshot.Slot::date).thenComparing(PlanningSnapshot.Slot::start));loads.merge(best.date(),bestLength,Integer::sum);remaining=Math.max(0,remaining-bestLength);
            }
            if(remaining>0)missing.add(new Unscheduled(item.id(),item.title(),remaining,"Teslimden önce tercihlere uygun yeterli boş aralık yok"));
        }
        if (commands.isEmpty()) return new Suggestion(null, List.copyOf(missing), missing.isEmpty()
                ? "Yerleştirilecek çalışma kalmadı" : "Çalışma yerleştirilemedi; unscheduled içindeki nedenleri incele");
        String id="suggest-"+snapshot.fingerprint().substring(0,20)+"-"+snapshot.week();
        var proposal=new PlanProposal(1,id,"Academic OS otomatik planlayıcı","Teslim ve günlük süre sınırlarına göre yerleştirildi",clock.instant().toString(),snapshot.week(),snapshot.fingerprint(),List.copyOf(commands),"pending","",List.of(),null);
        return new Suggestion(proposal,List.copyOf(missing),"Öneri hazır");
    }
}
