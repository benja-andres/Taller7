import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveTask;
import java.util.concurrent.atomic.AtomicInteger;

interface ShortestPathProblem {
    boolean isSolution();
    void applyMove(int move);
    void undoMove(int move);
    List<Integer> getPossibleMoves();
    List<Integer> getCurrentPath();
    int getCurrentPathLength();
    ShortestPathProblem cloneProblem();
}

class ParallelShortestPath extends RecursiveTask<List<Integer>> {
    private ShortestPathProblem problem;
    private AtomicInteger bestPathLength;

    public ParallelShortestPath(ShortestPathProblem problem, AtomicInteger bestPathLength) {
        this.problem = problem;
        this.bestPathLength = bestPathLength;
    }

    @Override
    protected List<Integer> compute() {
        if (problem.getCurrentPathLength() >= bestPathLength.get()) return null;

        if (problem.isSolution()) {
            int len = problem.getCurrentPathLength();
            bestPathLength.getAndUpdate(cur -> Math.min(cur, len));
            return new ArrayList<>(problem.getCurrentPath());
        }

        List<Integer> shortestPath = null;
        List<Integer> moves = problem.getPossibleMoves();
        if (moves.isEmpty()) return null;

        List<ParallelShortestPath> tasks = new ArrayList<>();
        for (int move : moves) {
            problem.applyMove(move);
            tasks.add(new ParallelShortestPath(problem.cloneProblem(), bestPathLength));
            problem.undoMove(move);
        }

        for (int i = 0; i < tasks.size() - 1; i++) tasks.get(i).fork();

        List<Integer> local = tasks.get(tasks.size() - 1).compute();
        if (local != null && (shortestPath == null || local.size() < shortestPath.size()))
            shortestPath = local;

        for (int i = 0; i < tasks.size() - 1; i++) {
            List<Integer> p = tasks.get(i).join();
            if (p != null && (shortestPath == null || p.size() < shortestPath.size()))
                shortestPath = p;
        }
        return shortestPath;
    }
}

class MyShortestPathProblem implements ShortestPathProblem {
    private int[][] graph;
    private int targetNode, currentNode;
    private List<Integer> currentPath;
    private boolean[] visited;

    public MyShortestPathProblem(int[][] graph, int startNode, int targetNode) {
        this.graph = graph; this.targetNode = targetNode; this.currentNode = startNode;
        this.currentPath = new ArrayList<>(); this.visited = new boolean[graph.length];
        this.currentPath.add(startNode); this.visited[startNode] = true;
    }

    private MyShortestPathProblem(int[][] graph, int targetNode, int currentNode,
                                   List<Integer> path, boolean[] visited) {
        this.graph = graph; this.targetNode = targetNode; this.currentNode = currentNode;
        this.currentPath = new ArrayList<>(path); this.visited = visited.clone();
    }

    @Override public boolean isSolution() { return currentNode == targetNode; }
    @Override public void applyMove(int m) { currentPath.add(m); visited[m]=true; currentNode=m; }
    @Override public void undoMove(int m) {
        currentPath.remove(currentPath.size()-1); visited[m]=false;
        currentNode = currentPath.get(currentPath.size()-1);
    }
    @Override public List<Integer> getPossibleMoves() {
        List<Integer> mv = new ArrayList<>();
        for (int i=0; i<graph.length; i++) if (graph[currentNode][i]==1 && !visited[i]) mv.add(i);
        return mv;
    }
    @Override public List<Integer> getCurrentPath() { return currentPath; }
    @Override public int getCurrentPathLength() { return currentPath.size(); }
    @Override public ShortestPathProblem cloneProblem() {
        return new MyShortestPathProblem(graph, targetNode, currentNode, currentPath, visited);
    }
}

public class Main {

    // Grafo denso SIN arista directa start→target para forzar exploración profunda
    static int[][] generateHardGraph(int n, double density, int start, int target, long seed) {
        java.util.Random rng = new java.util.Random(seed);
        int[][] g = new int[n][n];
        for (int i = 0; i < n; i++)
            for (int j = i+1; j < n; j++)
                if (rng.nextDouble() < density) { g[i][j]=1; g[j][i]=1; }
        // Eliminar la arista directa para obligar caminos largos
        g[start][target] = 0;
        g[target][start] = 0;
        return g;
    }

    public static void main(String[] args) {
        final int N      = 22;       // nodos
        final int START  = 0;
        final int TARGET = N - 1;

        // Varios grafos de prueba para acumular trabajo visible
        long[][] seeds = {{42L}, {123L}, {999L}, {777L}, {314L}};

        long totalSeq = 0, totalPar = 0;

        System.out.println("=== Grafo: " + N + " nodos, densidad 75%, sin arista directa 0→" + TARGET + " ===");
        System.out.println("Núcleos disponibles: " + Runtime.getRuntime().availableProcessors());
        System.out.println("Ejecutando " + seeds.length + " instancias distintas...\n");

        for (int run = 0; run < seeds.length; run++) {
            long seed = seeds[run][0];
            int[][] graph = generateHardGraph(N, 0.75, START, TARGET, seed);

            // Secuencial
            long t0 = System.currentTimeMillis();
            List<Integer> pathSeq = solveSequential(
                new MyShortestPathProblem(graph, START, TARGET), Integer.MAX_VALUE);
            long durSeq = System.currentTimeMillis() - t0;
            totalSeq += durSeq;

            // Paralelo
            AtomicInteger best = new AtomicInteger(Integer.MAX_VALUE);
            long t1 = System.currentTimeMillis();
            ForkJoinPool pool = new ForkJoinPool();
            List<Integer> pathPar = pool.invoke(
                new ParallelShortestPath(new MyShortestPathProblem(graph, START, TARGET), best));
            pool.shutdown();
            long durPar = System.currentTimeMillis() - t1;
            totalPar += durPar;

            int lenSeq = pathSeq != null ? pathSeq.size() : -1;
            int lenPar = pathPar != null ? pathPar.size() : -1;
            System.out.printf("Run %d (seed=%d): Seq=%d ms (long=%d)  Par=%d ms (long=%d)  %s%n",
                run+1, seed, durSeq, lenSeq, durPar, lenPar,
                lenSeq == lenPar ? "✓" : "✗ MISMATCH");
        }

        System.out.println("\n=== Totales acumulados ===");
        System.out.println("Secuencial : " + totalSeq + " ms");
        System.out.println("Paralelo   : " + totalPar + " ms");
        long diff = totalSeq - totalPar;
        System.out.println("Diferencia : " + diff + " ms  (" +
            (diff > 0 ? "paralelo más rápido ✓" : "secuencial más rápido") + ")");
    }

    static List<Integer> solveSequential(ShortestPathProblem p, int best) {
        if (p.isSolution()) return new ArrayList<>(p.getCurrentPath());
        if (p.getCurrentPathLength() >= best) return null;
        List<Integer> shortest = null;
        for (int move : p.getPossibleMoves()) {
            p.applyMove(move);
            List<Integer> path = solveSequential(p, best);
            if (path != null && (shortest == null || path.size() < shortest.size())) {
                shortest = path; best = path.size();
            }
            p.undoMove(move);
        }
        return shortest;
    }
}
