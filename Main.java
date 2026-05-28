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
    private static final int FORK_THRESHOLD = 4; 

    public ParallelShortestPath(ShortestPathProblem problem, AtomicInteger bestPathLength) {
        this.problem = problem;
        this.bestPathLength = bestPathLength;
    }

    @Override
    protected List<Integer> compute() {
        if (problem.getCurrentPathLength() >= bestPathLength.get()) return null;
        if (problem.isSolution()) {
            int len = problem.getCurrentPathLength();
            bestPathLength.updateAndGet(cur -> Math.min(cur, len));
            return new ArrayList<>(problem.getCurrentPath());
        }
        List<Integer> moves = problem.getPossibleMoves();
        if (moves.isEmpty()) return null;

        if (problem.getCurrentPathLength() >= FORK_THRESHOLD) {
            return Main.solveSequentialLocal(problem, bestPathLength);
        }

        List<Integer> shortestPath = null;
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
    private MyShortestPathProblem(int[][] graph, int targetNode, int currentNode, List<Integer> path, boolean[] visited) {
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
        for (int i=graph.length-1; i>=0; i--) if (graph[currentNode][i]==1 && !visited[i]) mv.add(i);
        return mv;
    }
    @Override public List<Integer> getCurrentPath() { return currentPath; }
    @Override public int getCurrentPathLength() { return currentPath.size(); }
    @Override public ShortestPathProblem cloneProblem() {
        return new MyShortestPathProblem(graph, targetNode, currentNode, currentPath, visited);
    }
}

public class Main {

    // Generador flexible de matrices
    static int[][] generateGraph(int n, int layers, double density, long seed) {
        java.util.Random rng = new java.util.Random(seed);
        int[][] g = new int[n][n];
        int perLayer = n / layers;
        for (int layer = 0; layer < layers - 1; layer++) {
            int from = layer * perLayer;
            int to   = (layer + 1) * perLayer;
            for (int i = from; i < from + perLayer; i++) {
                int randomNext = to + rng.nextInt(Math.min(perLayer, n - to));
                g[i][randomNext] = 1; g[randomNext][i] = 1;
            }
            for (int i = from; i < from + perLayer; i++)
                for (int j = to; j < Math.min(to + perLayer, n); j++)
                    if (rng.nextDouble() < density) { g[i][j]=1; g[j][i]=1; }
        }
        return g;
    }

    public static void main(String[] args) {
        System.out.println("=== BENCHMARK DE MATRICES (ESTRES DINÁMICO) ===");
        System.out.println("Núcleos de CPU detectados: " + Runtime.getRuntime().availableProcessors() + "\n");

        // Definimos los 4 escenarios de matrices a comparar
        String[] nombres = { "Matriz 1: Pequeña e hiperconectada", "Matriz 2: Mediana estándar", "Matriz 3: Grande (Laberinto complejo)", "Matriz 4: Monstruo masivo" };
        int[] nodos      = { 25, 80, 250, 400 };
        int[] capas      = { 5,  8,  12,  15 };
        double[] densidades = { 0.85, 0.50, 0.25, 0.18 }; // Menor densidad = más caminos difíciles (backtracking)

        List<String> reporteFinal = new ArrayList<>();
        ForkJoinPool pool = new ForkJoinPool();

        for (int k = 0; k < nombres.length; k++) {
            int N = nodos[k];
            int[][] graph = generateGraph(N, capas[k], densidades[k], 101L);
            
            System.out.println("Ejecutando " + nombres[k] + " (" + N + " nodos)...");

            // --- TEST SECUENCIAL ---
            long startSeq = System.currentTimeMillis();
            List<Integer> pathSeq = solveSequentialPure(new MyShortestPathProblem(graph, 0, N - 1), Integer.MAX_VALUE);
            long timeSeq = System.currentTimeMillis() - startSeq;

            // --- TEST PARALELO ---
            long startPar = System.currentTimeMillis();
            AtomicInteger best = new AtomicInteger(Integer.MAX_VALUE);
            List<Integer> pathPar = pool.invoke(new ParallelShortestPath(new MyShortestPathProblem(graph, 0, N - 1), best));
            long timePar = System.currentTimeMillis() - startPar;

            // Calcular Speedup
            double speedup = (double) timeSeq / Math.max(1, timePar);
            String ganador = (timeSeq < timePar) ? "Secuencial" : (timeSeq > timePar ? "Paralelo" : "Empate");
            
            reporteFinal.add(String.format("| %-32s | %-6d | %-7d ms | %-7d ms | %-8.2fx | %-10s |", 
                    nombres[k], N, timeSeq, timePar, speedup, ganador));
        }
        pool.shutdown();

        // Imprimir el Gran Resumen
        System.out.println("\n==========================================================================================");
        System.out.println("                                TABLA COMPARATIVA FINAL                                   ");
        System.out.println("==========================================================================================");
        System.out.println("| Escenario                        | Nodos  | Secuenc.  | Paralelo  | Speedup  | Ganador    |");
        System.out.println("==========================================================================================");
        for (String linea : reporteFinal) {
            System.out.println(linea);
        }
        System.out.println("==========================================================================================");
    }

    static List<Integer> solveSequentialPure(ShortestPathProblem p, int best) {
        if (p.getCurrentPathLength() >= best) return null;
        if (p.isSolution()) return new ArrayList<>(p.getCurrentPath());
        List<Integer> shortest = null;
        for (int move : p.getPossibleMoves()) {
            p.applyMove(move);
            List<Integer> path = solveSequentialPure(p, best);
            if (path != null && (shortest == null || path.size() < shortest.size())) { shortest = path; best = path.size(); }
            p.undoMove(move);
        }
        return shortest;
    }

    public static List<Integer> solveSequentialLocal(ShortestPathProblem p, AtomicInteger globalBest) {
        if (p.getCurrentPathLength() >= globalBest.get()) return null;
        if (p.isSolution()) {
            int len = p.getCurrentPathLength();
            globalBest.updateAndGet(cur -> Math.min(cur, len));
            return new ArrayList<>(p.getCurrentPath());
        }
        List<Integer> shortest = null;
        for (int move : p.getPossibleMoves()) {
            p.applyMove(move);
            List<Integer> path = solveSequentialLocal(p, globalBest);
            if (path != null && (shortest == null || path.size() < shortest.size())) shortest = path;
            p.undoMove(move);
        }
        return shortest;
    }
}
