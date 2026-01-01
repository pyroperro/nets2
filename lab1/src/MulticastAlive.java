import java.io.IOException;
import java.net.*;
import java.util.ArrayList;
import java.util.List;

public class MulticastAlive {

    private static final String MESSAGE = "alive";
    private static final int BUFFER_SIZE = 1024;
    private static final int TIMEOUT_MS = 1000;

    private static void sendMulticastMessage(
            InetAddress group,
            int port
    ) throws IOException {

        try (DatagramSocket socket = new DatagramSocket()) {

            byte[] data = MESSAGE.getBytes();
            DatagramPacket packet =
                    new DatagramPacket(data, data.length, group, port);

            socket.send(packet);
        }
    }

    private static List<String> receiveMulticastMessages(
            MulticastSocket socket
    ) throws IOException {

        List<String> list = new ArrayList<>();
        byte[] buf = new byte[BUFFER_SIZE];

        while (true) {
            DatagramPacket packet = new DatagramPacket(buf, buf.length);
            try {
                socket.receive(packet);
            } catch (SocketTimeoutException e) {
                break;
            }

            InetAddress addr = packet.getAddress();
            list.add(addr.getHostAddress());
        }
        return list;
    }

    public static void main(String[] args) throws Exception {

        String host;
        int port;

        if (args.length != 2) {
            System.out.println("Дефолтные аргументы: 239.255.255.254 5000");
            host = "239.255.255.254";
            port = 5000;
        } else {
            host = args[0];
            port = Integer.parseInt(args[1]);
        }

        InetAddress group = InetAddress.getByName(host);
        boolean ipv6 = group instanceof Inet6Address;

        System.out.printf(
                "Подключились на %s/%d (%s)%n",
                host,
                port,
                ipv6 ? "IPv6" : "IPv4"
        );

        MulticastSocket socket = new MulticastSocket(port);
        socket.setReuseAddress(true);
        socket.setSoTimeout(TIMEOUT_MS);

        if (ipv6) {
            socket.joinGroup(
                    new InetSocketAddress(group, port),
                    NetworkInterface.getByInetAddress(InetAddress.getLocalHost())
            );
        } else {
            socket.joinGroup(group);
        }

        while (true) {
            sendMulticastMessage(group, port);
            List<String> res = receiveMulticastMessages(socket);
            System.out.printf("Найдено копий: %d:%n", res.size());
            for (String ip : res) {
                System.out.println("\t" + ip);
            }
        }
    }
}
