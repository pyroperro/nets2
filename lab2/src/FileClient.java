import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;

public class FileClient {

    private static final int BUFFER_SIZE = 64 * 1024;

    public static void main(String[] args) throws IOException {
        if (args.length != 3) {
            System.err.println(
                    "Usage: java FileClient <file> <server> <port>");
            return;
        }

        Path file = Paths.get(args[0]);
        String host = args[1];
        int port = Integer.parseInt(args[2]);

        if (!Files.isRegularFile(file)) {
            System.err.println("Not a file");
            return;
        }

        String fileName = file.getFileName().toString();
        byte[] nameBytes = fileName.getBytes(StandardCharsets.UTF_8);
        long size = Files.size(file);

        try (
                Socket socket = new Socket(host, port);
                DataOutputStream out = new DataOutputStream(
                        new BufferedOutputStream(socket.getOutputStream()));
                DataInputStream in = new DataInputStream(
                        new BufferedInputStream(socket.getInputStream()));
                InputStream fileIn = new BufferedInputStream(
                        Files.newInputStream(file))
        ) {
            // ---- заголовок ----
            out.writeInt(nameBytes.length);
            out.write(nameBytes);
            out.writeLong(size);

            byte[] buffer = new byte[BUFFER_SIZE];
            int read;
            while ((read = fileIn.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            out.flush();

            byte status = in.readByte();
            if (status == 0) {
                System.out.println("Файл отправлен успешно");
            } else {
                System.out.println("Отправка не удалась");
            }
        }
    }
}
