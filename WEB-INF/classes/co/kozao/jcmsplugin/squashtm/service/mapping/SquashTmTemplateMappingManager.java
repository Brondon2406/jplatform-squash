package co.kozao.jcmsplugin.squashtm.service.mapping;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.log4j.Logger;

import com.jalios.jcms.Channel;
import com.jalios.util.Util;

import co.kozao.jcmsplugin.squashtm.model.mapping.TemplateMapping;
import co.kozao.jcmsplugin.squashtm.util.SquashTmConstants;

/**
 * Gère le template Excel actif et son mapping.
 *
 * Fichiers conservés dans le répertoire de stockage :
 *
 * - template-mapping.json : le mapping actif
 *
 * - active-template.xlsx : le template personnalisé proposé au téléchargement
 *
 * Sans ces fichiers, le module fonctionne avec le template historique
 * (TemplateMapping.createDefault()).
 *
 * Le manager conserve aussi, le temps de la configuration, les fichiers
 * téléversés par l'administrateur mais pas encore enregistrés.
 */
public class SquashTmTemplateMappingManager {

	private static final Logger LOGGER = Logger.getLogger(SquashTmTemplateMappingManager.class);

	private static final long PENDING_EXPIRATION_DELAY = 30L * 60L * 1000L;

	private static final SquashTmTemplateMappingManager INSTANCE = new SquashTmTemplateMappingManager();

	private final ConcurrentHashMap<String, PendingTemplate> pendingTemplates = new ConcurrentHashMap<>();

	private SquashTmTemplateMappingManager() {
	}

	public static SquashTmTemplateMappingManager getInstance() {
		return INSTANCE;
	}

	/*
	 * ================== MAPPING ACTIF ======================
	 */

	/**
	 * Retourne le mapping actif. Si aucun mapping n'est enregistré, ou s'il est
	 * illisible, le mapping du template historique est retourné.
	 *
	 * Le résultat est une copie : le modifier n'a aucun effet sur le mapping
	 * enregistré.
	 */
	public TemplateMapping getActiveMapping() {

		File mappingFile = getMappingFile();

		if (!mappingFile.isFile()) {
			return TemplateMapping.createDefault();
		}

		try {

			String json = new String(Files.readAllBytes(mappingFile.toPath()), StandardCharsets.UTF_8);

			TemplateMapping mapping = TemplateMapping.fromJson(json);

			if (mapping == null) {
				LOGGER.error("[TEMPLATE] Mapping vide : " + mappingFile.getAbsolutePath()
						+ ". Le mapping par défaut est utilisé.");
				return TemplateMapping.createDefault();
			}

			List<String> errors = mapping.validate();

			if (!errors.isEmpty()) {
				LOGGER.error("[TEMPLATE] Mapping invalide : " + errors + ". Le mapping par défaut est utilisé.");
				return TemplateMapping.createDefault();
			}

			return mapping;

		} catch (IOException | RuntimeException e) {

			LOGGER.error("[TEMPLATE] Impossible de lire le mapping : " + mappingFile.getAbsolutePath()
					+ ". Le mapping par défaut est utilisé.", e);

			return TemplateMapping.createDefault();
		}
	}

	/**
	 * Indique si un mapping personnalisé est enregistré.
	 */
	public boolean isCustomMappingActive() {
		return getMappingFile().isFile();
	}

	/**
	 * Retourne le template personnalisé, ou null si le template historique est
	 * utilisé.
	 */
	public File getCustomTemplateFile() {

		File templateFile = new File(getStorageDirectory(), SquashTmConstants.TEMPLATE_FILE_NAME);

		return templateFile.isFile() ? templateFile : null;
	}

	/**
	 * Enregistre un nouveau mapping et le template associé.
	 *
	 * Les deux fichiers sont écrits dans des fichiers temporaires, puis déplacés :
	 * un échec pendant l'écriture ne remplace donc jamais un mapping valide.
	 *
	 * @param mapping      mapping à enregistrer (doit être valide)
	 * @param templateFile fichier Excel correspondant
	 * @param updatedBy    identifiant de l'administrateur
	 * @throws IOException si le mapping est invalide ou si l'écriture échoue
	 */
	public synchronized void saveMapping(TemplateMapping mapping, File templateFile, String updatedBy)
			throws IOException {

		if (mapping == null) {
			throw new IOException("Le mapping est absent.");
		}

		List<String> errors = mapping.validate();

		if (!errors.isEmpty()) {
			throw new IOException("Le mapping est invalide : " + String.join(" ", errors));
		}

		if (templateFile == null || !templateFile.isFile()) {
			throw new IOException("Le fichier du modèle est introuvable.");
		}

		mapping.setUpdatedBy(updatedBy == null ? "" : updatedBy);
		mapping.setUpdatedAt(System.currentTimeMillis());

		File directory = getStorageDirectory();

		File temporaryMapping = new File(directory, SquashTmConstants.MAPPING_FILE_NAME + ".tmp");
		File temporaryTemplate = new File(directory, SquashTmConstants.TEMPLATE_FILE_NAME + ".tmp");

		try {

			Files.write(temporaryMapping.toPath(), mapping.toJson().getBytes(StandardCharsets.UTF_8));
			Files.copy(templateFile.toPath(), temporaryTemplate.toPath(), StandardCopyOption.REPLACE_EXISTING);

			Files.move(temporaryTemplate.toPath(), new File(directory, SquashTmConstants.TEMPLATE_FILE_NAME).toPath(),
					StandardCopyOption.REPLACE_EXISTING);

			Files.move(temporaryMapping.toPath(), new File(directory, SquashTmConstants.MAPPING_FILE_NAME).toPath(),
					StandardCopyOption.REPLACE_EXISTING);

		} finally {

			Files.deleteIfExists(temporaryMapping.toPath());
			Files.deleteIfExists(temporaryTemplate.toPath());
		}

		LOGGER.info("[TEMPLATE] Mapping enregistré par " + updatedBy + " : " + getMappingFile().getAbsolutePath());
	}

	/**
	 * Supprime le mapping et le template personnalisés. Le module revient au
	 * template historique.
	 */
	public synchronized void resetToDefault() throws IOException {

		Files.deleteIfExists(getMappingFile().toPath());
		Files.deleteIfExists(new File(getStorageDirectory(), SquashTmConstants.TEMPLATE_FILE_NAME).toPath());

		LOGGER.info("[TEMPLATE] Retour au template par défaut.");
	}

	/*
	 * ==================== FICHIERS TELEVERSES EN ATTENTE
	 * ================================
	 */

	/**
	 * Copie un fichier téléversé dans un fichier temporaire et retourne un jeton
	 * permettant de le retrouver.
	 */
	public String storePendingTemplate(File uploadedFile) throws IOException {

		if (uploadedFile == null || !uploadedFile.isFile()) {
			throw new IOException("Le fichier du modèle est introuvable.");
		}

		cleanupExpiredTemplates();

		File temporaryFile = Files.createTempFile("squashtm-template-", ".xlsx").toFile();

		try {
			Files.copy(uploadedFile.toPath(), temporaryFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
		} catch (IOException e) {
			temporaryFile.delete();
			throw e;
		}

		String token = UUID.randomUUID().toString();

		pendingTemplates.put(token, new PendingTemplate(temporaryFile));

		return token;
	}

	/**
	 * Retourne le fichier en attente associé au jeton, ou null s'il est inconnu ou
	 * expiré.
	 */
	public File getPendingTemplateFile(String token) {

		if (Util.isEmpty(token)) {
			return null;
		}

		cleanupExpiredTemplates();

		PendingTemplate pending = pendingTemplates.get(token);

		return pending == null ? null : pending.file;
	}

	/**
	 * Supprime un fichier en attente.
	 */
	public void discardPendingTemplate(String token) {

		if (Util.isEmpty(token)) {
			return;
		}

		PendingTemplate pending = pendingTemplates.remove(token);

		if (pending != null) {
			deleteQuietly(pending.file);
		}
	}

	private void cleanupExpiredTemplates() {

		long now = System.currentTimeMillis();

		for (String token : pendingTemplates.keySet()) {

			PendingTemplate pending = pendingTemplates.get(token);

			if (pending != null && now - pending.createdAt >= PENDING_EXPIRATION_DELAY
					&& pendingTemplates.remove(token, pending)) {

				deleteQuietly(pending.file);
			}
		}
	}

	private void deleteQuietly(File file) {

		try {
			Files.deleteIfExists(file.toPath());
		} catch (IOException e) {
			LOGGER.warn("[TEMPLATE] Impossible de supprimer le fichier temporaire : " + file.getAbsolutePath(), e);
		}
	}

	/*
	 * ================== STOCKAGE ===================
	 */

	private File getMappingFile() {
		return new File(getStorageDirectory(), SquashTmConstants.MAPPING_FILE_NAME);
	}

	/**
	 * Retourne le répertoire de stockage, en le créant si nécessaire.
	 *
	 * Ordre de résolution :
	 *
	 * 1. la propriété jcmsplugin.squashtm.template.storage-dir si elle est
	 * renseignée ;
	 *
	 * 2. sinon WEB-INF/data/squashtm, déduit de l'emplacement des classes du
	 * plugin.
	 *
	 * Le répertoire n'est pas dans le dossier du plugin : une mise à jour du plugin
	 * ne supprime donc pas le template personnalisé.
	 */
	private File getStorageDirectory() {

		File directory;

		String configuredDirectory = Channel.getChannel().getProperty(SquashTmConstants.SQUASHTM_TEMPLATE_STORAGE_DIR);

		if (Util.notEmpty(configuredDirectory)) {

			directory = new File(configuredDirectory.trim());

		} else {

			directory = new File(new File(resolveWebInfDirectory(), "data"), "squashtm");
		}

		if (!directory.isDirectory() && !directory.mkdirs()) {
			LOGGER.error("[TEMPLATE] Impossible de créer le répertoire de stockage : " + directory.getAbsolutePath());
		}

		return directory;
	}

	/**
	 * Retrouve le répertoire WEB-INF à partir de l'emplacement de cette classe
	 * (WEB-INF/classes ou WEB-INF/lib/xxx.jar).
	 */
	private File resolveWebInfDirectory() {

		try {

			File location = new File(
					SquashTmTemplateMappingManager.class.getProtectionDomain().getCodeSource().getLocation().toURI());

			File base = location.isDirectory() ? location : location.getParentFile();

			if (base != null && base.getParentFile() != null) {
				return base.getParentFile();
			}

		} catch (URISyntaxException | RuntimeException e) {
			LOGGER.warn("[TEMPLATE] Impossible de déterminer le répertoire WEB-INF.", e);
		}

		File fallback = new File(System.getProperty("java.io.tmpdir"), "squashtm-webinf");

		LOGGER.warn("[TEMPLATE] Répertoire de repli utilisé : " + fallback.getAbsolutePath()
				+ ". Renseignez la propriété " + SquashTmConstants.SQUASHTM_TEMPLATE_STORAGE_DIR + ".");

		return fallback;
	}

	/**
	 * Fichier téléversé en attente d'enregistrement.
	 */
	private static class PendingTemplate {

		private final File file;
		private final long createdAt = System.currentTimeMillis();

		private PendingTemplate(File file) {
			this.file = file;
		}
	}
}
