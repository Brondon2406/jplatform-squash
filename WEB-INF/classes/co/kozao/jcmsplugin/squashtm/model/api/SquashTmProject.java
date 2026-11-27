package co.kozao.jcmsplugin.squashtm.model.api;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

public class SquashTmProject {

	private final long id;
	private final String name;
	private final String label;
	private final String description;

	public SquashTmProject(long id, String name, String label, String description) {
		this.id = id;
		this.name = name != null ? name : "";
		this.label = label != null ? label : "";
		this.description = description != null ? description : "";
	}

	public static SquashTmProject fromJson(JsonObject json) {

		long id = 0;
		try {
			id = json.has("id") && !json.get("id").isJsonNull() ? json.get("id").getAsLong() : 0;
		} catch (Exception e) {
		}

		return new SquashTmProject(id, getString(json, "name"), getString(json, "label"),
				getString(json, "description"));
	}

	private static String getString(JsonObject json, String key) {

		JsonElement element = json.get(key);

		if (element == null || element.isJsonNull()) {
			return "";
		}

		try {
			return element.getAsString();
		} catch (Exception e) {
			return "";
		}
	}

	public long getId() {
		return id;
	}

	public String getName() {
		return name;
	}

	public String getLabel() {
		return label;
	}

	public String getDescription() {
		return description;
	}

	public String getDescriptionText() {
		return description.replaceAll("<[^>]*>", " ").replace("&nbsp;", " ").replaceAll("\\s+", " ").trim();
	}
}
