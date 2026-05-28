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
    
    // Profundizamos el umbral porque el árbol ahora es masivo
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
        // Invertimos el bucle para cambiar el sesgo direccional de búsqueda
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

    // Generador de "Laberinto" en lugar de "Autopista"
    static int[][] generateLabyrinthGraph(int n, int layers, long seed) {
        java.util.Random rng = new java.util.Random(seed);
        int[][] g = new int[n][n];
        int perLayer = n / layers;
        
        for (int layer = 0; layer < layers - 1; layer++) {
            int from = layer * perLayer;
            int to   = (layer + 1) * perLayer;
            
            // Forzamos al menos 1 ruta garantizada para que haya solución
            for (int i = from; i < from + perLayer; i++) {
                int randomNext = to + rng.nextInt(Math.min(perLayer, n - to));
                g[i][randomNext] = 1; g[randomNext][i] = 1;
            }

            // Conexiones escasas entre capas (30% vs 85% anterior)
            for (int i = from; i < from + perLayer; i++)
                for (int j = to; j < Math.min(to + perLayer, n); j++)
                    if (rng.nextDouble() < 0.30) { g[i][j]=1; g[j][i]=1; }
            
            // Conexiones internas para despistar (20% vs 70% anterior)
            for (int i = from; i < from + perLayer; i++)
                for (int j = i+1; j < from + perLayer; j++)
                    if (rng.nextDouble() < 0.20) { g[i][j]=1; g[j][i]=1; }
        }
        return g;
    }

    public static void main(String[] args) {
        // Modo bestia: 300 nodos, 12 capas
        final int N      = 300; 
        final int TARGET = N - 1;
        final int START  = 0;
        final int REPS   = 1; 

        // Usamos nuestro nuevo generador de laberintos
        int[][] graph = generateLabyrinthGraph(N, 12, 101L);

        System.out.println("=== Iniciando STRESS TEST (Laberinto) ===");
        System.out.println("Núcleos disponibles: " + Runtime.getRuntime().availableProcessors());
        System.out.println("Nodos: " + N + " | Capas: 12 | Conexiones: Bajas (Alto Backtracking)\n");

        // --- Secuencial ---
        long startTimeSeq = System.currentTimeMillis();
        List<Integer> shortestPathSeq = null;
        for (int i = 0; i < REPS; i++)
            shortestPathSeq = solveSequentialPure(
                    new MyShortestPathProblem(graph, START, TARGET), Integer.MAX_VALUE);
        long durationSeq = System.currentTimeMillis() - startTimeSeq;

        System.out.println("[Secuencial] Camino encontrado: " + shortestPathSeq);
        System.out.println("[Secuencial] Longitud del camino: " +
                (shortestPathSeq != null ? shortestPathSeq.size() : "N/A"));
        System.out.println("[Secuencial] Tiempo de ejecución: " + durationSeq + " ms");

        // --- Paralelo Híbrido ---
        ForkJoinPool pool = new ForkJoinPool();
        long startTimePar = System.currentTimeMillis();
        List<Integer> shortestPathPar = null;
        for (int i = 0; i < REPS; i++) {
            AtomicInteger best = new AtomicInteger(Integer.MAX_VALUE);
            shortestPathPar = pool.invoke(
                    new ParallelShortestPath(new MyShortestPathProblem(graph, START, TARGET), best));
        }
        long durationPar = System.currentTimeMillis() - startTimePar;
        pool.shutdown();

        System.out.println("\n[Paralelo Híbrido] Camino más corto: " + shortestPathPar);
        System.out.println("[Paralelo Híbrido] Longitud del camino: " +
                (shortestPathPar != null ? shortestPathPar.size() : "N/A"));
        System.out.println("[Paralelo Híbrido] Tiempo de ejecución: " + durationPar + " ms");

        // --- Resumen ---
        System.out.println("\n=== Resumen de Mejora ===");
        long diff = durationSeq - durationPar;
        System.out.println("Diferencia de tiempo: " + diff + " ms");
        if (diff > 0)
            System.out.printf("Speedup: %.2fx más rápido en paralelo%n", (double) durationSeq / Math.max(1, durationPar));
        else
            System.out.println("Speedup: el secuencial fue más rápido en este entorno");
    }

    static List<Integer> solveSequentialPure(ShortestPathProblem p, int best) {
        if (p.getCurrentPathLength() >= best) return null;
        if (p.isSolution()) return new ArrayList<>(p.getCurrentPath());
        
        List<Integer> shortest = null;
        for (int move : p.getPossibleMoves()) {
            p.applyMove(move);
            List<Integer> path = solveSequentialPure(p, best);
            if (path != null && (shortest == null || path.size() < shortest.size())) {
                shortest = path; 
                best = path.size();
            }
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
            if (path != null && (shortest == null || path.size() < shortest.size())) {
                shortest = path;
            }
            p.undoMove(move);
        }
        return shortest;
    }
}
