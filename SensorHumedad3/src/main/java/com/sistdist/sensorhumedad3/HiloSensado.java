package com.sistdist.sensorhumedad3;

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
    private final SensorHumedad3.ConexionProvider conexionProvider;
    private String hostActual = "desconocido";
    private int puertoActual = -1;

    public HiloSensado(SensorHumedad3.ConexionProvider provider){
        this.conexionProvider = provider;
    }

    public double generarHumedad(){
        return Math.random() * 100;
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
    public void run(){
        on = true;
        long next = System.currentTimeMillis() + PERIOD_MS;

        while(on){
            try {
                asegurarConexion();

                long wait = next - System.currentTimeMillis();
                if (wait > 0) {
                    Thread.sleep(wait);
                }

                humedad = generarHumedad();
                System.out.printf("[SENSOR-H3] Humedad: %.1f%% -> %s:%d%n", humedad, hostActual, puertoActual);

                pw.println(humedad);
                pw.flush();

                if (pw.checkError()) {
                    throw new IOException("Error al escribir en el socket");
                }

                next += PERIOD_MS;
            } catch (InterruptedException ex) {
                Logger.getLogger(HiloSensado.class.getName()).log(Level.SEVERE, null, ex);
                Thread.currentThread().interrupt();
                on = false;
            } catch (Exception e) {
                System.err.println("[SENSOR-H3] Error enviando datos al líder: " + e.getMessage());
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
            System.out.printf("[SENSOR-H3] Conexión perdida con %s:%d, reintentando...%n", hostActual, puertoActual);
        }

        cerrarConexionActual();
        SensorHumedad3.ConexionLider nuevaConexion = conexionProvider.nuevaConexion();
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
