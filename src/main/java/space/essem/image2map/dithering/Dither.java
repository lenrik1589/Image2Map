package space.essem.image2map.dithering;

import java.awt.image.BufferedImage;
import java.util.List;

import net.minecraft.block.MaterialColor;

public interface Dither {
	public BufferedImage dither(List<MaterialColor> meterials, BufferedImage image);
}
