import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveTask;
import java.util.concurrent.atomic.AtomicInteger; // FIX 1: para evitar race conditions

// --- 1. La Interfaz del Problema ---
interface ShortestPathProblem {
    boolean isSolution();
    void applyMove(int move);
    void undoMove(int move);
    List<Integer> getPossibleMoves();
    List<Integer> getCurrentPath();
    int getCurrentPathLength();
    ShortestPathProblem cloneProblem();
}

// --- 2. La Tarea Recursiva (ForkJoin) ---
class ParallelShortestPath extends RecursiveTask<List<Integer>> {
    private ShortestPathProblem problem;
    // FIX 2: usar AtomicInteger compartido para que todas las tareas
    // vean la mejor longitud actualizada y puedan podar ramas
    private AtomicInteger bestPathLength;

    public ParallelShortestPath(ShortestPathProblem problem, AtomicInteger bestPathLength) {
        this.problem = problem;
        this.bestPathLength = bestPathLength;
    }

    @Override
    protected List<Integer> compute() {
        // FIX 3: verificar si ya encontramos un camino igual o más corto
        // antes de procesar (poda temprana)
        if (problem.getCurrentPathLength() >= bestPathLength.get()) {
            return null;
        }

        if (problem.isSolution()) {
            // FIX 4: actualizar bestPathLength de forma atómica al encontrar solución
            int len = problem.getCurrentPathLength();
            bestPathLength.getAndUpdate(current -> Math.min(current, len));
            return new ArrayList<>(problem.getCurrentPath());
        }

        List<Integer> shortestPath = null;
        List<Integer> moves = problem.getPossibleMoves();

        // FIX 5: si no hay movimientos posibles, retornar null (sin camino)
        if (moves.isEmpty()) {
            return null;
        }

        List<ParallelShortestPath> tasks = new ArrayList<>();

        for (int move : moves) {
            problem.applyMove(move);

            // Clonar el problema con el movimiento ya aplicado
            ShortestPathProblem childProblem = problem.cloneProblem();
            // FIX 6: pasar el AtomicInteger compartido, no una copia del valor
            ParallelShortestPath child = new ParallelShortestPath(childProblem, bestPathLength);
            tasks.add(child);

            problem.undoMove(move);
        }

        // Bifurcar la primera tarea y ejecutar el resto en este hilo (optimización)
        // FIX 7: hacer fork de todas menos la última, y ejecutar la última localmente
        if (!tasks.isEmpty()) {
            for (int i = 0; i < tasks.size() - 1; i++) {
                tasks.get(i).fork();
            }

            // Ejecutar la última tarea directamente en este hilo
            List<Integer> localResult = tasks.get(tasks.size() - 1).compute();
            if (localResult != null && (shortestPath == null || localResult.size() < shortestPath.size())) {
                shortestPath = localResult;
            }

            // Unir el resto
            for (int i = 0; i < tasks.size() - 1; i++) {
                List<Integer> path = tasks.get(i).join();
                if (path != null && (shortestPath == null || path.size() < shortestPath.size())) {
                    shortestPath = path;
                }
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

    // Constructor privado para clonar
    private MyShortestPathProblem(int[][] graph, int targetNode, int currentNode,
                                   List<Integer> currentPath, boolean[] visited) {
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
        // FIX 8: obtener el nodo actual del path después de remover, no del parámetro
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
        return new MyShortestPathProblem(this.graph, this.targetNode,
                this.currentNode, this.currentPath, this.visited);
    }
}

// --- 4. Clase Principal y Demostración ---
public class Main {
    public static void main(String[] args) {
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

        System.out.println("=== Iniciando prueba de rendimiento ===");

        // --- Prueba Secuencial ---
        long startTimeSeq = System.currentTimeMillis();
        ShortestPathProblem problemSeq = new MyShortestPathProblem(graph, startNode, targetNode);
        List<Integer> shortestPathSeq = solveSequential(problemSeq, Integer.MAX_VALUE);
        long durationSeq = System.currentTimeMillis() - startTimeSeq;

        if (shortestPathSeq != null) {
            System.out.println("\n[Secuencial] Camino encontrado: " + shortestPathSeq);
            System.out.println("[Secuencial] Longitud del camino: " + shortestPathSeq.size());
        } else {
            System.out.println("\n[Secuencial] No se encontró ningún camino.");
        }
        System.out.println("[Secuencial] Tiempo de ejecución: " + durationSeq + " ms");

        // --- Prueba Paralela ---
        // FIX 9: usar AtomicInteger en lugar de int primitivo
        AtomicInteger bestLengthAtomic = new AtomicInteger(Integer.MAX_VALUE);

        long startTimePar = System.currentTimeMillis();
        ShortestPathProblem problemPar = new MyShortestPathProblem(graph, startNode, targetNode);
        ForkJoinPool forkJoinPool = new ForkJoinPool();
        ParallelShortestPath parallelTask = new ParallelShortestPath(problemPar, bestLengthAtomic);
        List<Integer> shortestPathPar = forkJoinPool.invoke(parallelTask);
        forkJoinPool.shutdown(); // FIX 10: cerrar el pool al terminar
        long durationPar = System.currentTimeMillis() - startTimePar;

        if (shortestPathPar != null) {
            System.out.println("\n[Paralelo]   Camino encontrado: " + shortestPathPar);
            System.out.println("[Paralelo]   Longitud del camino: " + shortestPathPar.size());
        } else {
            System.out.println("\n[Paralelo]   No se encontró ningún camino.");
        }
        System.out.println("[Paralelo]   Tiempo de ejecución: " + durationPar + " ms");

        // --- Resumen ---
        System.out.println("\n=== Resumen de Mejora ===");
        System.out.println("Diferencia de tiempo: " + (durationSeq - durationPar) + " ms");

        // FIX 11: verificar que ambas versiones coincidan en el resultado
        if (shortestPathSeq != null && shortestPathPar != null) {
            boolean sameLenght = shortestPathSeq.size() == shortestPathPar.size();
            System.out.println("¿Coinciden en longitud? " + (sameLenght ? "Sí ✓" : "No ✗"));
        }
    }

    private static List<Integer> solveSequential(ShortestPathProblem problem, int bestPathLength) {
        if (problem.isSolution()) {
            return new ArrayList<>(problem.getCurrentPath());
        }

        // FIX 12: poda — no seguir si ya superamos el mejor camino conocido
        if (problem.getCurrentPathLength() >= bestPathLength) {
            return null;
        }

        List<Integer> shortestPath = null;
        for (int move : problem.getPossibleMoves()) {
            problem.applyMove(move);
            List<Integer> path = solveSequential(problem, bestPathLength);
            if (path != null && (shortestPath == null || path.size() < shortestPath.size())) {
                shortestPath = path;
                bestPathLength = path.size(); // actualizar para podar ramas futuras
            }
            problem.undoMove(move);
        }
        return shortestPath;
    }
}
