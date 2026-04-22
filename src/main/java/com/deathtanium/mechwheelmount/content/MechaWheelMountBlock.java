package com.deathtanium.mechwheelmount.content;

import com.simibubi.create.api.schematic.requirement.SpecialBlockItemRequirement;
import com.simibubi.create.content.equipment.wrench.IWrenchable;
import com.simibubi.create.content.kinetics.base.HorizontalKineticBlock;
import com.simibubi.create.content.kinetics.base.IRotate;
import com.simibubi.create.content.schematics.requirement.ItemRequirement;
import com.simibubi.create.foundation.block.IBE;
import dev.simulated_team.simulated.multiloader.inventory.ContainerSlot;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class MechaWheelMountBlock extends HorizontalKineticBlock implements IBE<MechaWheelMountBlockEntity>, SpecialBlockItemRequirement, ExtraKinetics.ExtraKineticsBlock {

    public MechaWheelMountBlock(final Properties properties) {
        super(properties);
    }

    @Override
    protected void createBlockStateDefinition(final StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
    }

    @Override
    public void onRemove(final BlockState state, final Level level, final BlockPos pos, final BlockState newState, final boolean isMoving) {
        if (state.hasBlockEntity() && state.getBlock() != newState.getBlock()) {
            final MechaWheelMountBlockEntity be = (MechaWheelMountBlockEntity) level.getBlockEntity(pos);

            if (be != null && !be.getHeldItem().isEmpty()) {
                final Direction facing = state.getValue(MechaWheelMountBlock.HORIZONTAL_FACING);

                BlockPos dropPos = pos;

                if (facing != null) {
                    dropPos = dropPos.relative(facing);
                }

                Containers.dropItemStack(level, dropPos.getX(), dropPos.getY(), dropPos.getZ(), be.getHeldItem());
            }

            level.removeBlockEntity(pos);
        }
    }

    @Override
    public boolean hasShaftTowards(final LevelReader world, final BlockPos pos, final BlockState state, final Direction face) {
        return face == Direction.UP || face == state.getValue(HORIZONTAL_FACING).getOpposite();
    }

    @Override
    public Direction.Axis getRotationAxis(final BlockState state) {
        return state.getValue(HORIZONTAL_FACING).getAxis();
    }

    @Override
    public BlockState getStateForPlacement(final BlockPlaceContext context) {
        final Direction preferred = this.getPreferredHorizontalFacing(context);
        final boolean crouching = context.getPlayer() != null && context.getPlayer()
                .isShiftKeyDown();
        if (preferred == null || crouching) {
            final Direction horizontalDirection = context.getHorizontalDirection();
            return this.defaultBlockState().setValue(HORIZONTAL_FACING, crouching ? horizontalDirection : horizontalDirection.getOpposite());
        }
        return this.defaultBlockState().setValue(HORIZONTAL_FACING, preferred.getOpposite());

    }

    @Override
    public InteractionResult onWrenched(final BlockState state, final UseOnContext context) {
        final Level level = context.getLevel();
        final BlockPos pos = context.getClickedPos();
        final Direction clicked = context.getClickedFace();

        if (clicked == state.getValue(HORIZONTAL_FACING) && context.getPlayer() != null) {
            this.withBlockEntityDo(level, pos, mount -> {
                if (mount.zeroSteeringAngle()) {
                    level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_HIT, SoundSource.BLOCKS, 0.6f, 1.3f);
                }
            });
            return InteractionResult.sidedSuccess(level.isClientSide);
        }

        return super.onWrenched(state, context);
    }

    @Override
    protected ItemInteractionResult useItemOn(final ItemStack heldItem, final BlockState blockState, final Level level, final BlockPos blockPos, final Player player, final InteractionHand interactionHand, final BlockHitResult blockHitResult) {
        final Direction hitDirection = blockHitResult.getDirection();

        if(!hitDirection.equals(blockState.getValue(HORIZONTAL_FACING)) && hitDirection != Direction.DOWN) {
            return super.useItemOn(heldItem, blockState, level, blockPos, player, interactionHand, blockHitResult);
        }

        if (level.isClientSide()) {
            return this.onBlockEntityUseItemOn(level, blockPos, mount -> {
                final ItemStack potentialTire = mount.getHeldItem();
                if ((heldItem.isEmpty() && potentialTire.has(ModDataComponents.TIRE))
                        || (heldItem.has(ModDataComponents.TIRE) && potentialTire.has(ModDataComponents.TIRE))
                        || (heldItem.has(ModDataComponents.TIRE) && potentialTire.isEmpty())
                ) {
                    return ItemInteractionResult.SUCCESS;
                }

                return super.useItemOn(heldItem, blockState, level, blockPos, player, interactionHand, blockHitResult);
            });
        }

        if (this.switchStacks(level, blockPos, player, interactionHand)) {
            return ItemInteractionResult.CONSUME;
        }

        return super.useItemOn(heldItem, blockState, level, blockPos, player, interactionHand, blockHitResult);
    }

    private boolean switchStacks(final Level level, final BlockPos pos, final Player player, final InteractionHand hand) {
        final boolean[] passed = { false };

        final ItemStack heldItem = player.getItemInHand(hand);
        final TireLike tireLike = heldItem.get(ModDataComponents.TIRE);

        if (heldItem.isEmpty() || tireLike != null) {
            this.withBlockEntityDo(level, pos, mount -> {
                final ContainerSlot slot = mount.getInventory().slot;
                final ItemStack save = slot.getStack().copy();
                final ItemStack oldSlotItem = save.copy();

                slot.setStack(heldItem.copyWithCount(1));
                if (!player.hasInfiniteMaterials()) {
                    heldItem.shrink(1);
                }
                player.getInventory().placeItemBackInInventory(save);

                final ItemStack newSlotItem = slot.getStack();
                mount.setChanged();
                mount.sendData();
                passed[0] = true;

                final float pitch = 0.8f + level.random.nextFloat() * 0.4f;
                final float volume = .75f;

                if (oldSlotItem.isEmpty() && !newSlotItem.isEmpty()) {
                    // tire inserted
                    level.playSound(null, pos, SoundEvents.ITEM_FRAME_ADD_ITEM, SoundSource.PLAYERS, volume, pitch);
                } else if (!oldSlotItem.isEmpty() && newSlotItem.isEmpty()) {
                    // tire picked up
                    level.playSound(null, pos, SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, volume, pitch);
                } else if (!oldSlotItem.isEmpty()) {
                    // tire changed
                    level.playSound(null, pos, SoundEvents.ITEM_FRAME_ADD_ITEM, SoundSource.PLAYERS, volume, pitch);
                }
            });

        }

        return passed[0];
    }

    @Override
    public VoxelShape getShape(final BlockState pState, final BlockGetter pLevel, final BlockPos pPos, final CollisionContext ctx) {
        return Shapes.block();
    }

    @Override
    public Class<MechaWheelMountBlockEntity> getBlockEntityClass() {
        return MechaWheelMountBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends MechaWheelMountBlockEntity> getBlockEntityType() {
        return ModBlockEntities.STEERABLE_WHEEL_MOUNT.get();
    }

    @Override
    public ItemRequirement getRequiredItems(final BlockState state, @Nullable final BlockEntity blockEntity) {
        final ItemStack mountStack = ModBlocks.STEERABLE_WHEEL_MOUNT.asStack();
        if (blockEntity instanceof final MechaWheelMountBlockEntity wmbe) {
            final ItemStack heldItem = wmbe.getHeldItem();
            if (!heldItem.isEmpty()) {
                return new ItemRequirement(List.of(
                        new ItemRequirement.StackRequirement(mountStack, ItemRequirement.ItemUseType.CONSUME),
                        new ItemRequirement.StrictNbtStackRequirement(heldItem, ItemRequirement.ItemUseType.CONSUME)
                ));
            }
        }
        return new ItemRequirement(ItemRequirement.ItemUseType.CONSUME, mountStack);
    }

    @Override
    public IRotate getExtraKineticsRotationConfiguration() {
        return MechaWheelMountBlockEntity.SteeringInputKinetic.EXTRA_ROTATE_CONFIG;
    }

}