package com.deathtanium.mechwheelmount;

import com.deathtanium.mechwheelmount.content.MechaWheelMountBlockEntity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

@Mod(MechWheelMount.MOD_ID)
public class MechWheelMountNeoForge {
    public MechWheelMountNeoForge(final IEventBus modBus, final ModContainer modContainer) {
        MechWheelMount.init();
        MechWheelMount.getRegistrate().registerEventListeners(modBus);
        NeoForge.EVENT_BUS.addListener(MechWheelMountNeoForge::onLevelTickPost);
    }

    private static void onLevelTickPost(final LevelTickEvent.Post event) {
        if (event.getLevel().isClientSide()) {
            return;
        }
        MechaWheelMountBlockEntity.applyAllBatchedForces();
    }
}
