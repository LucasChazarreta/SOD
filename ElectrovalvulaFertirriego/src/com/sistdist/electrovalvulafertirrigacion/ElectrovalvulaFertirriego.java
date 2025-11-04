package com.sistdist.electrovalvulafertirriego;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.rmi.Naming;

import com.sistdist.interfaces.IControladorLider;

public class ElectrovalvulaFertirriego {

    private static final String LIDER_BIND = "rmi://localhost:1099/ControladorLider";
    private static final String HOST_POR_DEFECTO = "127.0.0.1";
    private static final int PUERTO_POR_DEFECTO = 20000;
    private static final long REINTENTO_MS = 1000L;

    public static void main(String[] args) {
        final int id = 7; // ID de la fertirrigación

        Socket socket = null;
        PrintWriter out = null;
        BufferedReader in = null;

        while (true) {
            try {
                if (socket == null || socket.isClosed() || !socket.isConnected()) {
                    socket = abrirConexionConLider();
                    out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);
                    in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));

                    out.println("electrovalvula;" + id);
                    out.flush();
                    System.out.println("[EV-FERTI] Handshake enviado: electrovalvula;" + id);
                }

                String comando = in.readLine();
                if (comando == null) {
                    throw new IOException("Conexión cerrada por el líder");
                }

                comando = comando.trim();

                if (comando.regionMatches(true, 0, "log|", 0, 4)) {
                    System.out.println(comando.substring(4));
                    continue;
                }

                if ("abrir".equalsIgnoreCase(comando)) {
                    System.out.println("[EV-FERTI] Válvula de fertirrigación abierta.");
                    out.println("EV" + id + ":ok_abierta");
                } else if ("cerrar".equalsIgnoreCase(comando)) {
                    System.out.println("[EV-FERTI] Válvula de fertirrigación cerrada.");
                    out.println("EV" + id + ":ok_cerrada");
                } else {
                    System.out.println("[EV-FERTI] Comando desconocido: " + comando);
                    out.println("EV" + id + ":error");
                }

                out.flush();
            } catch (IOException e) {
                System.err.println("[EV-FERTI] Error de comunicación: " + e.getMessage());
                cerrarRecursos(out, in, socket);
                out = null;
                in = null;
                socket = null;
                dormir();
            } catch (Exception e) {
                System.err.println("[EV-FERTI] Error al conectar con el líder: " + e.getMessage());
                cerrarRecursos(out, in, socket);
                out = null;
                in = null;
                socket = null;
                dormir();
            }
        }
    }

    private static Socket abrirConexionConLider() throws Exception {
        LiderInfo info = obtenerInfoLider();
        InetAddress ipServidor = InetAddress.getByName(info.host());
        Socket socket = new Socket(ipServidor, info.puerto());
        System.out.println("[EV-FERTI] Conectada a " + info.host() + ":" + info.puerto());
        return socket;
    }

    private static LiderInfo obtenerInfoLider() {
        String host = HOST_POR_DEFECTO;
        int puerto = PUERTO_POR_DEFECTO;

        try {
            IControladorLider stub = (IControladorLider) Naming.lookup(LIDER_BIND);
            String direccion = stub.obtenerLiderActual();
            host = extraerHost(direccion, host);

            int puertoObtenido = stub.obtenerPuertoLider();
            if (puertoObtenido > 0) {
                puerto = puertoObtenido;
            }

            System.out.println("[EV-FERTI] Líder actual: " + host + ":" + puerto);
        } catch (Exception e) {
            System.err.println("[EV-FERTI] No se pudo obtener líder actual (" + e.getMessage()
                    + "), usando " + host + ":" + puerto + ".");
        }

        return new LiderInfo(host, puerto);
    }

    private static String extraerHost(String direccionRmi, String hostPorDefecto) {
        if (direccionRmi == null || direccionRmi.isEmpty()) {
            return hostPorDefecto;
        }

        String sinPrefijo = direccionRmi.replaceFirst("^rmi://", "");
        int finHost = sinPrefijo.indexOf('/');
        String hostYPuerto = finHost >= 0 ? sinPrefijo.substring(0, finHost) : sinPrefijo;

        int separadorPuerto = hostYPuerto.indexOf(':');
        if (separadorPuerto >= 0) {
            return hostYPuerto.substring(0, separadorPuerto);
        }

        return hostYPuerto;
    }

    private static void cerrarRecursos(PrintWriter out, BufferedReader in, Socket socket) {
        if (out != null) {
            out.close();
        }
        try {
            if (in != null) {
                in.close();
            }
        } catch (IOException ignored) {
        }
        try {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        } catch (IOException ignored) {
        }
    }

    private static void dormir() {
        try {
            Thread.sleep(REINTENTO_MS);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }

    private static final class LiderInfo {
        private final String host;
        private final int puerto;

        private LiderInfo(String host, int puerto) {
            this.host = host;
            this.puerto = puerto;
        }

        String host() {
            return host;
        }

        int puerto() {
            return puerto;
        }
    }
}
