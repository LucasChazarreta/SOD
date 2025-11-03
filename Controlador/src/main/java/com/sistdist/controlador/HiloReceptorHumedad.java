package com.sistdist.controlador;

import java.io.*;
import java.net.Socket;
import java.util.logging.*;

public class HiloReceptorHumedad extends Thread {
    private final int parcelaId;
    private final BufferedReader br;

    public HiloReceptorHumedad(Socket ch, int parcelaId) {
        this.parcelaId = parcelaId;
        try {
            this.br = new BufferedReader(new InputStreamReader(ch.getInputStream()));
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }

    @Override
    public void run() {
        // System.out.println("Receptor humedad iniciado para parcela " + parcelaId);
        while (true) {
            try {
                String entrada = br.readLine();
                if (entrada == null) break;

                double h = Double.parseDouble(entrada);
                Controlador.humedades.put(parcelaId, h);

            } catch (IOException ex) {
                Logger.getLogger(HiloReceptorHumedad.class.getName()).log(Level.SEVERE, null, ex);
                break;
            } catch (NumberFormatException nfe) {
                System.out.println("Valor de humedad inválido recibido en parcela " + parcelaId);
            }
        }
    }
}
