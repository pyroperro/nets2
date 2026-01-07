package socks;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.StandardSocketOptions;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.*;


public class Socks5Proxy {
    private static final int BUFFER_SIZE = 16000;

    private static class ClientState {
        SocketChannel client;
        SocketChannel target;
        int state = 0;

        ByteBuffer parseBuf = ByteBuffer.allocate(BUFFER_SIZE);

        ByteBuffer clientToTarget = ByteBuffer.allocate(BUFFER_SIZE * 2);
        ByteBuffer targetToClient = ByteBuffer.allocate(BUFFER_SIZE * 2);

        boolean replySent = false;

        @Override
        public String toString() {
            try {
                SocketAddress a = client != null ? client.getRemoteAddress() : null;
                SocketAddress b = target != null ? target.getRemoteAddress() : null;
                return "ClientState{" + a + " -> " + b + ", state=" + state + '}';
            } catch (IOException e) {
                return "ClientState{ioexception, state=" + state + '}';
            }
        }
    }

    public static void main(String[] args) throws IOException {
        int port = 1080;
        if (args.length >= 1) {
            try {
                port = Integer.parseInt(args[0]);
            } catch (NumberFormatException ignored) {
            }
        }

        Selector selector = Selector.open();
        ServerSocketChannel server = ServerSocketChannel.open();
        server.configureBlocking(false);
        server.bind(new InetSocketAddress(port));
        server.register(selector, SelectionKey.OP_ACCEPT);

        Map<SocketChannel, ClientState> states = new HashMap<>();

        System.out.println("[main] SOCKS5 NIO proxy listening on port " + port + "...");

        while (true) {
            selector.select();
            Iterator<SelectionKey> it = selector.selectedKeys().iterator();

            while (it.hasNext()) {
                SelectionKey key = it.next();
                it.remove();

                try {
                    if (key.isValid() && key.isAcceptable()) {
                        ServerSocketChannel s = (ServerSocketChannel) key.channel();
                        SocketChannel client = s.accept();
                        if (client != null) {
                            client.configureBlocking(false);
                            client.setOption(StandardSocketOptions.TCP_NODELAY, true);
                            SelectionKey clientKey = client.register(selector, SelectionKey.OP_READ);
                            ClientState cs = new ClientState();
                            cs.client = client;
                            cs.state = 0;
                            states.put(client, cs);

                            System.out.println("[main] new connection from " + client.getRemoteAddress());
                        }
                    }

                    if (key.isValid() && key.isReadable()) {
                        SocketChannel ch = (SocketChannel) key.channel();
                        ClientState cs = states.get(ch);

                        if (cs == null) {
                            ClientState found = findByTarget(states, ch);
                            if (found != null) {
                                handleReadFromTarget(selector, key, found, states);
                            } else {
                                closeChannelQuietly(ch);
                            }
                        } else {
                            handleReadFromClient(selector, key, cs, states);
                        }
                    }

                    if (key.isValid() && key.isConnectable()) {
                        SocketChannel ch = (SocketChannel) key.channel();
                        ClientState found = findByTarget(states, ch);
                        if (found != null) {
                            finishConnect(selector, key, found, states);
                        }
                    }

                    if (key.isValid() && key.isWritable()) {
                        SocketChannel ch = (SocketChannel) key.channel();
                        ClientState cs = states.get(ch);

                        if (cs != null) {
                            writeBufferToChannel(cs.targetToClient, ch);
                            updateInterestForChannel(ch.keyFor(selector), cs.targetToClient);
                        } else {
                            ClientState found = findByTarget(states, ch);
                            if (found != null) {
                                writeBufferToChannel(found.clientToTarget, ch);
                                updateInterestForChannel(ch.keyFor(selector), found.clientToTarget);
                            }
                        }
                    }
                } catch (IOException ex) {
                    try {
                        if (key.channel() instanceof SocketChannel sc) {
                            ClientState cs = states.get(sc);
                            if (cs != null) closeClientState(states, cs);
                            else {
                                ClientState f = findByTarget(states, sc);
                                if (f != null) closeClientState(states, f);
                                else closeChannelQuietly(sc);
                            }
                        }
                    } catch (IOException ignored) {
                    }
                }
            }
        }
    }

    private static ClientState findByTarget(Map<SocketChannel, ClientState> states, SocketChannel target) {
        for (ClientState cs : states.values()) {
            if (target.equals(cs.target)) return cs;
        }
        return null;
    }

    private static void handleReadFromClient(Selector selector, SelectionKey key, ClientState cs, Map<SocketChannel, ClientState> states) throws IOException {
        SocketChannel client = cs.client;
        ByteBuffer parseBuf = cs.parseBuf;
        int r = client.read(parseBuf);
        if (r == -1) {
            System.out.println("[main] client closed: " + client.getRemoteAddress());
            closeClientState(states, cs);
            return;
        }
        if (r == 0) return;

        parseBuf.flip();

        if (cs.state == 0) {
            if (parseBuf.remaining() >= 2) {
                byte ver = parseBuf.get(0);
                byte nmethods = parseBuf.get(1);
                if (parseBuf.remaining() >= 2 + (nmethods & 0xFF)) {
                    if (ver != 0x05) {
                        System.out.println("[debug] wrong SOCKS version: " + ver);
                        closeClientState(states, cs);
                        return;
                    }

                    ByteBuffer resp = ByteBuffer.wrap(new byte[]{0x05, 0x00});
                    client.write(resp);

                    cs.state = 1;


                    int remaining = parseBuf.remaining() - (2 + (nmethods & 0xFF));
                    if (remaining > 0) {
                        byte[] tail = new byte[remaining];
                        parseBuf.position(2 + (nmethods & 0xFF));
                        parseBuf.get(tail);
                        parseBuf.clear();
                        parseBuf.put(tail);
                    } else {
                        parseBuf.clear();
                    }
                    return;
                }
            }
            parseBuf.compact();
            return;
        }

        if (cs.state == 1) {
            if (parseBuf.remaining() < 4) {
                parseBuf.compact();
                return;
            }

            byte[] header = new byte[4];
            parseBuf.get(header);
            byte ver = header[0];
            byte cmd = header[1];
            byte rsv = header[2];
            byte atyp = header[3];

            if (ver != 0x05) {
                System.out.println("[debug] wrong request ver: " + ver);
                closeClientState(states, cs);
                return;
            }

            if (cmd != 0x01) {
                System.out.println("[debug] unsupported CMD: " + cmd);
                client.write(ByteBuffer.wrap(new byte[]{0x05, 0x07}));
                closeClientState(states, cs);
                return;
            }

            if (atyp == 0x01) {
                if (parseBuf.remaining() < 6) {
                    parseBuf.position(parseBuf.position() - 4);
                    parseBuf.compact();
                    return;
                }
                byte[] addr4 = new byte[4];
                parseBuf.get(addr4);
                int port = ((parseBuf.get() & 0xFF) << 8) | (parseBuf.get() & 0xFF);

                InetSocketAddress dest = new InetSocketAddress((addr4[0] & 0xFF) + "." + (addr4[1] & 0xFF) + "." + (addr4[2] & 0xFF) + "." + (addr4[3] & 0xFF), port);
                System.out.println("[debug] request IPv4 connect to " + dest);

                startTargetConnect(selector, cs, dest);
                parseBuf.clear();
                return;
            } else if (atyp == 0x03) {
                if (parseBuf.remaining() < 1) {
                    parseBuf.position(parseBuf.position() - 4);
                    parseBuf.compact();
                    return;
                }
                int len = parseBuf.get() & 0xFF;
                if (parseBuf.remaining() < len + 2) {
                    parseBuf.position(parseBuf.position() - 5); // вернуть header + len
                    parseBuf.compact();
                    return;
                }
                byte[] domainBytes = new byte[len];
                parseBuf.get(domainBytes);
                String domain = new String(domainBytes);
                int port = ((parseBuf.get() & 0xFF) << 8) | (parseBuf.get() & 0xFF);

                System.out.println("[debug] request DOMAIN connect to " + domain + ":" + port);

                InetAddress[] addrs = InetAddress.getAllByName(domain);
                if (addrs.length == 0) {
                    client.write(ByteBuffer.wrap(new byte[]{0x05, 0x04}));
                    closeClientState(states, cs);
                    return;
                }
                InetSocketAddress dest = new InetSocketAddress(addrs[0], port);
                startTargetConnect(selector, cs, dest);
                parseBuf.clear();
                return;
            } else {
                client.write(ByteBuffer.wrap(new byte[]{0x05, 0x08}));
                closeClientState(states, cs);
                return;
            }
        }

        if (cs.state == 2) {

            parseBuf.mark();
            int remaining = parseBuf.remaining();
            if (remaining > 0) {
                ensureSpace(cs.clientToTarget, remaining);
                cs.clientToTarget.put(parseBuf);
                if (cs.target != null && cs.target.isOpen()) {
                    SelectionKey targetKey = cs.target.keyFor(selector);
                    if (targetKey != null) targetKey.interestOps(targetKey.interestOps() | SelectionKey.OP_WRITE);
                }
            }
            parseBuf.clear();
            return;
        }

        parseBuf.compact();
    }

    private static void handleReadFromTarget(Selector selector, SelectionKey key, ClientState cs, Map<SocketChannel, ClientState> states) throws IOException {
        SocketChannel target = cs.target;
        ByteBuffer buf = ByteBuffer.allocate(BUFFER_SIZE);
        int r = target.read(buf);
        if (r == -1) {
            System.out.println("[main] target closed: " + target.getRemoteAddress());
            closeClientState(states, cs);
            return;
        }
        if (r == 0) return;

        buf.flip();
        ensureSpace(cs.targetToClient, buf.remaining());
        cs.targetToClient.put(buf);

        SelectionKey clientKey = cs.client.keyFor(selector);
        if (clientKey != null) clientKey.interestOps(clientKey.interestOps() | SelectionKey.OP_WRITE);
    }

    private static void finishConnect(Selector selector, SelectionKey key, ClientState cs, Map<SocketChannel, ClientState> states) throws IOException {
        SocketChannel target = (SocketChannel) key.channel();
        try {
            if (target.finishConnect()) {
                key.interestOps(SelectionKey.OP_READ); // слушаем чтение от target

                if (!cs.replySent) {
                    byte[] reply = new byte[10];
                    reply[0] = 0x05;
                    reply[1] = 0x00;
                    reply[2] = 0x00;
                    reply[3] = 0x01;
                    reply[4] = 0; reply[5] = 0; reply[6] = 0; reply[7] = 0;
                    reply[8] = 0; reply[9] = 0;
                    cs.client.write(ByteBuffer.wrap(reply));
                    cs.replySent = true;
                }

                cs.state = 2;

                System.out.println("[debug] connected to target " + target.getRemoteAddress());
            }
        } catch (IOException e) {
            System.out.println("[debug] connect failed to target: " + e.getMessage());
            cs.client.write(ByteBuffer.wrap(new byte[]{0x05, 0x05})); // connection refused
            closeClientState(states, cs);
        }
    }

    private static void startTargetConnect(Selector selector, ClientState cs, InetSocketAddress dest) throws IOException {
        SocketChannel target = SocketChannel.open();
        target.configureBlocking(false);
        target.setOption(StandardSocketOptions.TCP_NODELAY, true);
        try {
            boolean connected = target.connect(dest);
            cs.target = target;
            int ops = connected ? SelectionKey.OP_READ : SelectionKey.OP_CONNECT;
            target.register(selector, ops);
        } catch (IOException e) {
            System.out.println("[debug] socket create/connect failed: " + e.getMessage());
            cs.client.write(ByteBuffer.wrap(new byte[]{0x05, 0x01}));
            closeClientState(Collections.singletonMap(cs.client, cs), cs);
        }
    }

    private static void ensureSpace(ByteBuffer buf, int needed) {
        if (buf.remaining() < needed) {
            int newCap = Math.max(buf.capacity() * 2, buf.position() + needed);
            ByteBuffer nb = ByteBuffer.allocate(newCap);
            buf.flip();
            nb.put(buf);
            throw new IllegalStateException("Buffer overflow: please increase BUFFER_SIZE or implement resizing.");
        }
    }

    private static void writeBufferToChannel(ByteBuffer buf, SocketChannel ch) throws IOException {
        buf.flip();
        while (buf.hasRemaining()) {
            int w = ch.write(buf);
            if (w == 0) break;
        }
        buf.compact();
    }

    private static void updateInterestForChannel(SelectionKey key, ByteBuffer buf) {
        if (key == null) return;
        int ops = key.interestOps();
        if (buf.position() > 0) {
            ops |= SelectionKey.OP_WRITE;
        } else {
            ops &= ~SelectionKey.OP_WRITE;
        }
        key.interestOps(ops);
    }

    private static void closeClientState(Map<SocketChannel, ClientState> states, ClientState cs) throws IOException {
        if (cs == null) return;
        try {
            if (cs.client != null) {
                states.remove(cs.client);
                cs.client.close();
            }
        } catch (IOException ignored) {
        }
        try {
            if (cs.target != null) cs.target.close();
        } catch (IOException ignored) {
        }
        System.out.println("[main] closed client state: " + cs);
    }

    private static void closeChannelQuietly(SocketChannel ch) {
        try {
            if (ch != null) ch.close();
        } catch (IOException ignored) {
        }
    }
}
