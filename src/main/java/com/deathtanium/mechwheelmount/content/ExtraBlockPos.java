package com.deathtanium.mechwheelmount.content;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;

/**
 * Just a helper block position class for ExtraKinetics block entities
 */
public class ExtraBlockPos extends BlockPos {

    public ExtraBlockPos(final Vec3i blockPos) {
        super(blockPos.getX(), blockPos.getY(), blockPos.getZ());
    }
}
