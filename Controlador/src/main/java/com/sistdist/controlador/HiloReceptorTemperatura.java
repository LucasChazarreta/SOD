package com.sistdist.controlador;

import java.io.*;
import java.net.*;
import java.util.logging.*;

public class HiloReceptorTemperatura extends Thread {
    private final int id;
    private final BufferedReader br;

    public HiloReceptorTemperatura(Socket s, int id) {
        this.id = id;
        try {
            this.br = new BufferedReader(new InputStreamReader(s.getInputStream()));
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }

    @Override
    public void run() {
        //System.out.println("[CTRL] Receptor de TEMPERATURA iniciado (id=" + id + ")");
        while (true) {
            try {
                String entrada = br.readLine();
                if (entrada == null) break;

                double t = Double.parseDouble(entrada.trim());
                Controlador.temp = t;
                //System.out.printf("[CTRL] Temp(id=%d) -> %.1f°C%n", id, t);
            } catch (IOException ex) {
                Logger.getLogger(HiloReceptorTemperatura.class.getName()).log(Level.SEVERE, null, ex);
                break;
            } catch (NumberFormatException nfe) {
                System.out.println("[CTRL] Valor de temperatura inválido.");
            }
        }
    }
}

