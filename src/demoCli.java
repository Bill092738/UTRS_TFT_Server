import java.io.*;
import java.net.*;

public class demoCli {
    private static final String SERVER_HOST = "127.0.0.1";
    private static final int SERVER_PORT = 23363;
    private static String userID = "User1"; // 默认用户ID
    private static String currentPosition = "(0,0)"; // 初始位置

    public static void main(String[] args) {
        try (Socket socket = new Socket(SERVER_HOST, SERVER_PORT);
             BufferedReader serverIn = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             PrintWriter serverOut = new PrintWriter(socket.getOutputStream(), true);
             BufferedReader userIn = new BufferedReader(new InputStreamReader(System.in))) {

            // 启动线程监听服务器消息
            new Thread(() -> {
                try {
                    String serverMessage;
                    while ((serverMessage = serverIn.readLine()) != null) {
                        System.out.println("[服务器消息] " + serverMessage);
                    }
                } catch (IOException e) {
                    System.err.println("与服务器通信失败: " + e.getMessage());
                }
            }).start();

            // 主线程监听用户输入
            String userInput;
            while ((userInput = userIn.readLine()) != null) {
                if (userInput.equals("/Help")) {
                    // 转换为服务端格式的 Help() 指令
                    serverOut.println("Help()");
                } else if (userInput.startsWith("/Move")) {
                    // 解析用户输入的目标位置
                    String[] parts = userInput.split("\\s+");
                    if (parts.length != 2 || !parts[1].matches("\\(\\d+,\\d+\\)")) {
                        System.out.println("指令格式错误！正确格式: /Move (x,y)");
                        continue;
                    }

                    String destination = parts[1].replaceAll("\\s+", ""); // 去除多余空格
                    String moveCommand = String.format("Move(%s, %s, %s)", userID, currentPosition, destination);

                    // 发送移动指令到服务器
                    serverOut.println(moveCommand);
                    currentPosition = destination; // 更新当前位置
                } else if (userInput.startsWith("/Broadcast")) {
                    // 解析广播指令
                    String[] parts = userInput.split("\\s+", 3);
                    if (parts.length != 3 || !parts[2].startsWith("\"") || !parts[2].endsWith("\"")) {
                        System.out.println("指令格式错误！正确格式: /Broadcast target \"message\"");
                        continue;
                    }

                    String target = parts[1];
                    String message = parts[2].substring(1, parts[2].length() - 1); // 去掉引号
                    String broadcastCommand = String.format("Broadcast(%s, %s, \"%s\")", userID, target, message);

                    // 发送广播指令到服务器
                    serverOut.println(broadcastCommand);
                } else {
                    System.out.println("未知指令！输入 /Help 查看可用指令。");
                }
            }
        } catch (IOException e) {
            System.err.println("无法连接到服务器: " + e.getMessage());
        }
    }
}