package net.shrimpworks.unreal.archive.content.mappacks;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import net.shrimpworks.unreal.archive.AuthorNames;
import net.shrimpworks.unreal.archive.Util;
import net.shrimpworks.unreal.archive.content.Content;
import net.shrimpworks.unreal.archive.content.Games;

public class MapPack extends Content {

	// Game/Type/A/
	private static final String PATH_STRING = "%s/%s/%s/%s/";

	public List<PackMap> maps = new ArrayList<>();
	public String gametype = "Mixed";
	public java.util.Map<String, Double> themes = new HashMap<>();

	@Override
	public Path contentPath(Path root) {
		String namePrefix = subGrouping();
		return root.resolve(String.format(PATH_STRING,
										  game,
										  "MapPacks",
										  namePrefix,
										  hashPath()
		));
	}

	@Override
	public Path slugPath(Path root) {
		String type = Util.slug(this.contentType.toLowerCase().replaceAll("_", "") + "s");
		String game = Util.slug(this.game);
		String gameType = Util.slug(this.gametype);
		String name = Util.slug(this.name + "_" + this.hash.substring(0, 8));
		return root.resolve(type).resolve(game).resolve(gameType).resolve(subGrouping()).resolve(name);
	}

	@Override
	public String autoDescription() {
		return String.format("%s, a %s map pack for %s containing %d maps, created by %s",
							 name, gametype, Games.byName(game).bigName, maps.size(), authorName());
	}

	@Override
	public List<String> autoTags() {
		List<String> tags = new ArrayList<>(super.autoTags());
		tags.add(gametype.toLowerCase());
		tags.addAll(maps.stream().filter(m -> m.name.contains("-"))
						.map(m -> m.name.split("-")[0].toLowerCase()).distinct().collect(Collectors.toList()));
		tags.addAll(maps.stream().filter(m -> m.name.contains("-"))
						.map(m -> m.name.split("-")[1].toLowerCase()).distinct().collect(Collectors.toList()));
		tags.addAll(themes.keySet().stream().map(String::toLowerCase).collect(Collectors.toList()));
		return tags;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		if (!super.equals(o)) return false;
		MapPack mapPack = (MapPack)o;
		return Objects.equals(maps, mapPack.maps)
			   && Objects.equals(gametype, mapPack.gametype)
			   && Objects.equals(themes, mapPack.themes);
	}

	@Override
	public int hashCode() {
		return Objects.hash(super.hashCode(), maps, gametype, themes);
	}

	public static class PackMap implements Comparable<PackMap> {

		public String name;
		public String title;
		public String author = "Unknown";

		public String authorName() {
			return AuthorNames.nameFor(author);
		}

		@Override
		public int compareTo(PackMap o) {
			return name.compareToIgnoreCase(o.name);
		}
	}
}
