package com.sistdist.sensortemperatura;

import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.Socket;
import com.sistdist.interfaces.IControladorLider;
import java.rmi.Naming;

public class SensorTemperatura {

    public static void main(String[] args) {
        try {
            // ======== NUEVO: Consultar al anillo quién es el líder ========
            String liderBind = "rmi://localhost:1099/ControladorLider";
            String host = "localhost";
            int puerto = 20000; // valor por defecto

            try {
                IControladorLider stub = (IControladorLider) Naming.lookup(liderBind);
                host = stub.obtenerLiderActual().replace("rmi://", "").split(":")[1].replace("/", "");
                puerto = stub.obtenerPuertoLider();
                System.out.println("[SensorTemperatura] Conectando al líder actual en " + host + ":" + puerto);
            } catch (Exception e) {
                System.err.println("[SensorTemperatura] No se pudo obtener líder actual, usando por defecto " + host + ":" + puerto);
            }

            // ======== seguir normalmente con tu conexión TCP ========
            InetAddress IPServidor = InetAddress.getByName(host);
            Socket socket = new Socket(IPServidor, puerto);
            PrintWriter pw = new PrintWriter(socket.getOutputStream());

            // Handshake inicial
            pw.println("sensorTemperatura;0");
            pw.flush();

            // Hilo que manda datos continuamente
            HiloSensadoTemperatura hilo = new HiloSensadoTemperatura(socket, pw);
            hilo.start();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
