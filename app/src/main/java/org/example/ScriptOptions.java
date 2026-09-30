package org.example;

public class ScriptOptions {
    private String interpreter;
    private String workingDirectory;
    private boolean captureOutput = true;
    private long timeoutSeconds = 300;

    public ScriptOptions() {
    }

    public String getInterpreter() {
        return interpreter;
    }

    public String getWorkingDirectory() {
        return workingDirectory;
    }

    public boolean isCaptureOutput() {
        return captureOutput;
    }

    public void setInterpreter(String interpreter) {
        this.interpreter = interpreter;
    }

    public void setWorkingDirectory(String workingDirectory) {
        this.workingDirectory = workingDirectory;
    }

    public void setCaptureOutput(boolean captureOutput) {
        this.captureOutput = captureOutput;
    }

    public long getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public void setTimeoutSeconds(long timeoutSeconds) {
        if (timeoutSeconds <= 0) throw new IllegalArgumentException("Script süresi pozitif olmalı");
        this.timeoutSeconds = timeoutSeconds;
    }
}
