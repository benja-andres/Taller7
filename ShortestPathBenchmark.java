import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class ShortestPathBenchmark {

    interface ShortestPathProblem {
        boolean isSolution();
        void applyMove(int move);
        void undoMove(int move);
        List<Integer> getPossibleMoves();
        List<Integer> getCurrentPath();
        int getCurrentPathLength();
        ShortestPathProblem deepCopy();
    }

    static class GraphProblem implements ShortestPathProblem {
        private final int[][] adjMatrix;
        private final int n;
        private final int end;
        private final List<Integer> path;
        private final boolean[] visited;

        GraphProblem(int[][] adjMatrix, int start, int end) {
            this.adjMatrix = adjMatrix;
            this.n         = adjMatrix.length;
            this.end       = end;
            this.path      = new ArrayList<>();
            this.visited   = new boolean[n];
            path.add(start);
            visited[start] = true;
        }

        private GraphProblem(GraphProblem other) {
            this.adjMatrix = other.adjMatrix;
            this.n         = other.n;
            this.end       = other.end;
            this.path      = new ArrayList<>(other.path);
            this.visited   = Arrays.copyOf(other.visited, other.n);
        }

        @Override public boolean isSolution() {
            return !path.isEmpty() && path.get(path.size() - 1) == end;
        }
        @Override public void applyMove(int move) { path.add(move); visited[move] = true; }
        @Override public void undoMove(int move)  { path.remove(path.size() - 1); visited[move] = false; }
        @Override public List<Integer> getCurrentPath()  { return new ArrayList<>(path); }
        @Override public int getCurrentPathLength()      { return path.size(); }
        @Override public ShortestPathProblem deepCopy()  { return new GraphProblem(this); }

        @Override
        public List<Integer> getPossibleMoves() {
            if (path.isEmpty()) return Collections.emptyList();
            int current = path.get(path.size() - 1);
            List<Integer> moves = new ArrayList<>();
            for (int i = 0; i < n; i++)
                if (adjMatrix[current][i] != 0 && !visited[i])
                    moves.add(i);
            return moves;
        }
    }

    static List<Integer> sequentialBacktracking(ShortestPathProblem problem) {
        if (problem.isSolution()) return problem.getCurrentPath();
        List<Integer> best = null;
        for (int move : problem.getPossibleMoves()) {
            problem.applyMove(move);
            List<Integer> path = sequentialBacktracking(problem);
            if (path != null && (best == null || path.size() < best.size()))
                best = path;
            problem.undoMove(move);
        }
        return best;
    }

    static class ParallelShortestPath extends RecursiveTask<List<Integer>> {
        private final ShortestPathProblem problem;
        private final AtomicInteger bestPathLength;

        ParallelShortestPath(ShortestPathProblem problem, AtomicInteger bestPathLength) {
            this.problem        = problem;
            this.bestPathLength = bestPathLength;
        }

        @Override
        protected List<Integer> compute() {
            if (problem.getCurrentPathLength() >= bestPathLength.get()) return null;
            if (problem.isSolution()) {
                int len = problem.getCurrentPathLength();
                bestPathLength.updateAndGet(cur -> Math.min(cur, len));
                return problem.getCurrentPath();
            }
            List<Integer> moves = problem.getPossibleMoves();
            if (moves.isEmpty()) return null;

            List<ParallelShortestPath> subtasks = new ArrayList<>();
            for (int move : moves) {
                ShortestPathProblem copy = problem.deepCopy();
                copy.applyMove(move);
                subtasks.add(new ParallelShortestPath(copy, bestPathLength));
            }
            for (int i = 0; i < subtasks.size() - 1; i++) subtasks.get(i).fork();
            List<Integer> best = subtasks.get(subtasks.size() - 1).compute();
            for (int i = 0; i < subtasks.size() - 1; i++) {
                List<Integer> result = subtasks.get(i).join();
                if (result != null && (best == null || result.size() < best.size()))
                    best = result;
            }
            return best;
        }
    }

    // Genera matriz completamente densa: cada nodo i conecta
    // con TODOS los nodos j > i, maximizando ramificacion
    static int[][] buildFullDense(int n) {
        int[][] m = new int[n][n];
        for (int i = 0; i < n; i++)
            for (int j = i + 1; j < n; j++)
                m[i][j] = 1;
        return m;
    }

    // =========================================================
    //  MATRICES FIJAS EN EL CODIGO
    // =========================================================

    // 7x7  — simple, pocos caminos   -> secuencial gana
    static final int[][] M7  = {
        {0, 1, 1, 0, 0, 0, 0},
        {0, 0, 0, 1, 1, 0, 0},
        {0, 0, 0, 1, 0, 1, 0},
        {0, 0, 0, 0, 0, 0, 1},
        {0, 0, 0, 0, 0, 1, 0},
        {0, 0, 0, 0, 0, 0, 1},
        {0, 0, 0, 0, 0, 0, 0}
    };

    // 8x8  — algo mas ramificado
    static final int[][] M8  = {
        {0, 1, 1, 1, 0, 0, 0, 0},
        {0, 0, 0, 0, 1, 1, 0, 0},
        {0, 0, 0, 0, 1, 0, 1, 0},
        {0, 0, 0, 0, 0, 1, 1, 0},
        {0, 0, 0, 0, 0, 0, 0, 1},
        {0, 0, 0, 0, 0, 0, 0, 1},
        {0, 0, 0, 0, 0, 0, 0, 1},
        {0, 0, 0, 0, 0, 0, 0, 0}
    };

    // 9x9  — denso
    static final int[][] M9  = buildFullDense(9);

    // 10x10 — muy denso
    static final int[][] M10 = buildFullDense(10);

    // 11x11 — explosion combinatoria
    static final int[][] M11 = buildFullDense(11);

    // 12x12 — paralelo empieza a ganar con claridad
    static final int[][] M12 = buildFullDense(12);

    // 13x13 — paralelo gana
    static final int[][] M13 = buildFullDense(13);

    // 14x14 — paralelo gana con margen
    static final int[][] M14 = buildFullDense(14);

    // 15x15 — paralelo gana claramente
    static final int[][] M15 = buildFullDense(15);

    // =========================================================
    //  Benchmark — promedio de 3 ejecuciones
    // =========================================================
    static double[] runBenchmark(String name, int[][] matrix, int start, int end) {
        int RUNS  = 3;
        int cores = Runtime.getRuntime().availableProcessors();
        double seqTotal = 0, parTotal = 0;
        List<Integer> seqPath = null, parPath = null;

        for (int r = 0; r < RUNS; r++) {
            GraphProblem seqP = new GraphProblem(matrix, start, end);
            long t0 = System.nanoTime();
            seqPath = sequentialBacktracking(seqP);
            seqTotal += (System.nanoTime() - t0) / 1_000_000.0;

            GraphProblem parP     = new GraphProblem(matrix, start, end);
            AtomicInteger bestLen = new AtomicInteger(Integer.MAX_VALUE);
            ForkJoinPool pool     = new ForkJoinPool(cores);
            long t1 = System.nanoTime();
            parPath = pool.invoke(new ParallelShortestPath(parP, bestLen));
            parTotal += (System.nanoTime() - t1) / 1_000_000.0;
            pool.shutdown();
        }

        double seqMs = seqTotal / RUNS;
        double parMs = parTotal / RUNS;

        System.out.println("\n=== Iniciando prueba de rendimiento [" + name + "] ===");
        System.out.println("[Secuencial] Camino encontrado: " + (seqPath != null ? seqPath : "No existe"));
        System.out.printf("[Secuencial] Tiempo de ejecucion: %.3f ms%n", seqMs);
        System.out.println("[Paralelo]   Camino encontrado: " + (parPath != null ? parPath : "No existe"));
        System.out.printf("[Paralelo]   Tiempo de ejecucion: %.3f ms%n", parMs);

        double diff = seqMs - parMs;
        System.out.println("=== Resumen de Mejora ===");
        System.out.printf("Diferencia de tiempo: %.3f ms%n", diff);
        if (diff > 0.001 && parMs > 0.001)
            System.out.printf(">> El PARALELO es mas rapido por %.3f ms (%.2fx mas rapido)%n", diff, seqMs / parMs);
        else if (diff > 0.001)
            System.out.printf(">> El PARALELO es mas rapido por %.3f ms%n", diff);
        else if (diff < -0.001 && seqMs > 0.001)
            System.out.printf(">> El SECUENCIAL es mas rapido por %.3f ms (%.2fx mas rapido)%n", Math.abs(diff), parMs / seqMs);
        else if (diff < -0.001)
            System.out.printf(">> El SECUENCIAL es mas rapido por %.3f ms%n", Math.abs(diff));
        else
            System.out.println(">> Ambos tomaron el mismo tiempo");

        return new double[]{seqMs, parMs};
    }

    public static void main(String[] args) {
        System.out.println("=== Taller 7 - Backtracking Paralelo: Camino mas corto ===");
        System.out.println("Nucleos disponibles: " + Runtime.getRuntime().availableProcessors());

        double[] r1  = runBenchmark("Matriz  7x7",  M7,   0, 6);
        double[] r2  = runBenchmark("Matriz  8x8",  M8,   0, 7);
        double[] r3  = runBenchmark("Matriz  9x9",  M9,   0, 8);
        double[] r4  = runBenchmark("Matriz 10x10", M10,  0, 9);
        double[] r5  = runBenchmark("Matriz 11x11", M11,  0, 10);
        double[] r6  = runBenchmark("Matriz 12x12", M12,  0, 11);
        double[] r7  = runBenchmark("Matriz 13x13", M13,  0, 12);
        double[] r8  = runBenchmark("Matriz 14x14", M14,  0, 13);
        double[] r9  = runBenchmark("Matriz 15x15", M15,  0, 14);

        System.out.println("\n=========================================");
        System.out.println("  TABLA COMPARATIVA FINAL");
        System.out.println("=========================================");
        System.out.printf("%-14s %12s %12s %12s%n", "Matriz", "Secuencial", "Paralelo", "Diferencia");
        System.out.println("-".repeat(54));
        String[]   nombres = {"7x7","8x8","9x9","10x10","11x11","12x12","13x13","14x14","15x15"};
        double[][] results = {r1, r2, r3, r4, r5, r6, r7, r8, r9};
        for (int i = 0; i < results.length; i++) {
            double diff    = results[i][0] - results[i][1];
            String ganador = diff > 0.001 ? "(+Paralelo)" : diff < -0.001 ? "(+Secuencial)" : "(Empate)";
            System.out.printf("%-14s %9.3f ms %9.3f ms %7.3f ms %s%n",
                nombres[i], results[i][0], results[i][1], diff, ganador);
        }
        System.out.println("=========================================");
    }
}
