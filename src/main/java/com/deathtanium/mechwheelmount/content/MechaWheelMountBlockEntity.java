package com.deathtanium.mechwheelmount.content;

import com.google.common.collect.ImmutableList;
import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.api.schematic.requirement.SpecialBlockEntityItemRequirement;
import com.simibubi.create.content.contraptions.actors.roller.RollerBlock;
import com.simibubi.create.content.equipment.clipboard.ClipboardCloneable;
import com.simibubi.create.content.kinetics.base.IRotate;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.schematics.requirement.ItemRequirement;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueBoxTransform;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueSettingsBoard;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueSettingsFormatter;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollValueBehaviour;
import com.simibubi.create.foundation.utility.CreateLang;
import dev.engine_room.flywheel.lib.transform.TransformStack;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.block.BlockEntitySubLevelActor;
import dev.ryanhcode.sable.api.math.OrientedBoundingBox3d;
import dev.ryanhcode.sable.api.physics.force.ForceTotal;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.api.physics.mass.MassData;
import dev.ryanhcode.sable.companion.math.JOMLConversion;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.mixinterface.clip_overwrite.ClipContextExtension;
import dev.ryanhcode.sable.physics.config.block_properties.PhysicsBlockPropertyHelper;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.simulated_team.simulated.multiloader.inventory.SingleSlotContainer;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import net.createmod.catnip.math.AngleHelper;
import net.createmod.catnip.math.VecHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Vec3i;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.Mth;
import net.minecraft.world.Clearable;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.neoforged.neoforge.common.Tags;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;
import org.joml.Vector3dc;

import java.util.Collection;
import java.util.List;

public class MechaWheelMountBlockEntity extends KineticBlockEntity implements BlockEntitySubLevelActor, Clearable, ClipboardCloneable, SpecialBlockEntityItemRequirement, ExtraKinetics {
    private static final MutableComponent SCROLL_OPTION_TITLE = ModLang.translate("scroll_option.suspension_strength").component();
    private static final MutableComponent RATIO_OPTION_TITLE = ModLang.translate("scroll_option.input_ratio").component();
    private static final double MAX_ALLOWED_EXTENSION = 0.65;
    private static final double NO_WHEEL_EXTENSION = 0.5;
    private static final float MAX_STEER_RADIANS = (float) Math.toRadians(45.0);
    private static final float BROKEN_STEER_RADIANS = (float) Math.toRadians(45.05);
    private static final float DEFAULT_INPUT_TO_TIRE_RATIO = 6f;
    private static final float DEGREES_PER_RADIAN = (float) (180f / Math.PI);

    private static final Collection<MechaWheelMountBlockEntity> QUEUED_WHEEL_MOUNTS = new ObjectOpenHashSet<>();

    private final MechaWheelMountInventory inventory;
    private final SteeringInputKinetic steeringInput;
    private SuspensionStrengthValueBehaviour strength;
    private InputRatioValueBehaviour inputRatio;

    private int clientSteeringSignal;
    protected int clientSteeringSignalLeft;
    protected int clientSteeringSignalRight;
    private int lastServerSteeringSignal;
    private int lastServerSteeringSignalLeft;
    private int lastServerSteeringSignalRight;

    private double extension = NO_WHEEL_EXTENSION;
    private double lastExtension = this.extension;
    private double chasingYaw;
    private double lastChasingYaw;
    private double lastAngle;
    private double angle;
    private double angularVelocity = 0.0;
    private double touchingFriction = 1.0;

    private float tireSteeringAngle;
    private float previousInputAngleDegrees;
    private float latestInputAngleDegrees;
    private boolean tireBrokenByOversteer;
    private boolean steeringInputBaselineInitialized;

    private boolean liftedUp;
    private final Vector3d queuedForcePos = new Vector3d();
    private final Vector3d queuedForce = new Vector3d();
    private final ForceTotal forceTotal = new ForceTotal();

    public MechaWheelMountBlockEntity(final BlockEntityType<?> type, final BlockPos pos, final BlockState state) {
        super(type, pos, state);
        this.inventory = new MechaWheelMountInventory(this);
        this.steeringInput = new SteeringInputKinetic(type, new ExtraBlockPos(pos), state, this);
    }

    public static void applyAllBatchedForces() {
        for (final MechaWheelMountBlockEntity blockEntity : QUEUED_WHEEL_MOUNTS) {
            if (!blockEntity.isRemoved()) {
                blockEntity.applyBatchedForces();
            }
        }
        QUEUED_WHEEL_MOUNTS.clear();
    }

    @Override
    public void addBehaviours(final List<BlockEntityBehaviour> behaviours) {
        this.strength = new SuspensionStrengthValueBehaviour(SCROLL_OPTION_TITLE, this, new BottomConfigValueBox());
        this.strength.onlyActiveWhen(() -> this.getHeldItem().has(ModDataComponents.TIRE))
                .withClientCallback(i -> this.sendData());
        behaviours.add(this.strength);
        this.strength.value = 10;

        this.inputRatio = new InputRatioValueBehaviour(RATIO_OPTION_TITLE, this, new BottomConfigValueBox());
        this.inputRatio.onlyActiveWhen(() -> this.getHeldItem().has(ModDataComponents.TIRE))
                .withClientCallback(i -> this.sendData());
        behaviours.add(this.inputRatio);
        this.inputRatio.value = (int) DEFAULT_INPUT_TO_TIRE_RATIO;
    }

    @Override
    public ItemRequirement getRequiredItems(final BlockState state) {
        final ItemStack stack = this.inventory.slot.getStack();
        if (stack.isEmpty()) {
            return super.getRequiredItems(state);
        }
        return new ItemRequirement(ItemRequirement.ItemUseType.CONSUME, stack);
    }

    public static double fudgeFriction(final double realValue) {
        if (realValue < 1) {
            return 0.1 + 0.9 * realValue;
        }
        return realValue;
    }

    @Override
    public void sable$physicsTick(final ServerSubLevel subLevel, final RigidBodyHandle handle, final double timeStep) {
        final ItemStack item = this.getHeldItem();
        final TireLike tire = item.get(ModDataComponents.TIRE);
        if (tire == null) {
            return;
        }

        final float radius = tire.radius();
        final double suspensionRestDistance = MAX_ALLOWED_EXTENSION;
        final MassData massData = subLevel.getMassTracker();
        final Direction facing = this.getBlockState().getValue(MechaWheelMountBlock.HORIZONTAL_FACING);
        final Vec3 localPos = this.getBlockPos().relative(facing).getCenter();
        this.queuedForcePos.set(localPos.x, localPos.y, localPos.z);
        final double normalMass = 1.0 / massData.getInverseNormalMass(this.queuedForcePos, OrientedBoundingBox3d.UP);

        final double effectiveStrength = this.strength.getValue();
        final double normalMassScaling = Math.min(normalMass / effectiveStrength, 1.0) * 10.0;
        final double strengthMul = effectiveStrength * normalMassScaling * 2;
        final double springStrength = effectiveStrength * normalMassScaling * 40;
        final double dampingStrength = effectiveStrength * normalMassScaling;

        final Pose3d pose = subLevel.logicalPose();
        final Direction.Axis axis = facing.getAxis();
        Vec3i normal = Direction.get(Direction.AxisDirection.POSITIVE, axis).getNormal();
        final Vector3dc sideD = this.getRotatedWheelAxis(normal);
        normal = new Vec3i(normal.getZ(), 0, normal.getX());
        final Vector3dc normalD = this.getRotatedWheelAxis(normal);

        final TerrainCastResult extensionToTerrain = this.computeMaxExtensionToTerrain(normalD, pose);
        final double maxExtension = extensionToTerrain.maxExtension();
        this.extension = Mth.lerp(1.0, this.extension, maxExtension);

        if (maxExtension > suspensionRestDistance + radius + 0.25) {
            this.extension = suspensionRestDistance;
            return;
        }

        final double distance = (suspensionRestDistance / 6.0) + this.extension;
        final double springLength = Mth.clamp(distance - radius, 0.0, suspensionRestDistance);

        final Vector3d velocity = Sable.HELPER.getVelocity(this.level, JOMLConversion.toJOML(localPos));
        final Vector3d localVelocity = pose.transformNormalInverse(velocity);
        final double dampingForce = -localVelocity.y * dampingStrength;
        final double springForce = ((suspensionRestDistance - springLength) * springStrength + dampingForce) * timeStep;

        final Vec3i rayHitNormal = extensionToTerrain.normal().getNormal();
        Vec3 localForce = new Vec3(springForce * rayHitNormal.getX(), springForce * rayHitNormal.getY(), springForce * rayHitNormal.getZ());
        if (extensionToTerrain.subLevel() != null) {
            localForce = extensionToTerrain.subLevel().logicalPose().transformNormal(localForce);
        }
        localForce = pose.transformNormalInverse(localForce);
        this.queuedForce.set(localForce.x, localForce.y, localForce.z);

        if (extensionToTerrain.minInteractingBlock() != null) {
            this.touchingFriction = fudgeFriction(PhysicsBlockPropertyHelper.getFriction(this.level.getBlockState(extensionToTerrain.minInteractingBlock())));
        } else {
            this.touchingFriction = 1.0;
        }

        final double brakeStrength = this.isBraking() ? 1.0 : 0.0;
        final double surfaceBraking = Math.min(this.touchingFriction, 1.0);
        final double brakingFrictionStrength = (0.075 + brakeStrength * 0.3) * surfaceBraking;
        final float kineticSpeed = facing.getAxis() == Direction.Axis.X ? this.getSpeed() : -this.getSpeed();

        this.queuedForce.fma(
                (localVelocity.dot(normalD) * -brakingFrictionStrength * strengthMul * timeStep)
                        + (kineticSpeed * (1.0 - brakeStrength) * surfaceBraking * 1.75 * timeStep),
                normalD
        );
        this.queuedForce.fma(localVelocity.dot(sideD) * -0.6 * this.touchingFriction * strengthMul * timeStep, sideD);

        this.forceTotal.applyImpulseAtPoint(subLevel, this.queuedForcePos, this.queuedForce);
        QUEUED_WHEEL_MOUNTS.add(this);
    }

    private void applyBatchedForces() {
        final SubLevel subLevel = Sable.HELPER.getContaining(this);
        if (subLevel == null) {
            return;
        }
        final RigidBodyHandle handle = RigidBodyHandle.of((ServerSubLevel) subLevel);
        handle.applyForcesAndReset(this.forceTotal);
    }

    @Override
    public void tick() {
        this.steeringInput.tick();
        super.tick();

        final ItemStack item = this.getHeldItem();
        final TireLike tire = item.get(ModDataComponents.TIRE);
        final float previousSteeringAngle = this.tireSteeringAngle;

        this.updateSteeringFromInput();
        this.lastChasingYaw = this.chasingYaw;
        this.chasingYaw = Mth.lerp(0.4, this.chasingYaw, this.computeYaw());

        if (!this.level.isClientSide) {
            this.updateSteeringSignalCache(true);
            return;
        }

        if (tire == null) {
            this.angle = 0.0;
            this.lastAngle = 0.0;
            this.tireSteeringAngle = 0;
            this.lastExtension = this.extension;
            this.extension = Mth.lerp(0.6, this.extension, NO_WHEEL_EXTENSION);
            return;
        }

        final float radius = tire.radius();
        final SubLevel subLevel = Sable.HELPER.getContaining(this);
        this.lastExtension = this.extension;
        this.extension = Mth.lerp(0.7, this.extension, this.computeMaxExtension(radius));

        final Direction facing = this.getBlockState().getValue(MechaWheelMountBlock.HORIZONTAL_FACING);
        final float speed = facing.getAxis() == Direction.Axis.X ? -this.getSpeed() : this.getSpeed();
        final double brakeFactor = this.isBraking() ? 0.0 : 1.0;
        final double rpt = speed * Math.PI * 2.0 / 60.0 / 20.0 * brakeFactor;
        final double attemptedAngularVelocity = Mth.lerp(0.2, this.angularVelocity, rpt);

        if (subLevel == null || this.liftedUp) {
            this.angularVelocity = attemptedAngularVelocity;
            this.lastAngle = this.angle;
            this.angle += this.angularVelocity;
            return;
        }

        final Vector3d velocity = Sable.HELPER.getVelocity(this.level, JOMLConversion.atCenterOf(this.getBlockPos().relative(facing)));
        final Vector3d localVelocity = subLevel.logicalPose().transformNormalInverse(velocity).div(20.0);
        final Direction.Axis axis = facing.getAxis();
        Vec3i normal = Direction.get(Direction.AxisDirection.POSITIVE, axis).getNormal();
        normal = new Vec3i(normal.getZ(), 0, normal.getX());
        final Vector3dc normalD = this.getRotatedWheelAxis(normal);

        final double translation = localVelocity.dot(normalD);
        final double circumference = Math.PI * radius * 2.0;
        double angularDelta = -translation / circumference * Math.PI * 2.0;
        if (this.touchingFriction < 1.0) {
            angularDelta = Mth.lerp(this.touchingFriction, attemptedAngularVelocity, angularDelta);
        }

        this.lastAngle = this.angle;
        this.angle += angularDelta;
        this.angularVelocity = angularDelta;

        if (Math.abs(previousSteeringAngle - this.tireSteeringAngle) > 1e-4f) {
            this.setChanged();
        }
    }

    private double computeMaxExtension(final float radius) {
        final SubLevel subLevel = Sable.HELPER.getContaining(this);
        if (subLevel == null) {
            return MAX_ALLOWED_EXTENSION;
        }

        final Direction facing = this.getBlockState().getValue(MechaWheelMountBlock.HORIZONTAL_FACING);
        final Pose3dc pose = subLevel.logicalPose();
        final Direction.Axis axis = facing.getAxis();
        Vec3i normal = Direction.get(Direction.AxisDirection.POSITIVE, axis).getNormal();
        normal = new Vec3i(normal.getZ(), 0, normal.getX());
        final Vector3dc rotatedAxis = this.getRotatedWheelAxis(normal);

        final TerrainCastResult extensionToTerrain = this.computeMaxExtensionToTerrain(rotatedAxis, pose);
        final double unclampedExtension = extensionToTerrain.maxExtension - radius;
        this.liftedUp = unclampedExtension > MAX_ALLOWED_EXTENSION;

        if (extensionToTerrain.minInteractingBlock() == null) {
            this.touchingFriction = 1.0;
        } else {
            this.touchingFriction = fudgeFriction(PhysicsBlockPropertyHelper.getFriction(this.level.getBlockState(extensionToTerrain.minInteractingBlock())));
        }

        return Mth.clamp(unclampedExtension, -0.45, MAX_ALLOWED_EXTENSION);
    }

    @Override
    public String getClipboardKey() {
        return "Wheel Mount";
    }

    @Override
    public boolean writeToClipboard(final HolderLookup.@NotNull Provider registries, final CompoundTag tag, final Direction side) {
        return false;
    }

    @Override
    public boolean readFromClipboard(final HolderLookup.@NotNull Provider registries, final CompoundTag tag, final Player player, final Direction side, final boolean simulate) {
        return false;
    }

    private record TerrainCastResult(double maxExtension, @NotNull Direction normal, @Nullable SubLevel subLevel,
                                     @Nullable BlockPos minInteractingBlock) {}

    private TerrainCastResult computeMaxExtensionToTerrain(final Vector3dc normalD, final Pose3dc pose) {
        final Direction facing = this.getBlockState().getValue(MechaWheelMountBlock.HORIZONTAL_FACING);
        final Vec3 wheelPosCenter = this.getBlockPos().relative(facing).getCenter();
        double minExtension = 5.0;
        Direction minNormal = Direction.UP;
        SubLevel minHitSubLevel = null;
        BlockPos minInteractingBlock = null;

        for (int i = -1; i <= 1; i++) {
            final Vec3 localPosO = wheelPosCenter.add(JOMLConversion.toMojang(normalD).scale(i));
            final ClipContext clipContext = new ClipContext(localPosO, localPosO.subtract(0.0, 5.0, 0.0), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, CollisionContext.empty());
            ((ClipContextExtension) clipContext).sable$setIgnoredSubLevel(Sable.HELPER.getContaining(this));
            final BlockHitResult clipResult = this.level.clip(clipContext);

            if (clipResult.getType() == HitResult.Type.MISS) {
                continue;
            }

            final SubLevel hitSubLevel = Sable.HELPER.getContaining(this.level, clipResult.getLocation());
            final Vec3 localHitPos = pose.transformPositionInverse(hitSubLevel == null ? clipResult.getLocation() : hitSubLevel.logicalPose().transformPosition(clipResult.getLocation()));
            if (localHitPos.y > wheelPosCenter.y) {
                continue;
            }
            if (localPosO.distanceTo(localHitPos) < 0.05) {
                continue;
            }

            final double dist = wheelPosCenter.y - localHitPos.y;
            if (dist <= 1e-5) {
                continue;
            }

            final Direction dir = clipResult.getDirection();
            final Vector3d hitNormal = new Vector3d(dir.getStepX(), dir.getStepY(), dir.getStepZ());
            if (hitSubLevel != null) {
                hitSubLevel.logicalPose().transformNormal(hitNormal);
            }
            pose.transformNormalInverse(hitNormal);
            if (hitNormal.dot(0.0, 1.0, 0.0) < 0.5) {
                continue;
            }

            minExtension = Math.min(minExtension, dist);
            minNormal = clipResult.getDirection();
            minHitSubLevel = hitSubLevel;
            minInteractingBlock = clipResult.getBlockPos();
        }

        return new TerrainCastResult(minExtension, minNormal, minHitSubLevel, minInteractingBlock);
    }

    private @NotNull Vector3dc getRotatedWheelAxis(final Vec3i normal) {
        final Vector3d normalD = new Vector3d(normal.getX(), normal.getY(), normal.getZ());
        normalD.rotateY(this.getChasingYaw());
        return normalD;
    }

    protected double getChasingYaw() {
        return this.chasingYaw;
    }

    protected double getLerpedYaw(final double partialTick) {
        return Mth.lerp(partialTick, this.lastChasingYaw, this.chasingYaw);
    }

    public float getLerpedAngle(final float partialTicks) {
        return (float) Mth.lerp(partialTicks, this.lastAngle, this.angle);
    }

    public double getLerpedExtension(final float partialTick) {
        return Mth.lerp(partialTick, this.lastExtension, this.extension);
    }

    protected double computeYaw() {
        return -this.tireSteeringAngle;
    }

    protected int getSteeringSignal() {
        if (this.level != null && this.level.isClientSide) {
            return this.clientSteeringSignal;
        }
        return this.lastServerSteeringSignal;
    }

    public ItemStack getHeldItem() {
        return this.inventory.getItem(0);
    }

    public SteeringInputKinetic getSteeringInput() {
        return this.steeringInput;
    }

    @Override
    protected void write(final CompoundTag tag, final HolderLookup.Provider registries, final boolean clientPacket) {
        tag.put("CurrentStack", this.getHeldItem().saveOptional(registries));
        tag.putFloat("TireSteeringAngle", this.tireSteeringAngle);
        tag.putFloat("InputWheelPrevAngle", this.previousInputAngleDegrees);
        tag.putFloat("InputWheelAngle", this.latestInputAngleDegrees);
        tag.putBoolean("TireBrokenByOversteer", this.tireBrokenByOversteer);

        if (clientPacket) {
            tag.putInt("SteeringSignalStrength", this.lastServerSteeringSignal);
            tag.putInt("SteeringSignalStrengthLeft", this.lastServerSteeringSignalLeft);
            tag.putInt("SteeringSignalStrengthRight", this.lastServerSteeringSignalRight);
        }

        super.write(tag, registries, clientPacket);
    }

    @Override
    protected void read(final CompoundTag tag, final HolderLookup.Provider registries, final boolean clientPacket) {
        final ItemStack stack = ItemStack.parseOptional(registries, tag.getCompound("CurrentStack"));
        this.inventory.suppressUpdate = true;
        this.inventory.slot.setStack(stack);
        this.inventory.suppressUpdate = false;

        if (tag.contains("TireSteeringAngle")) {
            this.tireSteeringAngle = tag.getFloat("TireSteeringAngle");
        }
        if (tag.contains("InputWheelPrevAngle")) {
            this.previousInputAngleDegrees = tag.getFloat("InputWheelPrevAngle");
        }
        if (tag.contains("InputWheelAngle")) {
            this.latestInputAngleDegrees = tag.getFloat("InputWheelAngle");
        }
        this.tireBrokenByOversteer = tag.getBoolean("TireBrokenByOversteer");
        this.steeringInputBaselineInitialized = false;

        if (clientPacket) {
            if (tag.contains("SteeringSignalStrength")) {
                this.clientSteeringSignal = tag.getInt("SteeringSignalStrength");
                this.clientSteeringSignalLeft = tag.getInt("SteeringSignalStrengthLeft");
                this.clientSteeringSignalRight = tag.getInt("SteeringSignalStrengthRight");
            }
            this.onStackChanged();
        }

        super.read(tag, registries, clientPacket);
    }

    public boolean zeroSteeringAngle() {
        if (Math.abs(this.tireSteeringAngle) < 1e-4f) {
            return false;
        }
        this.tireSteeringAngle = 0;
        this.tireBrokenByOversteer = false;
        this.updateSteeringSignalCache(false);
        this.sendData();
        this.setChanged();
        return true;
    }

    public boolean isBraking() {
        if (this.level == null) {
            return false;
        }
        for (final Direction direction : Direction.values()) {
            if (this.level.getSignal(this.worldPosition.relative(direction), direction.getOpposite()) > 0) {
                return true;
            }
        }
        return false;
    }

    private void updateSteeringFromInput() {
        if (this.level == null) {
            return;
        }

        final ItemStack wheel = this.getHeldItem();
        final float currentInput = this.steeringInput.getCurrentAngleDegrees();

        if (!wheel.has(ModDataComponents.TIRE)) {
            this.tireSteeringAngle = 0;
            this.previousInputAngleDegrees = currentInput;
            this.latestInputAngleDegrees = currentInput;
            this.tireBrokenByOversteer = false;
            this.steeringInputBaselineInitialized = true;
            return;
        }

        if (!this.steeringInputBaselineInitialized) {
            this.previousInputAngleDegrees = currentInput;
            this.latestInputAngleDegrees = currentInput;
            this.steeringInputBaselineInitialized = true;
            return;
        }

        final float previousAngle = this.tireSteeringAngle;
        final float deltaInput = currentInput - this.previousInputAngleDegrees;
        this.previousInputAngleDegrees = currentInput;
        this.latestInputAngleDegrees = currentInput;

        final float ratio = Math.max(1f, this.inputRatio != null ? this.inputRatio.getValue() : DEFAULT_INPUT_TO_TIRE_RATIO);
        this.tireSteeringAngle += (float) Math.toRadians(deltaInput / ratio);

        if (Math.abs(this.tireSteeringAngle) > BROKEN_STEER_RADIANS) {
            this.breakAttachedTire();
            return;
        }

        this.tireSteeringAngle = Mth.clamp(this.tireSteeringAngle, -MAX_STEER_RADIANS, MAX_STEER_RADIANS);
        this.tireBrokenByOversteer = false;

        if (!this.level.isClientSide && Math.abs(previousAngle - this.tireSteeringAngle) > 1e-6f) {
            this.updateSteeringSignalCache(false);
            this.sendData();
            this.setChanged();
        }
    }

    private void updateSteeringSignalCache(final boolean sendIfChanged) {
        final int signal = Mth.clamp(Math.round(this.tireSteeringAngle * DEGREES_PER_RADIAN / 3f), -15, 15);
        final int left = Math.max(0, signal);
        final int right = Math.max(0, -signal);
        final boolean changed = signal != this.lastServerSteeringSignal
                || left != this.lastServerSteeringSignalLeft
                || right != this.lastServerSteeringSignalRight;

        this.lastServerSteeringSignal = signal;
        this.lastServerSteeringSignalLeft = left;
        this.lastServerSteeringSignalRight = right;

        if (sendIfChanged && changed) {
            this.sendData();
        }
    }

    private void breakAttachedTire() {
        if (this.level == null || this.level.isClientSide) {
            this.tireSteeringAngle = Mth.clamp(this.tireSteeringAngle, -MAX_STEER_RADIANS, MAX_STEER_RADIANS);
            return;
        }
        if (this.getHeldItem().isEmpty()) {
            this.tireSteeringAngle = 0;
            return;
        }

        final ItemStack dropped = this.getHeldItem().copy();
        this.inventory.slot.setStack(ItemStack.EMPTY);
        final Direction facing = this.getBlockState().getValue(MechaWheelMountBlock.HORIZONTAL_FACING);
        final Vec3 spawn = Vec3.atCenterOf(this.worldPosition.relative(facing));
        final ItemEntity entity = new ItemEntity(this.level, spawn.x, spawn.y, spawn.z, dropped);
        this.level.addFreshEntity(entity);

        this.tireSteeringAngle = 0;
        this.tireBrokenByOversteer = true;
        this.steeringInputBaselineInitialized = false;
        this.updateSteeringSignalCache(false);
        this.sendData();
        this.setChanged();
    }

    @Override
    public void clearContent() {
        this.inventory.clearContent();
    }

    public SingleSlotContainer getInventory() {
        return this.inventory;
    }

    @Override
    public KineticBlockEntity getExtraKinetics() {
        return this.steeringInput;
    }

    @Override
    public boolean shouldConnectExtraKinetics() {
        return false;
    }

    @Override
    public String getExtraKineticsSaveName() {
        return "SteeringInput";
    }

    public void onStackChanged() {
        this.invalidateRenderBoundingBox();
    }

    @Override
    protected AABB createRenderBoundingBox() {
        AABB aabb = new AABB(this.getBlockPos());
        if (this.getHeldItem() != null && this.getHeldItem().has(ModDataComponents.TIRE)) {
            final TireLike tire = this.getHeldItem().getComponents().get(ModDataComponents.TIRE);
            aabb = aabb.inflate(tire.radius() + 1);
        }
        return aabb;
    }

    private static class SuspensionStrengthValueBehaviour extends ScrollValueBehaviour {
        private static final int MAX_SUSPENSION_STRENGTH = 180;

        public SuspensionStrengthValueBehaviour(final Component label, final SmartBlockEntity be, final ValueBoxTransform slot) {
            super(label, be, slot);
            this.between(5, MAX_SUSPENSION_STRENGTH);
        }

        @Override
        public ValueSettingsBoard createBoard(final Player player, final BlockHitResult hitResult) {
            return new ValueSettingsBoard(this.label, MAX_SUSPENSION_STRENGTH, 20, ImmutableList.of(ModLang.translate("scroll_option.suspension_strength_label").component()),
                    new ValueSettingsFormatter(ValueSettings::format));
        }

        @Override
        public boolean mayInteract(final Player player) {
            return player.getMainHandItem().isEmpty();
        }
    }

    private static final class BottomConfigValueBox extends ValueBoxTransform {
        @Override
        public void rotate(final LevelAccessor level, final BlockPos pos, final BlockState state, final PoseStack ms) {
            final Direction facing = state.getValue(RollerBlock.FACING);
            final float yRot = AngleHelper.horizontalAngle(facing) + 180;
            TransformStack.of(ms)
                    .rotateYDegrees(yRot)
                    .rotateXDegrees(270);
        }

        @Override
        public boolean testHit(final LevelAccessor level, final BlockPos pos, final BlockState state, final Vec3 localHit) {
            final Vec3 offset = this.getLocalOffset(level, pos, state);
            if (offset == null) {
                return false;
            }
            return localHit.distanceTo(offset) < this.scale / 3;
        }

        @Override
        public Vec3 getLocalOffset(final LevelAccessor level, final BlockPos pos, final BlockState state) {
            final Direction facing = state.getValue(RollerBlock.FACING);
            final float stateAngle = AngleHelper.horizontalAngle(facing) + 180;
            return VecHelper.rotateCentered(VecHelper.voxelSpace(8, 0.5f, 11), stateAngle, Direction.Axis.Y);
        }
    }

    private static class InputRatioValueBehaviour extends ScrollValueBehaviour {
        private static final int MAX_RATIO = 24;

        public InputRatioValueBehaviour(final Component label, final SmartBlockEntity be, final ValueBoxTransform slot) {
            super(label, be, slot);
            this.between(1, MAX_RATIO);
            this.withFormatter(v -> v + " : 1");
        }

        @Override
        public ValueSettingsBoard createBoard(final Player player, final BlockHitResult hitResult) {
            return new ValueSettingsBoard(this.label, MAX_RATIO, 1, ImmutableList.of(ModLang.translate("scroll_option.input_ratio_label").component()),
                    new ValueSettingsFormatter(ValueSettings::format));
        }

        @Override
        public boolean mayInteract(final Player player) {
            return player.getMainHandItem().is(Tags.Items.TOOLS_WRENCH);
        }
    }

    public static class SteeringInputKinetic extends KineticBlockEntity implements ExtraKinetics.ExtraKineticsBlockEntity {
        public static final IRotate EXTRA_ROTATE_CONFIG = new IRotate() {
            @Override
            public boolean hasShaftTowards(final LevelReader world, final BlockPos pos, final BlockState state, final Direction face) {
                return face == Direction.UP;
            }

            @Override
            public Direction.Axis getRotationAxis(final BlockState state) {
                return Direction.Axis.Y;
            }
        };

        private final KineticBlockEntity parent;
        private float currentAngleDegrees;

        public SteeringInputKinetic(final BlockEntityType<?> type, final ExtraBlockPos pos, final BlockState state, final KineticBlockEntity parent) {
            super(type, pos, state);
            this.parent = parent;
        }

        @Override
        public void tick() {
            super.tick();
            this.currentAngleDegrees += convertToAngular(this.getSpeed());
        }

        public float getCurrentAngleDegrees() {
            return this.currentAngleDegrees;
        }

        @Override
        protected void write(final CompoundTag tag, final HolderLookup.Provider registries, final boolean clientPacket) {
            tag.putFloat("CurrentAngle", this.currentAngleDegrees);
            super.write(tag, registries, clientPacket);
        }

        @Override
        protected void read(final CompoundTag tag, final HolderLookup.Provider registries, final boolean clientPacket) {
            this.currentAngleDegrees = tag.getFloat("CurrentAngle");
            super.read(tag, registries, clientPacket);
        }

        @Override
        public float propagateRotationTo(final KineticBlockEntity target, final BlockState stateFrom, final BlockState stateTo, final BlockPos diff, final boolean connectedViaAxes, final boolean connectedViaCogs) {
            return this.parent.propagateRotationTo(target, stateFrom, stateTo, diff, connectedViaAxes, connectedViaCogs);
        }

        @Override
        public KineticBlockEntity getParentBlockEntity() {
            return this.parent;
        }
    }
}
