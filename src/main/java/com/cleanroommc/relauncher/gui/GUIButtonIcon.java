package com.cleanroommc.relauncher.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.ResourceLocation;

public class GUIButtonIcon extends GuiButton {

    private static final ResourceLocation ICON = new ResourceLocation("relauncher", "textures/gui/icon.png");

    public GUIButtonIcon(int buttonId, int x, int y) {
        super(buttonId, x, y, 20, 20, "");
    }

    @Override
    public void drawButton(Minecraft mc, int mouseX, int mouseY, float partialTicks) {
        if (this.visible) {
            super.drawButton(mc, mouseX, mouseY, partialTicks);

            mc.getTextureManager().bindTexture(ICON);

            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
            GlStateManager.enableBlend();
            GlStateManager.tryBlendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA, GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);

            int iconSize = 16;
            int xPos = this.x + (this.width - iconSize) / 2;
            int yPos = this.y + (this.height - iconSize) / 2;

            drawModalRectWithCustomSizedTexture(xPos, yPos, 0, 0, iconSize, iconSize, iconSize, iconSize);
        }
    }
}
