import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

public class App {
    // 配置常量
    private static final String HOST = "127.0.0.1"; // 主机地址
    private static final int PORT_ACTIVE = 23363;  // 监听端口
    private static final String SAVE_FILE = "save.txt"; // 数据保存文件

    // 存储用户数据的线程安全Map
    private static final Map<String, String> userData = new ConcurrentHashMap<>();
    // 存储客户端连接的线程安全列表
    private static final List<Socket> clientSockets = Collections.synchronizedList(new ArrayList<>());
    // 定义一个二维数组来存储用户位置
    private static final String[][] positionGrid = new String[2001][2001]; // 数据范围 -1000 到 +1000

    // 初始化二维数组
    static {
        for (int i = 0; i < positionGrid.length; i++) {
            Arrays.fill(positionGrid[i], null); // 初始化为 null，表示没有用户
        }
    }

    public static void main(String[] args) {
        // 启动时读取 save.txt 恢复数据
        loadData();

        // 启动服务器
        try (ServerSocket serverSocket = new ServerSocket(PORT_ACTIVE, 50, InetAddress.getByName(HOST))) {
            System.out.println("服务器已启动，监听 " + HOST + ":" + PORT_ACTIVE);
            while (true) {
                Socket clientSocket = serverSocket.accept(); // 接受客户端连接
                System.out.println("新客户端连接: " + clientSocket.getInetAddress());
                clientSockets.add(clientSocket); // 添加到客户端列表
                new Thread(new ClientHandler(clientSocket)).start(); // 为每个客户端启动线程
            }
        } catch (IOException e) {
            System.err.println("服务器启动失败: " + e.getMessage());
        }

        // 注册关闭钩子，在程序结束时保存数据
        Runtime.getRuntime().addShutdownHook(new Thread(App::saveData));
    }

    /** 从 save.txt 读取数据以恢复状态 */
    private static void loadData() {
        File file = new File(SAVE_FILE);
        if (!file.exists()) {
            System.out.println("save.txt 不存在，启动时无数据恢复");
            return;
        }
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split("=", 2); // 格式: userID=value
                if (parts.length == 2) {
                    userData.put(parts[0], parts[1]);
                }
            }
            System.out.println("已从 save.txt 恢复数据: " + userData);
        } catch (IOException e) {
            System.err.println("读取 save.txt 失败: " + e.getMessage());
        }
    }

    /** 将内存数据保存到 save.txt */
    private static void saveData() {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(SAVE_FILE))) {
            for (Map.Entry<String, String> entry : userData.entrySet()) {
                writer.write(entry.getKey() + "=" + entry.getValue());
                writer.newLine();
            }
            System.out.println("数据已保存到 save.txt");
        } catch (IOException e) {
            System.err.println("保存数据到 save.txt 失败: " + e.getMessage());
        }
    }

    /** 广播消息给所有客户端 */
    private static void broadcast(String message) {
        synchronized (clientSockets) {
            Iterator<Socket> iterator = clientSockets.iterator();
            while (iterator.hasNext()) {
                Socket client = iterator.next();
                try {
                    PrintWriter out = new PrintWriter(client.getOutputStream(), true);
                    out.println(message);
                } catch (IOException e) {
                    System.err.println("广播失败，移除客户端: " + client.getInetAddress());
                    iterator.remove(); // 移除无法通信的客户端
                }
            }
        }
    }

    /** 客户端处理线程 */
    private static class ClientHandler implements Runnable {
        private final Socket socket;

        public ClientHandler(Socket socket) {
            this.socket = socket;
        }

        @Override
        public void run() {
            try (BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {
                String request;
                while ((request = in.readLine()) != null) {
                    System.out.println("收到请求: " + request);
                    processRequest(request);
                }
            } catch (IOException e) {
                System.err.println("客户端处理异常: " + e.getMessage());
            } finally {
                clientSockets.remove(socket);
                try {
                    socket.close();
                    System.out.println("客户端已断开: " + socket.getInetAddress());
                } catch (IOException e) {
                    System.err.println("关闭客户端连接失败: " + e.getMessage());
                }
            }
        }

        /** 处理客户端请求 */
        private void processRequest(String request) {
            // 解析请求字符串
            if (!request.matches("^[A-Za-z]+\\((.*)?\\)$")) { // 修改正则表达式
                System.err.println("非法请求格式: " + request);
                return;
            }

            String action = request.substring(0, request.indexOf("("));
            String paramsStr = request.substring(request.indexOf("(") + 1, request.indexOf(")"));
            String[] params = paramsStr.isEmpty() ? new String[0] : paramsStr.split(",\\s*");

            if (params.length < 1 && !"Help".equals(action)) { // Help 不需要参数
                System.err.println("参数不足: " + request);
                return;
            }

            String userID = params.length > 0 ? params[0].trim() : "";

            // 根据 Action 处理请求
            switch (action) {
                case "Broadcast":
                    if (params.length < 3) {
                        System.err.println("Broadcast 参数不足: " + request);
                        return;
                    }
                    String target = params[1].trim();
                    String message = params[2].trim().replaceAll("^\"|\"$", ""); // 移除引号
                    userData.put(userID, "Broadcast to " + target + ": " + message);
                    broadcast("用户 " + userID + " 广播: " + message);
                    break;

                case "Move":
                    if (params.length < 3) {
                        System.err.println("Move 参数不足: " + request);
                        return;
                    }

                    // 解析当前位置和目标位置
                    String currentPosition = params[1].trim().replaceAll("\\s+", ""); // 去除多余空格
                    String destination = params[2].trim().replaceAll("\\s+", ""); // 去除多余空格

                    // 解析坐标
                    int[] currentCoords = parseCoordinates(currentPosition);
                    int[] destinationCoords = parseCoordinates(destination);

                    if (currentCoords == null || destinationCoords == null) {
                        System.err.println("Move 坐标解析失败: " + request);
                        return;
                    }

                    // 检查坐标是否在范围内
                    if (!isWithinBounds(currentCoords) || !isWithinBounds(destinationCoords)) {
                        System.err.println("Move 坐标超出范围: " + request);
                        return;
                    }

                    // 检查当前位置是否与记录位置一致
                    if (!userID.equals(positionGrid[currentCoords[0] + 1000][currentCoords[1] + 1000])) {
                        System.err.println("当前位置与记录位置不一致: " + request);
                        return;
                    }

                    // 更新二维数组中的用户位置
                    positionGrid[currentCoords[0] + 1000][currentCoords[1] + 1000] = null; // 清除旧位置
                    positionGrid[destinationCoords[0] + 1000][destinationCoords[1] + 1000] = userID; // 设置新位置

                    // 广播用户移动信息
                    broadcast("用户 " + userID + " 从 " + currentPosition + " 移动到 " + destination);

                    // 输出当前内存数据
                    System.out.println("用户 " + userID + " 的新位置: " + destination);
                    break;

                case "Help":
                    System.out.println("合法指令列表:");
                    System.out.println("1. Broadcast(userID, target, \"message\") - 广播消息到目标");
                    System.out.println("2. Move(userID, currentPosition, destination) - 移动到目标位置");
                    System.out.println("3. Help() - 显示帮助信息");
                    break;

                default:
                    System.err.println("未知 Action: " + action);
                    return;
            }
            System.out.println("当前内存数据: " + userData);
        }
    }

    /** 解析坐标字符串为整数数组 */
    private static int[] parseCoordinates(String position) {
        try {
            String[] parts = position.replaceAll("[()]", "").split(",");
            int x = Integer.parseInt(parts[0].trim());
            int y = Integer.parseInt(parts[1].trim());
            return new int[]{x, y};
        } catch (Exception e) {
            return null; // 解析失败
        }
    }

    /** 检查坐标是否在范围内 */
    private static boolean isWithinBounds(int[] coords) {
        return coords[0] >= -1000 && coords[0] <= 1000 && coords[1] >= -1000 && coords[1] <= 1000;
    }
}
