package com.sistdist.controlador;

import java.io.*;
import java.net.*;
import java.util.logging.*;

public class HiloReceptorRadiacion extends Thread {
    private final int id;
    private final BufferedReader br;

    public HiloReceptorRadiacion(Socket s, int id) {
        this.id = id;
        try {
            this.br = new BufferedReader(new InputStreamReader(s.getInputStream()));
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }

    @Override
    public void run() {
        //System.out.println("[CTRL] Receptor de RADIACION iniciado (id=" + id + ")");
        while (true) {
            try {
                String entrada = br.readLine();
                if (entrada == null) break;

                double r = Double.parseDouble(entrada.trim());
                Controlador.rad = r;
                //System.out.printf("[CTRL] Radiación(id=%d) -> %.0f%n", id, r);
            } catch (IOException ex) {
                Logger.getLogger(HiloReceptorRadiacion.class.getName()).log(Level.SEVERE, null, ex);
                break;
            } catch (NumberFormatException nfe) {
                System.out.println("[CTRL] Valor de radiación inválido.");
            }
        }
    }
}
