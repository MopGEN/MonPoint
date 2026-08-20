import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;

public class Importador {

    public static void main(String[] args) {
        if (args.length < 4) {
            System.out.println("Uso: java Importador <host> <usuario> <contraseña> <archivoSQL>");
            return;
        }

        String host = args[0];
        String user = args[1];
        String password = args[2];
        String archivoSQL = args[3];

        try {
            ProcessBuilder pb = new ProcessBuilder(
                "mysql",
                "-h", host,
                "-u", user,
                "-p" + password,
                "-e", "source " + archivoSQL
            );

            pb.redirectErrorStream(true);
            Process process = pb.start();

            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String linea;
            while ((linea = reader.readLine()) != null) {
                System.out.println(linea);
            }

            int exitCode = process.waitFor();
            if (exitCode == 0) {
                System.out.println("Base de datos importada correctamente.");
            } else {
                System.err.println("Error al importar la base de datos. Código: " + exitCode);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
