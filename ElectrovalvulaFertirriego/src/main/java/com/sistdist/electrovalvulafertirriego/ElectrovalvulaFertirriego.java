package com.sistdist.electrovalvulafertirriego;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;

public class ElectrovalvulaFertirriego {
    public static void main(String[] args) {
        int id = 7; // ID de la fertirrigación
        try (Socket socket = new Socket("127.0.0.1", 20000);
             PrintWriter out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);
             BufferedReader in  = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))) {

            System.out.println("run:");
            out.println("electrovalvula;" + id);
            out.flush();
            System.out.println("[EV-FERTI] Conectada al controlador como EV" + id);

            String comando;
            while ((comando = in.readLine()) != null) {
                comando = comando.trim();

                // Logs empujados por el Controlador/FERTI
                if (comando.regionMatches(true, 0, "log|", 0, 4)) {
                    System.out.println(comando.substring(4));
                    continue;
                }

                if ("abrir".equalsIgnoreCase(comando)) {
                    System.out.println("[EV-FERTI] Válvula de fertirrigación abierta.");
                    out.println("EV" + id + ":ok_abierta");
                } else if ("cerrar".equalsIgnoreCase(comando)) {
                    System.out.println("[EV-FERTI] Válvula de fertirrigación cerrada.");
                    out.println("EV" + id + ":ok_cerrada");
                } else {
                    System.out.println("[EV-FERTI] Comando desconocido: " + comando);
                    out.println("EV" + id + ":error");
                }
                out.flush();
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}