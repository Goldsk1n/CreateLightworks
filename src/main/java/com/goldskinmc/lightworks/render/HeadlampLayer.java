package com.goldskinmc.lightworks.render;

import com.goldskinmc.lightworks.content.LightType;
import com.goldskinmc.lightworks.content.carried.Headlamps;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/**
 * Draws a fitted headlamp on the goggles or diving helmet a player wears, flat on the forehead: on the goggles' bridge
 * or a helmet's brow. The parts {@code headlamp/body} and {@code headlamp/lens} are laid out in the head's own space,
 * turned with the head; the
 * lens tinted by its dye and lit while on. Players only, as only they light a beam from it.
 */
public final class HeadlampLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    /** On a helmet the lamp sits this much higher than on goggles: on its brow, over the window. */
    private static final float HELMET_UP = 1.2f / 16;

    private HeadlampLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
        super(parent);
    }

    public static void register(EntityRenderersEvent.AddLayers event) {
        for (PlayerSkin.Model skin : event.getSkins()) {
            if (event.getSkin(skin) instanceof PlayerRenderer renderer) {
                renderer.addLayer(new HeadlampLayer(renderer));
            }
        }
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource buffers, int packedLight, AbstractClientPlayer player,
            float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw,
            float headPitch) {
        ItemStack head = player.getItemBySlot(EquipmentSlot.HEAD);
        if (!Headlamps.isFitted(head) || player.isInvisible()) {
            return;
        }
        poseStack.pushPose();
        getParentModel().getHead().translateAndRotate(poseStack);
        // Model space (y up, the head x 4-12, y 0-8, z 4-12) into the head's (y down, centred, face toward -z)
        poseStack.scale(-1, -1, 1);
        poseStack.translate(-0.5f, 0, -0.5f);
        if (!Headlamps.onGoggles(head)) {
            poseStack.translate(0, HELMET_UP, 0);
        }
        PartRenderer.solid(poseStack, buffers, Partials.get("headlamp/body"), packedLight);
        DyeColor colour = Headlamps.colour(head);
        int rgb = colour == null ? LightType.HEADLAMP.colour() : colour.getTextureDiffuseColor() & 0xFFFFFF;
        PartRenderer.lens(poseStack, buffers, "headlamp/lens", rgb, Headlamps.isOn(head) ? 1 : 0, packedLight);
        poseStack.popPose();
    }
}
