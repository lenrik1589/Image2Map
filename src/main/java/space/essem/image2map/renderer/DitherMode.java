package space.essem.image2map.renderer;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.IndexColorModel;
import java.util.List;
import java.util.*;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

import net.fabricmc.fabric.impl.client.indigo.renderer.helper.ColorHelper;
import net.minecraft.block.MaterialColor;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import space.essem.image2map.dithering.ErrorPropogatingDither;

public enum DitherMode {
	None(DitherMode::NoDither),
	Java(DitherMode::JavaDither),
	Floyd(ErrorPropogatingDither.FloydDither::dither),
	Stucki(ErrorPropogatingDither.StuckiDither::dither),
	Burkes(ErrorPropogatingDither.BurkesDither::dither),
	SierraLite(ErrorPropogatingDither.SierraLiteDither::dither),
	;

	public static List<MaterialColor> mapColors;
	private static final IndexColorModel colorModel;

	static {
		mapColors = Arrays.asList(MaterialColor.COLORS);
		mapColors = mapColors.stream().filter(Objects::nonNull).collect(Collectors.toList());
		List<Integer> colors = new ArrayList<>();
		mapColors.forEach(color -> {
			colors.add(ColorHelper.swapRedBlueIfNeeded(color.getRenderColor(0)));
			colors.add(ColorHelper.swapRedBlueIfNeeded(color.getRenderColor(1)));
			colors.add(ColorHelper.swapRedBlueIfNeeded(color.getRenderColor(2)));
			colors.add(ColorHelper.swapRedBlueIfNeeded(color.getRenderColor(3)));
		});
		byte[] r = new byte[colors.size()];
		byte[] g = new byte[colors.size()];
		byte[] b = new byte[colors.size()];
		for (int i = 0, colorsSize = colors.size(); i < colorsSize; i++) {
			Integer integer = colors.get(i);
			r[i] = (byte) ((integer >> 16) & 0xff);
			g[i] = (byte) ((integer >> 8) & 0xff);
			b[i] = (byte) (integer & 0xff);
		}
		colorModel = new IndexColorModel(
				8,
				colors.size(),
				r,
				g,
				b
		);
	}

	BiFunction<List<MaterialColor>, BufferedImage, BufferedImage> dither;

	DitherMode (BiFunction<List<MaterialColor>, BufferedImage, BufferedImage> ditherFunc) {
		dither = ditherFunc;
	}

	static BufferedImage JavaDither (List<MaterialColor> materials, BufferedImage image) {
		BufferedImage dithered = new BufferedImage(
				image.getWidth(),
				image.getHeight(),
				BufferedImage.TYPE_BYTE_INDEXED,
				colorModel
		);
		Graphics2D graphics = dithered.createGraphics();
		graphics.setRenderingHint(RenderingHints.KEY_DITHERING, RenderingHints.VALUE_DITHER_ENABLE);
		graphics.drawImage(image, 0, 0, null);
		graphics.dispose();
		return NoDither(materials, dithered);
	}

	public static int applyError (int orig, int err, double quant) {
		int pR = MathHelper.clamp((orig >> 16 & 0xff) + (int) ((double)((err >> 16 & 0xff) - 128) * quant), 0, 255);
		int pG = MathHelper.clamp((orig >> 8 & 0xff) + (int) ((double)((err >> 8 & 0xff) - 128) * quant), 0, 255);
		int pB = MathHelper.clamp((orig & 0xff) + (int) ((double)((err & 0xff) - 128) * quant), 0, 255);
		return 0xff000000 | pR << 16 | pG << 8 | pB;
	}

	static BufferedImage NoDither (List<MaterialColor> materials, BufferedImage image) {
		return image;
	}


	public static int nearestColor (List<MaterialColor> colors, Color imageColor) {
		Vec3d imageVec = new Vec3d(
				imageColor.getRed() / 255.,
				imageColor.getGreen() / 255.,
				imageColor.getBlue() / 255.
		);
		int best_color = 0;
		double lowest_distance = 10;
		for (int k = 0; k < colors.size(); k++) {
			for (int shadeInd = 0; shadeInd < 4; shadeInd++) {

				Color mcColor = new Color(ColorHelper.swapRedBlueIfNeeded(colors.get(k).getRenderColor(shadeInd)));
				double distance = imageVec.squaredDistanceTo(
						mcColor.getRed() / 255.,
						mcColor.getGreen() / 255.,
						mcColor.getBlue() / 255.
				);
				if (distance < lowest_distance) {
					lowest_distance = distance;
					// todo: handle shading with alpha values other than 255
					if (k == 0 && imageColor.getAlpha() == 255) {
						best_color = 119;
					} else {
						best_color = k * 4 + shadeInd;
					}
				}
			}
		}
		return best_color;
	}

	public static DitherMode fromString (String readString) {
		for (DitherMode check : values()) {
			if (check.name().compareToIgnoreCase(readString) == 0) {
				return check;
			}
		}
		return switch (readString.toLowerCase(Locale.ROOT)) {
			case "no", "near" -> None;
			case "dither", "floyd-steinberg" -> Floyd;
			default -> null;
		};
	}
}
