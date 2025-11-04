package com.sistdist.sensorlluvia;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.Random;
import java.util.logging.Level;
import java.util.logging.Logger;

public class HiloSensadoLluvia extends Thread {
    private static final long PERIOD_MS = 10_000L;
    private static final long REINTENTO_MS = 2_000L;

    private Socket conexion;
    private PrintWriter out;
    private final Random rand = new Random();
    private boolean on = true;
    private final SensorLluvia.ConexionProvider conexionProvider;
    private String hostActual = "desconocido";
    private int puertoActual = -1;
    private int valorActual = 0;

    public HiloSensadoLluvia(SensorLluvia.ConexionProvider provider) {
        this.conexionProvider = provider;
    }

    @Override
    public void run() {
        long next = System.currentTimeMillis() + PERIOD_MS;
        on = true;

        while (on) {
            try {
                asegurarConexion();

                long wait = next - System.currentTimeMillis();
                if (wait > 0) {
                    Thread.sleep(wait);
                }

                valorActual = rand.nextDouble() < 0.2 ? 1 : 0;
                System.out.printf("[SENSOR-L] Valor: %d -> %s:%d%n", valorActual, hostActual, puertoActual);

                out.println(valorActual);
                out.flush();

                if (out.checkError()) {
                    throw new IOException("Error al escribir en el socket");
                }

                next += PERIOD_MS;
            } catch (InterruptedException ex) {
                Logger.getLogger(HiloSensadoLluvia.class.getName()).log(Level.SEVERE, null, ex);
                Thread.currentThread().interrupt();
                on = false;
            } catch (Exception e) {
                System.err.println("[SENSOR-L] Error enviando datos al líder: " + e.getMessage());
                cerrarConexionActual();
                next = System.currentTimeMillis() + PERIOD_MS;
                try {
                    Thread.sleep(REINTENTO_MS);
                } catch (InterruptedException ex) {
                    Logger.getLogger(HiloSensadoLluvia.class.getName()).log(Level.SEVERE, null, ex);
                    Thread.currentThread().interrupt();
                    on = false;
                }
            }
        }

        cerrarConexionActual();
    }

    private void asegurarConexion() throws Exception {
        if (conexion != null && conexion.isConnected() && !conexion.isClosed() && !conexion.isOutputShutdown()
                && out != null && !out.checkError()) {
            return;
        }

        if (conexion != null) {
            System.out.printf("[SENSOR-L] Conexión perdida con %s:%d, reintentando...%n", hostActual, puertoActual);
        }

        cerrarConexionActual();
        SensorLluvia.ConexionLider nuevaConexion = conexionProvider.nuevaConexion();
        this.conexion = nuevaConexion.socket();
        this.out = nuevaConexion.writer();
        this.hostActual = nuevaConexion.host();
        this.puertoActual = nuevaConexion.puerto();
    }

    private void cerrarConexionActual() {
        if (out != null) {
            out.close();
            out = null;
        }

        if (conexion != null) {
            try {
                conexion.close();
            } catch (IOException ignored) {
            }
            conexion = null;
        }

        hostActual = "desconocido";
        puertoActual = -1;
    }
}