package net.shrimpworks.unreal.archive.indexer;

import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.channels.Channels;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import javax.imageio.ImageIO;

import net.shrimpworks.unreal.packages.Package;
import net.shrimpworks.unreal.packages.PackageReader;
import net.shrimpworks.unreal.packages.entities.ExportedObject;
import net.shrimpworks.unreal.packages.entities.Import;
import net.shrimpworks.unreal.packages.entities.Named;
import net.shrimpworks.unreal.packages.entities.ObjectReference;
import net.shrimpworks.unreal.packages.entities.objects.Object;
import net.shrimpworks.unreal.packages.entities.objects.Texture;
import net.shrimpworks.unreal.packages.entities.properties.ObjectProperty;
import net.shrimpworks.unreal.packages.entities.properties.Property;

public class IndexUtils {

	/**
	 * Extract preview images/screenshots from a map package.
	 * <p>
	 * It tries hard.
	 *
	 * @param incoming   the package being indexed
	 * @param map        the map package
	 * @param screenshot screenshot property collected from the map
	 * @return list of images read from the map
	 */
	public static List<BufferedImage> screenshots(Incoming incoming, Package map, Property screenshot) {
		List<BufferedImage> images = new ArrayList<>();
		if (screenshot != null) {
			ObjectReference shotRef = ((ObjectProperty)screenshot).value;
			Named shotResolved = shotRef.get();

			Package shotPackage = map;

			try {
				Object object = null;

				if (shotResolved instanceof Import) {
					// sigh... its stored in another package
					Named pkg = ((Import)shotResolved).packageName.get();
					try {
						String parentPkg = pkg instanceof Import ? ((Import)pkg).packageName.get().name().name : "None";
						shotPackage = findPackage(incoming, parentPkg.equals("None") ? pkg.name().name : parentPkg);
						ExportedObject exp = shotPackage.objectByName(((Import)shotResolved).name);
						object = exp.object();
					} catch (Exception e) {
						// oh well, no screenshots
					}
				} else {
					ExportedObject exp = map.objectByRef(shotRef);
					object = exp.object();
				}

				if (object != null) {
					// get a texture form a UT2003/4 material sequence (they cycle several images in the map preview)
					if (object.className().equals("MaterialSequence")) {
						Property fallbackMaterial = object.property("FallbackMaterial");
						if (fallbackMaterial != null) {
							ExportedObject fallback = shotPackage.objectByRef(((ObjectProperty)fallbackMaterial).value);
							Object fallbackObj = fallback.object();
							if (fallbackObj instanceof Texture) {
								object = fallbackObj;
							}
						} else {
							// just find some textures that look like screenshots
							Collection<ExportedObject> textures = shotPackage.objectsByClassName("Texture");
							for (ExportedObject texture : textures) {
								if (texture.name.name.toLowerCase().contains("shot")
									|| texture.name.name.toLowerCase().contains("screen")
									|| texture.name.name.toLowerCase().contains("preview")) {
									object = texture.object();
									break;
								}
							}

							// still not found anything... look for a texture with typical preview dimensions (512x256)
							if (!(object instanceof Texture)) {
								for (ExportedObject texture : textures) {
									Texture tex = (Texture)texture.object();
									Texture.MipMap mip = tex.mipMaps()[0];
									if (mip.width == 512 && mip.height == 256) {
										object = texture.object();
										break;
									}
								}
							}
						}
					}

					if (object instanceof Texture) {
						Texture.MipMap[] mipMaps = ((Texture)object).mipMaps();
						BufferedImage bufferedImage = mipMaps[0].get();
						images.add(bufferedImage);
					}
				}
			} catch (Exception e) {
				incoming.log.log(IndexLog.EntryType.CONTINUE, "Failed to read screenshot from packages", e);
			} finally {
				// cleanup if we spun up an external package for screenshots
				if (shotPackage != map) {
					try {
						shotPackage.close();
					} catch (IOException e) {
						incoming.log.log(IndexLog.EntryType.INFO, "Screenshot cleanup failed", e);
					}
				}
			}
		}

		return images;
	}

	/**
	 * Write out a collection of {@link BufferedImage}s as files and collect
	 * them as content attachments.
	 *
	 * @param shotTemplate template for filenames, should contain %s and %d
	 * @param content      the content
	 * @param screenshots  images to save
	 * @param attachments  attachment collection to populate
	 * @throws IOException failed to write files
	 */
	public static void saveImages(
			String shotTemplate, Content content, List<BufferedImage> screenshots, Set<IndexResult.NewAttachment> attachments)
			throws IOException {
		for (int i = 0; i < screenshots.size(); i++) {
			String shotName = String.format(shotTemplate, content.name.replaceAll(" ", "_"), attachments.size() + 1);
			Path out = Paths.get(shotName);
			ImageIO.write(screenshots.get(i), "png", out.toFile());
			attachments.add(new IndexResult.NewAttachment(Content.AttachmentType.IMAGE, shotName, out));
		}
	}

	/**
	 * Search for a package within the indexed content.
	 *
	 * @param incoming content being indexed
	 * @param pkg      package to find
	 * @return a package
	 */
	private static Package findPackage(Incoming incoming, String pkg) {
		Set<Incoming.IncomingFile> files = incoming.files(Incoming.FileType.IMPORTANT);
		for (Incoming.IncomingFile f : files) {
			String name = f.fileName();
			name = name.substring(0, name.lastIndexOf("."));
			if (name.equalsIgnoreCase(pkg)) {
				return new Package(new PackageReader(f.asChannel()));
			}
		}
		throw new IllegalStateException("Failed to find package " + pkg);
	}

	/**
	 * Find and return all image files within content being indexed.
	 *
	 * @param incoming content being indexed
	 * @return found images
	 */
	public static List<BufferedImage> findImageFiles(Incoming incoming) {
		List<BufferedImage> images = new ArrayList<>();
		Set<Incoming.IncomingFile> files = incoming.files(Incoming.FileType.IMAGE);
		for (Incoming.IncomingFile img : files) {
			try {
				BufferedImage image = ImageIO.read(Channels.newInputStream(Objects.requireNonNull(img.asChannel())));
				if (image != null) images.add(image);
			} catch (Exception e) {
				incoming.log.log(IndexLog.EntryType.CONTINUE, "Failed to load image from archive", e);
			}
		}

		return images;
	}

	/**
	 * Read and return all text content within content being indexed.
	 *
	 * @param incoming content being indexed
	 * @return all lines from plain text content
	 * @throws IOException failed to read files
	 */
	public static List<String> textContent(Incoming incoming) throws IOException {
		List<String> lines = new ArrayList<>();
		for (Incoming.IncomingFile f : incoming.files(Incoming.FileType.TEXT, Incoming.FileType.HTML)) {
			try (BufferedReader br = new BufferedReader(Channels.newReader(f.asChannel(), StandardCharsets.UTF_8.name()))) {
				lines.addAll(br.lines().collect(Collectors.toList()));
			} catch (UncheckedIOException e) {
				incoming.log.log(IndexLog.EntryType.INFO, "Could not read file as UTF-8, trying ISO-8859-1", e);
				try (BufferedReader br = new BufferedReader(Channels.newReader(f.asChannel(), StandardCharsets.ISO_8859_1.name()))) {
					lines.addAll(br.lines().collect(Collectors.toList()));
				} catch (UncheckedIOException ex) {
					incoming.log.log(IndexLog.EntryType.CONTINUE, "Failed to load text content from incoming package", e);
				}
			}
		}

		return lines;
	}

}
