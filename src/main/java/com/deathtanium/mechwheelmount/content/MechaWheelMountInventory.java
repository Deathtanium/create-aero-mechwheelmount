package com.deathtanium.mechwheelmount.content;

import dev.simulated_team.simulated.multiloader.inventory.ItemInfoWrapper;
import dev.simulated_team.simulated.multiloader.inventory.SingleSlotContainer;

public class MechaWheelMountInventory extends SingleSlotContainer {
    private final MechaWheelMountBlockEntity blockEntity;
    public boolean suppressUpdate = false;

    public MechaWheelMountInventory(final MechaWheelMountBlockEntity blockEntity) {
        super(1);
        this.blockEntity = blockEntity;
    }

    @Override
    public boolean canInsertItem(final ItemInfoWrapper item) {
        final TireLike tireLike = ItemInfoWrapper.generateFromInfo(item).get(ModDataComponents.TIRE);
        return tireLike != null;
    }

    @Override
    public void setChanged() {
        if (!suppressUpdate) {
            this.blockEntity.notifyUpdate();
        }
    }
}
