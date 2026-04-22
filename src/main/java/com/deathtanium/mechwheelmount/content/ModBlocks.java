package com.deathtanium.mechwheelmount.content;

import com.simibubi.create.AllTags;
import com.simibubi.create.content.contraptions.actors.roller.RollerBlockItem;
import com.simibubi.create.foundation.data.BlockStateGen;
import com.simibubi.create.foundation.data.SharedProperties;
import com.simibubi.create.infrastructure.config.CStress;
import com.tterrag.registrate.providers.RegistrateRecipeProvider;
import com.tterrag.registrate.util.entry.BlockEntry;
import com.deathtanium.mechwheelmount.MechWheelMount;
import dev.simulated_team.simulated.registrate.SimulatedRegistrate;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.data.recipes.ShapelessRecipeBuilder;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.level.material.MapColor;

import static com.simibubi.create.foundation.data.ModelGen.customItemModel;
import static com.simibubi.create.foundation.data.TagGen.axeOrPickaxe;

public class ModBlocks {
    private static final SimulatedRegistrate REGISTRATE = MechWheelMount.getRegistrate();

    public static final BlockEntry<MechaWheelMountBlock> STEERABLE_WHEEL_MOUNT =
            REGISTRATE.block("steerable_wheel_mount", MechaWheelMountBlock::new)
                    .initialProperties(SharedProperties::stone)
                    .properties(p -> p.mapColor(MapColor.COLOR_GRAY)
                            .noOcclusion()
                            .isRedstoneConductor((state, level, pos) -> false))
                    .transform(axeOrPickaxe())
                    .addLayer(() -> RenderType::cutoutMipped)
                    .tag(AllTags.AllBlockTags.SAFE_NBT.tag)
                    .blockstate(BlockStateGen.horizontalBlockProvider(true))
                    .transform(CStress.setImpact(16.0))
                    .item(RollerBlockItem::new)
                    .transform(customItemModel())
                    .recipe((c, p) -> ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, c.get(), 1)
                            .requires(dev.ryanhcode.offroad.index.OffroadBlocks.WHEEL_MOUNT.get())
                            .unlockedBy("has_ingredient", RegistrateRecipeProvider.has(dev.ryanhcode.offroad.index.OffroadBlocks.WHEEL_MOUNT.get()))
                            .save(p))
                    .register();

    public static void init() {
    }
}
