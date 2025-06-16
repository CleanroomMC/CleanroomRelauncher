package com.cleanroommc.relauncher.wrapper;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class RelaunchMainWrapperV3 {
    public static void main(String[] args) throws Throwable {
        String mainClassName = System.getProperty("cleanroom.mainClass");
        long parentId = Long.parseLong(System.getProperty("cleanroom.relauncher.parent"));
        ProcessHandle thisProcess = ProcessHandle.current();
        ProcessHandle parentProcess = ProcessHandle.of(parentId)
                .or(thisProcess::parent)
                .orElseThrow(() -> new RuntimeException("Unable to grab parent process!"));

        ExecutorService watcher = Executors.newSingleThreadExecutor(r -> {
            Thread thread = new Thread(r, "Relauncher Parent Watcher");
            thread.setDaemon(true);
            return thread;
        });
        parentProcess.onExit()
                .thenRunAsync(() -> System.exit(0), watcher);

        MethodHandle mainHandle = MethodHandles.lookup().findStatic(
                Class.forName(mainClassName),
                "main",
                MethodType.methodType(void.class, String[].class));
        mainHandle.invoke((Object) args);
    }

}
