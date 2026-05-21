package com.pvpbot.stabshot.item;

import com.pvpbot.stabshot.logic.StabLogic;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.component.type.UnbreakableComponent;
import net.minecraft.entity.EquipmentSlot;
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
 * StabRodItem â€” a fishing rod that fires a stab column on right-click.
 *
 * Rendering: uses stab_rod.json model pointing to minecraft:item/fishing_rod.
 *            Looks exactly like a vanilla fishing rod. No custom texture needed.
 *
 * Finite rods (/giveob 3 stab):
 *   - maxDamage = 1 so they have 1 durability point.
 *   - On use: stack.damage(1, player, hand) triggers vanilla break animation + sound.
 *   - NOT enchantable â€” isEnchantable() returns false, isEnchantable tag false.
 *   - No Unbreakable component.
 *
 * Infinite rods (/giveob infinite stab):
 *   - UnbreakableComponent(false) = unbreakable, no tooltip shown.
 *   - UsesLeft = -1 in custom NBT.
 *
 * Identity: STAB_ROD_KEY NBT tag. Renaming via anvil loses the tag = rod won't fire.
 */
public class StabRodItem extends FishingRodItem {

    public static final String STAB_ROD_KEY = "StabShotRod";

    public StabRodItem(Settings settings) {
        super(settings);
    }

    // -------------------------------------------------------------------------
    // Enchant prevention â€” no mending, no flame, nothing
    // -------------------------------------------------------------------------

    @Override
    public boolean isEnchantable(ItemStack stack) {
        return false;
    }

    // -------------------------------------------------------------------------
    // Use â€” raycast to block, fire stab, break rod
    // -------------------------------------------------------------------------

    @Override
    public TypedActionResult<ItemStack> use(World world,
                                             net.minecraft.entity.player.PlayerEntity playerEntity,
                                             Hand hand) {
        ItemStack stack = playerEntity.getStackInHand(hand);

        if (!isStabRod(stack)) {
            return TypedActionResult.pass(stack);
        }

        if (world.isClient()) {
            return TypedActionResult.success(stack);
        }

        ServerPlayerEntity player = (ServerPlayerEntity) playerEntity;
        ServerWorld serverWorld   = (ServerWorld) world;

        // Raycast to aimed block â€” max 64 blocks
        var eyePos  = player.getEyePos();
        var lookVec = player.getRotationVec(1.0f);
        var endVec  = eyePos.add(lookVec.multiply(64.0));

        BlockHitResult hit = serverWorld.raycast(new RaycastContext(
                eyePos, endVec,
                RaycastContext.ShapeType.OUTLINE,
                RaycastContext.FluidHandling.NONE,
                player
        ));

        if (hit.getType() == HitResult.Type.MISS) {
            player.sendMessage(Text.literal("Â§cNo block in range (max 64 blocks)."), true);
            return TypedActionResult.fail(stack);
        }

        BlockPos target = hit.getBlockPos();
        StabLogic.summonStab(serverWorld, target.getX(), target.getY(), target.getZ());

        // Consume rod
        consumeRod(stack, player, hand);

        return TypedActionResult.success(stack);
    }

    /**
     * Finite rod: damage by 1 â€” triggers vanilla break animation, sound, particles.
     * Infinite rod (UsesLeft == -1): do nothing.
     */
    private void consumeRod(ItemStack stack, ServerPlayerEntity player, Hand hand) {
        NbtComponent data = stack.get(DataComponentTypes.CUSTOM_DATA);
        if (data == null) return;

        int uses = data.copyNbt().getInt("UsesLeft");
        if (uses == -1) return; // infinite â€” never breaks

        // Use EquipmentSlot mapped from Hand â€” this is the safe 1.21.1 signature
        EquipmentSlot slot = (hand == Hand.MAIN_HAND)
                ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND;
        stack.damage(1, player, slot);
    }

    // -------------------------------------------------------------------------
    // Tooltip
    // -------------------------------------------------------------------------

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context,
                               List<Text> tooltip, TooltipType type) {
        NbtComponent data = stack.get(DataComponentTypes.CUSTOM_DATA);
        if (data != null) {
            int uses = data.copyNbt().getInt("UsesLeft");
            if (uses == -1) {
                tooltip.add(Text.literal("Â§aÂ§lInfinite uses").formatted(Formatting.GREEN));
            } else {
                tooltip.add(Text.literal("Â§61 use â€” breaks on fire").formatted(Formatting.GOLD));
            }
        }
        tooltip.add(Text.literal("Â§7Aim at a block, right-click to fire.").formatted(Formatting.GRAY));
        tooltip.add(Text.literal("Â§cCommand-only. Cannot be enchanted.").formatted(Formatting.DARK_RED));
    }

    // -------------------------------------------------------------------------
    // Factory
    // -------------------------------------------------------------------------

    /**
     * @param item The registered StabRodItem.
     * @param uses -1 = infinite (unbreakable). 1 = single use, breaks on fire.
     */
    public static ItemStack createStack(StabRodItem item, int uses) {
        ItemStack stack = new ItemStack(item);

        // Custom name â€” italic false so it doesn't look like a renamed item
        stack.set(DataComponentTypes.CUSTOM_NAME,
                Text.literal("Â§6Â§lStab Shot Rod").styled(s -> s.withItalic(false)));

        // NBT marker + use type
        NbtCompound nbt = new NbtCompound();
        nbt.putBoolean(STAB_ROD_KEY, true);
        nbt.putInt("UsesLeft", uses);
        stack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(nbt));

        // Lore
        List<Text> lore = (uses == -1)
                ? List.of(
                    Text.literal("Â§aÂ§lInfinite uses"),
                    Text.literal("Â§7Aim at a block and right-click to fire."),
                    Text.literal("Â§cCannot be enchanted."))
                : List.of(
                    Text.literal("Â§61 use â€” breaks on fire"),
                    Text.literal("Â§7Aim at a block and right-click to fire."),
                    Text.literal("Â§cCannot be enchanted."));
        stack.set(DataComponentTypes.LORE, new LoreComponent(lore));

        // Infinite rod = unbreakable (hidden tooltip). Finite rod = breakable (1 durability).
        if (uses == -1) {
            stack.set(DataComponentTypes.UNBREAKABLE, new UnbreakableComponent(false));
        }

        return stack;
    }

    public static boolean isStabRod(ItemStack stack) {
        if (stack.isEmpty()) return false;
        NbtComponent data = stack.get(DataComponentTypes.CUSTOM_DATA);
        if (data == null) return false;
        return data.copyNbt().getBoolean(STAB_ROD_KEY);
    }
}
