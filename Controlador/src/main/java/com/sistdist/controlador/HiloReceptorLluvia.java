package com.sistdist.controlador;

import java.io.*;
import java.net.*;
import java.util.logging.*;

public class HiloReceptorLluvia extends Thread {
    private final int id;
    private final BufferedReader br;

    public HiloReceptorLluvia(Socket s, int id) {
        this.id = id;
        try {
            this.br = new BufferedReader(new InputStreamReader(s.getInputStream()));
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }

    @Override
    public void run() {
        //System.out.println("[CTRL] Receptor de LLUVIA iniciado (id=" + id + ")");
        while (true) {
            try {
                String entrada = br.readLine();
                if (entrada == null) break;

                int v = Integer.parseInt(entrada.trim()); // 0/1 
                // 0 -> no llueve 1-> si llueve
                Controlador.lluvia = (v > 0);
                //System.out.printf("[CTRL] Lluvia(id=%d) -> %s%n", id, Controlador.lluvia ? "SI" : "NO");
            } catch (IOException ex) {
                Logger.getLogger(HiloReceptorLluvia.class.getName()).log(Level.SEVERE, null, ex);
                break;
            } catch (NumberFormatException nfe) {
                System.out.println("[CTRL] Valor de lluvia inválido: " + nfe.getMessage());
            }
        }
    }
}
