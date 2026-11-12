package eu.sytxlabs.tsautocraft.planner;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.ToLongFunction;

/**
 * Resolves a request recursively into steps (pattern x count). Free of Minecraft so the self test runs without the game;
 * {@code K} = item key (needs equals/hashCode), {@code P} = pattern. Stock counts once (virtual storage), surplus is
 * reused, the first pattern without missing items wins, cycles and too deep trees count as missing.
 */
public final class CraftingPlanner<K, P> {
	public static final int MAX_DEPTH = 48;
	/** Guard against absurd requests that would otherwise create millions of steps. */
	public static final long MAX_CRAFTS_PER_STEP = 1_000_000L;

	public record Step<P>(P pattern, long crafts) {}

	/**
	 * One need in the recipe tree (only recorded on request): {@code amount} of {@code item}, covered by {@code fromStock} (storage or surplus), {@code crafts} of {@code pattern} and {@code missing}.
	 * Children are the inputs of the pattern.
	 */
	public static final class Node<K, P> {
		public final K item;
		public final long amount;
		public long fromStock, crafts, missing;
		public P pattern;
		public final List<Node<K, P>> children = new ArrayList<>();

		Node(K item, long amount) {
			this.item = item;
			this.amount = amount;
		}
	}

	/** {@code tree} = root node (the target), null if the tree was not recorded. */
	public record Result<K, P>(List<Step<P>> steps, Map<K, Long> missing, Map<K, Long> fromStock, Node<K, P> tree) {
		public Result(List<Step<P>> steps, Map<K, Long> missing, Map<K, Long> fromStock) {
			this(steps, missing, fromStock, null);
		}

		public boolean isComplete() {
			return missing.isEmpty();
		}
	}

	private final ToLongFunction<K> stock;
	private final Function<K, List<P>> patternsFor;
	private final Function<P, Map<K, Long>> inputsOf;
	private final Function<P, Map<K, Long>> outputsOf;

	// State of one planning run
	private Map<K, Long> virtual;
	private Map<K, Long> fromStock;
	private LinkedHashMap<P, Long> steps;
	private Map<K, Long> missing;
	private Set<K> inProgress;
	private Node<K, P> current;

	/**
	 * @param stock       current stock in the network
	 * @param patternsFor all patterns that have {@code K} as an output (order = priority)
	 * @param inputsOf    inputs of a pattern per single crafting operation
	 * @param outputsOf   outputs of a pattern per single crafting operation
	 */
	public CraftingPlanner(ToLongFunction<K> stock, Function<K, List<P>> patternsFor, Function<P, Map<K, Long>> inputsOf, Function<P, Map<K, Long>> outputsOf) {
		this.stock = stock;
		this.patternsFor = patternsFor;
		this.inputsOf = inputsOf;
		this.outputsOf = outputsOf;
	}

	/** @param useStockForTarget true = existing stock of the target counts (requester), false = craft exactly {@code amount} new items (terminal request) */
	public Result<K, P> plan(K target, long amount, boolean useStockForTarget) {
		return plan(target, amount, useStockForTarget, false);
	}

	/** @param recordTree also build the recipe tree ({@link Result#tree()}) */
	public Result<K, P> plan(K target, long amount, boolean useStockForTarget, boolean recordTree) {
		virtual = new HashMap<>();
		fromStock = new HashMap<>();
		steps = new LinkedHashMap<>();
		missing = new LinkedHashMap<>();
		inProgress = new HashSet<>();
		Node<K, P> holder = recordTree ? new Node<>(null, 0) : null;
		Node<K, P> root = null;

		if (amount > 0) {
			if (useStockForTarget) {
				current = holder;
				need(target, amount, 0);
				if (holder != null) root = holder.children.getFirst();
			} else {
				if (holder != null) root = new Node<>(target, amount);
				current = root;
				craft(target, amount, 0);
			}
		}
		current = null;

		List<Step<P>> list = new ArrayList<>(steps.size());
		steps.forEach((p, c) -> list.add(new Step<>(p, c)));
		return new Result<>(Collections.unmodifiableList(list), Collections.unmodifiableMap(missing), Collections.unmodifiableMap(fromStock), root);
	}

	private long available(K k) {
		return virtual.computeIfAbsent(k, stock::applyAsLong);
	}

	/** Cover a need: first from (virtual) storage, craft the rest. */
	private void need(K k, long amount, int depth) {
		Node<K, P> parent = current, node = null;
		if (parent != null) {
			node = new Node<>(k, amount);
			parent.children.add(node);
		}
		long avail = available(k);
		long take = Math.min(avail, amount);
		if (take > 0) {
			virtual.put(k, avail - take);
			fromStock.merge(k, take, Long::sum);
			amount -= take;
			if (node != null) node.fromStock = take;
		}
		if (amount > 0) {
			current = node;
			try {
				craft(k, amount, depth);
			} finally {
				current = parent;
			}
		}
	}

	private void addMissing(K k, long amount) {
		missing.merge(k, amount, Long::sum);
		if (current != null) current.missing += amount;
	}

	private void craft(K k, long amount, int depth) {
		List<P> candidates = patternsFor.apply(k);
		if (candidates == null || candidates.isEmpty() || depth > MAX_DEPTH || inProgress.contains(k)) {
			addMissing(k, amount);
			return;
		}
		if (candidates.size() == 1) {
			applyPattern(k, amount, candidates.getFirst(), depth);
			return;
		}
		Node<K, P> node = current;
		int childrenBefore = node != null ? node.children.size() : 0;
		long nodeMissingBefore = node != null ? node.missing : 0;
		for (P p : candidates) {
			Snapshot s = snapshot();
			int missingBefore = missing.size();
			long missingSumBefore = sum(missing);
			applyPattern(k, amount, p, depth);
			if (missing.size() == missingBefore && sum(missing) == missingSumBefore) return;
			restore(s);
			if (node != null) {
				node.children.subList(childrenBefore, node.children.size()).clear();
				node.missing = nodeMissingBefore;
				node.pattern = null;
				node.crafts = 0;
			}
		}
		applyPattern(k, amount, candidates.getFirst(), depth);
	}

	private void applyPattern(K k, long amount, P p, int depth) {
		long outPer = outputsOf.apply(p).getOrDefault(k, 0L);
		if (outPer <= 0) {
			addMissing(k, amount);
			return;
		}
		long crafts = ceilDiv(amount, outPer);
		if (crafts > MAX_CRAFTS_PER_STEP) {
			addMissing(k, amount);
			return;
		}
		if (current != null) {
			current.pattern = p;
			current.crafts = crafts;
		}
		inProgress.add(k);
		for (Map.Entry<K, Long> in : inputsOf.apply(p).entrySet()) {
			need(in.getKey(), Math.multiplyExact(in.getValue(), crafts), depth + 1);
		}
		inProgress.remove(k);
		steps.merge(p, crafts, Long::sum);
		for (Map.Entry<K, Long> out : outputsOf.apply(p).entrySet()) {
			long produced = Math.multiplyExact(out.getValue(), crafts);
			if (out.getKey().equals(k)) produced -= amount;
			if (produced > 0) virtual.put(out.getKey(), available(out.getKey()) + produced); // on top of the real stock, not instead of it
		}
	}

	private static long ceilDiv(long a, long b) {
		return (a + b - 1) / b;
	}

	private static <K> long sum(Map<K, Long> m) {
		long s = 0;
		for (long v : m.values()) s += v;
		return s;
	}

	private record Snapshot(Map<?, Long> virtual, Map<?, Long> fromStock, LinkedHashMap<?, Long> steps, Map<?, Long> missing) {}

	private Snapshot snapshot() {
		return new Snapshot(new HashMap<>(virtual), new HashMap<>(fromStock), new LinkedHashMap<>(steps), new LinkedHashMap<>(missing));
	}

	@SuppressWarnings("unchecked")
	private void restore(Snapshot s) {
		virtual = (Map<K, Long>) s.virtual();
		fromStock = (Map<K, Long>) s.fromStock();
		steps = (LinkedHashMap<P, Long>) s.steps();
		missing = (Map<K, Long>) s.missing();
	}
}
