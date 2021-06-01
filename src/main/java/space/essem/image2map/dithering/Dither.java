package space.essem.image2map.dithering;

import java.awt.image.BufferedImage;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import net.minecraft.block.MaterialColor;

public interface Dither {
	List<MaterialColor> defaultColors = Arrays.asList(MaterialColor.COLORS).stream().filter(Objects::nonNull).collect(Collectors.toList());
	BufferedImage dither (List<MaterialColor> materials, BufferedImage image);
}
