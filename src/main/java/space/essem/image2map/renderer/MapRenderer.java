package space.essem.image2map.renderer;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

import net.minecraft.block.MaterialColor;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.FilledMapItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.map.MapState;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import static space.essem.image2map.Image2Map.LOGGER;
import static space.essem.image2map.renderer.DitherMode.mapColors;
import static space.essem.image2map.renderer.DitherMode.nearestColor;

public class MapRenderer {

	public static List<ItemStack> render (
			BufferedImage image,
			DitherMode mode,
			int w,
			int h,
			ServerWorld world,
			double x,
			double z,
			PlayerEntity player // mapcreate FLOYD file:///home/tent/middleman/img.png
	) {
		List<ItemStack> stacks = new ArrayList<>();
		player.sendMessage(Text.of("Applying selected dithering pattern"), false);
		BufferedImage ditheredImage = mode.dither.apply(
				mapColors,
				image
		);
		player.sendMessage(Text.of("Remaping map to pallet"), false);
		Byte[] bytes = remapToPallet(mapColors, ditheredImage).toArray(new Byte[0]);
		byte[] colors = new byte[bytes.length];
		for (int i = 0, bytesLength = bytes.length; i < bytesLength; i++) {
			byte b = bytes[i];
			colors[i] = b;
		}
		for (int j = 0; j < h; j++) {
			for (int i = 0; i < w; i++) {
				LOGGER.info("x: {}, y: {}", i, j);
				player.sendMessage(Text.of(String.format("Splitting map x: %d, y: %d, of %d×%d (%d/%d)", i, j, h, w,
				                                         j * w + i, w * h)), false);
				ItemStack stack = FilledMapItem.createMap(world, (int) x + i, (int) z + i, (byte) 0, false, false);
				stack.setCustomName(Text.of("map [" + (i + 1) + "×" + (j + 1) + "] " + mode.name()).copy().formatted(Formatting.GREEN));
				MapState state = FilledMapItem.getMapState(stack, world);
				state.locked = true;
				for (int row = 0; row < 128; row ++){
					System.arraycopy(colors, (j * w * 128 + i + row * w) * 128, state.colors, row * 128, 128);
				}
				stacks.add(stack);
			}
		}
		return stacks;
	}

	static List<Byte> remapToPallet (List<MaterialColor> materials, BufferedImage image) {
		List<Byte> dithered = new ArrayList<>();
		for (int y = 0; y < image.getHeight(); y++) {
			for (int x = 0; x < image.getWidth(); x++) {
				dithered.add((byte) nearestColor(materials, new Color(image.getRGB(x, y))));
			}
		}
		return dithered;
	}

	public static BufferedImage convertToBufferedImage (Image image) {
		BufferedImage newImage = new BufferedImage(image.getWidth(null), image.getHeight(null),
		                                           BufferedImage.TYPE_4BYTE_ABGR);
		Graphics2D g = newImage.createGraphics();
		g.drawImage(image, 0, 0, null);
		g.dispose();
		return newImage;
	}

}