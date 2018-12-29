package net.shrimpworks.unreal.archive.www.content;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.shrimpworks.unreal.archive.content.Content;
import net.shrimpworks.unreal.archive.content.ContentManager;
import net.shrimpworks.unreal.archive.www.Templates;

public class FileDetails extends ContentPageGenerator {

	private final Map<Content.ContentFile, List<Content>> contentFiles;

	public FileDetails(ContentManager content, Path output, Path staticRoot, boolean localImages) {
		super(content, output.resolve("files"), staticRoot, localImages);

		this.contentFiles = new HashMap<>();
		content.search(null, null, null, null)
			   .forEach(c -> {
				   for (Content.ContentFile f : c.files) {
					   Collection<Content> contents = contentFiles.computeIfAbsent(f, h -> new ArrayList<>());
					   contents.add(c);
				   }
			   });
	}

	@Override
	public int generate() {
		int count = 0;
		try {

			for (Map.Entry<Content.ContentFile, List<Content>> e : contentFiles.entrySet()) {
				// we're only interested in multi-use files
				if (e.getValue().size() < 2) continue;

				Path p = root.resolve(e.getKey().hash.substring(0, 2));

				e.getValue().sort(Comparator.comparing(a -> a.name));

				Templates.template("content/files/file.ftl")
						 .put("static", p.relativize(staticRoot))
						 .put("title", String.join(" / ", "Files", e.getKey().name))
						 .put("file", e.getKey())
						 .put("packages", e.getValue())
						 .put("siteRoot", root.resolve("files").relativize(root))
						 .write(p.resolve(e.getKey().hash + ".html"));
				count++;
			}

		} catch (
				IOException e) {
			throw new RuntimeException("Failed to render page", e);
		}

		return count;
	}

}