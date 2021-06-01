package space.essem.image2map;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.net.URLConnection;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import me.sargunvohra.mcmods.autoconfig1u.AutoConfig;
import me.sargunvohra.mcmods.autoconfig1u.serializer.GsonConfigSerializer;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v1.CommandRegistrationCallback;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtHelper;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.LiteralText;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import space.essem.image2map.config.Image2MapConfig;
import space.essem.image2map.renderer.DitherMode;
import space.essem.image2map.renderer.MapRenderer;

import static com.mojang.brigadier.arguments.IntegerArgumentType.getInteger;
import static com.mojang.brigadier.arguments.IntegerArgumentType.integer;
import static com.mojang.brigadier.arguments.StringArgumentType.greedyString;
import static net.minecraft.server.command.CommandManager.argument;
import static space.essem.image2map.renderer.MapRenderer.convertToBufferedImage;

public class Image2Map implements ModInitializer {
	public static Image2MapConfig CONFIG = AutoConfig.register(Image2MapConfig.class, GsonConfigSerializer::new).getConfig();
	public static Logger LOGGER = LogManager.getLogger("I2M");

	@Override
	public void onInitialize () {
		LOGGER.info("Loading Image2Map...");

		CommandRegistrationCallback.EVENT.register(
				(dispatcher, dedicated) -> dispatcher.register(
						CommandManager.literal(
								"mapcreate"
						).requires(
								source -> source.hasPermissionLevel(
										CONFIG.minPermLevel
								)
						).then(
								argument(
										"mode",
										new DitherArdumentType()
								)/*.suggests(
										new DitherModeSuggestionProvider()
								)*/.then(
										argument(
												"path",
												greedyString()
										).executes(
												this::createMap1_1
										)
								)
						)
								.then(
										argument(
												"width",
												integer(1)
										).then(
												argument(
														"height",
														integer(1)
												).then(
														argument(
																"mode",
																new DitherArdumentType()
														)/*.suggests(
														new DitherModeSuggestionProvider()
												)*/.then(
																argument(
																		"path",
																		greedyString()
																).executes(
																		context -> this.createMap(
																				context,
																				getInteger(context, "width"),
																				getInteger(context, "height")
																		)
																)
														)
												)
										)
								)
				)
		);
	}

//	static class DitherModeSuggestionProvider implements SuggestionProvider<ServerCommandSource> {
//
//		@Override
//		public CompletableFuture<Suggestions> getSuggestions (
//				CommandContext<ServerCommandSource> context,
//				SuggestionsBuilder builder
//		) throws CommandSyntaxException {
//			builder.suggest("none");
//			builder.suggest("dither");
//			return builder.buildFuture();
//		}
//
//	}

	static class DitherArdumentType implements ArgumentType<DitherMode> {

		@Override public DitherMode parse (StringReader reader) throws CommandSyntaxException {
			DitherMode mode = DitherMode.fromString(reader.readString());
			if (mode == null){
				throw new SimpleCommandExceptionType(Text.of("invalid dither mode string")).create();
			}
			return mode;
		}

		@Override public <S> CompletableFuture<Suggestions> listSuggestions (CommandContext<S> context, SuggestionsBuilder builder) {
			for (DitherMode mode : DitherMode.values()) { builder.suggest(mode.name()); }
			return builder.buildFuture();
		}

	}

//	public enum DitherMode {
//		NONE,
//		FLOYD;
//
//		public static DitherMode fromString (String string) throws CommandSyntaxException {
//			if (string.equalsIgnoreCase("NONE")) {
//				return DitherMode.NONE;
//			} else if (string.equalsIgnoreCase("DITHER") || string.equalsIgnoreCase("FLOYD")) { return DitherMode.FLOYD; }
//			throw new SimpleCommandExceptionType(Text.of("invalid dither mode")).create();
//		}
//	}

	private int createMap1_1 (CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
		return createMap(context, 1, 1);
	}

	private int createMap (CommandContext<ServerCommandSource> context, int width, int height) throws CommandSyntaxException {
		ServerCommandSource source = context.getSource();
		Vec3d pos = source.getPosition();
		PlayerEntity player = source.getPlayer();
		DitherMode mode = context.getArgument("mode", DitherMode.class);
		String input = StringArgumentType.getString(context, "path");

		source.sendFeedback(new LiteralText("Generating image map..."), false);
		BufferedImage image;
		try {
			if (isValid(input)) {
				URL url = new URL(input);
				URLConnection connection = url.openConnection();
				connection.setRequestProperty("User-Agent", "Image2Map mod");
				connection.connect();
				image = ImageIO.read(connection.getInputStream());
			} else if (CONFIG.allowLocalFiles) {
				File file = new File(input);
				image = ImageIO.read(file);
			} else {
				image = null;
			}
		} catch (IOException e) {
			source.sendFeedback(new LiteralText("That doesn't seem to be a valid image."), false);
			return 0;
		}

		if (image == null) {
			source.sendFeedback(new LiteralText("That doesn't seem to be a valid image."), false);
			return 0;
		}

		Image scaledInstance = image.getScaledInstance(128 * width, 128 * height, Image.SCALE_SMOOTH);
		BufferedImage scaledImage = convertToBufferedImage(scaledInstance);

		List<ItemStack> maps = MapRenderer.render(
				scaledImage,
				mode,
				width,
				height,
				source.getWorld(),
				pos.x,
				pos.z,
				player
		);
		if (maps.size() < 9 * 4) {
			for (ItemStack stack : maps) {
				if (!player.inventory.insertStack(stack)) {
					ItemEntity itemEntity = new ItemEntity(
							player.world,
							player.getPos().x,
							player.getPos().y,
							player.getPos().z,
							stack
					);
					player.world.spawnEntity(itemEntity);
				}
			}
		} else {
			for(int i = 0, count = MathHelper.ceil(maps.size() / 27f); i < count; i ++) {
				ItemStack shulker = Items.LIME_SHULKER_BOX.getDefaultStack();
				shulker.setCustomName(Text.of("Shulker №" + (i + 1)));
				CompoundTag data = shulker.getOrCreateTag();
				ListTag items = new ListTag();
				for (int j = i * 27, stop = Math.min((i + 1)*27, maps.size()); j < stop; j ++){
					CompoundTag compoundTag = maps.get(j).toTag(new CompoundTag());
					compoundTag.putInt("Slot", 27 - (stop - j));
					items.add(compoundTag);
				}
				CompoundTag tag = new CompoundTag();
				tag.put("Items", items);
				data.put("BlockEntityTag", tag);
				if (!player.inventory.insertStack(shulker)) {
					ItemEntity itemEntity = new ItemEntity(
							player.world,
							player.getPos().x,
							player.getPos().y,
							player.getPos().z,
							shulker
					);
					player.world.spawnEntity(itemEntity);
				}
			}
		}
		source.sendFeedback(new LiteralText("Done!"), false);

		return 1;
	}


	private static boolean isValid (String url) {
		try {
			new URL(url).toURI();
			return true;
		} catch (Exception e) {
			return false;
		}
	}

}
