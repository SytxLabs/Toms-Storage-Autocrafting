package eu.sytxlabs.tsautocraft;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.google.gson.annotations.SerializedName;


/**
 * {@code config/tsautocraft.json}, read at startup and written by the config screen (Mod Menu); the values are read live,
 * so changes in the screen apply right away (in singleplayer; a dedicated server reads the file at startup). Missing options are filled with their defaults and written back.
 */
public final class TsAutocraftConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static TsAutocraftConfig instance = new TsAutocraftConfig();

	public enum CrafterTrigger {
		@SerializedName("external") EXTERNAL, // The provider only fills the crafter; the player's own redstone circuit makes it craft.
		@SerializedName("direct") DIRECT, // The provider makes the crafter craft directly (scheduled block tick), no redstone signal.
		@SerializedName("redstone") REDSTONE // 2-tick redstone pulse from the provider to the crafter; the provider is then a redstone source.
	}
	public CrafterTrigger crafterTrigger = CrafterTrigger.EXTERNAL;

	/**
	 * false (default): crafter slot settings are never changed. The recipe is moved inside the 3x3 grid so that it only uses enabled slots;
	 * if it does not fit, the job waits with "crafter slot disabled". true: inputs go exactly to the pattern's positions and the crafter re-enables disabled slots it gets items in.
	 */
	public boolean enableDisabledCrafterSlots = false;

	//region Pattern Provider / crafter

	/** Minimum ticks between two deliveries to the same crafter. */
	public int crafterCooldownTicks = 6;
	/** Length of the redstone pulse in "redstone" mode, also how long the provider front lights up. */
	public int crafterPulseTicks = 2;
	/** "direct"/"redstone": a crafter still holding foreign items after this many ticks is emptied back into the network. */
	public int crafterStuckTicks = 20;
	/** Lock mode "until result": a side of a Pattern Provider is unlocked after this many seconds even if the results never came back (0 = never). */
	public int providerLockTimeoutSeconds = 120;
	/** Cancelling a job returns its inputs still waiting in crafters (not triggered yet) and, where clearly identifiable, in machine input slots. */
	public boolean returnInputsOnCancel = true;
	//endregion

	//region Jobs

	/** Running jobs per Autocrafting Terminal (without upgrades). */
	public int maxJobsPerTerminal = 8;
	/** Additional jobs per Capacity Upgrade in an Autocrafting Terminal. */
	public int jobsPerCapacityUpgrade = 8;
	/** Largest amount one request may ask for. */
	public int maxRequestAmount = 100_000;
	/** A waiting job ends after this many seconds without any result coming back. */
	public int jobResultTimeoutSeconds = 120;
	/** Without Importer reports: a waiting job ends after this many seconds without the target stock rising. */
	public int jobIdleSeconds = 20;
	/** Finished/cancelled jobs of the network an Autocrafting Terminal keeps in its history (0 = off). */
	public int jobHistorySize = 20;
	//endregion

	//region Requester

	/** How often (ticks) a Requester checks its stock. */
	public int requesterCheckTicks = 20;
	/** At most this many items per Requester job; the rest follows in the next round. */
	public int requesterMaxBatch = 1024;
	/** A Requester job that waits this many seconds for missing ingredients is planned again. */
	public int requesterReplanSeconds = 10;
	//endregion

	//region Level Maintainer

	/** How often (ticks) a Level Maintainer checks its stock (halved per Speed Upgrade). */
	public int maintainerCheckTicks = 40;
	/** A Level Maintainer job that waits this many seconds for missing ingredients (nothing in flight) is planned again. */
	public int maintainerReplanSeconds = 10;
	//endregion

	//region Importer / Exporter / terminals

	/** Ticks between two Importer/Exporter operations. */
	public int ioIntervalTicks = 8;
	/** Items moved per Importer/Exporter operation (at most one stack). */
	public int ioItemsPerOperation = 16;
	/** An Importer/Exporter that found nothing to move skips up to this many operations (one more per idle operation). */
	public int ioIdleMaxSkip = 3;
	/** How often (ticks) Tom's terminals refresh the list of craftable items. */
	public int craftablesSyncTicks = 20;
	//endregion

	public static TsAutocraftConfig get() {
		return instance;
	}

	/** Unknown or nonsensical values -> defaults / sane minimums. */
	private void sanitize() {
		if (crafterTrigger == null) crafterTrigger = CrafterTrigger.EXTERNAL;
		crafterCooldownTicks = Math.max(1, crafterCooldownTicks);
		crafterPulseTicks = Math.max(1, crafterPulseTicks);
		crafterStuckTicks = Math.max(1, crafterStuckTicks);
		providerLockTimeoutSeconds = Math.max(0, providerLockTimeoutSeconds);
		maxJobsPerTerminal = Math.max(1, maxJobsPerTerminal);
		jobsPerCapacityUpgrade = Math.max(0, jobsPerCapacityUpgrade);
		maxRequestAmount = Math.max(1, maxRequestAmount);
		jobResultTimeoutSeconds = Math.max(1, jobResultTimeoutSeconds);
		jobIdleSeconds = Math.max(1, jobIdleSeconds);
		jobHistorySize = Math.clamp(jobHistorySize, 0, 100);
		requesterCheckTicks = Math.max(1, requesterCheckTicks);
		requesterMaxBatch = Math.max(1, requesterMaxBatch);
		requesterReplanSeconds = Math.max(1, requesterReplanSeconds);
		maintainerCheckTicks = Math.max(1, maintainerCheckTicks);
		maintainerReplanSeconds = Math.max(1, maintainerReplanSeconds);
		ioIntervalTicks = Math.max(1, ioIntervalTicks);
		ioItemsPerOperation = Math.clamp(ioItemsPerOperation, 1, 64);
		ioIdleMaxSkip = Math.clamp(ioIdleMaxSkip, 0, 20);
		craftablesSyncTicks = Math.max(1, craftablesSyncTicks);
	}

	/** Defaults (for the config screen's reset buttons). */
	public static TsAutocraftConfig defaults() {
		return new TsAutocraftConfig();
	}

	/** Writes the current values (config screen). */
	public static void save() {
		instance.sanitize();
		write(file());
	}

	private static Path file() {
		return eu.sytxlabs.tsautocraft.platform.Platform.configDir().resolve(TsAutocraft.MODID + ".json");
	}

	private static void write(Path file) {
		try (Writer w = Files.newBufferedWriter(file)) {
			GSON.toJson(instance, w);
		} catch (IOException e) {
			TsAutocraft.LOGGER.error("Could not write {}", file, e);
		}
	}

	public static void load() {
		Path file = file();
		TsAutocraftConfig cfg = null;
		if (Files.exists(file)) {
			try (Reader r = Files.newBufferedReader(file)) {
				cfg = GSON.fromJson(r, TsAutocraftConfig.class);
			} catch (IOException | JsonParseException e) {
				TsAutocraft.LOGGER.error("Could not read {}, using defaults", file, e);
			}
		}
		instance = cfg != null ? cfg : new TsAutocraftConfig();
		instance.sanitize();
		write(file);
	}

	/** {@code /tsautocraft reload}: reads the file again; a broken file keeps the current values. */
	public static boolean reload() {
		Path file = file();
		if (!Files.exists(file)) return false;
		try (Reader r = Files.newBufferedReader(file)) {
			TsAutocraftConfig cfg = GSON.fromJson(r, TsAutocraftConfig.class);
			if (cfg == null) return false;
			cfg.sanitize();
			instance = cfg;
			return true;
		} catch (IOException | JsonParseException e) {
			TsAutocraft.LOGGER.error("Could not read {}", file, e);
			return false;
		}
	}
}
