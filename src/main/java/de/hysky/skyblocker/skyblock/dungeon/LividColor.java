package de.hysky.skyblocker.skyblock.dungeon;

import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import de.hysky.skyblocker.annotations.Init;
import de.hysky.skyblocker.config.SkyblockerConfigManager;
import de.hysky.skyblocker.config.configs.DungeonsConfig;
import de.hysky.skyblocker.skyblock.dungeon.secrets.DungeonManager;
import de.hysky.skyblocker.utils.Constants;
import de.hysky.skyblocker.utils.Utils;
import de.hysky.skyblocker.utils.render.LevelRenderExtractionCallback;
import de.hysky.skyblocker.utils.render.primitive.PrimitiveCollector;
import de.hysky.skyblocker.utils.scheduler.MessageScheduler;

public class LividColor {
	private static final Map<Block, ChatFormatting> WOOL_TO_FORMATTING = Map.of(
			Blocks.WOOL.red(), ChatFormatting.RED,
			Blocks.WOOL.yellow(), ChatFormatting.YELLOW,
			Blocks.WOOL.lime(), ChatFormatting.GREEN,
			Blocks.WOOL.green(), ChatFormatting.DARK_GREEN,
			Blocks.WOOL.blue(), ChatFormatting.BLUE,
			Blocks.WOOL.magenta(), ChatFormatting.LIGHT_PURPLE,
			Blocks.WOOL.purple(), ChatFormatting.DARK_PURPLE,
			Blocks.WOOL.gray(), ChatFormatting.GRAY,
			Blocks.WOOL.white(), ChatFormatting.WHITE
	);
	private static final Map<String, ChatFormatting> LIVID_TO_FORMATTING = Map.of(
			"Hockey Livid", ChatFormatting.RED,
			"Arcade Livid", ChatFormatting.YELLOW,
			"Smile Livid", ChatFormatting.GREEN,
			"Frog Livid", ChatFormatting.DARK_GREEN,
			"Scream Livid", ChatFormatting.BLUE,
			"Crossed Livid", ChatFormatting.LIGHT_PURPLE,
			"Purple Livid", ChatFormatting.DARK_PURPLE,
			"Doctor Livid", ChatFormatting.GRAY,
			"Vendetta Livid", ChatFormatting.WHITE
	);
	private static final Supplier<DungeonsConfig.Livid> CONFIG = () -> SkyblockerConfigManager.get().dungeons.livid;
	private static ChatFormatting color = ChatFormatting.AQUA;
	private static Block lastColor = Blocks.AIR;

	private static boolean isInitialized = false;
	/**
	 * The correct livid may change color in M5, so we use the entity id to track the correct original livid.
	 */
	private static boolean correctLividIdFound = false;
	private static int correctLividId = 0;
	private static final long OFFSET_DURATION = 2000;
	private static long toggleTime = 0;

	@Init
	public static void init() {
		ClientPlayConnectionEvents.JOIN.register((_, _, _) -> LividColor.reset());
		LevelRenderExtractionCallback.EVENT.register(LividColor::update);
	}

	private static void update(PrimitiveCollector collector) {
		DungeonsConfig.Livid config = SkyblockerConfigManager.get().dungeons.livid;
		if (!(config.enableLividColorText || config.enableLividColorTitle || config.enableLividColorGlow || config.enableLividColorBoundingBox)) return;

		Minecraft client = Minecraft.getInstance();

		if (!(Utils.isInDungeons() && DungeonManager.isInBoss() && client.player != null && client.level != null)) return;

		Block currentColor = client.level.getBlockState(new BlockPos(5, 110, 42)).getBlock();
		if (!(WOOL_TO_FORMATTING.containsKey(currentColor) && !currentColor.equals(lastColor))) return;

		if (!isInitialized && client.player.hasEffect(MobEffects.BLINDNESS)) {
			toggleTime = System.currentTimeMillis();
			isInitialized = true;
		} else if (isInitialized && System.currentTimeMillis() - toggleTime >= OFFSET_DURATION) {
			onLividColorFound(client, currentColor);
			if (!correctLividIdFound) {
				String lividName = LIVID_TO_FORMATTING.entrySet().stream()
						.filter(entry -> entry.getValue() == color)
						.map(Map.Entry::getKey)
						.findFirst()
						.orElse("unknown");
				client.level.players().stream()
						.filter(entity -> entity.getName().getString().equals(lividName))
						.findFirst()
						.ifPresent(entity -> correctLividId = entity.getId());
				correctLividIdFound = true;
			}
			lastColor = currentColor;
		}

	}

	private static void onLividColorFound(Minecraft client, Block color) {
		LividColor.color = WOOL_TO_FORMATTING.get(color);
		String colorString = BuiltInRegistries.BLOCK.getKey(color).getPath();
		colorString = colorString.substring(0, colorString.length() - 5).toUpperCase(Locale.ENGLISH);
		Component message = Component.literal(CONFIG.get().lividColorText.replaceAll("\\[color]", colorString)).withStyle(LividColor.color);
		if (CONFIG.get().enableLividColorText) {
			MessageScheduler.INSTANCE.sendMessageAfterCooldown("/pc " + Constants.PREFIX.get().append(message).getString(), true);
		}
		if (CONFIG.get().enableLividColorTitle) {
			client.gui.hud.resetTitleTimes();
			client.gui.hud.setTitle(message);
		}
	}

	public static boolean allowGlow() {
		return !SkyblockerConfigManager.get().dungeons.livid.enableLividColorGlow || !DungeonManager.getBoss().isFloor(5);
	}

	public static boolean shouldGlow(String name) {
		return SkyblockerConfigManager.get().dungeons.livid.enableLividColorGlow && color == LIVID_TO_FORMATTING.get(name);
	}

	public static boolean shouldDrawBoundingBox(String name) {
		return SkyblockerConfigManager.get().dungeons.livid.enableLividColorBoundingBox && color == LIVID_TO_FORMATTING.get(name);
	}

	/**
	 * @return true when the given player is one of the fake Livids and not the correct one, so its render can be
	 *         skipped entirely. Only hides once the correct color is known so the identification phase stays visible.
	 */
	public static boolean shouldHideLivid(Player player) {
		if (!CONFIG.get().hideIncorrectLivids || !correctLividIdFound) return false;
		if (!(Utils.isInDungeons() && DungeonManager.isInBoss() && DungeonManager.getBoss().isFloor(5))) return false;
		return LIVID_TO_FORMATTING.containsKey(player.getName().getString()) && player.getId() != correctLividId;
	}

	/**
	 * @return true when the given projectile was shot by one of the fake Livids, so the screen isn't flooded with
	 *         their arrows. Arrows from real players, the correct livid, or with an unresolvable owner stay visible.
	 */
	public static boolean shouldHideLividArrow(Projectile projectile) {
		if (!CONFIG.get().hideIncorrectLivids) return false;
		if (!(Utils.isInDungeons() && DungeonManager.isInBoss() && DungeonManager.getBoss().isFloor(5))) return false;
		return projectile.getOwner() instanceof Player player && shouldHideLivid(player);
	}

	@SuppressWarnings("DataFlowIssue")
	public static int getGlowColor(String name) {
		if (SkyblockerConfigManager.get().dungeons.livid.enableSolidColor) return SkyblockerConfigManager.get().dungeons.livid.customColor.getRGB();
		if (LIVID_TO_FORMATTING.containsKey(name)) return TextColor.fromLegacyFormat(LIVID_TO_FORMATTING.get(name)).getValue();
		return TextColor.WHITE.getValue();
	}

	public static int getCorrectLividId() {
		return correctLividId;
	}

	private static void reset() {
		lastColor = Blocks.AIR;
		toggleTime = 0;
		isInitialized = false;
		correctLividIdFound = false;
		correctLividId = 0;
		color = ChatFormatting.AQUA;
	}
}
