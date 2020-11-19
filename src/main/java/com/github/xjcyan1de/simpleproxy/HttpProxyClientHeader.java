package com.github.xjcyan1de.simpleproxy;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

public class HttpProxyClientHeader {

    private final StringBuilder lineBuf = new StringBuilder();
    private String method;
    private String host;
    private int port;
    private boolean https;
    private boolean complete;
    private ByteBuf byteBuf = Unpooled.buffer();

    public void digest(ByteBuf in) {
        while (in.isReadable()) {
            if (complete) {
                throw new IllegalStateException("already complete");
            }
            String line = readLine(in);
            if (line == null) {
                return;
            }
            if (method == null) {
                method = line.split(" ")[0];
                https = method.equalsIgnoreCase("CONNECT");
            }
            if (line.startsWith("Host: ")) {
                String[] arr = line.split(":");
                host = arr[1].trim();
                if (arr.length == 3) {
                    port = Integer.parseInt(arr[2]);
                } else if (https) {
                    port = 443; // https
                } else {
                    port = 80; // http
                }
            }
            if (line.isEmpty()) {
                if (host == null || port == 0) {
                    throw new IllegalStateException("cannot find header 'Host'");
                }
                byteBuf = byteBuf.asReadOnly();
                complete = true;
                break;
            }
        }
    }

    private String readLine(ByteBuf in) {
        while (in.isReadable()) {
            byte b = in.readByte();
            byteBuf.writeByte(b);
            lineBuf.append((char) b);
            int len = lineBuf.length();
            if (len >= 2 && lineBuf.substring(len - 2).equals("\r\n")) {
                String line = lineBuf.substring(0, len - 2);
                lineBuf.delete(0, len);
                return line;
            }
        }
        return null;
    }

    public String getMethod() {
        return method;
    }

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    public boolean isHttps() {
        return https;
    }

    public boolean isComplete() {
        return complete;
    }

    public ByteBuf getByteBuf() {
        return byteBuf;
    }
}
