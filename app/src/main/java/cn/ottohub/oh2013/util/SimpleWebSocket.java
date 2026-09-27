package cn.ottohub.oh2013.util;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.URI;
import java.util.Random;

import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

/**
 * 极简 WebSocket 客户端（文本帧），兼容旧设备，无第三方依赖。
 */
public class SimpleWebSocket {

    public interface Listener {
        void onOpen();
        void onMessage(String text);
        void onClose();
        void onError(Exception e);
    }

    private final URI uri;
    private final Listener listener;
    private volatile boolean running;
    private Socket socket;
    private OutputStream out;
    private Thread readerThread;

    public SimpleWebSocket(String url, Listener listener) throws Exception {
        this.uri = new URI(url);
        this.listener = listener;
    }

    public void connect() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    doConnect();
                } catch (Exception e) {
                    if (listener != null) listener.onError(e);
                    closeQuietly();
                }
            }
        }, "oh-ws-connect").start();
    }

    public synchronized void sendText(String text) throws Exception {
        if (out == null || !running) {
            throw new IllegalStateException("WebSocket 未连接");
        }
        byte[] payload = text.getBytes("UTF-8");
        ByteArrayOutputStream frame = new ByteArrayOutputStream();
        frame.write(0x81); // FIN + text
        int len = payload.length;
        byte maskBit = (byte) 0x80;
        if (len < 126) {
            frame.write(maskBit | len);
        } else if (len < 65536) {
            frame.write(maskBit | 126);
            frame.write((len >> 8) & 0xFF);
            frame.write(len & 0xFF);
        } else {
            throw new IllegalArgumentException("消息过长");
        }
        byte[] mask = new byte[4];
        new Random().nextBytes(mask);
        frame.write(mask);
        for (int i = 0; i < payload.length; i++) {
            frame.write(payload[i] ^ mask[i % 4]);
        }
        out.write(frame.toByteArray());
        out.flush();
    }

    public void close() {
        running = false;
        closeQuietly();
    }

    private void doConnect() throws Exception {
        String scheme = uri.getScheme();
        String host = uri.getHost();
        int port = uri.getPort();
        boolean ssl = "wss".equalsIgnoreCase(scheme);
        if (port < 0) {
            port = ssl ? 443 : 80;
        }

        if (ssl) {
            SSLSocketFactory factory = NetWorkUtil.getTrustAllSSLSocketFactory();
            if (factory == null) {
                factory = (SSLSocketFactory) SSLSocketFactory.getDefault();
            }
            SSLSocket sslSocket = (SSLSocket) factory.createSocket(host, port);
            try {
                // 与 HTTP 客户端一致：显式启用 TLS1.2（API 22 默认工厂有时仍偏旧）
                String[] desired = new String[]{"TLSv1.2", "TLSv1.1", "TLSv1"};
                String[] supported = sslSocket.getSupportedProtocols();
                java.util.ArrayList<String> enable = new java.util.ArrayList<String>();
                if (supported != null) {
                    for (int i = 0; i < desired.length; i++) {
                        for (int j = 0; j < supported.length; j++) {
                            if (desired[i].equalsIgnoreCase(supported[j])) {
                                enable.add(supported[j]);
                                break;
                            }
                        }
                    }
                }
                if (enable.size() > 0) {
                    sslSocket.setEnabledProtocols(enable.toArray(new String[enable.size()]));
                }
            } catch (Exception ignored) {
            }
            try {
                // SNI：部分服务器要求
                java.lang.reflect.Method m = sslSocket.getClass()
                        .getMethod("setHostname", String.class);
                m.invoke(sslSocket, host);
            } catch (Exception ignored) {
            }
            try {
                sslSocket.startHandshake();
            } catch (Exception e) {
                try { sslSocket.close(); } catch (Exception ignored) {}
                throw e;
            }
            socket = sslSocket;
        } else {
            socket = new Socket(host, port);
        }
        socket.setSoTimeout(0);
        out = socket.getOutputStream();
        final InputStream in = socket.getInputStream();

        String path = uri.getRawPath();
        if (path == null || path.length() == 0) path = "/";
        String query = uri.getRawQuery();
        if (query != null && query.length() > 0) {
            path = path + "?" + query;
        }

        String key = generateKey();
        StringBuilder req = new StringBuilder();
        req.append("GET ").append(path).append(" HTTP/1.1\r\n");
        req.append("Host: ").append(host).append("\r\n");
        req.append("Upgrade: websocket\r\n");
        req.append("Connection: Upgrade\r\n");
        req.append("Sec-WebSocket-Key: ").append(key).append("\r\n");
        req.append("Sec-WebSocket-Version: 13\r\n");
        req.append("Origin: https://www.ottohub.cn\r\n");
        req.append("User-Agent: OTTOhub\r\n");
        req.append("\r\n");
        out.write(req.toString().getBytes("UTF-8"));
        out.flush();

        String statusLine = readLine(in);
        if (statusLine == null || statusLine.indexOf("101") < 0) {
            throw new IOExceptionCompat("握手失败: " + statusLine);
        }
        // 读完响应头
        while (true) {
            String line = readLine(in);
            if (line == null || line.length() == 0) break;
        }

        running = true;
        if (listener != null) listener.onOpen();

        readerThread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    readLoop(in);
                } catch (Exception e) {
                    if (running && listener != null) listener.onError(e);
                } finally {
                    running = false;
                    if (listener != null) listener.onClose();
                    closeQuietly();
                }
            }
        }, "oh-ws-read");
        readerThread.start();
    }

    private void readLoop(InputStream in) throws Exception {
        while (running) {
            int b1 = in.read();
            if (b1 < 0) break;
            int b2 = in.read();
            if (b2 < 0) break;
            int opcode = b1 & 0x0F;
            boolean masked = (b2 & 0x80) != 0;
            long len = b2 & 0x7F;
            if (len == 126) {
                len = ((in.read() & 0xFF) << 8) | (in.read() & 0xFF);
            } else if (len == 127) {
                len = 0;
                for (int i = 0; i < 8; i++) {
                    len = (len << 8) | (in.read() & 0xFF);
                }
            }
            byte[] mask = null;
            if (masked) {
                mask = new byte[4];
                readFully(in, mask, 0, 4);
            }
            if (len > 1024 * 1024) {
                throw new IOExceptionCompat("帧过大");
            }
            byte[] payload = new byte[(int) len];
            readFully(in, payload, 0, (int) len);
            if (masked && mask != null) {
                for (int i = 0; i < payload.length; i++) {
                    payload[i] = (byte) (payload[i] ^ mask[i % 4]);
                }
            }
            if (opcode == 0x8) { // close
                break;
            } else if (opcode == 0x9) { // ping → pong
                writeControl(0xA, payload);
            } else if (opcode == 0x1) { // text
                String text = new String(payload, "UTF-8");
                if (listener != null) listener.onMessage(text);
            }
        }
    }

    private synchronized void writeControl(int opcode, byte[] payload) throws Exception {
        if (out == null) return;
        if (payload == null) payload = new byte[0];
        ByteArrayOutputStream frame = new ByteArrayOutputStream();
        frame.write(0x80 | (opcode & 0x0F));
        frame.write(0x80 | payload.length);
        byte[] mask = new byte[4];
        new Random().nextBytes(mask);
        frame.write(mask);
        for (int i = 0; i < payload.length; i++) {
            frame.write(payload[i] ^ mask[i % 4]);
        }
        out.write(frame.toByteArray());
        out.flush();
    }

    private void closeQuietly() {
        try {
            if (out != null) out.close();
        } catch (Exception ignored) {
        }
        try {
            if (socket != null) socket.close();
        } catch (Exception ignored) {
        }
        out = null;
        socket = null;
    }

    private static String readLine(InputStream in) throws Exception {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        int prev = -1;
        while (true) {
            int c = in.read();
            if (c < 0) {
                if (buf.size() == 0) return null;
                break;
            }
            if (prev == '\r' && c == '\n') {
                break;
            }
            if (c != '\r') {
                buf.write(c);
            }
            prev = c;
        }
        return new String(buf.toByteArray(), "UTF-8");
    }

    private static void readFully(InputStream in, byte[] buf, int off, int len) throws Exception {
        int read = 0;
        while (read < len) {
            int n = in.read(buf, off + read, len - read);
            if (n < 0) throw new IOExceptionCompat("EOF");
            read += n;
        }
    }

    private static String generateKey() {
        byte[] nonce = new byte[16];
        new Random().nextBytes(nonce);
        return base64Encode(nonce);
    }

    /** 简易 Base64（兼容 minSdk 3，不依赖 android.util.Base64） */
    private static String base64Encode(byte[] data) {
        final char[] table = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/".toCharArray();
        StringBuilder sb = new StringBuilder((data.length + 2) / 3 * 4);
        int i = 0;
        while (i < data.length) {
            int b0 = data[i++] & 0xFF;
            int b1 = i < data.length ? (data[i++] & 0xFF) : -1;
            int b2 = i < data.length ? (data[i++] & 0xFF) : -1;
            sb.append(table[b0 >> 2]);
            sb.append(table[((b0 & 0x3) << 4) | (b1 >= 0 ? (b1 >> 4) : 0)]);
            sb.append(b1 >= 0 ? table[((b1 & 0xF) << 2) | (b2 >= 0 ? (b2 >> 4) : 0)] : '=');
            sb.append(b2 >= 0 ? table[b2 & 0x3F] : '=');
        }
        return sb.toString();
    }

    private static class IOExceptionCompat extends Exception {
        IOExceptionCompat(String msg) {
            super(msg);
        }
    }
}
