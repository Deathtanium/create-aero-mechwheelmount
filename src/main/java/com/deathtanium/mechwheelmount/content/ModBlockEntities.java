package com.deathtanium.mechwheelmount.content;

import com.tterrag.registrate.util.entry.BlockEntityEntry;
import com.deathtanium.mechwheelmount.MechWheelMount;
import dev.simulated_team.simulated.registrate.SimulatedRegistrate;
import dev.simulated_team.simulated.service.SimInventoryService;

public class ModBlockEntities {
    private static final SimulatedRegistrate REGISTRATE = MechWheelMount.getRegistrate();

    public static final BlockEntityEntry<MechaWheelMountBlockEntity> STEERABLE_WHEEL_MOUNT = REGISTRATE
            .blockEntity("steerable_wheel_mount", MechaWheelMountBlockEntity::new)
            .onRegister(SimInventoryService.INSTANCE.registerInventory((be, dir) -> be.getInventory()))
            .validBlocks(ModBlocks.STEERABLE_WHEEL_MOUNT)
            .renderer(() -> MechaWheelMountRenderer::new)
            .register();

    public static void init() {
    }
}
