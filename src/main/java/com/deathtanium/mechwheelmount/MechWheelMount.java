package com.deathtanium.mechwheelmount;

import com.simibubi.create.foundation.item.ItemDescription;
import com.simibubi.create.foundation.item.KineticStats;
import com.simibubi.create.foundation.item.TooltipModifier;
import com.tterrag.registrate.providers.ProviderType;
import com.tterrag.registrate.util.nullness.NonNullSupplier;
import com.deathtanium.mechwheelmount.content.MechaWheelMountBlockEntity;
import com.deathtanium.mechwheelmount.content.ModBlockEntities;
import com.deathtanium.mechwheelmount.content.ModBlocks;
import com.deathtanium.mechwheelmount.content.ModDataComponents;
import com.deathtanium.mechwheelmount.content.ModLang;
import com.deathtanium.mechwheelmount.content.ModPartialModels;
import dev.simulated_team.simulated.registrate.SimulatedRegistrate;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.createmod.catnip.lang.FontHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MechWheelMount {
    public static final String MOD_ID = "mechwheelmount";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static final NonNullSupplier<SimulatedRegistrate> REGISTRATE = NonNullSupplier.lazy(() ->
            (SimulatedRegistrate) new SimulatedRegistrate(path(MOD_ID), MOD_ID).defaultCreativeTab((ResourceKey<CreativeModeTab>) null));

    public static void init() {
        setTooltips();
        getRegistrate().addDataGenerator(ProviderType.LANG, ModLang::registrateLang);
        ModPartialModels.init();
        ModBlocks.init();
        ModBlockEntities.init();
        ModDataComponents.init();
    }

    private static void setTooltips() {
        getRegistrate().setTooltipModifierFactory(item -> new ItemDescription.Modifier(item, FontHelper.Palette.STANDARD_CREATE)
                .andThen(TooltipModifier.mapNull(KineticStats.create(item))));
    }

    public static SimulatedRegistrate getRegistrate() {
        return REGISTRATE.get();
    }

    public static ResourceLocation path(final String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }

    public static void onPhysicsTick(final net.minecraft.server.level.ServerLevel level, final double timeStep) {
        MechaWheelMountBlockEntity.applyAllBatchedForces();
    }
}
