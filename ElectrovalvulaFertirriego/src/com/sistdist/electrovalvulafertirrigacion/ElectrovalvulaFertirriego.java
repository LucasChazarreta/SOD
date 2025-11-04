package com.sistdist.electrovalvulafertirrigacion;

import com.sistdist.interfaces.IClienteEM;
import com.sistdist.interfaces.IControladorLider;
import com.sistdist.interfaces.IServicioExclusionMutua;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.rmi.Naming;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class ElectrovalvulaFertirriego {

    private static final int ID_ELECTROVALVULA = 7;
    private static final String CONTROLADOR_HOST = "127.0.0.1";
    private static final int CONTROLADOR_PUERTO = 20000;
    private static final String LIDER_BIND = "rmi://localhost:1099/ControladorLider";
    private static final int DURACION_FERTI_DEF = 30; // segundos
    private static final int TIMEOUT_TOKEN_SEG = 45;

    public static void main(String[] args) {
        try (Socket socket = new Socket(CONTROLADOR_HOST, CONTROLADOR_PUERTO);
             PrintWriter out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))) {

            MutualExclusionClient emClient = crearClienteEM();

            System.out.println("run:");
            out.println("electrovalvula;" + ID_ELECTROVALVULA);
            out.flush();
            System.out.println("[EV-FERTI] Conectada al controlador como EV" + ID_ELECTROVALVULA);

            String comando;
            while ((comando = in.readLine()) != null) {
                comando = comando.trim();

                if (comando.regionMatches(true, 0, "log|", 0, 4)) {
                    System.out.println(comando.substring(4));
                    continue;
                }

                if ("abrir".equalsIgnoreCase(comando)) {
                    System.out.println("[EV-FERTI] Válvula de fertirrigación abierta.");
                    out.println("EV" + ID_ELECTROVALVULA + ":ok_abierta");
                } else if ("cerrar".equalsIgnoreCase(comando)) {
                    System.out.println("[EV-FERTI] Válvula de fertirrigación cerrada.");
                    out.println("EV" + ID_ELECTROVALVULA + ":ok_cerrada");
                } else if (comando.toLowerCase(Locale.ROOT).startsWith("fertirrigar")) {
                    manejarFertirrigacion(comando, emClient, out);
                } else if ("em_obtener".equalsIgnoreCase(comando) || "obtener_token".equalsIgnoreCase(comando)) {
                    manejarObtenerToken(emClient, out);
                } else if ("em_devolver".equalsIgnoreCase(comando) || "devolver_token".equalsIgnoreCase(comando)) {
                    manejarDevolverToken(emClient, out);
                } else {
                    System.out.println("[EV-FERTI] Comando desconocido: " + comando);
                    out.println("EV" + ID_ELECTROVALVULA + ":error");
                }
                out.flush();
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static MutualExclusionClient crearClienteEM() {
        try {
            return new MutualExclusionClient();
        } catch (RemoteException e) {
            System.err.println("[EV-FERTI][EM] No se pudo inicializar el cliente RMI: " + e.getMessage());
            return null;
        }
    }

    private static void manejarObtenerToken(MutualExclusionClient emClient, PrintWriter out) {
        if (emClient == null) {
            System.err.println("[EV-FERTI][EM] Cliente EM no disponible para obtener token.");
            out.println("EV" + ID_ELECTROVALVULA + ":error_em_no_disponible");
            return;
        }
        try {
            System.out.println("[EV-FERTI][EM] Solicitando token...");
            emClient.solicitarToken();
            System.out.println("[EV-FERTI][EM] Token obtenido correctamente.");
            out.println("EV" + ID_ELECTROVALVULA + ":ok_token_obtenido");
        } catch (Exception e) {
            System.err.println("[EV-FERTI][EM] Error al obtener token: " + e.getMessage());
            out.println("EV" + ID_ELECTROVALVULA + ":error_token");
        }
    }

    private static void manejarDevolverToken(MutualExclusionClient emClient, PrintWriter out) {
        if (emClient == null) {
            System.err.println("[EV-FERTI][EM] Cliente EM no disponible para devolver token.");
            out.println("EV" + ID_ELECTROVALVULA + ":error_em_no_disponible");
            return;
        }
        try {
            System.out.println("[EV-FERTI][EM] Devolviendo token...");
            emClient.devolverToken();
            System.out.println("[EV-FERTI][EM] Token devuelto correctamente.");
            out.println("EV" + ID_ELECTROVALVULA + ":ok_token_devuelto");
        } catch (Exception e) {
            System.err.println("[EV-FERTI][EM] Error al devolver token: " + e.getMessage());
            out.println("EV" + ID_ELECTROVALVULA + ":error_token");
        }
    }

    private static void manejarFertirrigacion(String comando, MutualExclusionClient emClient, PrintWriter out) {
        if (emClient == null) {
            System.err.println("[EV-FERTI] No se puede iniciar fertirrigación: cliente EM no disponible.");
            out.println("EV" + ID_ELECTROVALVULA + ":error_em_no_disponible");
            return;
        }
        int duracion = extraerDuracion(comando, DURACION_FERTI_DEF);
        System.out.printf(Locale.ROOT, "[EV-FERTI] Solicitud de fertirrigación por %d segundos.%n", duracion);
        out.println("EV" + ID_ELECTROVALVULA + ":ok_ferti_en_cola");

        Thread hilo = new Thread(() -> ejecutarSecuenciaFerti(emClient, duracion), "ferti-em-seq");
        hilo.setDaemon(true);
        hilo.start();
    }

    private static void ejecutarSecuenciaFerti(MutualExclusionClient emClient, int duracionSegundos) {
        boolean tokenAdquirido = false;
        try {
            System.out.println("[EV-FERTI][EM] Intentando obtener token...");
            emClient.solicitarToken();
            tokenAdquirido = true;
            System.out.println("[EV-FERTI][EM] Token recibido.");

            System.out.printf(Locale.ROOT, "[EV-FERTI] Fertirrigando durante %d segundos...%n", duracionSegundos);
            Thread.sleep(Math.max(1, duracionSegundos) * 1000L);
            System.out.println("[EV-FERTI] Fertirrigación finalizada.");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.err.println("[EV-FERTI] Fertirrigación interrumpida.");
        } catch (Exception e) {
            System.err.println("[EV-FERTI] Error durante la fertirrigación: " + e.getMessage());
            e.printStackTrace(System.err);
        } finally {
            if (tokenAdquirido) {
                try {
                    System.out.println("[EV-FERTI][EM] Devolviendo token...");
                    emClient.devolverToken();
                    System.out.println("[EV-FERTI][EM] Token devuelto.");
                } catch (Exception e) {
                    System.err.println("[EV-FERTI][EM] Error al devolver token: " + e.getMessage());
                    emClient.invalidarServicio();
                }
            }
        }
    }

    private static int extraerDuracion(String comando, int defecto) {
        String resto = comando.substring("fertirrigar".length()).trim();
        if (resto.isEmpty()) return defecto;
        if (resto.charAt(0) == ';' || resto.charAt(0) == ':' || resto.charAt(0) == ',') {
            resto = resto.substring(1).trim();
        }
        if (resto.isEmpty()) return defecto;
        try {
            return Math.max(1, Integer.parseInt(resto.split("\\s")[0]));
        } catch (NumberFormatException ex) {
            System.err.println("[EV-FERTI] Duración inválida (" + resto + "), usando " + defecto + " segundos.");
            return defecto;
        }
    }

    private static String resolverUrlEM() {
        final String hostPorDefecto = "localhost";
        final int puertoPorDefecto = 10000;
        try {
            IControladorLider stub = (IControladorLider) Naming.lookup(LIDER_BIND);
            String direccion = stub.obtenerLiderActual();
            int puertoCtrl = stub.obtenerPuertoLider();
            String host = extraerHost(direccion, hostPorDefecto);
            int puertoEM = puertoDesdeControlador(puertoCtrl);
            return "rmi://" + host + ":" + puertoEM + "/servidorCentralEM";
        } catch (Exception e) {
            System.err.println("[EV-FERTI][EM] No se pudo resolver líder actual (" + e.getMessage() + "). Usando valores por defecto.");
            return "rmi://" + hostPorDefecto + ":" + puertoPorDefecto + "/servidorCentralEM";
        }
    }

    private static String extraerHost(String direccionRmi, String fallback) {
        if (direccionRmi == null || direccionRmi.isBlank()) {
            return fallback;
        }
        String sinPrefijo = direccionRmi.replaceFirst("^rmi://", "");
        int finHost = sinPrefijo.indexOf('/') >= 0 ? sinPrefijo.indexOf('/') : sinPrefijo.length();
        String hostYpuerto = sinPrefijo.substring(0, finHost);
        int separador = hostYpuerto.indexOf(':');
        if (separador >= 0) {
            return hostYpuerto.substring(0, separador);
        }
        return hostYpuerto;
    }

    private static int puertoDesdeControlador(int puertoCtrl) {
        if (puertoCtrl >= 20000 && puertoCtrl <= 20010) {
            return 10000 + (puertoCtrl - 20000);
        }
        return 10000;
    }

    private static final class MutualExclusionClient extends UnicastRemoteObject implements IClienteEM {

        private final Object lock = new Object();
        private final AtomicBoolean tengoToken = new AtomicBoolean(false);
        private volatile CountDownLatch tokenLatch = new CountDownLatch(0);
        private volatile IServicioExclusionMutua servicio;

        protected MutualExclusionClient() throws RemoteException {
            super();
        }

        public void solicitarToken() throws Exception {
            synchronized (lock) {
                if (tengoToken.get()) {
                    System.out.println("[EV-FERTI][EM] El token ya estaba en posesión de la EV.");
                    return;
                }
                CountDownLatch latch = new CountDownLatch(1);
                tokenLatch = latch;
                resolverServicio().ObtenerRecurso(this);
                if (!latch.await(TIMEOUT_TOKEN_SEG, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Tiempo de espera agotado esperando el token EM");
                }
                tengoToken.set(true);
            }
        }

        public void devolverToken() throws Exception {
            synchronized (lock) {
                if (!tengoToken.get()) {
                    System.out.println("[EV-FERTI][EM] No se devuelve token porque no está en posesión.");
                    return;
                }
                resolverServicio().DevolverRecurso();
                tengoToken.set(false);
            }
        }

        public void invalidarServicio() {
            servicio = null;
        }

        @Override
        public void RecibirToken() throws RemoteException {
            CountDownLatch latch = tokenLatch;
            if (latch != null) {
                latch.countDown();
            }
        }

        private IServicioExclusionMutua resolverServicio() throws Exception {
            if (servicio != null) {
                return servicio;
            }
            String url = resolverUrlEM();
            servicio = (IServicioExclusionMutua) Naming.lookup(url);
            System.out.println("[EV-FERTI][EM] Conectado al servidor de exclusión mutua en " + url);
            return servicio;
        }
    }
}
