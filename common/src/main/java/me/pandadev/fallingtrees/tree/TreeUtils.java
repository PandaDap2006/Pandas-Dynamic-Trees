package me.pandadev.fallingtrees.tree;

import me.pandadev.fallingtrees.FallingTrees;
import me.pandadev.fallingtrees.config.ClientConfig;
import me.pandadev.fallingtrees.entity.TreeEntity;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.core.BlockPos;
import net.minecraft.stats.Stats;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Math;
import org.joml.Vector3d;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TreeUtils {

	@Environment(EnvType.CLIENT)
	public static boolean isMiningOneBlock(Player player) {
		if (FallingTrees.getClientConfig().one_block_mining_method.equals(ClientConfig.OneBlockMiningEnum.CROUCH)) {
			return player.isCrouching();
		}
		return FallingTrees.getClientConfig().is_mining_one_block;
	}

	public static void breakTree(Player player, Level level, BlockPos pos) {
		TreeCache cache = TreeCache.getOrCreateCache("tree_breaking", pos, level, player);
		if (cache != null) {
			List<BlockPos> tree = cache.blocks();
			Map<BlockPos, BlockState> treeBlocks = new HashMap<>();
			for (BlockPos treePos : tree) {
				BlockState state = level.getBlockState(treePos);
				treeBlocks.put(treePos.subtract(pos), state);
			}

			if (cache.treeType().extraBlockRequirement(treeBlocks, level)) {
				TreeEntity treeEntity = new TreeEntity(FallingTrees.TREE_ENTITY.get(), level).setBlocks(treeBlocks);
				Vector3d position = new Vector3d(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
				treeEntity.setPos(position.x, position.y, position.z);
				ItemStack usedItem = player.getMainHandItem();
				treeEntity.usedItem = usedItem;

				treeEntity.setRotationY((float) Math.atan2(player.getX() - position.x, player.getZ() - position.z));
				level.addFreshEntity(treeEntity);
//				if (FallingTrees.serverConfig.sound_effect)
//					level.playSound(null, pos, FallingTrees.TREE_FALL.get(), SoundSource.BLOCKS,
//							0.25f*FallingTrees.serverConfig.sound_effect_volume, 1);

				int LogAmount = cache.getLogAmount();
				if (usedItem.isDamageableItem()) {
					usedItem.hurtAndBreak((int) (LogAmount * FallingTrees.getServerConfig().item_damage_multiplier), player, player1 -> {});
				}

				player.causeFoodExhaustion(Math.min(0.005F * LogAmount * FallingTrees.getServerConfig().food_exhaustion_multiplier,
						FallingTrees.getServerConfig().food_exhaustion_limit));

				for (BlockPos blockPos: tree) {
					BlockState state = level.getBlockState(blockPos);
					player.awardStat(Stats.BLOCK_MINED.get(state.getBlock()));
					level.setBlock(blockPos, Blocks.AIR.defaultBlockState(), 0);
				}
				for (Map.Entry<BlockPos, BlockState> entry : treeBlocks.entrySet()) {
					level.sendBlockUpdated(entry.getKey().offset(pos), entry.getValue(), Blocks.AIR.defaultBlockState(), 3);
				}
			}
		}
	}

	@Environment(EnvType.CLIENT)
	public static boolean shouldTreeFall(Player player) {
		if (FallingTrees.getServerConfig().tree_limit.only_fall_on_tool_use) {
			return player.getMainHandItem().getItem() instanceof AxeItem && !(FallingTrees.getServerConfig().allow_one_block_mining && TreeUtils.isMiningOneBlock(player));
		}
		return !(FallingTrees.getServerConfig().allow_one_block_mining && TreeUtils.isMiningOneBlock(player));
	}
}
