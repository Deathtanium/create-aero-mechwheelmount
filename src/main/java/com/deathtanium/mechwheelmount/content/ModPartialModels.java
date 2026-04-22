package com.deathtanium.mechwheelmount.content;

import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import com.deathtanium.mechwheelmount.MechWheelMount;

public class ModPartialModels {
    public static final PartialModel
            DIODE_LEFT = block("wheel_mount/diode_left"),
            DIODE_RIGHT = block("wheel_mount/diode_right"),
            TELE_OUTER = block("wheel_mount/tele_outer"),
            TELE_INNER = block("wheel_mount/tele_inner"),
            TELE_MOUNT = block("wheel_mount/mount"),
            SPRING_UPPER = block("wheel_mount/spring_upper"),
            SPRING_MIDDLE = block("wheel_mount/spring_middle"),
            SPRING_LOWER = block("wheel_mount/spring_lower"),
            TOP_SHAFT = block("wheel_mount/top_shaft");

    private static PartialModel block(final String path) {
        return PartialModel.of(MechWheelMount.path("block/" + path));
    }

    public static void init() {
    }
}
