package space.essem.image2map.dithering;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import net.fabricmc.fabric.impl.client.indigo.renderer.helper.ColorHelper;
import net.minecraft.block.MaterialColor;

import static space.essem.image2map.renderer.DitherMode.applyError;
import static space.essem.image2map.renderer.DitherMode.nearestColor;

public class ErrorPropogatingDither implements Dither {
	private final List<List<Double>> numerators;
	private final Integer denominator;
	private final Integer offsetX;

	private ErrorPropogatingDither (List<List<Integer>> numerators, Integer denominator, int x) {
		this.numerators = numerators.stream().map(
				cl -> cl.stream().map(
						v -> (double) v
				).collect(Collectors.toList())
		).collect(Collectors.toList());
		this.denominator = denominator;
		offsetX = x;
	}

	public static final ErrorPropogatingDither FloydDither = new ErrorPropogatingDither(
			Arrays.asList(
					Arrays.asList(7),
					Arrays.asList(3, 5, 1)
			),
			16,
			1
	);

	public static final ErrorPropogatingDither StuckiDither = new ErrorPropogatingDither(
			Arrays.asList(
					Arrays.asList(8, 4),
					Arrays.asList(2, 4, 8, 4, 2),
					Arrays.asList(1, 2, 4, 2, 1)
			),
			42,
			2
	);

	public static final ErrorPropogatingDither SierraLiteDither = new ErrorPropogatingDither(
			Arrays.asList(
					Arrays.asList(2),
					Arrays.asList(1, 1, 0)
			),
			4,
			1
	);

	public static final ErrorPropogatingDither BurkesDither = new ErrorPropogatingDither(
			Arrays.asList(
					Arrays.asList(8, 4),
					Arrays.asList(2, 4, 8, 4, 2)
			),
			32,
			2
	);

	public BufferedImage dither (List<MaterialColor> materials, BufferedImage image) { // /mapcreate 2 3 OtherFloyd file:///home/tent/middleman/2.jpg
		try {
			for (int y = 0; y < image.getHeight(); y++) {
				for (int x = 0; x < image.getWidth(); x++) {
					int oldRgb = image.getRGB(x, y);
					int val = nearestColor(materials, new Color(oldRgb));
					int newRgb = ColorHelper.swapRedBlueIfNeeded(
							materials.get(val >> 2).getRenderColor(val & 3)
					);
					image.setRGB(x, y, newRgb);
					int errRgb = ((128 + (oldRgb >> 16 & 0xff) - (newRgb >> 16 & 0xff)) << 16) |
					             ((128 + (oldRgb >> 8 & 0xff) - (newRgb >> 8 & 0xff)) << 8) |
					             (128 + (oldRgb & 0xff) - (newRgb & 0xff)) |
					             0xff000000;
					for (int i = 0, j = Math.min(numerators.get(0).size(), image.getWidth() - x - numerators.get(0).size()); i < j; i++) {
						int pixelColor = image.getRGB(x + 1 + i, y);
						image.setRGB(x + 1 + i, y, applyError(pixelColor, errRgb, numerators.get(0).get(i) / denominator));
					}
					for (int j = 1, mj = Math.min(numerators.size(), image.getHeight() - y - numerators.size()); j < mj; j++) {
						for (
								int i = Math.max(0, offsetX - x),
								mi = Math.min(numerators.get(j).size(), image.getWidth() + offsetX - x - numerators.get(j).size());
								i < mi;
								i++
						) {
							int pixelColor = image.getRGB(x + i - offsetX, y + j);
							image.setRGB(
									x + i - offsetX, y + j,
									applyError(
											pixelColor,
											errRgb,
											numerators.get(j).get(i) / denominator
									)
							);
						}
					}
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return image;
	}

}
