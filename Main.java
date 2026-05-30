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
        private final int start;
        private final int end;
        private final List<Integer> path;
        private final boolean[] visited;

        GraphProblem(int[][] adjMatrix, int start, int end) {
            this.adjMatrix = adjMatrix;
            this.n         = adjMatrix.length;
            this.start     = start;
            this.end       = end;
            this.path      = new ArrayList<>();
            this.visited   = new boolean[n];
            path.add(start);
            visited[start] = true;
        }

        private GraphProblem(GraphProblem other) {
            this.adjMatrix = other.adjMatrix;
            this.n         = other.n;
            this.start     = other.start;
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
        @Override public int getCurrentPathLength()       { return path.size(); }
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
        List<Integer> shortestPath = null;
        for (int move : problem.getPossibleMoves()) {
            problem.applyMove(move);
            List<Integer> path = sequentialBacktracking(problem);
            if (path != null && (shortestPath == null || path.size() < shortestPath.size()))
                shortestPath = path;
            problem.undoMove(move);
        }
        return shortestPath;
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

            List<Integer> shortestPath = subtasks.get(subtasks.size() - 1).compute();

            for (int i = 0; i < subtasks.size() - 1; i++) {
                List<Integer> result = subtasks.get(i).join();
                if (result != null && (shortestPath == null || result.size() < shortestPath.size()))
                    shortestPath = result;
            }
            return shortestPath;
        }
    }

    static int[][] buildDenseMatrix(int n) {
        Random rnd = new Random(42);
        int[][] m = new int[n][n];
        for (int i = 0; i < n; i++)
            for (int j = 0; j < n; j++)
                if (i != j && rnd.nextDouble() < 0.70)
                    m[i][j] = 1;
        return m;
    }

    static int[][] buildSparseMatrix(int n) {
        int[][] m = new int[n][n];
        Random rnd = new Random(42);
        for (int i = 0; i < n - 1; i++) m[i][i + 1] = 1;
        for (int i = 0; i < n; i++)
            for (int j = 0; j < n; j++)
                if (i != j && m[i][j] == 0 && rnd.nextDouble() < 0.15)
                    m[i][j] = 1;
        return m;
    }

    static int[][] buildObstacleMatrix(int side) {
        int n = side * side;
        int[][] m = new int[n][n];
        Random rnd = new Random(42);
        Set<Integer> blocked = new HashSet<>();
        while (blocked.size() < n / 5) {
            int node = 1 + rnd.nextInt(n - 2);
            blocked.add(node);
        }
        for (int r = 0; r < side; r++) {
            for (int c = 0; c < side; c++) {
                int node = r * side + c;
                if (blocked.contains(node)) continue;
                int[] neighbors = {
                        (r-1)*side + c, (r+1)*side + c,
                        r*side + (c-1), r*side + (c+1)
                };
                for (int nb : neighbors)
                    if (nb >= 0 && nb < n && !blocked.contains(nb))
                        m[node][nb] = 1;
            }
        }
        return m;
    }

    static double[] runBenchmark(String matrixName, int[][] matrix, int start, int end) {

        // --- Secuencial ---
        GraphProblem seqProblem = new GraphProblem(matrix, start, end);
        long t0 = System.nanoTime();
        List<Integer> seqPath = sequentialBacktracking(seqProblem);
        double seqMs = (System.nanoTime() - t0) / 1_000_000.0;

        // --- Paralelo ---
        GraphProblem parProblem = new GraphProblem(matrix, start, end);
        AtomicInteger bestLen   = new AtomicInteger(Integer.MAX_VALUE);
        ForkJoinPool  pool      = new ForkJoinPool();
        long t1 = System.nanoTime();
        List<Integer> parPath = pool.invoke(new ParallelShortestPath(parProblem, bestLen));
        double parMs = (System.nanoTime() - t1) / 1_000_000.0;
        pool.shutdown();

        // --- Output ---
        System.out.println("\n=== Iniciando prueba de rendimiento [" + matrixName + "] ===");
        System.out.println("[Secuencial] Camino encontrado: " + (seqPath != null ? seqPath : "No existe"));
        System.out.printf("[Secuencial] Tiempo de ejecucion: %.3f ms%n", seqMs);
        System.out.println("[Paralelo]   Camino encontrado: " + (parPath != null ? parPath : "No existe"));
        System.out.printf("[Paralelo]   Tiempo de ejecucion: %.3f ms%n", parMs);

        double diff = seqMs - parMs;
        System.out.println("=== Resumen de Mejora ===");
        System.out.printf("Diferencia de tiempo: %.3f ms%n", diff);
        if (diff > 0.001 && parMs > 0.001) {
            System.out.printf(">> El PARALELO es mas rapido por %.3f ms (%.2fx mas rapido)%n", diff, seqMs / parMs);
        } else if (diff > 0.001) {
            System.out.printf(">> El PARALELO es mas rapido por %.3f ms%n", diff);
        } else if (diff < -0.001 && seqMs > 0.001) {
            System.out.printf(">> El SECUENCIAL es mas rapido por %.3f ms (%.2fx mas rapido)%n", Math.abs(diff), parMs / seqMs);
        } else if (diff < -0.001) {
            System.out.printf(">> El SECUENCIAL es mas rapido por %.3f ms%n", Math.abs(diff));
        } else {
            System.out.println(">> Ambos tomaron el mismo tiempo");
        }

        return new double[]{seqMs, parMs};
    }

    public static void main(String[] args) {
        System.out.println("=== Taller 7 - Backtracking Paralelo: Camino mas corto ===");

        double[] r1 = runBenchmark("DENSA (70% aristas, 10 nodos)",    buildDenseMatrix(10),    0, 9);
        double[] r2 = runBenchmark("DISPERSA (15% aristas, 10 nodos)", buildSparseMatrix(10),   0, 9);
        double[] r3 = runBenchmark("CON OBSTACULOS (cuadricula 4x4)",  buildObstacleMatrix(4),  0, 15);

        System.out.println("\n=========================================");
        System.out.println("  TABLA COMPARATIVA FINAL");
        System.out.println("=========================================");
        System.out.printf("%-32s %12s %12s %12s%n", "Matriz", "Secuencial", "Paralelo", "Diferencia");
        System.out.println("-".repeat(70));
        String[] nombres    = {"DENSA", "DISPERSA", "CON OBSTACULOS"};
        double[][] results  = {r1, r2, r3};
        for (int i = 0; i < 3; i++) {
            double diff = results[i][0] - results[i][1];
            String ganador = diff > 0.001 ? "(+Paralelo)" : diff < -0.001 ? "(+Secuencial)" : "(Empate)";
            System.out.printf("%-32s %9.3f ms %9.3f ms %7.3f ms %s%n",
                    nombres[i], results[i][0], results[i][1], diff, ganador);
        }
        System.out.println("=========================================");
    }
}
