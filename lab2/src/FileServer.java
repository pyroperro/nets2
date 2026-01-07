import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.concurrent.*;

public class FileServer {

    private static final int BUFFER_SIZE = 64 * 1024;

    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            System.err.println("Usage: java FileServer <port>");
            return;
        }

        int port = Integer.parseInt(args[0]);

        Path uploadDir = Paths.get("uploads").toAbsolutePath().normalize();
        Files.createDirectories(uploadDir);

        ExecutorService pool = Executors.newCachedThreadPool();

        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("Server listening on port " + port);

            while (true) {
                Socket client = serverSocket.accept();
                pool.submit(new ClientHandler(client, uploadDir));
            }
        }
    }

    static class ClientHandler implements Runnable {

        private final Socket socket;
        private final Path uploadDir;

        ClientHandler(Socket socket, Path uploadDir) {
            this.socket = socket;
            this.uploadDir = uploadDir;
        }

        @Override
        public void run() {
            String clientId = socket.getRemoteSocketAddress().toString();

            try (
                    DataInputStream in = new DataInputStream(
                            new BufferedInputStream(socket.getInputStream()));
                    DataOutputStream out = new DataOutputStream(
                            new BufferedOutputStream(socket.getOutputStream()))
            ) {
                // ---- имя файла ----
                int nameLen = in.readInt();
                if (nameLen <= 0 || nameLen > 4096) {
                    throw new IOException("Invalid filename length");
                }

                byte[] nameBytes = new byte[nameLen];
                in.readFully(nameBytes);
                String fileName = new String(nameBytes, StandardCharsets.UTF_8);

                fileName = Paths.get(fileName).getFileName().toString(); // защита от ../

                // ---- размер ----
                long declaredSize = in.readLong();
                if (declaredSize < 0) {
                    throw new IOException("Invalid file size");
                }

                Path outFile = uploadDir.resolve(fileName).normalize();
                if (!outFile.startsWith(uploadDir)) {
                    throw new IOException("Path traversal attempt");
                }

                System.out.println("[" + clientId + "] Receiving: " + fileName +
                        " (" + declaredSize + " bytes)");

                // ---- приём данных ----
                long received = 0;
                byte[] buffer = new byte[BUFFER_SIZE];

                long startTime = System.nanoTime();
                long lastTime = startTime;
                long lastBytes = 0;

                try (OutputStream fileOut =
                             new BufferedOutputStream(Files.newOutputStream(outFile))) {

                    while (received < declaredSize) {
                        int toRead = (int) Math.min(buffer.length,
                                declaredSize - received);
                        int read = in.read(buffer, 0, toRead);
                        if (read == -1) {
                            break;
                        }

                        fileOut.write(buffer, 0, read);
                        received += read;

                        long now = System.nanoTime();
                        if (now - lastTime >= 3_000_000_000L) {
                            printSpeed(clientId, received, lastBytes,
                                    now - lastTime, now - startTime);
                            lastTime = now;
                            lastBytes = received;
                        }
                    }
                }

                long endTime = System.nanoTime();
                if (endTime - startTime < 3_000_000_000L) {
                    printSpeed(clientId, received, 0,
                            endTime - startTime, endTime - startTime);
                }

                boolean ok = (received == declaredSize);
                out.writeByte(ok ? 0 : 1);
                out.flush();

                System.out.println("[" + clientId + "] Finished: " +
                        (ok ? "OK" : "ERROR"));

            } catch (Exception e) {
                System.err.println("[" + clientId + "] Error: " + e.getMessage());
            } finally {
                try { socket.close(); } catch (IOException ignored) {}
            }
        }

        private void printSpeed(String id,
                                long totalBytes,
                                long prevBytes,
                                long intervalNs,
                                long totalNs) {

            double instSpeed = (totalBytes - prevBytes) /
                    (intervalNs / 1e9);
            double avgSpeed = totalBytes /
                    (totalNs / 1e9);

            System.out.printf("[%s] speed: %.2f B/s, avg: %.2f B/s%n",
                    id, instSpeed, avgSpeed);
        }
    }
}
