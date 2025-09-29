package com.cleanroommc.relauncher.wrapper;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

public class RelaunchMainWrapper {
    public static void main(String[] args) throws Throwable {
        String mainClassName = System.getProperty("cleanroom.relauncher.mainClass");
        long parentId = Long.parseLong(System.getProperty("cleanroom.relauncher.parent"));
        ProcessHandle thisProcess = ProcessHandle.current();
        ProcessHandle parentProcess = ProcessHandle.of(parentId)
                .or(thisProcess::parent)
                .orElseThrow(() -> new RuntimeException("Unable to grab parent process!"));

        // Parent watcher (Java 9+)
        parentProcess.onExit().thenRun(() -> System.exit(0));

        MethodHandles.lookup()
            .findStatic(Class.forName(mainClassName), "main", MethodType.methodType(void.class, String[].class))
            .invoke((Object) args);
    }

}
