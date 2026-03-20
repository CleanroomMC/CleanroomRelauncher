package com.cleanroommc.relauncher;

import com.cleanroommc.relauncher.gui.GUIButtonIcon;
import com.cleanroommc.relauncher.gui.GUIRelauncherMenu;
import net.minecraft.client.gui.GuiButton;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.util.List;


@Mod(modid = Tags.MOD_ID, name = Tags.MOD_NAME, version = Tags.VERSION)
public class OptionsMenuHandler {

    private static final int RELAUNCHER_BUTTON_ID = 989;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        MinecraftForge.EVENT_BUS.register(new OptionsMenuHandler());
        CleanroomRelauncher.LOGGER.info("Pre-Init Event has fired Relauncher");
    }


    @SubscribeEvent
    public void onGuiInit(GuiScreenEvent.InitGuiEvent.Post event) {
        if (event.getGui() instanceof net.minecraft.client.gui.GuiOptions) {

            String targetText = net.minecraft.util.text.translation.I18n.translateToLocal("options.skinCustomisation");

            List<GuiButton> buttons = event.getButtonList();
            for (GuiButton b : buttons) {
                if (targetText.equals(b.displayString)) {
                    buttons.removeIf(btn -> btn.id == RELAUNCHER_BUTTON_ID);
                    GuiButton relauncherBtn = new GUIButtonIcon(
                            RELAUNCHER_BUTTON_ID,
                            b.x,
                            b.y - 24
                    );

                    buttons.add(relauncherBtn);
                    break;
                }
            }
        }
    }
    @SubscribeEvent
    public void onActionPerformed(GuiScreenEvent.ActionPerformedEvent.Post event) {
        if (event.getButton().id == RELAUNCHER_BUTTON_ID) {
            event.getGui().mc.displayGuiScreen(new GUIRelauncherMenu(event.getGui()));
        }
    }

}