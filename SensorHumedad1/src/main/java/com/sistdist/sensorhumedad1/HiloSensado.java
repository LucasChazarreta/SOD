
package com.sistdist.sensorhumedad1;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.logging.Level;
import java.util.logging.Logger;

public class HiloSensado extends Thread {

    private static final long PERIOD_MS = 10_000L;
    private static final long REINTENTO_MS = 2_000L;

    private boolean on;
    private double humedad;
    private Socket cnxServidor;
    private PrintWriter pw;
    private final SensorHumedad1.ConexionProvider conexionProvider;
    private String hostActual = "desconocido";
    private int puertoActual = -1;

    public HiloSensado(SensorHumedad1.ConexionProvider provider) {
        this.conexionProvider = provider;
    }

    public double generarHumedad() {
        // 50% de probabilidad de generar un valor menor a 50
        if (Math.random() < 0.5) {
            // Genera un valor entre 0 y 50 (activará la fertirrigación)
            return Math.random() * 50;
        } else {
            // Genera un valor entre 50 y 100 (no activará la fertirrigación)
            return 50 + Math.random() * 50;
        }
    }

    public void encender(){
        on = true;
    }

    public void apagar(){
        on = false;
    }

    public double leerHumedad(){
        return humedad;
    }

    @Override
    public void run() {
        on = true;
        long next = System.currentTimeMillis() + PERIOD_MS;

        while (on) {
            try {
                asegurarConexion();

                long wait = next - System.currentTimeMillis();
                if (wait > 0) {
                    Thread.sleep(wait);
                }

                // humedad = generarHumedad();
                humedad = 10;
                System.out.printf("[SENSOR-H1] Humedad: %.1f%% -> %s:%d%n", humedad, hostActual, puertoActual);

                pw.println(humedad);
                pw.flush();

                if (pw.checkError()) {
                    throw new IOException("Error al escribir en el socket");
                }

                next += PERIOD_MS; // siguiente disparo exacto
            } catch (InterruptedException ex) {
                Logger.getLogger(HiloSensado.class.getName()).log(Level.SEVERE, null, ex);
                Thread.currentThread().interrupt();
                on = false;
            } catch (Exception e) {
                System.err.println("[SENSOR-H1] Error enviando datos al líder: " + e.getMessage());
                cerrarConexionActual();
                next = System.currentTimeMillis() + PERIOD_MS;
                try {
                    Thread.sleep(REINTENTO_MS);
                } catch (InterruptedException ex) {
                    Logger.getLogger(HiloSensado.class.getName()).log(Level.SEVERE, null, ex);
                    Thread.currentThread().interrupt();
                    on = false;
                }
            }
        }

        cerrarConexionActual();
    }

    private void asegurarConexion() throws Exception {
        if (cnxServidor != null && cnxServidor.isConnected() && !cnxServidor.isClosed()
                && !cnxServidor.isOutputShutdown() && pw != null && !pw.checkError()) {
            return;
        }

        if (cnxServidor != null) {
            System.out.printf("[SENSOR-H1] Conexión perdida con %s:%d, reintentando...%n", hostActual, puertoActual);
        }

        cerrarConexionActual();
        SensorHumedad1.ConexionLider nuevaConexion = conexionProvider.nuevaConexion();
        this.cnxServidor = nuevaConexion.socket();
        this.pw = nuevaConexion.writer();
        this.hostActual = nuevaConexion.host();
        this.puertoActual = nuevaConexion.puerto();
    }

    private void cerrarConexionActual() {
        if (pw != null) {
            pw.close();
            pw = null;
        }

        if (cnxServidor != null) {
            try {
                cnxServidor.close();
            } catch (IOException ignored) {
            }
            cnxServidor = null;
        }

        hostActual = "desconocido";
        puertoActual = -1;
    }
}
