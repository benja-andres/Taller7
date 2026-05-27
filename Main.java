import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveTask;

// --- 1. La Interfaz del Problema ---
interface ShortestPathProblem {
    boolean isSolution();
    void applyMove(int move);
    void undoMove(int move);
    List<Integer> getPossibleMoves();
    List<Integer> getCurrentPath();
    int getCurrentPathLength();
    // Método añadido para evitar condiciones de carrera en hilos paralelos
    ShortestPathProblem cloneProblem(); 
}

// --- 2. La Tarea Recursiva (ForkJoin) ---
class ParallelShortestPath extends RecursiveTask<List<Integer>> {
    private ShortestPathProblem problem;
    private int bestPathLength;

    public ParallelShortestPath(ShortestPathProblem problem, int bestPathLength) {
        this.problem = problem;
        this.bestPathLength = bestPathLength;
    }

    @Override
    protected List<Integer> compute() {
        if (problem.isSolution()) {
            return new ArrayList<>(problem.getCurrentPath());
        }

        List<Integer> shortestPath = null;
        List<Integer> moves = problem.getPossibleMoves();
        List<ParallelShortestPath> tasks = new ArrayList<>();

        for (int move : moves) {
            problem.applyMove(move);

            if (problem.getCurrentPathLength() < bestPathLength) {
                // Clonamos el problema con el movimiento actual aplicado para el nuevo hilo
                ShortestPathProblem childProblem = problem.cloneProblem();
                ParallelShortestPath child = new ParallelShortestPath(childProblem, bestPathLength);
                tasks.add(child);
            }

            problem.undoMove(move);
        }

        // Bifurcar tareas para ejecución paralela
        for (ParallelShortestPath task : tasks) {
            task.fork();
        }

        // Unir resultados y encontrar el camino más corto
        for (ParallelShortestPath task : tasks) {
            List<Integer> path = task.join();
            if (path != null && (shortestPath == null || path.size() < shortestPath.size())) {
                shortestPath = path;
                bestPathLength = path.size();
            }
        }

        return shortestPath;
    }
}

// --- 3. Implementación Concreta del Laberinto/Grafo ---
class MyShortestPathProblem implements ShortestPathProblem {
    private int[][] graph;
    private int targetNode;
    private int currentNode;
    private List<Integer> currentPath;
    private boolean[] visited;

    public MyShortestPathProblem(int[][] graph, int startNode, int targetNode) {
        this.graph = graph;
        this.targetNode = targetNode;
        this.currentNode = startNode;
        this.currentPath = new ArrayList<>();
        this.visited = new boolean[graph.length];
        
        this.currentPath.add(startNode);
        this.visited[startNode] = true;
    }

    private MyShortestPathProblem(int[][] graph, int targetNode, int currentNode, List<Integer> currentPath, boolean[] visited) {
        this.graph = graph;
        this.targetNode = targetNode;
        this.currentNode = currentNode;
        this.currentPath = new ArrayList<>(currentPath);
        this.visited = visited.clone();
    }

    @Override
    public boolean isSolution() {
        return currentNode == targetNode;
    }

    @Override
    public void applyMove(int move) {
        currentPath.add(move);
        visited[move] = true;
        currentNode = move;
    }

    @Override
    public void undoMove(int move) {
        currentPath.remove(currentPath.size() - 1);
        visited[move] = false;
        currentNode = currentPath.get(currentPath.size() - 1);
    }

    @Override
    public List<Integer> getPossibleMoves() {
        List<Integer> moves = new ArrayList<>();
        for (int i = 0; i < graph.length; i++) {
            if (graph[currentNode][i] == 1 && !visited[i]) {
                moves.add(i);
            }
        }
        return moves;
    }

    @Override
    public List<Integer> getCurrentPath() {
        return currentPath;
    }

    @Override
    public int getCurrentPathLength() {
        return currentPath.size();
    }

    @Override
    public ShortestPathProblem cloneProblem() {
        return new MyShortestPathProblem(this.graph, this.targetNode, this.currentNode, this.currentPath, this.visited);
    }
}

// --- 4. Clase Principal y Demostración ---
public class Main {
    public static void main(String[] args) {
        // Grafo de ejemplo
        int[][] graph = {
            {0, 1, 1, 1, 0, 0, 0, 0, 0, 0},
            {1, 0, 1, 0, 1, 1, 0, 0, 0, 0},
            {1, 1, 0, 1, 0, 1, 1, 0, 0, 0},
            {1, 0, 1, 0, 0, 0, 1, 1, 0, 0},
            {0, 1, 0, 0, 0, 1, 0, 0, 1, 0},
            {0, 1, 1, 0, 1, 0, 1, 0, 1, 1},
            {0, 0, 1, 1, 0, 1, 0, 1, 0, 1},
            {0, 0, 0, 1, 0, 0, 1, 0, 0, 1},
            {0, 0, 0, 0, 1, 1, 0, 0, 0, 1},
            {0, 0, 0, 0, 0, 1, 1, 1, 1, 0}
        };

        int startNode = 0;
        int targetNode = 9;
        int initialBestPathLength = Integer.MAX_VALUE;

        System.out.println("=== Iniciando prueba de rendimiento ===");

        // Prueba Secuencial
        long startTimeSeq = System.currentTimeMillis();
        ShortestPathProblem problemSeq = new MyShortestPathProblem(graph, startNode, targetNode);
        List<Integer> shortestPathSeq = solveSequential(problemSeq, initialBestPathLength);
        long endTimeSeq = System.currentTimeMillis();
        long durationSeq = endTimeSeq - startTimeSeq;

        System.out.println("\n[Secuencial] Camino encontrado: " + shortestPathSeq);
        System.out.println("[Secuencial] Tiempo de ejecución: " + durationSeq + " ms");

        // Prueba Paralela
        long startTimePar = System.currentTimeMillis();
        ShortestPathProblem problemPar = new MyShortestPathProblem(graph, startNode, targetNode);
        ForkJoinPool forkJoinPool = new ForkJoinPool();
        ParallelShortestPath parallelShortestPath = new ParallelShortestPath(problemPar, initialBestPathLength);
        List<Integer> shortestPathPar = forkJoinPool.invoke(parallelShortestPath);
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

    private static List<Integer> solveSequential(ShortestPathProblem problem, int bestPathLength) {
        if (problem.isSolution()) {
            return new ArrayList<>(problem.getCurrentPath());
        }

        List<Integer> shortestPath = null;
        for (int move : problem.getPossibleMoves()) {
            problem.applyMove(move);
            if (problem.getCurrentPathLength() < bestPathLength) {
                List<Integer> path = solveSequential(problem, bestPathLength);
                if (path != null && (shortestPath == null || path.size() < shortestPath.size())) {
                    shortestPath = path;
                    bestPathLength = path.size();
                }
            }
            problem.undoMove(move);
        }
        return shortestPath;
    }
}