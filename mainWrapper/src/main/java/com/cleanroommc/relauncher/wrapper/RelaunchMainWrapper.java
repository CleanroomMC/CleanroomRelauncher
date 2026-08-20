package com.cleanroommc.relauncher.wrapper;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

public class RelaunchMainWrapper {

    private static final int PARENT_READY_SIGNAL = 1;

    public static void main(String[] args) throws Throwable {
        String mainClassName = System.getProperty("cleanroom.relauncher.mainClass");
        long parentId = Long.parseLong(System.getProperty("cleanroom.relauncher.parent"));
        ProcessHandle thisProcess = ProcessHandle.current();
        ProcessHandle parentProcess = ProcessHandle.of(parentId)
                .or(thisProcess::parent)
                .orElseThrow(() -> new RuntimeException("Unable to grab parent process!"));

        awaitParentReady(parentProcess);

        // Parent watcher (Java 9+)
        parentProcess.onExit().thenRun(() -> System.exit(0));

        MethodHandles.lookup()
            .findStatic(Class.forName(mainClassName), "main", MethodType.methodType(void.class, String[].class))
            .invoke((Object) args);
    }

    private static void awaitParentReady(ProcessHandle parentProcess) throws Exception {
        int signal = System.in.read();
        if (signal == -1 || !parentProcess.isAlive()) {
            System.exit(0);
        }
        if (signal != PARENT_READY_SIGNAL) {
            throw new IllegalStateException("Received an invalid parent cleanup handshake");
        }
    }

}
