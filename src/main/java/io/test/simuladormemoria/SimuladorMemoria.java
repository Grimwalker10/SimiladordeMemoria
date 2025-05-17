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
    private static final int PAGE_SIZE = 10;
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

        // Componentes para referencia/modificación
        TextField nombreRefField = new TextField();
        nombreRefField.setPromptText("Nombre del proceso");
        Button referenciarBtn = new Button("Referenciar");
        referenciarBtn.setOnAction(e -> referenciarProceso(nombreRefField.getText()));

        Button modificarBtn = new Button("Modificar");
        modificarBtn.setOnAction(e -> modificarProceso(nombreRefField.getText()));

        // Componentes principales
        TextField nombreField = new TextField();
        nombreField.setPromptText("Nombre del proceso");
        TextField tamanoField = new TextField();
        tamanoField.setPromptText("Tamaño");

        Button agregarBtn = new Button("Agregar proceso");
        agregarBtn.setOnAction(e -> agregarProceso(nombreField, tamanoField));

        liberarCombo = new ComboBox<>();
        Button liberarBtn = new Button("Liberar proceso");
        liberarBtn.setOnAction(e -> {
            liberarProcesoDesdeRAM();
            moverDesdeSwapAutomatico();
            actualizarVista();
        });

        swapCombo = new ComboBox<>();
        Button liberarSwapBtn = new Button("Liberar de Swap");
        liberarSwapBtn.setOnAction(e -> liberarProcesoDesdeSwap());

        comboReemplazo = new ComboBox<>();
        comboReemplazo.getItems().addAll("LRU", "FIFO", "Clock");
        comboReemplazo.setValue("LRU");
        comboReemplazo.setOnAction(e -> algoritmoReemplazo = comboReemplazo.getValue());

        Button regresarBtn = new Button("Regresar al Menú Principal");
        regresarBtn.setOnAction(e -> {
            inicializarMemoria();
            swap.clear();
            primaryStage.setScene(escenaMenu);
        });

        memoriaView = new VBox(5);
        memoriaView.setPadding(new Insets(10));
        actualizarVista();

        // Organización de componentes
        HBox filaRefMod = new HBox(10, nombreRefField, referenciarBtn, modificarBtn);
        HBox filaEntrada = new HBox(10, nombreField, tamanoField, agregarBtn);
        HBox filaLiberacion = new HBox(10, liberarCombo, liberarBtn);
        HBox filaSwap = new HBox(10, swapCombo, liberarSwapBtn);
        HBox filaConfig = new HBox(10, new Label("Algoritmo Reemplazo:"), comboReemplazo);

        contenedorPrincipal.getChildren().addAll(
                filaEntrada, filaLiberacion, filaConfig, filaRefMod,
                memoriaView, filaSwap, regresarBtn
        );

        escenaSimulacion = new Scene(contenedorPrincipal, 800, 750);
    }

    private boolean nombreProcesoExiste(String nombre) {
        return memoriaFisica.stream().anyMatch(b -> b.isOcupado() && b.proceso.nombre.equals(nombre)) ||
                swap.stream().anyMatch(p -> p.nombre.equals(nombre));
    }

    private void agregarProceso(TextField nombreField, TextField tamanoField) {
        String nombre = nombreField.getText();
        if (nombre.isEmpty()) {
            mostrarAlerta("Error", "Nombre requerido");
            return;
        }
        if (nombreProcesoExiste(nombre)) {
            mostrarAlerta("Error", "Proceso ya existe");
            return;
        }

        try {
            int tam = Integer.parseInt(tamanoField.getText());
            if (tam > TAMANIO_RAM) {
                mostrarAlerta("Error", "Tamaño excede RAM");
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
            if (proc != null) {
                swap.add(proc);
                memoriaFisica.stream()
                        .filter(b -> b.proceso != null && b.proceso.nombre.equals(proc.nombre))
                        .forEach(BloqueMemoria::liberar);
            }
        }
    }

    private void referenciarProceso(String nombre) {
        if (nombre == null || nombre.isEmpty()) {
            mostrarAlerta("Error", "Ingrese nombre");
            return;
        }

        boolean enRAM = memoriaFisica.stream()
                .anyMatch(b -> b.isOcupado() && b.proceso.nombre.equals(nombre));

        if (enRAM) {
            memoriaFisica.stream()
                    .filter(b -> b.isOcupado() && b.proceso.nombre.equals(nombre))
                    .forEach(marco -> {
                        marco.referenceBit = true;
                        marco.lastAccessed = tiempoSimulacion++;
                    });
            actualizarVista();
        } else {
            Optional<Proceso> procSwap = swap.stream()
                    .filter(p -> p.nombre.equals(nombre))
                    .findFirst();

            if (procSwap.isPresent()) {
                Proceso p = procSwap.get();
                moverDesdeSwap(p);
                actualizarVista();
            } else {
                mostrarAlerta("Error", "Proceso no encontrado");
            }
        }
    }

    private void moverDesdeSwap(Proceso proceso) {
        List<BloqueMemoria> marcosLibres = memoriaFisica.stream()
                .filter(b -> !b.isOcupado())
                .collect(Collectors.toList());

        int paginasRequeridas = proceso.paginas.size();

        if (marcosLibres.size() >= paginasRequeridas) {
            asignarMarcos(proceso, marcosLibres);
            swap.remove(proceso);
        } else {
            int marcosNecesarios = paginasRequeridas - marcosLibres.size();
            List<BloqueMemoria> victimas = seleccionarVictimas(marcosNecesarios);

            if (victimas.size() >= marcosNecesarios) {
                liberarVictimas(victimas);
                asignarMarcos(proceso, memoriaFisica.stream()
                        .filter(b -> !b.isOcupado())
                        .collect(Collectors.toList()));
                swap.remove(proceso);
            } else {
                mostrarAlerta("Error", "Espacio insuficiente");
            }
        }
    }

    private void modificarProceso(String nombre) {
        if (nombre == null || nombre.isEmpty()) {
            mostrarAlerta("Error", "Ingrese nombre");
            return;
        }

        boolean enRAM = memoriaFisica.stream()
                .anyMatch(b -> b.isOcupado() && b.proceso.nombre.equals(nombre));

        if (enRAM) {
            memoriaFisica.stream()
                    .filter(b -> b.isOcupado() && b.proceso.nombre.equals(nombre))
                    .forEach(marco -> {
                        marco.referenceBit = true;
                        marco.modificationBit = true;
                        marco.lastAccessed = tiempoSimulacion++;
                    });
            actualizarVista();
        } else {
            mostrarAlerta("Info", "Proceso debe estar en RAM");
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

        memoriaView.getChildren().add(new Label("Memoria RAM (" + TAMANIO_RAM + " KB):"));
        for (BloqueMemoria marco : memoriaFisica) {
            String texto;
            if (marco.isOcupado()) {
                String base = String.format("Marco %d-%d: %s (Página %d)",
                        marco.inicio, marco.inicio + marco.tamano - 1,
                        marco.proceso.nombre, marco.numeroPagina);

                if (algoritmoReemplazo.equals("LRU") || algoritmoReemplazo.equals("Clock")) {
                    String bits = String.format(" [R:%d, M:%d]",
                            marco.referenceBit ? 1 : 0,
                            marco.modificationBit ? 1 : 0);
                    base += bits;
                }
                texto = base;
            } else {
                texto = String.format("Marco %d-%d: Libre",
                        marco.inicio, marco.inicio + marco.tamano - 1);
            }

            Label etiqueta = new Label(texto);
            etiqueta.setStyle("-fx-border-color: black; -fx-padding: 5; " +
                    (marco.isOcupado() ? "-fx-background-color: lightgreen;" : "-fx-background-color: lightgray;"));
            memoriaView.getChildren().add(etiqueta);
            if (marco.isOcupado()) {
                liberarCombo.getItems().add(marco.proceso.nombre);
            }
        }

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

        Proceso(String nombre, int tamano) {
            this.nombre = nombre;
            this.tamano = tamano;
            this.paginas = new ArrayList<>();
            int numPages = (tamano + PAGE_SIZE - 1) / PAGE_SIZE;
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
        boolean modificationBit;

        BloqueMemoria(int inicio, int tamano) {
            this.inicio = inicio;
            this.tamano = tamano;
            this.proceso = null;
            this.numeroPagina = -1;
            this.referenceBit = false;
            this.modificationBit = false;
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
            this.modificationBit = false;
        }

        void liberar() {
            this.proceso = null;
            this.numeroPagina = -1;
            this.referenceBit = false;
            this.modificationBit = false;
        }
    }
}