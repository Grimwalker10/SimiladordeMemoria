package io.test.simuladormemoria;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import javafx.geometry.*;
import java.util.*;
import java.util.stream.Collectors;

public class SimuladorMemoria extends Application {

    private final List<BloqueMemoria> memoriaFisica = new ArrayList<>();
    private final List<Proceso> swap = new ArrayList<>();
    private final int TAMANIO_RAM = 100;
    private final int PAGE_SIZE = 10; // Tamaño de página en KB
    private String algoritmo;
    private String algoritmoReemplazo = "LRU";
    private VBox memoriaView;
    private ComboBox<String> liberarCombo;
    private ComboBox<String> swapCombo;
    private ComboBox<String> comboReemplazo;
    private Scene escenaMenu;
    private Scene escenaSimulacion;

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
        // Verificar en memoria física (incluye filtro de null)
        boolean existeEnRAM = memoriaFisica.stream()
                .filter(b -> b.isOcupado() && b.proceso != null)
                .anyMatch(b -> b.proceso.nombre.equals(nombre));

        // Verificar en swap (filtra procesos null)
        boolean existeEnSwap = swap.stream()
                .filter(Objects::nonNull)  // Filtra elementos null
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
            int paginasRequeridas = nuevo.paginas.size();

            List<BloqueMemoria> marcosLibres = memoriaFisica.stream()
                    .filter(b -> !b.isOcupado())
                    .collect(Collectors.toList());

            if (marcosLibres.size() >= paginasRequeridas) {
                asignarMarcos(nuevo, marcosLibres);
            } else {
                int marcosNecesarios = paginasRequeridas - marcosLibres.size();
                List<BloqueMemoria> victimas = seleccionarVictimas(marcosNecesarios);
                if (victimas.size() < marcosNecesarios) {
                    swap.add(nuevo);
                    mostrarAlerta("Info", "Proceso movido a Swap");
                } else {
                    liberarVictimas(victimas);
                    asignarMarcos(nuevo, memoriaFisica.stream()
                            .filter(b -> !b.isOcupado())
                            .collect(Collectors.toList()));
                }
            }

            nombreField.clear();
            tamanoField.clear();
            actualizarVista();

        } catch (NumberFormatException e) {
            mostrarAlerta("Error", "Tamaño inválido");
        }
    }

    private void asignarMarcos(Proceso proceso, List<BloqueMemoria> marcos) {
        for (int i = 0; i < proceso.paginas.size() && i < marcos.size(); i++) {
            BloqueMemoria marco = marcos.get(i);
            marco.asignar(proceso, i);
            proceso.paginas.get(i).marco = marco;
            marco.lastAccessed = tiempoSimulacion++;
            marco.loadTime = tiempoSimulacion;
            marco.referenceBit = true;
        }
    }

    private List<BloqueMemoria> seleccionarVictimas(int cantidad) {
        List<BloqueMemoria> candidatos = memoriaFisica.stream()
                .filter(BloqueMemoria::isOcupado)
                .collect(Collectors.toList());

        switch (algoritmoReemplazo) {
            case "LRU":
                candidatos.sort(Comparator.comparingLong(b -> b.lastAccessed));
                break;
            case "FIFO":
                candidatos.sort(Comparator.comparingLong(b -> b.loadTime));
                break;
            case "Clock":
                return seleccionarVictimasClock(cantidad);
        }

        return candidatos.subList(0, Math.min(cantidad, candidatos.size()));
    }

    private List<BloqueMemoria> seleccionarVictimasClock(int cantidad) {
        List<BloqueMemoria> victimas = new ArrayList<>();
        int intentos = 0;
        while (victimas.size() < cantidad && intentos < 2 * memoriaFisica.size()) {
            BloqueMemoria marco = memoriaFisica.get(clockHand);
            if (marco.isOcupado()) {
                if (!marco.referenceBit) {
                    victimas.add(marco);
                } else {
                    marco.referenceBit = false;
                }
            }
            clockHand = (clockHand + 1) % memoriaFisica.size();
            intentos++;
        }
        return victimas;
    }

    private void liberarVictimas(List<BloqueMemoria> victimas) {
        for (BloqueMemoria victima : victimas) {
            Proceso proc = victima.proceso;
            if (proc != null) {  // Validación clave
                swap.add(proc);
                memoriaFisica.stream()
                        .filter(b -> b.proceso != null && b.proceso.nombre.equals(proc.nombre))
                        .forEach(BloqueMemoria::liberar);
            }
        }
    }

    private void liberarProcesoDesdeRAM() {
        String nombre = liberarCombo.getValue();
        if (nombre != null) {
            memoriaFisica.stream()
                    .filter(b -> b.isOcupado() && b.proceso.nombre.equals(nombre))
                    .forEach(BloqueMemoria::liberar);
            swap.removeIf(p -> p.nombre.equals(nombre));
            actualizarVista();
        }
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
        while (it.hasNext()) {
            Proceso p = it.next();
            List<BloqueMemoria> marcosLibres = memoriaFisica.stream()
                    .filter(b -> !b.isOcupado())
                    .collect(Collectors.toList());
            if (marcosLibres.size() >= p.paginas.size()) {
                asignarMarcos(p, marcosLibres);
                it.remove();
            }
        }
    }

    private void actualizarVista() {
        memoriaView.getChildren().clear();
        liberarCombo.getItems().clear();
        swapCombo.getItems().clear();

        // RAM
        memoriaView.getChildren().add(new Label("Memoria RAM (" + TAMANIO_RAM + " KB):"));
        for (BloqueMemoria marco : memoriaFisica) {
            Label etiqueta = new Label(marco.toString());
            etiqueta.setStyle("-fx-border-color: black; -fx-padding: 5; " +
                    (marco.isOcupado() ? "-fx-background-color: lightgreen;" : "-fx-background-color: lightgray;"));
            memoriaView.getChildren().add(etiqueta);
            if (marco.isOcupado()) {
                liberarCombo.getItems().add(marco.proceso.nombre);
            }
        }

        // Swap - Añadir filtro para null
        memoriaView.getChildren().add(new Label("\nMemoria Swap:"));
        if (swap.isEmpty()) {
            memoriaView.getChildren().add(new Label("(Vacía)"));
        } else {
            for (Proceso p : swap) {
                if (p == null) continue; // Filtra procesos nulos
                Label etiqueta = new Label(p.toString());
                etiqueta.setStyle("-fx-border-color: red; -fx-padding: 5; -fx-background-color: #FFE4E1;");
                memoriaView.getChildren().add(etiqueta);
                swapCombo.getItems().add(p.nombre);
            }
        }
    }

    private void inicializarMemoria() {
        memoriaFisica.clear();
        int numFrames = TAMANIO_RAM / PAGE_SIZE;
        for (int i = 0; i < numFrames; i++) {
            memoriaFisica.add(new BloqueMemoria(i * PAGE_SIZE, PAGE_SIZE));
        }
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
        List<Pagina> paginas;
        long lastAccessed;
        long loadTime;
        boolean referenceBit;

        Proceso(String nombre, int tamano) {
            this.nombre = nombre;
            this.tamano = tamano;
            this.paginas = new ArrayList<>();
            int numPages = (tamano + 10 - 1) / 10; // PAGE_SIZE=10
            for (int i = 0; i < numPages; i++) {
                paginas.add(new Pagina(i));
            }
        }

        class Pagina {
            int numero;
            BloqueMemoria marco;

            Pagina(int numero) {
                this.numero = numero;
            }
        }

        @Override
        public String toString() {
            return nombre + " (" + tamano + " KB)";
        }
    }

    static class BloqueMemoria {
        int inicio;
        int tamano;
        Proceso proceso;
        int numeroPagina;
        long lastAccessed;
        long loadTime;
        boolean referenceBit;

        BloqueMemoria(int inicio, int tamano) {
            this.inicio = inicio;
            this.tamano = tamano;
            this.proceso = null;
            this.numeroPagina = -1;
        }

        boolean isOcupado() {
            return proceso != null;
        }

        void asignar(Proceso proceso, int numeroPagina) {
            this.proceso = proceso;
            this.numeroPagina = numeroPagina;
            this.lastAccessed = System.currentTimeMillis();
            this.loadTime = this.lastAccessed;
            this.referenceBit = true;
        }

        void liberar() {
            this.proceso = null;
            this.numeroPagina = -1;
            this.referenceBit = false;
        }

        @Override
        public String toString() {
            if (isOcupado()) {
                return String.format("Marco %d-%d: %s (Página %d)",
                        inicio, inicio + tamano - 1, proceso.nombre, numeroPagina);
            } else {
                return String.format("Marco %d-%d: Libre", inicio, inicio + tamano - 1);
            }
        }
    }
}