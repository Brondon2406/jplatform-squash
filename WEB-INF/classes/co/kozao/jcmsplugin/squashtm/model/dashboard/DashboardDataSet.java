package co.kozao.jcmsplugin.squashtm.model.dashboard;

import java.util.List;

/**
 * Données chargées pour une catégorie du dashboard Squash TM.
 *
 * @author Severin Kengne
 */
public final class DashboardDataSet {

	public final List<SquashTestItem> items;
	private final long loadedAt;
	private final boolean partial;
	private final boolean fromCache;

	public DashboardDataSet(List<SquashTestItem> items, long loadedAt, boolean partial, boolean fromCache) {
		this.items = items;
		this.loadedAt = loadedAt;
		this.partial = partial;
		this.fromCache = fromCache;
	}

	public DashboardDataSet asCached() {
		return new DashboardDataSet(items, loadedAt, partial, true);
	}

	public List<SquashTestItem> getItems() {
		return items;
	}

	public long getLoadedAt() {
		return loadedAt;
	}

	public boolean isPartial() {
		return partial;
	}

	public boolean isFromCache() {
		return fromCache;
	}
}