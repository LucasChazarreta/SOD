package com.sistdist.sensortemperatura;

import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.Socket;
import java.rmi.Naming;

import com.sistdist.interfaces.IControladorLider;

public class SensorTemperatura {

    private static final String LIDER_BIND = "rmi://localhost:1099/ControladorLider";
    private static final String HOST_POR_DEFECTO = "localhost";
    private static final int PUERTO_POR_DEFECTO = 20000;

    private static volatile LiderInfo ultimoLiderConocido = new LiderInfo(HOST_POR_DEFECTO, PUERTO_POR_DEFECTO);

    public static void main(String[] args) {
        HiloSensadoTemperatura hilo = new HiloSensadoTemperatura(SensorTemperatura::abrirConexionConLider);
        hilo.start();
    }

    private static ConexionLider abrirConexionConLider() throws Exception {
        LiderInfo info = obtenerInfoLider();
        InetAddress ipServidor = InetAddress.getByName(info.host());
        Socket socket = new Socket(ipServidor, info.puerto());
        PrintWriter pw = new PrintWriter(socket.getOutputStream(), true);

        // Handshake inicial para registrar el sensor en el controlador
        pw.println("sensorTemperatura;0");

        System.out.println("[SensorTemperatura] Conectado al líder en " + info.host() + ":" + info.puerto());
        return new ConexionLider(socket, pw, info.host(), info.puerto());
    }

    private static LiderInfo obtenerInfoLider() {
        LiderInfo ultimoLider = ultimoLiderConocido;
        String host = ultimoLider.host();
        int puerto = ultimoLider.puerto();

        try {
            IControladorLider stub = (IControladorLider) Naming.lookup(LIDER_BIND);
            String direccion = stub.obtenerLiderActual();
            host = extraerHost(direccion, host);

            int puertoObtenido = stub.obtenerPuertoLider();
            if (puertoObtenido > 0) {
                puerto = puertoObtenido;
            }

            LiderInfo nuevoLider = new LiderInfo(host, puerto);
            ultimoLiderConocido = nuevoLider;
            System.out.println("[SensorTemperatura] Líder actual: " + host + ":" + puerto);
        } catch (Exception e) {
            System.err.println("[SensorTemperatura] No se pudo obtener líder actual (" + e.getMessage()
                    + "), usando último conocido " + host + ":" + puerto);
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

    @FunctionalInterface
    public interface ConexionProvider {
        ConexionLider nuevaConexion() throws Exception;
    }

    public record ConexionLider(Socket socket, PrintWriter writer, String host, int puerto) {
    }

    private record LiderInfo(String host, int puerto) {
    }
}
