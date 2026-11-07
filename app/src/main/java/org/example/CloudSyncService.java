package org.example;

import java.nio.file.*;
import java.time.*;
import java.util.Properties;
import java.util.concurrent.*;

/** Separate schedulers keep a slow Drive transfer from delaying calendar delivery. */
final class CloudSyncService implements AutoCloseable {
    private final Path root;
    private final GoogleCalendarService calendar;
    private final DriveSyncService drive;
    private final ScheduledExecutorService workers=Executors.newScheduledThreadPool(2,r->{Thread t=new Thread(r,"academic-os-cloud-sync");t.setDaemon(true);return t;});
    private final CopyOnWriteArrayList<Runnable> listeners=new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<Runnable> dataListeners=new CopyOnWriteArrayList<>();
    private volatile boolean calendarAutomatic=true;
    private volatile String calendarStatus="Henüz senkronize edilmedi",driveStatus="Henüz senkronize edilmedi";
    CloudSyncService(Path root,GoogleCalendarService calendar,DriveSyncService drive){this.root=root;this.calendar=calendar;this.drive=drive;
        try{Path path=root.resolve("data/sync/calendar.properties");if(Files.exists(path)){Properties p=new Properties();try(var reader=Files.newBufferedReader(path)){p.load(reader);}calendarAutomatic=Boolean.parseBoolean(p.getProperty("automatic","true"));}}catch(Exception error){calendarStatus=error.getMessage();}}
    void start(){workers.scheduleWithFixedDelay(()->{if(calendarAutomatic)pollCalendar();},0,30,TimeUnit.SECONDS);
        workers.scheduleWithFixedDelay(()->{try{if(drive.automatic())pollDrive();}catch(Exception error){driveStatus=error.getMessage();notifyChange();}},0,30,TimeUnit.SECONDS);}
    void pollCalendar(){try{synchronizeCalendarNow();}catch(Exception error){calendarStatus="Takvim senkronizasyonu: "+error.getMessage();notifyChange();}}
    void synchronizeCalendarNow()throws Exception{
        boolean changed=false;
        if(calendar instanceof CalendarSyncService syncing){changed=syncing.synchronize();calendarStatus=calendar.isConnected()?"Son kontrol: "+LocalTime.now().withNano(0)+" · "+syncing.pendingCount()+" işlem bekliyor":"Google hesabına bağlan";
            if(syncing.getSynchronizationError()!=null)calendarStatus+=" · "+syncing.getSynchronizationError();}
        else if(calendar.isConnected()){calendar.loadEventsForMonth(YearMonth.now());calendarStatus="Takvim yenilendi";changed=true;}
        else calendarStatus="Google hesabına bağlan";
        if(changed)for(Runnable listener:dataListeners)listener.run();notifyChange();
        if(!calendar.isConnected())throw new java.io.IOException("Önce Google Calendar hesabına bağlan.");
    }
    void pollDrive(){try{synchronizeDriveNow();}catch(Exception error){driveStatus="Drive senkronizasyonu: "+error.getMessage();notifyChange();}}
    void synchronizeDriveNow()throws Exception{var result=drive.synchronize();driveStatus="Son kontrol: "+LocalTime.now().withNano(0)+" · "+result.uploads()+" yüklendi · "+result.downloads()+" indirildi · "+result.conflicts()+" çakışma";
        if(!result.warnings().isEmpty())driveStatus+=" · "+String.join(" · ",result.warnings());
        notifyChange();
    }
    GoogleCalendarService calendar(){return calendar;}DriveSyncService drive(){return drive;}
    String calendarStatus(){return calendarStatus;}String driveStatus(){return driveStatus;}
    boolean calendarAutomatic(){return calendarAutomatic;}
    void calendarAutomatic(boolean value)throws java.io.IOException{AtomicFileWriter.writeText(root.resolve("data/sync/calendar.properties"),"automatic="+value+"\n");calendarAutomatic=value;}
    void addListener(Runnable listener){listeners.add(listener);}void removeListener(Runnable listener){listeners.remove(listener);}
    void addDataListener(Runnable listener){dataListeners.add(listener);}
    private void notifyChange(){for(Runnable listener:listeners)try{listener.run();}catch(RuntimeException ignored){/* Disposed UI listeners must not stop synchronization. */}}
    @Override public void close(){workers.shutdownNow();}
}
