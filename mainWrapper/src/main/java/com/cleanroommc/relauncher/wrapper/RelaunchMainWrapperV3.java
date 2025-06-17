package com.cleanroommc.relauncher.wrapper;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

public class RelaunchMainWrapperV3 {
    public static void main(String[] args) throws Throwable {
        String mainClassName = System.getProperty("cleanroom.mainClass");
        long parentId = Long.parseLong(System.getProperty("cleanroom.relauncher.parent"));
        ProcessHandle thisProcess = ProcessHandle.current();
        ProcessHandle parentProcess = ProcessHandle.of(parentId)
                .or(thisProcess::parent)
                .orElseThrow(() -> new RuntimeException("Unable to grab parent process!"));

        // Parent watcher (Java 9+)
        parentProcess.onExit()
                .thenRun(() -> System.exit(0));

        MethodHandle mainHandle = MethodHandles.lookup().findStatic(
                Class.forName(mainClassName),
                "main",
                MethodType.methodType(void.class, String[].class));
        mainHandle.invoke((Object) args);
    }

}
