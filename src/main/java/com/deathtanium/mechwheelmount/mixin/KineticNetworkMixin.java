package com.deathtanium.mechwheelmount.mixin;

import com.simibubi.create.content.kinetics.KineticNetwork;
import com.deathtanium.mechwheelmount.content.ExtraBlockPos;
import com.deathtanium.mechwheelmount.content.ExtraKinetics;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(KineticNetwork.class)
public class KineticNetworkMixin {

   @Redirect(method = "calculateCapacity", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;getBlockEntity(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/entity/BlockEntity;"))
    public BlockEntity mechwheelmount$extraKineticsCapacity(final Level instance, final BlockPos blockPos) {
       final BlockEntity be = instance.getBlockEntity(blockPos);
       if (be instanceof final ExtraKinetics ek && blockPos instanceof ExtraBlockPos) {
           return ek.getExtraKinetics();
       }

       return be;
   }

    @Redirect(method = "calculateStress", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;getBlockEntity(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/entity/BlockEntity;"))
    public BlockEntity mechwheelmount$extraKineticsStress(final Level instance, final BlockPos blockPos) {
        final BlockEntity be = instance.getBlockEntity(blockPos);
        if (be instanceof final ExtraKinetics ek && blockPos instanceof ExtraBlockPos) {
            return ek.getExtraKinetics();
        }

        return be;
    }


}
