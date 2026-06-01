package com.pvpbot.stabshot.item;

import com.pvpbot.stabshot.logic.StabLogic;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.component.type.UnbreakableComponent;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.FishingRodItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Style;
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

public class StabRodItem extends FishingRodItem {

    public static final String STAB_ROD_KEY = "StabShotRod";

    public StabRodItem(Settings settings) {
        super(settings);
    }

    @Override
    public boolean isEnchantable(ItemStack stack) {
        return false;
    }

    @Override
    public TypedActionResult<ItemStack> use(World world,
                                             net.minecraft.entity.player.PlayerEntity playerEntity,
                                             Hand hand) {
        ItemStack stack = playerEntity.getStackInHand(hand);

        if (!isStabRod(stack)) return TypedActionResult.pass(stack);
        if (world.isClient()) return TypedActionResult.success(stack);

        ServerPlayerEntity player = (ServerPlayerEntity) playerEntity;
        ServerWorld serverWorld   = (ServerWorld) world;

        var eyePos  = player.getEyePos();
        var lookVec = player.getRotationVec(1.0f);
        var endVec  = eyePos.add(lookVec.multiply(64.0));

        BlockHitResult hit = serverWorld.raycast(new RaycastContext(
                eyePos, endVec,
                RaycastContext.ShapeType.OUTLINE,
                RaycastContext.FluidHandling.NONE,
                player));

        if (hit.getType() == HitResult.Type.MISS) {
            player.sendMessage(
                Text.literal("No block in range (max 64 blocks).")
                    .formatted(Formatting.RED),
                true);
            return TypedActionResult.fail(stack);
        }

        BlockPos target = hit.getBlockPos();
        StabLogic.summonStab(serverWorld, target.getX(), target.getY(), target.getZ());
        consumeRod(stack, player, hand);

        return TypedActionResult.success(stack);
    }

    private void consumeRod(ItemStack stack, ServerPlayerEntity player, Hand hand) {
        NbtComponent data = stack.get(DataComponentTypes.CUSTOM_DATA);
        if (data == null) return;
        if (data.copyNbt().getInt("UsesLeft") == -1) return;
        EquipmentSlot slot = (hand == Hand.MAIN_HAND)
                ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND;
        stack.damage(1, player, slot);
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context,
                               List<Text> tooltip, TooltipType type) {
        NbtComponent data = stack.get(DataComponentTypes.CUSTOM_DATA);
        if (data != null) {
            int uses = data.copyNbt().getInt("UsesLeft");
            if (uses == -1) {
                tooltip.add(Text.literal("Infinite Uses")
                    .setStyle(Style.EMPTY.withColor(Formatting.GREEN).withItalic(false)));
            } else {
                tooltip.add(Text.literal("Single Use")
                    .setStyle(Style.EMPTY.withColor(Formatting.GOLD).withItalic(false)));
            }
        }
        tooltip.add(Text.literal("Aim at a block and right-click to fire.")
            .setStyle(Style.EMPTY.withColor(Formatting.GRAY).withItalic(false)));
        tooltip.add(Text.literal("Cannot be enchanted.")
            .setStyle(Style.EMPTY.withColor(Formatting.DARK_RED).withItalic(false)));
    }

    public static ItemStack createStack(StabRodItem item, int uses) {
        ItemStack stack = new ItemStack(item);

        // Clean name â€” no encoding artifacts
        Text name = (uses == -1)
            ? Text.literal("StabShot")
                  .setStyle(Style.EMPTY.withColor(Formatting.DARK_PURPLE).withBold(true).withItalic(false))
            : Text.literal("Stab Shot")
                  .setStyle(Style.EMPTY.withColor(Formatting.RED).withBold(true).withItalic(false));

        stack.set(DataComponentTypes.CUSTOM_NAME, name);

        // NBT marker
        NbtCompound nbt = new NbtCompound();
        nbt.putBoolean(STAB_ROD_KEY, true);
        nbt.putInt("UsesLeft", uses);
        stack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(nbt));

        // Lore â€” no raw section signs, uses Formatting enum only
        List<Text> lore = (uses == -1)
            ? List.of(
                Text.literal("He Who Calls Fire From The Heavens")
                    .setStyle(Style.EMPTY.withColor(Formatting.DARK_PURPLE).withBold(true).withItalic(false))
                Text.literal("Will Learn That Nothing built")
                    .setStyle(Style.EMPTY.withColor(Formatting.RED).withBold(true).withItalic(false))
                Text.literal("Can Forever Last.")
                    .setStyle(Style.EMPTY.withColor(Formatting.DARK_RED).withBold(true).withItalic(false))
            : List.of(
                Text.literal("He Who Calls Fire From The Heavens")
                    .setStyle(Style.EMPTY.withColor(Formatting.DARK_PURPLE).withBold(true).withItalic(false))
                Text.literal("Will Learn that")
                    .setStyle(Style.EMPTY.withColor(Formatting.RED).withBold(true).withItalic(false))
                Text.literal("Nothing Built Can Forever Last.")
                    .setStyle(Style.EMPTY.withColor(Formatting.DARK_RED).withBold(true).withItalic(false))

        stack.set(DataComponentTypes.LORE, new LoreComponent(lore));

        if (uses == -1) {
            stack.set(DataComponentTypes.UNBREAKABLE, new UnbreakableComponent(false));
        }

        return stack;
    }

    public static boolean isStabRod(ItemStack stack) {
        if (stack.isEmpty()) return false;
        NbtComponent data = stack.get(DataComponentTypes.CUSTOM_DATA);
        return data != null && data.copyNbt().getBoolean(STAB_ROD_KEY);
    }
    }
