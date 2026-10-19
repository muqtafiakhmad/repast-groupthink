package jp.ac.titech.dis.cs.nk;

import java.util.List;

public interface NKLandscapeFactory <TComponent>{
	/**
	 * Generate a landscape whose fitness table is drawn from a random seed. The
	 * seed is retained by the returned landscape (see {@link NKLandscape#getSeed()})
	 * so the exact same landscape can be regenerated.
	 *
	 * @param N number of loci
	 * @param K number of loci each locus depends on, besides itself
	 * @param neighbours dependency matrix, see {@code NKUtils.generate*Neighborhood}
	 * @return a landscape with a complete fitness table
	 */
	NKLandscape<TComponent> generateRandomLandscape(Integer N, Integer K, List<List<Byte>> neighbours);

	/**
	 * Same as {@link #generateRandomLandscape(Integer, Integer, List)} but the
	 * fitness table is fully determined by {@code seed}: two calls with the same
	 * arguments and the same seed return landscapes with identical fitness tables.
	 *
	 * @param seed seed for the fitness draws and the lowest-fitness search; when
	 *             {@code null} a random seed is drawn and retained
	 * @return a landscape with a complete fitness table
	 */
	NKLandscape<TComponent> generateRandomLandscape(Integer N, Integer K, List<List<Byte>> neighbours, Long seed);

	/**
	 * Write a landscape to {@code filePath} as JSON so it can be shared between runs.
	 *
	 * @param landscape landscape to persist
	 * @param filePath destination file
	 */
	void dumpLandscape(NKLandscape<TComponent> landscape, String filePath);

	/**
	 * Read a landscape written by {@link #dumpLandscape}. The fitness table and the
	 * lowest-fitness gene are validated (and, where the file predates those checks,
	 * repaired) before being returned.
	 *
	 * @param filePath source file
	 * @return the persisted landscape
	 * @throws IllegalStateException if the file is unreadable or its fitness table
	 *                               is incomplete
	 */
	NKLandscape<TComponent> loadLandscape(String filePath);

	/**
	 * Read a landscape at the conventional location described by a path.
	 *
	 * @param landscapePath conventional path
	 * @return the persisted landscape
	 */
	NKLandscape<TComponent> loadLandscape(NKLandscapePath landscapePath);
}
