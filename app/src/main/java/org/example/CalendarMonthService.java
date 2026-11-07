package org.example;

import java.nio.file.Path;
import java.time.*;
import java.util.*;

/** The month calendar uses the same records as Today/Agenda, with deadlines shown on their actual date. */
final class CalendarMonthService {
    record Month(List<CalendarEventItem> items,List<String> warnings,int googleCount,int localCount) {}
    Month load(Path root,YearMonth month,List<CalendarEventItem> google,boolean showLocal) {
        List<CalendarEventItem> items=new ArrayList<>(google);Set<String> warnings=new LinkedHashSet<>();int localCount=0;
        if(showLocal)for(var day:new DailyAgendaService().loadMonth(root,month)) {
            warnings.addAll(day.errors());
            for(var event:day.events())if(!event.kind().equals("Google")) {
                String source=event.study()?"Çalışma":"Ders",id="local:"+(event.study()?"study":"courses");
                var item=CalendarEventItem.timed(event.id()+"-"+day.date()+"-"+event.start(),event.title(),day.date().atTime(event.start()),day.date().atTime(event.end()),event.detail(),"")
                        .withGoogleMetadata(day.date(),"",false,false).withSource(id,source,event.study()?"#8e24aa":"#0b8043").withDone(event.done());items.add(item);localCount++;
            }
            for(var todo:day.todos())if(todo.date().equals(day.date())&&!todo.module().equals("calendar")) {
                String name=switch(todo.module()){case "classroom"->"Classroom";case "finance"->"Burs / başvuru";case "courses"->"Ödev / sınav";default->"Görev";};
                var item=CalendarEventItem.allDay(todo.id(),todo.title(),todo.date(),todo.detail(),"");
                if(todo.dueTime()!=null)item=CalendarEventItem.timed(todo.id(),todo.title(),todo.date().atTime(todo.dueTime()),todo.date().atTime(todo.dueTime()),todo.detail(),"");
                items.add(item.withGoogleMetadata(todo.date(),"",false,false).withSource("local:"+todo.module(),name,todo.module().equals("finance")?"#e37400":"#d50000").withDone(todo.done()));localCount++;
            }
        }
        items.sort(Comparator.comparing(CalendarEventItem::getDate).thenComparing(item->item.getStartTime()==null?LocalTime.MIN:item.getStartTime()).thenComparing(CalendarEventItem::getTitle));
        return new Month(List.copyOf(items),List.copyOf(warnings),google.size(),localCount);
    }
}
