package com.cleanroommc.relauncher;

import com.google.common.eventbus.EventBus;
import net.minecraftforge.fml.common.DummyModContainer;
import net.minecraftforge.fml.common.LoadController;
import net.minecraftforge.fml.common.MetadataCollection;
import net.minecraftforge.fml.common.ModMetadata;

import java.io.InputStream;
import java.util.Collections;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;

public class RelauncherModContainer extends DummyModContainer {

    private static ModMetadata load() {
        try (JarFile jar = new JarFile(RelauncherEntryPoint.source)) {
            ZipEntry modInfo = jar.getEntry("mcmod.info");
            try (InputStream inputStream = jar.getInputStream(modInfo)) {
                return MetadataCollection.from(inputStream, Tags.MOD_ID).getMetadataForId(Tags.MOD_ID, Collections.emptyMap());
            }
        } catch (Throwable t) {
            ModMetadata meta = new ModMetadata();
            meta.modId = Tags.MOD_ID;
            meta.name = Tags.MOD_NAME;
            meta.version = Tags.VERSION;
            meta.authorList.add("Rongmario");
            return meta;
        }
    }

    public RelauncherModContainer() {
        super(load());
    }

    @Override
    public String getGuiClassName() {
        return "com.cleanroommc.relauncher.gui.RelauncherGuiFactory";
    }

    @Override
    public boolean registerBus(EventBus bus, LoadController controller) {
        return true;
    }

}