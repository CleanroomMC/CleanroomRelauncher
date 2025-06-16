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

        Thread thread = new Thread("Relauncher Parent Watcher") {

            @Override
            public void run() {
                while (parentProcess.isAlive()) {
                    try {
                        // Arbitrary sleep required (#24)
                        Thread.sleep(100);
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                }
                System.exit(0);
            }
        };
        thread.setDaemon(true);
        thread.start();

        MethodHandle mainHandle = MethodHandles.lookup().findStatic(
                Class.forName(mainClassName),
                "main",
                MethodType.methodType(void.class, String[].class));
        mainHandle.invoke((Object) args);
    }

}
