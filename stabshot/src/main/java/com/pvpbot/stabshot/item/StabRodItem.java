package com.pvpbot.stabshot.item;

import com.pvpbot.stabshot.logic.StabLogic;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.item.FishingRodItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;

import java.util.List;

/**
 * StabRodItem — a FishingRod subclass that fires a stab shot instead of casting.
 *
 * Identity check: the item has a custom NBT tag STAB_ROD_KEY = true so that:
 *  - Renaming with an anvil does NOT produce a functional rod (the key is gone).
 *  - Only items given via /giveob or the bot integration carry the tag.
 *  - isStabRod(stack) checks for this tag — used by PvPBot's integration.
 */
public class StabRodItem extends FishingRodItem {

    /** NBT key that marks a stack as a legitimate stab rod. */
    public static final String STAB_ROD_KEY = "StabShotRod";

    public StabRodItem(Settings settings) {
        super(settings);
    }

    /**
     * On right-click: raycast to find the block the player is looking at,
     * then fire the stab column at that position.
     */
    @Override
    public TypedActionResult<ItemStack> use(World world, net.minecraft.entity.player.PlayerEntity playerEntity, Hand hand) {
        ItemStack stack = playerEntity.getStackInHand(hand);

        // Validate the item is a legitimate stab rod
        if (!isStabRod(stack)) {
            return TypedActionResult.pass(stack);
        }

        if (world.isClient()) {
            return TypedActionResult.success(stack);
        }

        ServerPlayerEntity player = (ServerPlayerEntity) playerEntity;
        ServerWorld serverWorld   = (ServerWorld) world;

        // Raycast from eye position to find aimed block (max 64 blocks, no fluids)
        var eyePos = player.getEyePos();
        var lookVec = player.getRotationVec(1.0f);
        var endVec  = eyePos.add(lookVec.multiply(64.0));

        BlockHitResult hit = serverWorld.raycast(new RaycastContext(
                eyePos, endVec,
                RaycastContext.ShapeType.OUTLINE,
                RaycastContext.FluidHandling.NONE,
                player
        ));

        if (hit.getType() == HitResult.Type.MISS) {
            player.sendMessage(Text.literal("§cNo block in range (max 64 blocks)."), true);
            return TypedActionResult.fail(stack);
        }

        // Fire at the aimed block position
        BlockPos target = hit.getBlockPos();
        StabLogic.summonStab(serverWorld, target.getX(), target.getY(), target.getZ());

        // Consume one use (durability) — item is unbreakable by default but
        // if the player has a finite-use rod from /giveob <amount> we count down
        consumeUse(stack, player);

        return TypedActionResult.success(stack);
    }

    /**
     * Consume one use from the item's custom use counter.
     * If the counter hits 0, remove the item from the player's hand.
     * If uses == -1 (infinite), do nothing.
     */
    private void consumeUse(ItemStack stack, ServerPlayerEntity player) {
        NbtComponent customData = stack.get(DataComponentTypes.CUSTOM_DATA);
        if (customData == null) return;

        NbtCompound nbt = customData.copyNbt();
        int uses = nbt.getInt("UsesLeft");
        if (uses <= 0) return; // -1 = infinite, 0 = already expired

        uses--;
        nbt.putInt("UsesLeft", uses);
        stack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(nbt));

        if (uses == 0) {
            // Remove from hand
            player.setStackInHand(Hand.MAIN_HAND, ItemStack.EMPTY);
        } else {
            // Update lore to show remaining uses
            updateLore(stack, uses);
        }
    }

    /** Updates the lore line that shows remaining uses. */
    private static void updateLore(ItemStack stack, int uses) {
        stack.set(DataComponentTypes.LORE,
                new net.minecraft.component.type.LoreComponent(List.of(
                        Text.literal("§7Aim at a block and right-click to fire."),
                        Text.literal("§6Uses remaining: §f" + uses)
                )));
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        tooltip.add(Text.literal("§7Aim at a block and right-click to fire.").formatted(Formatting.GRAY));
        tooltip.add(Text.literal("§cObtainable via command only.").formatted(Formatting.DARK_RED));
    }

    // -------------------------------------------------------------------------
    // Static factory & helpers
    // -------------------------------------------------------------------------

    /**
     * Creates a stab rod ItemStack with the proper NBT marker, custom name,
     * and use count.
     *
     * @param uses Number of uses. Use -1 for infinite (no counter shown).
     */
    public static ItemStack createStack(StabRodItem item, int uses) {
        ItemStack stack = new ItemStack(item);

        // Custom name — cannot be renamed away via anvil since the NBT key survives
        stack.set(DataComponentTypes.CUSTOM_NAME,
                Text.literal("§6§lStab Shot Rod").styled(s -> s.withItalic(false)));

        // Mark as legitimate stab rod + store use count
        NbtCompound nbt = new NbtCompound();
        nbt.putBoolean(STAB_ROD_KEY, true);
        nbt.putInt("UsesLeft", uses);
        stack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(nbt));

        // Lore
        if (uses > 0) {
            stack.set(DataComponentTypes.LORE,
                    new net.minecraft.component.type.LoreComponent(List.of(
                            Text.literal("§7Aim at a block and right-click to fire."),
                            Text.literal("§6Uses remaining: §f" + uses)
                    )));
        } else {
            stack.set(DataComponentTypes.LORE,
                    new net.minecraft.component.type.LoreComponent(List.of(
                            Text.literal("§7Aim at a block and right-click to fire."),
                            Text.literal("§a§lInfinite uses")
                    )));
        }

        // Make unbreakable so durability never runs out
        stack.set(DataComponentTypes.UNBREAKABLE,
                new net.minecraft.component.type.UnbreakableComponent(false));

        return stack;
    }

    /**
     * Returns true if this ItemStack is a legitimate stab rod
     * (has the STAB_ROD_KEY NBT marker).
     *
     * Used by PvPBot's integration to skip fishing rod checks.
     */
    public static boolean isStabRod(ItemStack stack) {
        if (stack.isEmpty()) return false;
        NbtComponent customData = stack.get(DataComponentTypes.CUSTOM_DATA);
        if (customData == null) return false;
        return customData.copyNbt().getBoolean(STAB_ROD_KEY);
    }
}
