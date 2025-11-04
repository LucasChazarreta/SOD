package com.sistdist.electrovalvula3;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.Socket;
import java.rmi.Naming;

import com.sistdist.interfaces.IControladorLider;

public class Electrovalvula3 {

    private static final String LIDER_BIND = "rmi://localhost:1099/ControladorLider";
    private static final String HOST_POR_DEFECTO = "127.0.0.1";
    private static final int PUERTO_POR_DEFECTO = 20000;
    private static final long REINTENTO_MS = 1000L;

    public static void main(String[] args) {
        final int id = 3;

        Socket socket = null;
        PrintWriter out = null;
        BufferedReader in = null;

        while (true) {
            try {
                if (socket == null || socket.isClosed() || !socket.isConnected()) {
                    socket = abrirConexionConLider(id);
                    out = new PrintWriter(socket.getOutputStream(), true);
                    in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

                    out.println("electrovalvula;" + id);
                    out.flush();
                    System.out.println("[EV" + id + "] Handshake enviado: electrovalvula;" + id);
                }

                String comando = in.readLine();
                if (comando == null) {
                    throw new IOException("Conexión cerrada por el líder");
                }

                if ("abrir".equals(comando)) {
                    System.out.println("[EV" + id + "] Válvula abierta, bomba activada.");
                } else if ("cerrar".equals(comando)) {
                    System.out.println("[EV" + id + "] Válvula cerrada, bomba detenida.");
                } else {
                    System.out.println("[EV" + id + "] Comando desconocido: " + comando);
                }

            } catch (IOException e) {
                System.err.println("[EV" + id + "] Error de comunicación: " + e.getMessage());
                cerrarRecursos(out, in, socket);
                out = null;
                in = null;
                socket = null;
                dormir();
            } catch (Exception e) {
                System.err.println("[EV" + id + "] Error al conectar con el líder: " + e.getMessage());
                cerrarRecursos(out, in, socket);
                out = null;
                in = null;
                socket = null;
                dormir();
            }
        }
    }

    private static Socket abrirConexionConLider(int id) throws Exception {
        LiderInfo info = obtenerInfoLider(id);
        InetAddress ipServidor = InetAddress.getByName(info.host());
        Socket socket = new Socket(ipServidor, info.puerto());
        System.out.println("[EV" + id + "] Conectada a " + info.host() + ":" + info.puerto());
        return socket;
    }

    private static LiderInfo obtenerInfoLider(int id) {
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

            System.out.println("[EV" + id + "] Líder actual: " + host + ":" + puerto);
        } catch (Exception e) {
            System.err.println("[EV" + id + "] No se pudo obtener líder actual (" + e.getMessage()
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
