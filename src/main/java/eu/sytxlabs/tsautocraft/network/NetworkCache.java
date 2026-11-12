package eu.sytxlabs.tsautocraft.network;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import com.tom.storagemod.block.entity.IInventoryConnector;
import com.tom.storagemod.inventory.IInventoryAccess;
import com.tom.storagemod.inventory.InventoryCableNetwork;

import eu.sytxlabs.tsautocraft.api.CraftingProvider;
import eu.sytxlabs.tsautocraft.api.JobOwner;
import eu.sytxlabs.tsautocraft.api.NetworkBridges;
import eu.sytxlabs.tsautocraft.job.CraftingJob;
import eu.sytxlabs.tsautocraft.job.Reservations;

/**
 * One shared view of a cable network (nodes, connector, providers by priority, job owners, jobs) for all our blocks.
 * Keyed by node position: a {@link InventoryCableNetwork} walk from a node returns a new set every time. Reused for at
 * most {@link #MAX_AGE} ticks; {@link #invalidate()} drops all, the job list is rebuilt per tick and on {@link #jobsChanged()}.
 */
public final class NetworkCache {
	private static final int MAX_AGE = 20;
	/** Snapshot per node position (every node of a network maps to its snapshot). */
	private static final Map<Level, Map<BlockPos, Snapshot>> LEVELS = new WeakHashMap<>();
	private static int epoch, jobsEpoch, patternEpoch;

	private NetworkCache() {}

	/** Something changed that a snapshot cannot see (new block, provider priority): rebuild on the next use. */
	public static void invalidate() {
		epoch++;
	}

	/** Patterns of a provider changed: craftable lists are rebuilt. */
	public static void patternsChanged() {
		patternEpoch++;
	}

	/** A job started: only the job lists are rebuilt. */
	public static void jobsChanged() {
		jobsEpoch++;
	}

	public static Snapshot of(Level level, BlockPos pos) {
		Map<BlockPos, Snapshot> map = LEVELS.computeIfAbsent(level, _ -> new java.util.HashMap<>());
		long now = level.getGameTime();
		Snapshot s = map.get(pos);
		if (s != null && s.epoch == epoch && now >= s.built && now - s.built < MAX_AGE) return s;
		Collection<BlockPos> nodes = InventoryCableNetwork.getNetwork(level).getNetworkNodes(pos);
		if (map.size() > 4096) map.values().removeIf(o -> now < o.built || now - o.built >= MAX_AGE || o.epoch != epoch);
		s = new Snapshot(level, nodes, now);
		for (BlockPos p : nodes) map.put(p, s);
		map.put(pos.immutable(), s);
		return s;
	}

	public static final class Snapshot {
		private final long built;
		private final int epoch;
		private final List<BlockEntity> nodes;
		private final IInventoryConnector connector;
		private final List<CraftingProvider> providers;
		private final List<JobOwner> owners;
		private Set<Object> storages;
		private List<CraftingJob> jobs;
		private Reservations reservations;
		private List<net.minecraft.world.item.ItemStack> craftables;
		private int craftablesEpoch;
		private long jobsTime = Long.MIN_VALUE;
		private int jobsEpoch;

		private Snapshot(Level level, Collection<BlockPos> positions, long now) {
			built = now;
			epoch = NetworkCache.epoch;
			List<BlockEntity> n = new ArrayList<>();
			List<CraftingProvider> p = new ArrayList<>();
			List<JobOwner> o = new ArrayList<>();
			IInventoryConnector c = null;
			for (BlockPos pos : positions) {
				if (!level.isLoaded(pos)) continue;
				BlockEntity be = level.getBlockEntity(pos);
				if (be == null) continue;
				n.add(be);
				if (c == null && be instanceof IInventoryConnector ic && ic.hasConnectedInventories()) c = ic;
				if (be instanceof CraftingProvider cp) p.add(cp);
				if (be instanceof JobOwner jo) o.add(jo);
			}
			// Networks in other levels joined by NetworkBridges (API 4): BFS, every block entity once
			if (NetworkBridges.any()) {
				Set<BlockEntity> seen = Collections.newSetFromMap(new IdentityHashMap<>());
				seen.addAll(n);
				java.util.ArrayDeque<NetworkBridges.Target> queue = new java.util.ArrayDeque<>();
				for (BlockEntity be : n) queue.addAll(NetworkBridges.targets(be));
				while (!queue.isEmpty()) {
					NetworkBridges.Target t = queue.poll();
					if (!t.level().isLoaded(t.pos())) continue;
					BlockEntity entry = t.level().getBlockEntity(t.pos());
					if (entry != null && seen.contains(entry)) continue;
					for (BlockPos pos : InventoryCableNetwork.getNetwork(t.level()).getNetworkNodes(t.pos())) {
						if (!t.level().isLoaded(pos)) continue;
						BlockEntity be = t.level().getBlockEntity(pos);
						if (be == null || !seen.add(be)) continue;
						n.add(be);
						if (c == null && be instanceof IInventoryConnector ic && ic.hasConnectedInventories()) c = ic;
						if (be instanceof CraftingProvider cp) p.add(cp);
						if (be instanceof JobOwner jo) o.add(jo);
						queue.addAll(NetworkBridges.targets(be));
					}
				}
			}
			p.sort(java.util.Comparator.comparingInt(CraftingProvider::providerPriority).reversed());
			nodes = Collections.unmodifiableList(n);
			connector = c;
			providers = Collections.unmodifiableList(p);
			owners = Collections.unmodifiableList(o);
		}

		/** Node block entities (may contain removed ones until the next rebuild: check {@link BlockEntity#isRemoved()}). */
		public List<BlockEntity> nodes() {
			return nodes;
		}

		/** Inventory Connector with storages, or null. */
		public IInventoryConnector connector() {
			IInventoryConnector c = connector;
			return c != null && !(c instanceof BlockEntity be && be.isRemoved()) && c.hasConnectedInventories() ? c : null;
		}

		/** Crafting providers, highest priority first (stable). Read only. */
		public List<CraftingProvider> providers() {
			for (CraftingProvider p : providers) {
				if (p instanceof BlockEntity be && be.isRemoved()) return providers.stream().filter(x -> !(x instanceof BlockEntity b && b.isRemoved())).toList();
			}
			return providers;
		}

		public List<JobOwner> owners() {
			for (JobOwner o : owners) {
				if (o instanceof BlockEntity be && be.isRemoved()) return owners.stream().filter(x -> !(x instanceof BlockEntity b && b.isRemoved())).toList();
			}
			return owners;
		}

		/** Platform handlers of the connector's inventories (identity), see {@link NetworkLink#isNetworkStorage}. */
		Set<Object> storages() {
			if (storages == null) {
				Set<Object> s = Collections.newSetFromMap(new IdentityHashMap<>());
				IInventoryConnector c = connector();
				if (c != null) for (IInventoryAccess a : c.getConnectedInventories()) s.add(a.<Object>getPlatformHandler());
				storages = s;
			}
			return storages;
		}

		/** Active jobs of all owners, rebuilt once per tick and when a job starts. */
		public List<CraftingJob> jobs(Level level) {
			long now = level.getGameTime();
			if (jobs == null || jobsTime != now || jobsEpoch != NetworkCache.jobsEpoch) {
				List<CraftingJob> l = new ArrayList<>();
				for (JobOwner o : owners()) l.addAll(o.activeJobs());
				jobs = l;
				reservations = null;
				jobsTime = now;
				jobsEpoch = NetworkCache.jobsEpoch;
			}
			return jobs;
		}

		/** Craftable outputs of the network (read only), rebuilt only when patterns changed. */
		public List<net.minecraft.world.item.ItemStack> craftables() {
			if (craftables == null || craftablesEpoch != NetworkCache.patternEpoch) {
				craftables = List.copyOf(CraftingJob.craftables(providers()));
				craftablesEpoch = NetworkCache.patternEpoch;
			}
			return craftables;
		}

		/** What the running jobs have reserved, read live from the jobs (stays exact while they dispatch). */
		public Reservations reservations(Level level) {
			List<CraftingJob> j = jobs(level);
			if (reservations == null) reservations = Reservations.live(j);
			return reservations;
		}
	}
}
