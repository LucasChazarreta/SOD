package com.sistdist.controlador;

import java.io.*;
import java.net.*;
import java.rmi.Naming;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.server.UnicastRemoteObject;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.ConcurrentHashMap;

import com.sistdist.interfaces.IClienteEM;
import com.sistdist.interfaces.IServicioExclusionMutua;
import com.sistdist.election.RingNode;
import com.sistdist.interfaces.IControladorLider;

import java.io.InputStream;
import java.util.Properties;

public class Controlador {

    // ===== Config =====
    public static final boolean LOG_CONFIRMACIONES_EV = false;
    public static final int NUM_PARCELAS = 5;
    public static final Map<Integer, Double> humedades = new ConcurrentHashMap<>();
    public static volatile Double temp = null, rad = null;
    public static volatile Boolean lluvia = null;
    public static final Map<Integer, PrintWriter> conexionesEV = new ConcurrentHashMap<>();
    private static final boolean[] riegoPendiente = new boolean[NUM_PARCELAS + 1];
    private static volatile boolean fertiPendiente = false;
    private static final BlockingQueue<String> eventos = new LinkedBlockingQueue<>();

    // ===== ELECCIÓN EN ANILLO =====
    private static RingNode nodoAnillo;
    private static volatile long uidLiderActual = -1;

    // ===== EM (RMI) =====
    public static IServicioExclusionMutua serverEM;
    public static ServerRMI serverRMI;
    public static final Semaphore semToken = new Semaphore(0, true);

    // ===== DUEÑO DEL TOKEN (para mensajes de “ocupado”) =====
    public static final java.util.concurrent.atomic.AtomicBoolean tokenEnUso = new java.util.concurrent.atomic.AtomicBoolean(false);
    public static volatile String duenoToken = null; // "FERTIRRIGACION" o "RIEGO Pn"

    // ===== Cola de órdenes =====
    public static final BlockingQueue<Orden> colaOrdenes = new LinkedBlockingQueue<>();

    public static record Orden(Tipo tipo, int parcela, int segundos, double inrSnap) {
        public enum Tipo {
            RIEGO, FERTI
        }
    }

    public static void logEvento(String s) {
        if (s != null && !s.isEmpty()) {
            eventos.offer(s);
        }
    }

    // ======== NUEVO: para consulta de líder ========
    private static String liderActualBind = null;
    private static int puertoLiderActual = -1;

    public static void main(String[] args) {

        // 1) Iniciar ANILLO primero (hooks listos)
        iniciarAnillo();

        // 2) RMI/EM (ServerRMI local + conexión al EM actual vía Directorio/ClienteEM)
        iniciarRMI_EM();

        // 3) Detector de caídas del EM (dispara elección)
        new DetectorCaidaEM().start();

        // 4) Hilos de parcelas
        for (int i = 1; i <= NUM_PARCELAS; i++) {
            new HiloParcela(i, humedades, conexionesEV).start();
        }

        // 5) Ejecutores (bomba y fertirrigación)
        new EjecutorBomba().start();
        new Fertirrigacion().start();

        // 6) Impresor de eventos
        new Thread(() -> {
            while (true) {
                try {
                    String ev = eventos.take();
                    System.out.println();
                    System.out.print(ev);
                } catch (InterruptedException e) {
                    return;
                }
            }
        }, "EventPrinter").start();

        // 7) Impresor + Scheduler (snapshot cada 10 s)
        new Thread(() -> {
            while (true) {
                try {
                    System.out.println("--------------------------------");

                    boolean hayAlgunaOrdenRiego = false;

                    // 1) Mostrar parcelas y decidir riego
                    for (int i = 1; i <= NUM_PARCELAS; i++) {
                        Double H = humedades.get(i);
                        if (H == null || temp == null || rad == null || lluvia == null) {
                            System.out.printf("Parcela %d -> Esperando datos...%n", i);
                            riegoPendiente[i] = false;
                            continue;
                        }

                        double inr = 0.5 * (1 - H / 100.0) + 0.3 * (temp / 40.0) + 0.2 * (rad / 1000.0);
                        int mins = 0;

                        if (!lluvia) {
                            if (inr > 0.9) mins = 10;
                            else if (inr > 0.8) mins = 7;
                            else if (inr > 0.7) mins = 5;
                        }

                        String riego = lluvia ? "NO (LLUVIA)" : (mins > 0 ? "SI (" + mins + " min)" : "NO (INR bajo)");
                        System.out.printf("Parcela %d -> Humedad = %.2f %% | INR = %.3f | Riego = %s%n", i, H, inr, riego);

                        if (mins > 0
                                && conexionesEV.get(6) != null
                                && conexionesEV.get(i) != null
                                && !riegoPendiente[i]) {

                            int seg = mins; // para minutos reales: mins * 60
                            boolean colaVaciaAntes = colaOrdenes.isEmpty();

                            colaOrdenes.offer(new Orden(Orden.Tipo.RIEGO, i, seg, inr));
                            riegoPendiente[i] = true;
                            hayAlgunaOrdenRiego = true;

                            if (!colaVaciaAntes) logEvento(String.format("[RIEGO P%d] Esperando su turno...\n", i));
                        } else {
                            riegoPendiente[i] = false;
                        }
                    }

                    // 2) Sensores globales
                    System.out.println();
                    System.out.println("--- Sensores Globales ---");
                    System.out.printf("Temperatura = %s%n", temp == null ? "Esperando..." : String.format("%.2f Grados C.", temp));
                    System.out.printf("Radiacion   = %s%n", rad == null ? "Esperando..." : String.format("%.2f W/m", rad));
                    System.out.printf("Lloviendo   = %s%n", lluvia == null ? "Esperando..." : (lluvia ? "SI" : "NO"));

                    // 3) Scheduler de FERTI
                    if (!hayAlgunaOrdenRiego && !fertiPendiente
                            && conexionesEV.get(6) != null && conexionesEV.get(7) != null
                            && Boolean.FALSE.equals(lluvia)) {

                        boolean condicionesFerti = false;
                        for (int i = 1; i <= NUM_PARCELAS && !condicionesFerti; i++) {
                            Double H = humedades.get(i);
                            if (H == null || temp == null || rad == null) continue;
                            double inr = 0.5 * (1 - H / 100.0) + 0.3 * (temp / 40.0) + 0.2 * (rad / 1000.0);
                            if (H < 25.0 && inr < 0.5) condicionesFerti = true;
                        }
                        if (condicionesFerti) {
                            colaOrdenes.offer(new Orden(Orden.Tipo.FERTI, 0, 5, 0.0));
                            fertiPendiente = true;
                        } else fertiPendiente = false;
                    } else fertiPendiente = false;

                    Thread.sleep(10_000);

                } catch (InterruptedException e) {
                    return;
                }
            }
        }, "Impresor+Scheduler").start();

        // 8) Servidor TCP (sensores y EV)
        int puertoCtrl = Integer.parseInt(System.getProperty("ctrl.tcp.port", "20000"));
        try (ServerSocket server = new ServerSocket(puertoCtrl)) {
            System.out.println("CONTROLADOR escuchando en " + puertoCtrl + "...");
            while (true) {
                Socket s = server.accept();
                BufferedReader bf = new BufferedReader(new InputStreamReader(s.getInputStream()));
                String header = bf.readLine();
                if (header == null) { s.close(); continue; }
                String[] parts = header.split(";");
                String tipo = parts[0];

                switch (tipo) {
                    case "electrovalvula" -> {
                        int evId = Integer.parseInt(parts[1]);
                        PrintWriter pw = new PrintWriter(s.getOutputStream(), true);
                        conexionesEV.put(evId, pw);
                        new HiloReceptorEV(s, evId).start();
                    }
                    case "sensorHumedad" -> new HiloReceptorHumedad(s, Integer.parseInt(parts[1])).start();
                    case "sensorTemperatura" -> new HiloReceptorTemperatura(s, Integer.parseInt(parts[1])).start();
                    case "sensorRadiacion" -> new HiloReceptorRadiacion(s, Integer.parseInt(parts[1])).start();
                    case "sensorLluvia" -> new HiloReceptorLluvia(s, Integer.parseInt(parts[1])).start();
                    default -> s.close();
                }
            }
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    // ===== RMI/EM =====
    private static void iniciarRMI_EM() {
        new Thread(() -> {
            try {
                serverRMI = new ServerRMI();
                try { LocateRegistry.createRegistry(1099); } catch (Exception ignored) {}
                Naming.rebind("rmi://localhost:1099/ServerRMI", serverRMI);

                // ======== NUEVO: publicar el servicio de consulta de líder ========
                try {
                    Naming.rebind("rmi://localhost:1099/ControladorLider", new LiderRMI());
                    System.out.println("[RMI] Servicio ControladorLider publicado correctamente.");
                } catch (Exception e) {
                    System.err.println("[RMI] Error al publicar ControladorLider: " + e);
                }

                ClienteEM.reconectarConLiderActual();

            } catch (Exception e) {
                System.err.println("[Controlador] Error al iniciar RMI/EM: " + e.getMessage());
                e.printStackTrace();
            }
        }, "bootstrap-rmi-em").start();
    }

    public static class ServerRMI extends UnicastRemoteObject implements IClienteEM {
        public ServerRMI() throws RemoteException { super(); }
        @Override public void RecibirToken() throws RemoteException { semToken.release(); }
    }

    // ===== Anillo =====
    private static void iniciarAnillo() {
        try {
            Properties cfg = new Properties();
            try (InputStream in = Controlador.class.getClassLoader().getResourceAsStream("ring.properties")) {
                if (in != null) cfg.load(in);
                else System.err.println("[Anillo] No se encontró ring.properties en resources (se usarán -D).");
            }

            sobreescribir(cfg, "ring.uid");
            sobreescribir(cfg, "ring.myBind");
            sobreescribir(cfg, "ring.successorBind");
            sobreescribir(cfg, "ring.registryPort");

            nodoAnillo = RingNode.createAndBind(cfg,
                    // ==== alSerLider ====
                    () -> {
                        try {
                            int puerto = PuertoEM.porUid(nodoAnillo.getMyUid());
                            ArrancadorServidorEM.iniciarSiNoEstaCorriendo(puerto);

                            liderActualBind = System.getProperty("ring.myBind");
                            puertoLiderActual = Integer.parseInt(System.getProperty("ctrl.tcp.port"));
                            System.out.println("[Controlador] Ahora soy LÍDER del anillo en: " + liderActualBind + " | puerto TCP=" + puertoLiderActual);

                        } catch (Exception e) {
                            System.err.println("[Anillo] Error al iniciar EM local (líder): " + e.getMessage());
                        }
                    },
                    // ==== alAnunciarLider ====
                    (uidLider) -> {
                        try {
                            uidLiderActual = uidLider;
                            DirectorioCoordinador.actualizarDesdeUidLider(uidLider);
                            ClienteEM.reconectarConLiderActual();

                            if (nodoAnillo != null && nodoAnillo.getMyUid() != uidLider) {
                                ArrancadorServidorEM.detenerSiEsEste(PuertoEM.porUid(nodoAnillo.getMyUid()));
                            }

                            String nuevoLiderBind = bindAnilloPorUid(uidLider);
                            int puertoDetectado = puertoCtrlPorUid(uidLider);
                            if (nuevoLiderBind != null && puertoDetectado > 0) {
                                liderActualBind = nuevoLiderBind;
                                puertoLiderActual = puertoDetectado;
                                System.out.println("[Controlador] Nuevo líder detectado: " + liderActualBind + " | puerto TCP=" + puertoLiderActual);
                            } else {
                                System.err.println("[Controlador] No se pudo resolver bind/puerto para UID=" + uidLider);
                            }

                        } catch (Exception e) {
                            System.err.println("[Anillo] Error al procesar líder electo: " + e.getMessage());
                        }
                    });

            System.out.println("[Anillo] Nodo anillo listo. UID=" + nodoAnillo.getMyUid());

        } catch (Exception e) {
            System.err.println("[Anillo] Error al iniciar anillo: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public static class DetectorCaidaEM extends Thread {
        private volatile boolean seguir = true;
        @Override
        public void run() {
            while (seguir) {
                try {
                    boolean ok = ClienteEM.ping();
                    if (!ok && nodoAnillo != null) {
                        System.out.println("[Detector] EM caído -> disparo elección en anillo.");
                        nodoAnillo.startElection();
                        Thread.sleep(15_000);
                    } else Thread.sleep(5_000);
                } catch (InterruptedException ignored) { return; }
            }
        }
        public void detener() { this.seguir = false; }
    }

    private static void sobreescribir(Properties p, String k) {
        String v = System.getProperty(k);
        if (v != null && !v.trim().isEmpty()) p.setProperty(k, v);
    }

    // ===== Servicio RMI para que sensores consulten el líder actual =====
    public static class LiderRMI extends UnicastRemoteObject implements IControladorLider {
        public LiderRMI() throws RemoteException { super(); }
        @Override public String obtenerLiderActual() throws RemoteException { return liderActualBind; }
        @Override public int obtenerPuertoLider() throws RemoteException { return puertoLiderActual; }
    }

    // ===== Helpers de mapeo UID -> bind/puerto CTRL =====
    private static String bindAnilloPorUid(long uid) {
        if (uid == 101) return "rmi://localhost:1101/RingElection-CTRL1";
        if (uid == 102) return "rmi://localhost:1102/RingElection-CTRL2";
        if (uid == 103) return "rmi://localhost:1103/RingElection-CTRL3";
        return null;
    }

    private static int puertoCtrlPorUid(long uid) {
        if (uid == 101) return 20000;
        if (uid == 102) return 20001;
        if (uid == 103) return 20002;
        return -1;
    }

    public static synchronized void actualizarServerEM(IServicioExclusionMutua nuevo) {
        serverEM = nuevo;
    }
}
