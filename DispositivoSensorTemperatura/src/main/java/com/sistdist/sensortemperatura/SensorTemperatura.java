package com.sistdist.sensortemperatura;

import com.sistdist.interfaces.IControladorLider;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.Socket;
import java.rmi.Naming;

/**
 * Sensor de temperatura con reconexión automática al nuevo líder
 */
public class SensorTemperatura {

    public static void main(String[] args) {
        new SensorTemperatura().iniciar();
    }

    private void iniciar() {
        while (true) {
            String host = "localhost";
            int puerto = 20000;

            try {
                // ====== Consultar líder actual via RMI ======
                String liderBind = "rmi://localhost:1099/ControladorLider";
                IControladorLider stub = (IControladorLider) Naming.lookup(liderBind);

                String liderUrl = stub.obtenerLiderActual(); // ej: rmi://localhost:1103/RingElection-CTRL3
                host = liderUrl.replace("rmi://", "").split(":")[0];
                puerto = stub.obtenerPuertoLider();

                System.out.println("[SensorTemperatura] Conectando al líder actual en " + host + ":" + puerto);

                // ====== Crear conexión TCP al líder ======
                InetAddress IPServidor = InetAddress.getByName(host);
                try (Socket socket = new Socket(IPServidor, puerto)) {

                    PrintWriter pw = new PrintWriter(socket.getOutputStream(), true);
                    pw.println("sensorTemperatura;0");
                    pw.flush();

                    // ====== Hilo de envío periódico ======
                    HiloSensadoTemperatura hilo = new HiloSensadoTemperatura(socket, pw);
                    hilo.start();

                    // ====== Monitorear conexión ======
                    while (!socket.isClosed() && socket.isConnected()) {
                        Thread.sleep(10000);
                    }

                    System.out.println("[SensorTemperatura] Conexión cerrada con el líder " + host + ". Intentando reconectar...");

                } catch (Exception e) {
                    System.err.println("[SensorTemperatura] Error en la conexión TCP con el líder (" + host + ":" + puerto + "): " + e.getMessage());
                }

            } catch (Exception e) {
                System.err.println("[SensorTemperatura] No se pudo obtener líder actual: " + e.getMessage());
            }

            // ====== Esperar un poco antes de reintentar ======
            try {
                Thread.sleep(5000);
            } catch (InterruptedException ignored) {}
        }
    }
}
