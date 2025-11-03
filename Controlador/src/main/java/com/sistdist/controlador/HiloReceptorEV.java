package com.sistdist.controlador;

import java.io.*;
import java.net.*;
import java.util.logging.*;

public class HiloReceptorEV extends Thread {
    private final int evId;
    private final BufferedReader br;

    public HiloReceptorEV(Socket s, int evId) {
        this.evId = evId;
        try {
            this.br = new BufferedReader(new InputStreamReader(s.getInputStream()));
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }

    @Override
    public void run() {
        if (Controlador.LOG_CONFIRMACIONES_EV) {
        System.out.println("[CTRL] Receptor iniciado para EV" + evId);}
        while (true) {
            try {
                String entrada = br.readLine();
                if (entrada == null) break;

                // Mostrar confirmaciones que mande la EV
                if (Controlador.LOG_CONFIRMACIONES_EV) {
                System.out.printf("[CTRL] Confirmación de EV%d -> %s%n", evId, entrada);}

            } catch (IOException ex) {
                Logger.getLogger(HiloReceptorEV.class.getName()).log(Level.SEVERE, null, ex);
                break;
            }
        }
    }
}

