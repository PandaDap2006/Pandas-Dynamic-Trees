package me.pandamods.fallingtrees.event;

import dev.architectury.event.EventResult;
import dev.architectury.event.events.common.BlockEvent;
import dev.architectury.utils.value.IntValue;
import me.pandamods.fallingtrees.api.TreeData;
import me.pandamods.fallingtrees.api.TreeDataBuilder;
import me.pandamods.fallingtrees.api.TreeRegistry;
import me.pandamods.fallingtrees.api.Tree;
import me.pandamods.fallingtrees.compat.Compat;
import me.pandamods.fallingtrees.compat.TreeChopCompat;
import me.pandamods.fallingtrees.config.CommonConfig;
import me.pandamods.fallingtrees.config.FallingTreesConfig;
import me.pandamods.fallingtrees.entity.TreeEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Optional;
import java.util.Set;

public class EventHandler {
	public static void register() {
		BlockEvent.BREAK.register(EventHandler::onBlockBreak);
	}

	private static EventResult onBlockBreak(Level level, BlockPos blockPos, BlockState blockState, ServerPlayer serverPlayer, IntValue intValue) {
		if (serverPlayer != null && !TreeChopCompat.isChoppable(level, blockPos) && makeTreeFall(blockPos, level, serverPlayer)) {
			return EventResult.interruptFalse();
		}
		return EventResult.pass();
	}

	public static boolean makeTreeFall(BlockPos blockPos, LevelAccessor level, Player player) {
		if (level.isClientSide()) return false;
		Optional<Tree> treeTypeOptional = TreeRegistry.getTree(level.getBlockState(blockPos));
		return treeTypeOptional.filter(treeType -> makeTreeFall(treeType, blockPos, level, player)).isPresent();
	}

	public static boolean makeTreeFall(Tree tree, BlockPos blockPos, LevelAccessor level, Player player) {
		if (level.isClientSide()) return false;
		BlockState blockState = level.getBlockState(blockPos);
		CommonConfig commonConfig = FallingTreesConfig.getCommonConfig();

		if (!tree.willTreeFall(blockPos, level, player)) return false;
		ItemStack mainItem = player.getItemBySlot(EquipmentSlot.MAINHAND);

		TreeData treeData = tree.getTreeData(new TreeDataBuilder(), blockPos, level);
		Set<BlockPos> treeBlockPos = treeData.blocks();
		if (!treeData.shouldFall()) return false;

		if (!Compat.hasTreeChop() || !(level instanceof Level && TreeChopCompat.isChoppable((Level) level, blockPos))) {
			if (!mainItem.isEmpty()) {
				mainItem.hurtAndBreak(commonConfig.disableExtraToolDamage ? 1 : treeData.toolDamage(), player,
						entity -> entity.broadcastBreakEvent(EquipmentSlot.MAINHAND));
			}
			float defaultExhaustion = 0.005f;
			player.causeFoodExhaustion(commonConfig.disableExtraFoodExhaustion ? defaultExhaustion : defaultExhaustion * treeData.foodExhaustionMultiply());
		}
		player.awardStat(Stats.BLOCK_MINED.get(blockState.getBlock()), treeData.awardedBlocks());

		TreeEntity.destroyTree(treeBlockPos, blockPos, level, tree, player);
		return true;
	}
}
