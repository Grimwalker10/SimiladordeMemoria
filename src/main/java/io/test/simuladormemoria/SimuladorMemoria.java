package io.test.simuladormemoria;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import javafx.geometry.*;
import java.util.*;

public class SimuladorMemoria extends Application {

    private final List<BloqueMemoria> memoriaFisica = new ArrayList<>();
    private final List<Proceso> swap = new ArrayList<>();
    private final int TAMANIO_RAM = 100;
    private String algoritmo;
    private String algoritmoReemplazo = "LRU";
    private VBox memoriaView;
    private ComboBox<String> liberarCombo;
    private ComboBox<String> swapCombo;
    private ComboBox<String> comboReemplazo;
    private Scene escenaMenu;
    private Scene escenaSimulacion;

    private int nextFitStartIndex = 0;
    private int clockHand = 0;
    private long tiempoSimulacion = 0;

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) {
        primaryStage.setTitle("Simulador de Gestión de Memoria");
        crearMenuPrincipal(primaryStage);
        primaryStage.show();
    }

    private void crearMenuPrincipal(Stage primaryStage) {
        VBox mainMenu = new VBox(20);
        mainMenu.setAlignment(Pos.CENTER);
        mainMenu.setPadding(new Insets(20));

        Button[] botonesAlgoritmos = {
                crearBotonAlgoritmo("First Fit", primaryStage),
                crearBotonAlgoritmo("Best Fit", primaryStage),
                crearBotonAlgoritmo("Worst Fit", primaryStage),
                crearBotonAlgoritmo("Next Fit", primaryStage)
        };

        Button salirBtn = new Button("Salir");
        salirBtn.setOnAction(e -> primaryStage.close());

        mainMenu.getChildren().addAll(botonesAlgoritmos);
        mainMenu.getChildren().add(salirBtn);

        escenaMenu = new Scene(mainMenu, 300, 300);
        primaryStage.setScene(escenaMenu);
    }

    private Button crearBotonAlgoritmo(String nombreAlgoritmo, Stage stage) {
        Button btn = new Button(nombreAlgoritmo);
        btn.setOnAction(e -> {
            algoritmo = nombreAlgoritmo;
            inicializarMemoria();
            swap.clear();
            crearInterfazSimulacion(stage);
            stage.setScene(escenaSimulacion);
        });
        btn.setMinWidth(150);
        return btn;
    }

    private void crearInterfazSimulacion(Stage primaryStage) {
        VBox contenedorPrincipal = new VBox(10);
        contenedorPrincipal.setPadding(new Insets(10));

        // Componentes de entrada
        TextField nombreField = new TextField();
        nombreField.setPromptText("Nombre del proceso");
        TextField tamanoField = new TextField();
        tamanoField.setPromptText("Tamaño");

        Button agregarBtn = new Button("Agregar proceso");
        agregarBtn.setOnAction(e -> agregarProceso(nombreField, tamanoField));

        // Componentes de liberación
        liberarCombo = new ComboBox<>();
        Button liberarBtn = new Button("Liberar proceso");
        liberarBtn.setOnAction(e -> {
            liberarProcesoDesdeRAM();
            moverDesdeSwapAutomatico();
            actualizarVista();
        });

        // Componentes de swap
        swapCombo = new ComboBox<>();
        Button liberarSwapBtn = new Button("Liberar de Swap");
        liberarSwapBtn.setOnAction(e -> liberarProcesoDesdeSwap());

        // Configuración algoritmos
        comboReemplazo = new ComboBox<>();
        comboReemplazo.getItems().addAll("LRU", "FIFO", "Clock");
        comboReemplazo.setValue("LRU");
        comboReemplazo.setOnAction(e -> algoritmoReemplazo = comboReemplazo.getValue());

        // Botón de regreso
        Button regresarBtn = new Button("Regresar al Menú Principal");
        regresarBtn.setOnAction(e -> {
            inicializarMemoria();
            swap.clear();
            primaryStage.setScene(escenaMenu);
        });

        // Vista de memoria
        memoriaView = new VBox(5);
        memoriaView.setPadding(new Insets(10));
        actualizarVista();

        // Organización de componentes
        HBox filaEntrada = new HBox(10, nombreField, tamanoField, agregarBtn);
        HBox filaLiberacion = new HBox(10, liberarCombo, liberarBtn);
        HBox filaSwap = new HBox(10, swapCombo, liberarSwapBtn);
        HBox filaConfig = new HBox(10, new Label("Algoritmo Reemplazo:"), comboReemplazo);

        contenedorPrincipal.getChildren().addAll(
                filaEntrada, filaLiberacion, filaConfig, memoriaView, filaSwap, regresarBtn
        );

        escenaSimulacion = new Scene(contenedorPrincipal, 700, 550);
    }

    private boolean nombreProcesoExiste(String nombre) {
        // Verificar en memoria física
        boolean existeEnRAM = memoriaFisica.stream()
                .filter(b -> b.ocupado)
                .anyMatch(b -> b.proceso.nombre.equals(nombre));

        // Verificar en swap
        boolean existeEnSwap = swap.stream()
                .anyMatch(p -> p.nombre.equals(nombre));

        return existeEnRAM || existeEnSwap;
    }

    private void agregarProceso(TextField nombreField, TextField tamanoField) {
        String nombre = nombreField.getText();
        if (nombre.isEmpty()) {
            mostrarAlerta("Error", "El nombre del proceso es requerido");
            return;
        }
        if (nombreProcesoExiste(nombre)) {
            mostrarAlerta("Error", "Ya existe un proceso con ese nombre en RAM o Swap");
            return;
        }

        try {
            int tam = Integer.parseInt(tamanoField.getText());
            if (tam > TAMANIO_RAM) {
                mostrarAlerta("Error", "El proceso excede el tamaño máximo de RAM (" + TAMANIO_RAM + " KB)");
                return;
            }

            Proceso nuevo = new Proceso(nombre, tam);
            boolean asignado = asignarProceso(nuevo);

            if (!asignado) {
                if (liberarEspacioParaProceso(nuevo.tamano)) {
                    asignado = asignarProceso(nuevo);
                }
            }

            if (!asignado) {
                swap.add(nuevo);
                mostrarAlerta("Info", "Proceso movido a Swap");
                moverDesdeSwapAutomatico();
            }

            nombreField.clear();
            tamanoField.clear();
            actualizarVista();

        } catch (NumberFormatException e) {
            mostrarAlerta("Error", "Tamaño inválido");
        }
    }

    private int obtenerMaximoBloqueLibre() {
        return memoriaFisica.stream()
                .filter(b -> !b.ocupado)
                .mapToInt(b -> b.tamano)
                .max()
                .orElse(0);
    }

    private boolean asignarProceso(Proceso proceso) {
        return switch (algoritmo) {
            case "First Fit" -> asignarFirstFit(proceso);
            case "Best Fit" -> asignarBestFit(proceso);
            case "Worst Fit" -> asignarWorstFit(proceso);
            case "Next Fit" -> asignarNextFit(proceso);
            default -> false;
        };
    }

    private boolean liberarEspacioParaProceso(int tamanoRequerido) {
        while (obtenerMaximoBloqueLibre() < tamanoRequerido) {
            Proceso victima = seleccionarVictima();
            if (victima == null) return false;

            swap.add(victima);
            liberarProcesoDesdeRAM(victima.nombre);
            fusionarBloquesLibres();
        }
        return true;
    }

    private void liberarProcesoDesdeRAM() {
        String nombre = liberarCombo.getValue();
        if (nombre != null) {
            liberarProcesoDesdeRAM(nombre);
            moverDesdeSwapAutomatico();
            actualizarVista();
        }
    }

    private void liberarProcesoDesdeRAM(String nombre) {
        memoriaFisica.stream()
                .filter(b -> b.ocupado && b.proceso.nombre.equals(nombre))
                .findFirst()
                .ifPresent(b -> {
                    b.ocupado = false;
                    b.proceso = null;
                    fusionarBloquesLibres();
                });
    }

    private Proceso seleccionarVictima() {
        List<Proceso> procesosEnRAM = new ArrayList<>();
        for (BloqueMemoria bloque : memoriaFisica) {
            if (bloque.ocupado) procesosEnRAM.add(bloque.proceso);
        }
        if (procesosEnRAM.isEmpty()) return null;

        return switch (algoritmoReemplazo) {
            case "LRU" -> seleccionarLRU(procesosEnRAM);
            case "FIFO" -> seleccionarFIFO(procesosEnRAM);
            case "Clock" -> seleccionarClock();
            default -> null;
        };
    }

    private Proceso seleccionarLRU(List<Proceso> procesos) {
        return procesos.stream()
                .min(Comparator.comparingLong(p -> p.lastAccessed))
                .orElse(null);
    }

    private Proceso seleccionarFIFO(List<Proceso> procesos) {
        return procesos.stream()
                .min(Comparator.comparingLong(p -> p.loadTime))
                .orElse(null);
    }

    private Proceso seleccionarClock() {
        if (memoriaFisica.isEmpty()) return null;

        int size = memoriaFisica.size();
        int intentos = 0;

        while (intentos < 2 * size) {
            BloqueMemoria bloque = memoriaFisica.get(clockHand);
            if (bloque.ocupado) {
                Proceso p = bloque.proceso;
                if (!p.referenceBit) {
                    // Encontramos víctima
                    clockHand = (clockHand + 1) % size;
                    return p;
                } else {
                    // Dar segunda oportunidad
                    p.referenceBit = false;
                }
            }
            clockHand = (clockHand + 1) % size;
            intentos++;
        }

        // Fallback: seleccionar el primero ocupado
        for (BloqueMemoria bloque : memoriaFisica) {
            if (bloque.ocupado) {
                return bloque.proceso;
            }
        }
        return null;
    }

    private boolean asignarFirstFit(Proceso proceso) {
        for (BloqueMemoria bloque : memoriaFisica) {
            if (!bloque.ocupado && bloque.tamano >= proceso.tamano) {
                dividirBloque(bloque, proceso);
                return true;
            }
        }
        return false;
    }

    private boolean asignarBestFit(Proceso proceso) {
        BloqueMemoria mejor = null;
        for (BloqueMemoria bloque : memoriaFisica) {
            if (!bloque.ocupado && bloque.tamano >= proceso.tamano) {
                if (mejor == null || bloque.tamano < mejor.tamano) {
                    mejor = bloque;
                }
            }
        }
        if (mejor != null) {
            dividirBloque(mejor, proceso);
            return true;
        }
        return false;
    }

    private boolean asignarWorstFit(Proceso proceso) {
        BloqueMemoria peor = null;
        for (BloqueMemoria bloque : memoriaFisica) {
            if (!bloque.ocupado && bloque.tamano >= proceso.tamano) {
                if (peor == null || bloque.tamano > peor.tamano) {
                    peor = bloque;
                }
            }
        }
        if (peor != null) {
            dividirBloque(peor, proceso);
            return true;
        }
        return false;
    }

    private boolean asignarNextFit(Proceso proceso) {
        int size = memoriaFisica.size();
        for (int i = 0; i < size; i++) {
            int idx = (nextFitStartIndex + i) % size;
            BloqueMemoria bloque = memoriaFisica.get(idx);
            if (!bloque.ocupado && bloque.tamano >= proceso.tamano) {
                dividirBloque(bloque, proceso);
                nextFitStartIndex = (idx + 1) % size;
                return true;
            }
        }
        return false;
    }

    private void dividirBloque(BloqueMemoria original, Proceso proceso) {
        int indice = memoriaFisica.indexOf(original);
        BloqueMemoria ocupado = new BloqueMemoria(original.inicio, proceso.tamano, proceso);

        proceso.lastAccessed = tiempoSimulacion;
        proceso.loadTime = (algoritmoReemplazo.equals("FIFO")) ? tiempoSimulacion : proceso.loadTime;
        proceso.referenceBit = true;
        tiempoSimulacion++;

        original.inicio += proceso.tamano;
        original.tamano -= proceso.tamano;

        if (original.tamano == 0) memoriaFisica.remove(original);
        memoriaFisica.add(indice, ocupado);
    }

    private void liberarProcesoDesdeSwap() {
        String nombre = swapCombo.getValue();
        if (nombre != null) {
            swap.removeIf(p -> p.nombre.equals(nombre));
            actualizarVista();
        }
    }

    private void moverDesdeSwapAutomatico() {
        Iterator<Proceso> it = swap.iterator();
        boolean algunMovido = false;

        while (it.hasNext()) {
            Proceso p = it.next();
            if (p.tamano > TAMANIO_RAM) continue;

            if (asignarProceso(p)) {
                p.lastAccessed = tiempoSimulacion;
                p.referenceBit = true;
                tiempoSimulacion++;
                it.remove();
                algunMovido = true;
            }
        }

        if (algunMovido) actualizarVista();
    }

    private void fusionarBloquesLibres() {
        ListIterator<BloqueMemoria> it = memoriaFisica.listIterator();
        BloqueMemoria anterior = null;

        while (it.hasNext()) {
            BloqueMemoria actual = it.next();
            if (anterior != null && !anterior.ocupado && !actual.ocupado) {
                anterior.tamano += actual.tamano;
                it.remove();
            } else {
                anterior = actual;
            }
        }
    }

    private void actualizarVista() {
        memoriaView.getChildren().clear();
        liberarCombo.getItems().clear();
        swapCombo.getItems().clear();

        // RAM
        memoriaView.getChildren().add(new Label("Memoria RAM (" + TAMANIO_RAM + " KB):"));
        for (BloqueMemoria bloque : memoriaFisica) {
            Label etiqueta = new Label(bloque.toString());
            etiqueta.setStyle("-fx-border-color: black; -fx-padding: 5; " +
                    (bloque.ocupado ? "-fx-background-color: lightgreen;" : "-fx-background-color: lightgray;"));
            memoriaView.getChildren().add(etiqueta);

            if (bloque.ocupado) liberarCombo.getItems().add(bloque.proceso.nombre);
        }

        // Swap
        memoriaView.getChildren().add(new Label("\nMemoria Swap:"));
        if (swap.isEmpty()) {
            memoriaView.getChildren().add(new Label("(Vacía)"));
        } else {
            for (Proceso p : swap) {
                Label etiqueta = new Label(p.toString());
                etiqueta.setStyle("-fx-border-color: red; -fx-padding: 5; -fx-background-color: #FFE4E1;");
                memoriaView.getChildren().add(etiqueta);
                swapCombo.getItems().add(p.nombre);
            }
        }
    }

    private void inicializarMemoria() {
        memoriaFisica.clear();
        memoriaFisica.add(new BloqueMemoria(0, TAMANIO_RAM));
        nextFitStartIndex = 0;
        clockHand = 0;
        tiempoSimulacion = 0;
    }

    private void mostrarAlerta(String titulo, String mensaje) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(titulo);
        alert.setHeaderText(null);
        alert.setContentText(mensaje);
        alert.showAndWait();
    }

    static class Proceso {
        String nombre;
        int tamano;
        long lastAccessed;
        long loadTime;
        boolean referenceBit;

        Proceso(String nombre, int tamano) {
            this.nombre = nombre;
            this.tamano = tamano;
            this.lastAccessed = 0;
            this.loadTime = 0;
            this.referenceBit = false;
        }

        @Override
        public String toString() {
            return nombre + " (" + tamano + " KB)";
        }
    }

    static class BloqueMemoria {
        int inicio;
        int tamano;
        boolean ocupado;
        Proceso proceso;

        BloqueMemoria(int inicio, int tamano) {
            this(inicio, tamano, null);
        }

        BloqueMemoria(int inicio, int tamano, Proceso proceso) {
            this.inicio = inicio;
            this.tamano = tamano;
            this.proceso = proceso;
            this.ocupado = (proceso != null);
        }

        @Override
        public String toString() {
            return String.format("%s | Inicio: %d | Tamaño: %d KB",
                    (ocupado ? proceso.nombre : "Libre"), inicio, tamano);
        }
    }
}