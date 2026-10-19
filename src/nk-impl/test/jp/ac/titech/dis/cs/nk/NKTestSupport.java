package jp.ac.titech.dis.cs.nk;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

import java.io.File;
import java.util.List;
import java.util.Map;

/**
 * Assertion helpers shared by the NK test suite.
 *
 * <p>
 * Kept free of test class state so every test can use them without setup, and
 * written against the plain {@link org.testng.Assert} primitives so they work
 * with any TestNG version the Eclipse container provides.
 * </p>
 */
public final class NKTestSupport {

	private NKTestSupport() {
	}

	/**
	 * Assert that {@code body} throws an instance of {@code expected}. Anything
	 * else - including no exception at all - fails the test.
	 */
	public static void assertThrows(Class<? extends Throwable> expected, Runnable body) {
		try {
			body.run();
			fail("expected " + expected.getSimpleName() + " but nothing was thrown");
		} catch (Throwable thrown) {
			assertTrue(expected.isInstance(thrown),
					"expected " + expected.getSimpleName() + " but got " + thrown);
		}
	}

	/**
	 * Assert the defining property of an NK dependency matrix: N x N, only 0/1
	 * entries, exactly K dependencies per row, and no self-dependency.
	 *
	 * @param message context to include in failure messages
	 * @param matrix  dependency matrix under test
	 * @param n       number of loci
	 * @param k       dependencies expected per row
	 */
	public static void assertWellFormed(String message, List<List<Byte>> matrix, int n, int k) {
		assertEquals(matrix.size(), n, message + ": row count");
		for(int row = 0; row < n; row++) {
			List<Byte> line = matrix.get(row);
			assertEquals(line.size(), n, message + ": row " + row + " length");
			int dependencies = 0;
			for(int column = 0; column < n; column++) {
				Byte value = line.get(column);
				assertTrue(NKUtils.isDependencyMarker(value),
						message + ": row " + row + " holds a value that is neither 0 nor 1");
				if(NKUtils.isExistingDependency(value)) {
					assertTrue(column != row, message + ": locus " + row + " depends on itself");
					dependencies++;
				}
			}
			assertEquals(dependencies, k, message + ": dependency count of row " + row);
		}
	}

	/**
	 * Assert that every component of a materialised fitness table holds exactly
	 * 2^(K+1) entries - i.e. that the table really covers every input combination.
	 */
	public static void assertTableComplete(String message, NKLandscapeImpl landscape) {
		long required = 1L << (landscape.getK() + 1);
		List<Map<String, Double>> table = landscape.getComponentFitnessCache();
		assertEquals(table.size(), landscape.getN().intValue(), message + ": component count");
		for(int component = 0; component < table.size(); component++) {
			assertEquals(table.get(component).size(), required,
					message + ": component " + component + " holds " + table.get(component).size()
							+ " of " + required + " entries");
		}
		assertTrue(landscape.isComplete(), message + ": isComplete() still reports false");
	}

	/** Best-effort recursive delete, used to clean up temporary output directories. */
	public static void deleteQuietly(File file) {
		if(file == null || !file.exists()) {
			return;
		}
		if(file.isDirectory()) {
			File[] children = file.listFiles();
			if(children != null) {
				for(File child : children) {
					deleteQuietly(child);
				}
			}
		}
		file.delete();
	}
}
