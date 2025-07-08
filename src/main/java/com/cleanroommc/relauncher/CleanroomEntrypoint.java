package com.cleanroommc.relauncher;

import net.minecraftforge.fml.relauncher.FMLLaunchHandler;
import net.minecraftforge.fml.relauncher.IFMLLoadingPlugin;

import javax.annotation.Nullable;
import java.util.Map;

public class CleanroomEntrypoint implements IFMLLoadingPlugin {

    public CleanroomEntrypoint() {
        if (FMLLaunchHandler.side().isClient()) {
            CleanroomRelauncher.run();
        } else {
            CleanroomRelauncher.LOGGER.fatal("Server-side relaunching is not yet supported!");
        }
    }

    @Override
    public String[] getASMTransformerClass() {
        return new String[0];
    }

    @Override
    public String getModContainerClass() {
        return null;
    }

    @Nullable
    @Override
    public String getSetupClass() {
        return null;
    }

    @Override
    public void injectData(Map<String, Object> data) { }

    @Override
    public String getAccessTransformerClass() {
        return null;
    }

}
