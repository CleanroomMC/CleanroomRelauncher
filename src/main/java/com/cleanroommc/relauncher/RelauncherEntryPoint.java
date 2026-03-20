package com.cleanroommc.relauncher;

import net.minecraftforge.fml.relauncher.FMLLaunchHandler;
import net.minecraftforge.fml.relauncher.IFMLLoadingPlugin;

import javax.annotation.Nullable;
import java.io.File;
import java.util.Map;

public class RelauncherEntryPoint implements IFMLLoadingPlugin {

    static File source;

    public RelauncherEntryPoint() {
        if (FMLLaunchHandler.side().isClient()) {
            CleanroomRelauncher.run();
        } else {
            CleanroomRelauncher.LOGGER.fatal("Server-side relaunching is not yet supported!");
        }
    }

    @Override
    public String[] getASMTransformerClass() {
        return null;
    }

    @Override
    public String getModContainerClass() {
        return "com.cleanroommc.relauncher.RelauncherModContainer";
    }

    @Nullable
    @Override
    public String getSetupClass() {
        return null;
    }

    @Override
    public void injectData(Map<String, Object> data) {
        source = (File) data.get("location");
    }

    @Override
    public String getAccessTransformerClass() {
        return null;
    }

}
