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

    public static void main(String[] args) {
        // Matriz de adyacencia 20x20
        int[][] graph = {
            {0,1,1,1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0},
            {1,0,1,0,1,1,0,0,0,0,0,0,0,0,0,0,0,0,0,0},
            {1,1,0,1,0,1,1,0,0,0,0,0,0,0,0,0,0,0,0,0},
            {1,0,1,0,0,0,1,1,0,0,0,0,0,0,0,0,0,0,0,0},
            {0,1,0,0,0,1,0,0,1,1,0,0,0,0,0,0,0,0,0,0},
            {0,1,1,0,1,0,1,0,0,1,1,0,0,0,0,0,0,0,0,0},
            {0,0,1,1,0,1,0,1,0,0,1,1,0,0,0,0,0,0,0,0},
            {0,0,0,1,0,0,1,0,0,0,0,1,1,0,0,0,0,0,0,0},
            {0,0,0,0,1,0,0,0,0,1,0,0,0,1,1,0,0,0,0,0},
            {0,0,0,0,1,1,0,0,1,0,1,0,0,0,1,1,0,0,0,0},
            {0,0,0,0,0,1,1,0,0,1,0,1,0,0,0,1,1,0,0,0},
            {0,0,0,0,0,0,1,1,0,0,1,0,1,0,0,0,1,1,0,0},
            {0,0,0,0,0,0,0,1,0,0,0,1,0,1,0,0,0,1,1,0},
            {0,0,0,0,0,0,0,0,1,0,0,0,1,0,1,0,0,0,1,1},
            {0,0,0,0,0,0,0,0,1,1,0,0,0,1,0,1,0,0,0,1},
            {0,0,0,0,0,0,0,0,0,1,1,0,0,0,1,0,1,0,0,1},
            {0,0,0,0,0,0,0,0,0,0,1,1,0,0,0,1,0,1,0,1},
            {0,0,0,0,0,0,0,0,0,0,0,1,1,0,0,0,1,0,1,1},
            {0,0,0,0,0,0,0,0,0,0,0,0,1,1,0,0,0,1,0,1},
            {0,0,0,0,0,0,0,0,0,0,0,0,0,1,1,1,1,1,1,0}
        };

        int startNode  = 0;
        int targetNode = 19;
        int initialBestPathLength = Integer.MAX_VALUE;

        System.out.println("=== Iniciando prueba de rendimiento ===");

        // Prueba Secuencial
        long startTimeSeq = System.currentTimeMillis();
        ShortestPathProblem problemSeq = new MyShortestPathProblem(graph, startNode, targetNode);
        List<Integer> shortestPathSeq = solveSequential(problemSeq, initialBestPathLength);
        long endTimeSeq = System.currentTimeMillis();
        long durationSeq = endTimeSeq - startTimeSeq;

        if (shortestPathSeq != null) {
            System.out.println("\n[Secuencial] Camino encontrado: " + shortestPathSeq);
            System.out.println("[Secuencial] Longitud del camino: " + shortestPathSeq.size());
        } else {
            System.out.println("\n[Secuencial] No se encontró ningún camino.");
        }
        System.out.println("[Secuencial] Tiempo de ejecución: " + durationSeq + " ms");

        // Prueba Paralela
        AtomicInteger bestLengthAtomic = new AtomicInteger(Integer.MAX_VALUE);
        long startTimePar = System.currentTimeMillis();
        ShortestPathProblem problemPar = new MyShortestPathProblem(graph, startNode, targetNode);
        ForkJoinPool forkJoinPool = new ForkJoinPool();
        ParallelShortestPath parallelShortestPath = new ParallelShortestPath(problemPar, bestLengthAtomic);
        List<Integer> shortestPathPar = forkJoinPool.invoke(parallelShortestPath);
        forkJoinPool.shutdown();
        long endTimePar = System.currentTimeMillis();
        long durationPar = endTimePar - startTimePar;

        if (shortestPathPar != null) {
            System.out.println("\n[Paralelo] Camino más corto: " + shortestPathPar);
            System.out.println("[Paralelo] Longitud del camino: " + shortestPathPar.size());
        } else {
            System.out.println("\n[Paralelo] No se encontró ningún camino.");
        }
        System.out.println("[Paralelo] Tiempo de ejecución: " + durationPar + " ms");

        // Resultados
        System.out.println("\n=== Resumen de Mejora ===");
        System.out.println("Diferencia de tiempo: " + (durationSeq - durationPar) + " ms");
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
