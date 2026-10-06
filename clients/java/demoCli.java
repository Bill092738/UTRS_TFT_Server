import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 控制台客户端 / console client.
 * 用法: java demoCli [userID] [host] [port]   (默认 User1 127.0.0.1 23363)
 */
public class demoCli {
    // 服务器回复中本用户的位置: Welcome(User1, (x,y)) 或 Moved(User1, (a,b), (x,y))
    private static final Pattern POSITION_REPLY =
            Pattern.compile("^(?:Welcome|Moved)\\((\\S+?),.*(\\(-?\\d+,-?\\d+\\))\\)$");

    private static volatile String currentPosition = "(0,0)"; // 以服务器确认的位置为准

    public static void main(String[] args) {
        String userID = args.length > 0 ? args[0] : "User1";
        String host = args.length > 1 ? args[1] : "127.0.0.1";
        int port = args.length > 2 ? Integer.parseInt(args[2]) : 23363;

        try (Socket socket = new Socket(host, port);
             BufferedReader serverIn = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
             PrintWriter serverOut = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);
             BufferedReader userIn = new BufferedReader(new InputStreamReader(System.in))) {

            // 启动线程监听服务器消息
            Thread listener = new Thread(() -> {
                try {
                    String serverMessage;
                    while ((serverMessage = serverIn.readLine()) != null) {
                        Matcher m = POSITION_REPLY.matcher(serverMessage);
                        if (m.matches() && m.group(1).equals(userID)) {
                            currentPosition = m.group(2);
                        }
                        System.out.println("[服务器消息] " + serverMessage);
                    }
                } catch (IOException e) {
                    System.err.println("与服务器通信失败: " + e.getMessage());
                }
            });
            listener.setDaemon(true);
            listener.start();

            // 登录, 服务器会回复 Welcome(userID, (x,y)) 告知当前位置
            serverOut.println("Join(" + userID + ")");

            // 主线程监听用户输入
            String userInput;
            while ((userInput = userIn.readLine()) != null) {
                if (userInput.equals("/Help")) {
                    serverOut.println("Help()");
                } else if (userInput.equals("/Users")) {
                    serverOut.println("Users()");
                } else if (userInput.startsWith("/Move")) {
                    // 解析用户输入的目标位置, 允许负数, 例如 /Move (-3,4)
                    String destination = userInput.substring(5).replaceAll("\\s+", "");
                    if (!destination.matches("\\(-?\\d+,-?\\d+\\)")) {
                        System.out.println("指令格式错误！正确格式: /Move (x,y)");
                        continue;
                    }
                    // 位置在收到服务器的 Moved(...) 后才更新, 被拒绝时不会与服务器不同步
                    serverOut.println(String.format("Move(%s, %s, %s)", userID, currentPosition, destination));
                } else if (userInput.startsWith("/Broadcast")) {
                    String[] parts = userInput.split("\\s+", 3);
                    if (parts.length != 3 || parts[2].length() < 2 || !parts[2].startsWith("\"") || !parts[2].endsWith("\"")) {
                        System.out.println("指令格式错误！正确格式: /Broadcast target \"message\"");
                        continue;
                    }
                    String target = parts[1];
                    String message = parts[2].substring(1, parts[2].length() - 1)
                            .replace("\\", "\\\\").replace("\"", "\\\"");
                    serverOut.println(String.format("Broadcast(%s, %s, \"%s\")", userID, target, message));
                } else if (userInput.equals("/Quit")) {
                    break;
                } else {
                    System.out.println("未知指令！可用: /Move (x,y)  /Broadcast target \"message\"  /Users  /Help  /Quit");
                }
            }
        } catch (IOException e) {
            System.err.println("无法连接到服务器: " + e.getMessage());
        }
    }
}
