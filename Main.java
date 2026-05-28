import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveTask;

public class Main {

    // =========================================================================
    // 1. INTERFAZ DEL PROBLEMA (Definida en el Taller 7) [cite: 26, 27]
    // =========================================================================
    public interface ShortestPathProblem {
        boolean isSolution(); [cite: 27]
        void applyMove(int move); [cite: 27]
        void undoMove(int move); [cite: 27]
        List<Integer> getPossibleMoves(); [cite: 27]
        List<Integer> getCurrentPath(); [cite: 27]
        int getCurrentPathLength(); [cite: 27]
    }

    // =========================================================================
    // 2. IMPLEMENTACIÓN CONCRETA DEL PROBLEMA (Grafo para Camino Más Corto)
    // =========================================================================
    public static class MyShortestPathProblem implements ShortestPathProblem {
        private final int[][] graph;
        private final int startNode;
        private final int endNode;
        private final List<Integer> currentPath;
        private final boolean[] visited;

        public MyShortestPathProblem(int[][] graph, int startNode, int endNode) {
            this.graph = graph;
            this.startNode = startNode;
            this.endNode = endNode;
            this.currentPath = new ArrayList<>();
            this.visited = new boolean[graph.length];
            
            // El camino inicial comienza en el nodo de origen
            this.currentPath.add(startNode);
            this.visited[startNode] = true;
        }

        // Constructor de clonación: Crucial para que los hilos no compartan memoria ni colisionen
        public MyShortestPathProblem(MyShortestPathProblem target) {
            this.graph = target.graph;
            this.startNode = target.startNode;
            this.endNode = target.endNode;
            this.currentPath = new ArrayList<>(target.currentPath);
            this.visited = target.visited.clone();
        }

        @Override
        public boolean isSolution() {
            return !currentPath.isEmpty() && currentPath.get(currentPath.size() - 1) == endNode; [cite: 27]
        }

        @Override
        public void applyMove(int move) {
            currentPath.add(move); [cite: 27]
            visited[move] = true;
        }

        @Override
        public void undoMove(int move) {
            if (!currentPath.isEmpty() && currentPath.get(currentPath.size() - 1) == move) {
                currentPath.remove(currentPath.size() - 1); [cite: 27]
                visited[move] = false;
            }
        }

        @Override
        public List<Integer> getPossibleMoves() {
            List<Integer> moves = new ArrayList<>(); [cite: 27]
            int currentNode = currentPath.get(currentPath.size() - 1);
            
            if (currentNode == endNode) return moves;

            for (int neighbor = 0; neighbor < graph.length; neighbor++) {
                if (graph[currentNode][neighbor] > 0 && !visited[neighbor]) {
                    moves.add(neighbor);
                }
            }
            return moves; [cite: 27]
        }

        @Override
        public List<Integer> getCurrentPath() {
            return new ArrayList<>(currentPath); [cite: 27]
        }

        @Override
        public int getCurrentPathLength() {
            return currentPath.size(); [cite: 27]
        }
    }

    // =========================================================================
    // 3. ENFOQUE PARALELO: DIVIDE Y VENCERÁS (ForkJoinPool) [cite: 24, 29]
    // =========================================================================
    public static class ParallelShortestPath extends RecursiveTask<List<Integer>> { [cite: 24, 29]
        private final MyShortestPathProblem problem;
        private int bestPathLength;

        public ParallelShortestPath(MyShortestPathProblem problem, int bestPathLength) { [cite: 29]
            this.problem = problem; [cite: 29]
            this.bestPathLength = bestPathLength; [cite: 29]
        } [cite: 29]

        @Override
        protected List<Integer> compute() { [cite: 29]
            if (problem.isSolution()) { [cite: 29]
                return problem.getCurrentPath(); [cite: 29]
            } [cite: 29]

            List<Integer> shortestPath = null; [cite: 29]
            List<Integer> moves = problem.getPossibleMoves(); [cite: 29]
            List<ParallelShortestPath> tasks = new ArrayList<>(); [cite: 29]

            for (int move : moves) { [cite: 29]
                // Clonamos el estado para aislar la rama de ejecución de cada hilo
                MyShortestPathProblem problemClone = new MyShortestPathProblem(this.problem);
                problemClone.applyMove(move); [cite: 29]

                if (problemClone.getCurrentPathLength() < bestPathLength) { [cite: 29]
                    ParallelShortestPath child = new ParallelShortestPath(problemClone, bestPathLength); [cite: 29]
                    tasks.add(child); [cite: 29]
                } [cite: 29]
                // El undoMove secuencial se omite aquí ya que cada hilo maneja su propia copia aislada [cite: 29]
            } [cite: 29]

            // Fork: Bifurcar subtareas de forma asíncrona en el Pool [cite: 29, 32]
            for (ParallelShortestPath task : tasks) { [cite: 29]
                task.fork(); [cite: 29]
            } [cite: 29]

            // Join: Sincronizar hilos, recolectar soluciones y podar caminos ineficientes [cite: 29, 32]
            for (ParallelShortestPath task : tasks) { [cite: 29]
                List<Integer> path = task.join(); [cite: 29]
                if (path != null && (shortestPath == null || path.size() < shortestPath.size())) { [cite: 29]
                    shortestPath = path; [cite: 29]
                    bestPathLength = path.size(); [cite: 29]
                } [cite: 29]
            } [cite: 29]

            return shortestPath; [cite: 29]
        }
    }

    // =========================================================================
    // 4. ENFOQUE SECUENCIAL: BACKTRACKING CLÁSICO (Línea base de comparación)
    // =========================================================================
    public static class SequentialShortestPath {
        private List<Integer> bestPath = null;
        private int bestPathLength = Integer.MAX_VALUE;

        public List<Integer> findShortestPath(MyShortestPathProblem problem) {
            solve(problem);
            return bestPath;
        }

        private void solve(MyShortestPathProblem problem) {
            if (problem.isSolution()) {
                List<Integer> current = problem.getCurrentPath();
                if (current.size() < bestPathLength) {
                    bestPath = current;
                    bestPathLength = current.size();
                }
                return;
            }

            for (int move : problem.getPossibleMoves()) {
                problem.applyMove(move);
                // Poda de árboles: Solo seguir explorando si el camino actual es prometedor
                if (problem.getCurrentPathLength() < bestPathLength) {
                    solve(problem);
                }
                problem.undoMove(move);
            }
        }
    }

    // =========================================================================
    // 5. MÉTODO PRINCIPAL: EJECUCIÓN Y BENCHMARKING DE RENDIMIENTO [cite: 31]
    // =========================================================================
    public static void main(String[] args) { [cite: 31]
        // Generación de un grafo denso de 13 nodos para crear suficiente complejidad computacional
        int numNodes = 13;
        int[][] denseGraph = new int[numNodes][numNodes];
        for (int i = 0; i < numNodes; i++) {
            for (int j = 0; j < numNodes; j++) {
                if (i != j) denseGraph[i][j] = 1; // Conexión completa para forzar la recursión exhaustiva
            }
        }

        int startNode = 0;
        int endNode = numNodes - 1;

        System.out.println("=================================================");
        System.out.println("  INICIANDO DEMOSTRACIÓN DE PARALELIZACIÓN       ");
        System.out.println("=================================================");

        // --- MÓDULO 1: EJECUCIÓN SECUENCIAL ---
        MyShortestPathProblem sequentialProblem = new MyShortestPathProblem(denseGraph, startNode, endNode);
        SequentialShortestPath sequentialSolver = new SequentialShortestPath();
        
        long startTimeSeq = System.nanoTime();
        List<Integer> shortestPathSeq = sequentialSolver.findShortestPath(sequentialProblem);
        long endTimeSeq = System.nanoTime();
        
        double durationSeq = (endTimeSeq - startTimeSeq) / 1_000_000.0; // Conversión a milisegundos

        System.out.println("\n[MÉTODO SECUENCIAL] Ejecutando en un único hilo...");
        System.out.println(" -> Camino más corto encontrado: " + shortestPathSeq);
        System.out.printf(" -> Tiempo de ejecución: %.2f ms\n", durationSeq);

        // --- MÓDULO 2: EJECUCIÓN PARALELA CON FORKJOINPOOL --- [cite: 31]
        MyShortestPathProblem parallelProblem = new MyShortestPathProblem(denseGraph, startNode, endNode); [cite: 31]
        int initialBestPathLength = Integer.MAX_VALUE; [cite: 31]
        
        // El constructor vacío utiliza automáticamente todos los núcleos asignados al entorno (ej. tus 5 núcleos virtuales) [cite: 31]
        ForkJoinPool forkJoinPool = new ForkJoinPool(); [cite: 31]
        ParallelShortestPath parallelShortestPath = new ParallelShortestPath(parallelProblem, initialBestPathLength); [cite: 31]
        
        long startTimePar = System.nanoTime();
        List<Integer> shortestPathPar = forkJoinPool.invoke(parallelShortestPath); [cite: 31]
        long endTimePar = System.nanoTime();
        
        double durationPar = (endTimePar - startTimePar) / 1_000_000.0; // Conversión a milisegundos

        System.out.println("\n[MÉTODO PARALELO] Ejecutando mediante ForkJoinPool (Multi-Thread)...");
        System.out.println(" -> Camino más corto encontrado: " + shortestPathPar); [cite: 31]
        System.out.printf(" -> Tiempo de ejecución: %.2f ms\n", durationPar);

        // --- MÓDULO 3: ANÁLISIS DE RENDIMIENTO (SOLICITUD DEL TALLER) --- [cite: 41, 42]
        double speedup = durationSeq / durationPar;
        System.out.println("\n=================================================");
        System.out.println("          RESULTADOS E INDICADORES               ");
        System.out.println("=================================================");
        System.out.printf(" MEJORA DE RENDIMIENTO (SPEEDUP): %.2fx\n", speedup);
        if (speedup > 1.0) {
            System.out.printf(" El algoritmo paralelo es un %.1f%% más rápido.\n", (speedup - 1) * 100);
        } else {
            System.out.println(" Nota: El costo de creación de hilos superó el tiempo de cómputo.");
        }
        System.out.println("=================================================");
        
        forkJoinPool.shutdown();
    }
}
