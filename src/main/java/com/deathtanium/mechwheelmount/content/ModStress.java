package com.deathtanium.mechwheelmount.content;

import com.tterrag.registrate.builders.BlockBuilder;
import com.tterrag.registrate.util.nullness.NonNullUnaryOperator;
import net.minecraft.world.level.block.Block;

/**
 * Minimal stress helper used for Registrate transforms.
 * This addon keeps default Create stress behavior and does not expose config knobs.
 */
public class ModStress {
    public static <B extends Block, P> NonNullUnaryOperator<BlockBuilder<B, P>> setImpact(final double value) {
        return builder -> builder;
    }
}
