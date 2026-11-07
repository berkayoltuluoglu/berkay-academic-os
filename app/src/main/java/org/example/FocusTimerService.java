package org.example;

import java.io.IOException;
import java.nio.file.*;
import java.time.Clock;
import java.util.Properties;

/** Wall-clock countdown: it survives navigation, suspension and application restarts. */
public final class FocusTimerService {
    public static final long DURATION = 25 * 60_000L;
    private final Path file;
    private final Clock clock;
    private long remaining = DURATION, deadline;
    private long duration = DURATION;
    private String error;
    private byte[] baseline;
    private int creditMinutes=-1;
    private boolean breakMode;
    private int completedCycles;
    private String cycleId="";
    private String targetId = "", targetKind = "", focusId = "";

    public FocusTimerService(Path file) { this(file, Clock.systemDefaultZone()); }
    FocusTimerService(Path file, Clock clock) {
        this.file = file; this.clock = clock;
        if (Files.exists(file)) {
            try {
                baseline=FinanceStorageService.readLimited(file);
                Properties p = new Properties(); p.load(new java.io.StringReader(new String(baseline, java.nio.charset.StandardCharsets.UTF_8)));
                if (!java.util.Set.of("1","2").contains(p.getProperty("version"))) throw new IOException("Sayaç sürümü desteklenmiyor");
                long storedRemaining = Long.parseLong(p.getProperty("remaining"));
                duration = Long.parseLong(p.getProperty("duration", Long.toString(DURATION)));
                long storedDeadline = Long.parseLong(p.getProperty("deadline"));
                if (duration < 60000 || duration > 180 * 60000L || storedRemaining < 0 || storedRemaining > duration || storedDeadline < 0)
                    throw new IOException("Sayaç kaydı geçersiz");
                remaining = storedRemaining; deadline = storedDeadline;
                creditMinutes=Integer.parseInt(p.getProperty("creditMinutes","-1"));breakMode=Boolean.parseBoolean(p.getProperty("breakMode","false"));
                completedCycles=Integer.parseInt(p.getProperty("completedCycles","0"));cycleId=p.getProperty("cycleId","");
                if(completedCycles<0||completedCycles>1000000||(!cycleId.isBlank()&&!cycleId.matches("[a-zA-Z0-9_-]{1,80}")))throw new IOException("Odak döngüsü geçersiz");
                if(creditMinutes < -1 || creditMinutes>180)throw new IOException("Odak süresi geçersiz");
                targetId = p.getProperty("targetId", ""); targetKind = p.getProperty("targetKind", ""); focusId = p.getProperty("focusId", "");
                if (targetId.isBlank()!=targetKind.isBlank())throw new IOException("Sayaç hedefi eksik");
                if ((!targetId.isBlank() && (!targetId.matches("[a-zA-Z0-9_-]{1,80}") || !java.util.Set.of("task", "work", "session").contains(targetKind)))
                        || (!focusId.isBlank() && !focusId.matches("[a-zA-Z0-9_-]{1,80}"))) throw new IOException("Sayaç hedefi geçersiz");
            } catch (Exception ex) { error = "Odak sayacı okunamadı: " + ex.getMessage(); }
        }
    }

    public synchronized long remainingMillis() { return deadline == 0 ? remaining : Math.max(0, deadline - clock.millis()); }
    public synchronized boolean running() { return deadline != 0 && remainingMillis() > 0; }
    public String error() { return error; }
    public synchronized long durationMillis() { return duration; }
    public synchronized String targetId() { return targetId; }
    public synchronized String targetKind() { return targetKind; }
    public synchronized void selectTarget(String kind, String id) throws IOException {
        if (running() || remainingMillis() != duration) throw new IOException("Çalışma hedefini değiştirmeden önce sayacı sıfırla.");
        if (kind.isBlank() != id.isBlank() || (!kind.isBlank() && !java.util.Set.of("task", "work", "session").contains(kind))
                || (!id.isBlank() && !id.matches("[a-zA-Z0-9_-]{1,80}"))) throw new IOException("Çalışma hedefi geçersiz.");
        String oldKind = targetKind, oldId = targetId;
        targetKind = kind; targetId = id;
        try { persist(remaining, deadline); } catch (IOException failure) { targetKind = oldKind; targetId = oldId; throw failure; }
    }
    public synchronized void creditCompleted() throws IOException {
        if (error != null) throw new IOException(error);
        if (!breakMode && creditMinutes != 0 && deadline != 0 && remainingMillis() == 0 && !targetId.isBlank() && !focusId.isBlank()) {
            Path data = file.toAbsolutePath().getParent();
            new StudyTimeLogService(data.getParent()).credit(new StudyTimeLogService.Entry(focusId, targetId, targetKind,
                    (creditMinutes >= 0 ? creditMinutes : (int) (duration / 60000)), java.time.Instant.ofEpochMilli(deadline).toString()));
        }
    }
    public synchronized void configureMinutes(int minutes) throws IOException {
        if (minutes < 1 || minutes > 180) throw new IOException("Odak süresi 1–180 dakika olabilir.");
        long next = minutes * 60000L;
        if (next == duration) return;
        if (running()) throw new IOException("Odak süresini değiştirmeden önce sayacı duraklat.");
        creditCompleted();
        long old = duration; String oldFocusId = focusId;int oldCredit=creditMinutes;boolean oldBreak=breakMode; duration = next; focusId = "";creditMinutes=-1;breakMode=false;
        try { persist(next, 0); } catch (IOException failure) { duration = old; focusId = oldFocusId;creditMinutes=oldCredit;breakMode=oldBreak; throw failure; }
    }

    public synchronized void toggle() throws IOException {
        long next = remainingMillis();
        if (next == 0) creditCompleted();
        if (running()) persist(next, 0);
        else {
            String oldId = focusId;int oldCredit=creditMinutes;
            if (next == 0) { next = duration; focusId = ""; creditMinutes=-1; }
            if (focusId.isBlank()) focusId = java.util.UUID.randomUUID().toString();
            try { persist(next, clock.millis() + next); } catch (IOException failure) { focusId = oldId;creditMinutes=oldCredit; throw failure; }
        }
    }
    public synchronized void reset() throws IOException {
        creditCompleted(); String old = focusId;int oldCredit=creditMinutes;boolean oldBreak=breakMode; focusId = "";
        creditMinutes=-1;breakMode=false;
        try { persist(duration, 0); } catch (IOException failure) { focusId = old;creditMinutes=oldCredit;breakMode=oldBreak; throw failure; }
    }

    public synchronized int completedCycles(){return completedCycles;}
    public synchronized boolean breakMode() { return breakMode; }
    public synchronized void finish() throws IOException {
        if(remainingMillis()==0){creditCompleted();return;}
        int old=creditMinutes;creditMinutes=(int)((duration-remainingMillis())/60000);
        try { persist(0,clock.millis()); } catch(IOException failure){creditMinutes=old;throw failure;}
        creditCompleted();
    }
    public synchronized void startBreak(int minutes) throws IOException {
        creditCompleted();
        if(!breakMode&&remainingMillis()==0&&!focusId.isBlank()&&!focusId.equals(cycleId)){completedCycles++;cycleId=focusId;}
        reset(); configureMinutes(minutes);breakMode=true;
        try { toggle(); } catch(IOException failure){breakMode=false;throw failure;}
    }
    public synchronized void startSession(String id,int minutes) throws IOException {
        if(running())throw new IOException("Önce mevcut odağı bitir veya duraklat");
        reset();selectTarget("session",id);configureMinutes(minutes);toggle();
    }
    private void persist(long nextRemaining, long nextDeadline) throws IOException {
        if (error != null) throw new IOException(error + "; mevcut dosya korunuyor");
        String content="version=2\nremaining=" + nextRemaining + "\ndeadline=" + nextDeadline + "\nduration=" + duration
                + "\ntargetKind=" + targetKind + "\ntargetId=" + targetId + "\nfocusId=" + focusId + "\ncreditMinutes="+creditMinutes+"\nbreakMode="+breakMode+"\ncompletedCycles="+completedCycles+"\ncycleId="+cycleId+"\n";
        Path absolute=file.toAbsolutePath().normalize(),parent=absolute.getParent();
        byte[] bytes=content.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        var before=new java.util.LinkedHashMap<String,byte[]>();before.put(absolute.getFileName().toString(),baseline);
        PlanningFiles.commit(parent,before,java.util.Map.of(absolute.getFileName().toString(),bytes));baseline=bytes;
        remaining = nextRemaining; deadline = nextDeadline;
    }
}
