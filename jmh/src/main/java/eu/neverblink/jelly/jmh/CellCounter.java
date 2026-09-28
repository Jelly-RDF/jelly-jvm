package eu.neverblink.jelly.jmh;

import org.openjdk.jmh.annotations.AuxCounters;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;

/**
 * Counts result set cells (rows × variables) processed by a benchmark, so that JMH reports
 * throughput in cells per second.
 * <p>
 * {@code @OperationsPerInvocation} would do the same, but it only takes a compile-time constant, and
 * the row count is a benchmark parameter. Add the invocation's cell count to this counter once per
 * invocation, and JMH reports it next to the primary result as {@code <benchmark>:cells}, in
 * cells/s. The primary result is then invocations (whole result sets) per second.
 */
@State(Scope.Thread)
@AuxCounters(AuxCounters.Type.OPERATIONS)
public class CellCounter {

    public long cells;

    @Setup(Level.Iteration)
    public void reset() {
        cells = 0;
    }
}
