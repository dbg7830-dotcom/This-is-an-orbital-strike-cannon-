package com.pvpbot.stabshot.item;

import com.pvpbot.stabshot.logic.StabLogic;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.component.type.UnbreakableComponent;
import net.minecraft.item.FishingRodItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
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
 * StabRodItem — extends FishingRodItem so it renders as a vanilla fishing rod.
 *
 * Texture fix: we use Items.FISHING_ROD's model via the ITEM_MODEL component
 * so no custom texture asset is needed — it looks exactly like a fishing rod.
 *
 * Use count fix: /giveob 3 stab gives 3 SEPARATE ItemStacks each with 1 use.
 * Each stack breaks (is removed) after one fire.
 *
 * Identity: custom NBT tag StabShotRod=true. Renaming via anvil loses the tag
 * so the renamed rod won't function.
 */
public class StabRodItem extends FishingRodItem {

    public static final String STAB_ROD_KEY = "StabShotRod";

    public StabRodItem(Settings settings) {
        super(settings);
    }

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

        // Raycast to aimed block — max 64 blocks
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
            player.sendMessage(Text.literal("§cNo block in range (max 64 blocks)."), true);
            return TypedActionResult.fail(stack);
        }

        BlockPos target = hit.getBlockPos();
        StabLogic.summonStab(serverWorld, target.getX(), target.getY(), target.getZ());

        // Each stab rod has exactly 1 use — consume it now
        consumeRod(stack, player, hand);

        return TypedActionResult.success(stack);
    }

    /**
     * Consumes this rod after one use.
     *  - Infinite rod (UsesLeft == -1): do nothing.
     *  - Finite rod (UsesLeft >= 1): remove the stack entirely.
     *    The player gets /giveob 3 stab → 3 separate rods, each consumed after 1 use.
     */
    private void consumeRod(ItemStack stack, ServerPlayerEntity player, Hand hand) {
        NbtComponent customData = stack.get(DataComponentTypes.CUSTOM_DATA);
        if (customData == null) return;

        int uses = customData.copyNbt().getInt("UsesLeft");
        if (uses == -1) return; // infinite — don't consume

        // Finite rod — one use per rod, remove it
        player.setStackInHand(hand, ItemStack.EMPTY);
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context,
                               List<Text> tooltip, TooltipType type) {
        NbtComponent data = stack.get(DataComponentTypes.CUSTOM_DATA);
        if (data != null) {
            int uses = data.copyNbt().getInt("UsesLeft");
            if (uses == -1) {
                tooltip.add(Text.literal("§a§lInfinite uses").formatted(Formatting.GREEN));
            } else {
                tooltip.add(Text.literal("§61 use per rod").formatted(Formatting.GOLD));
            }
        }
        tooltip.add(Text.literal("§7Aim at a block and right-click to fire.").formatted(Formatting.GRAY));
        tooltip.add(Text.literal("§cCommand-only item.").formatted(Formatting.DARK_RED));
    }

    // =========================================================================
    // Factory — creates a stab rod stack that LOOKS like a vanilla fishing rod
    // =========================================================================

    /**
     * Creates a stab rod ItemStack.
     *
     * Uses Items.FISHING_ROD as the model reference via ITEM_MODEL component
     * so no custom texture is needed — renders as a normal fishing rod in-hand
     * and in inventory.
     *
     * @param item The registered StabRodItem instance.
     * @param uses -1 = infinite. Any positive number = finite (1 use per rod,
     *             caller must create `uses` separate stacks for finite use).
     */
    public static ItemStack createStack(StabRodItem item, int uses) {
        ItemStack stack = new ItemStack(item);

        // Point the model to vanilla fishing_rod — fixes purple texture completely.
        // ITEM_MODEL component overrides which model JSON the game uses for this stack.
        stack.set(DataComponentTypes.ITEM_MODEL,
                new net.minecraft.util.Identifier("minecraft", "fishing_rod"));

        // Custom name
        stack.set(DataComponentTypes.CUSTOM_NAME,
                Text.literal("§6§lStab Shot Rod").styled(s -> s.withItalic(false)));

        // NBT marker + use count
        NbtCompound nbt = new NbtCompound();
        nbt.putBoolean(STAB_ROD_KEY, true);
        nbt.putInt("UsesLeft", uses);
        stack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(nbt));

        // Lore
        List<Text> lore = uses == -1
                ? List.of(Text.literal("§a§lInfinite uses"),
                          Text.literal("§7Aim at a block and right-click to fire."))
                : List.of(Text.literal("§6§l 1 use per rod — use it wisely."),
                          Text.literal("§c§l Aim at a block and right-click to fire."));
        stack.set(DataComponentTypes.LORE, new LoreComponent(lore));

        // Unbreakable — no durability bar shown
        stack.set(DataComponentTypes.UNBREAKABLE, new UnbreakableComponent(false));

        return stack;
    }

    public static boolean isStabRod(ItemStack stack) {
        if (stack.isEmpty()) return false;
        NbtComponent data = stack.get(DataComponentTypes.CUSTOM_DATA);
        if (data == null) return false;
        return data.copyNbt().getBoolean(STAB_ROD_KEY);
    }
}
