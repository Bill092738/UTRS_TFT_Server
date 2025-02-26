import asyncio
import json

class GameServer:
    def __init__(self, host='0.0.0.0', port=3000):
        self.host = host
        self.port = port
        self.clients = {}  # 存储所有连接的客户端 (writer对象)
        self.command_handlers = {}  # 命令处理函数字典

    async def start(self):
        server = await asyncio.start_server(
            self.handle_client, self.host, self.port
        )
        print(f"服务端已启动，监听 {self.host}:{self.port}")
        async with server:
            await server.serve_forever()

    async def handle_client(self, reader, writer):
        client_id = str(writer.get_extra_info('peername'))
        print(f"新客户端连接: {client_id}")
        self.clients[client_id] = writer

        # 初始化客户端握手
        await self.send_command(writer, "__net__handshake", {"status": "connected"})

        buffer = ""
        delimiter = "\n\t\n"  # 与客户端的命令分隔符一致

        try:
            while True:
                data = await reader.read(4096)
                if not data:
                    break

                buffer += data.decode()
                while delimiter in buffer:
                    command_str, _, buffer = buffer.partition(delimiter)
                    await self.process_command(client_id, command_str)

        except ConnectionResetError:
            print(f"客户端 {client_id} 异常断开")
        except Exception as e:
            print(f"处理客户端 {client_id} 时发生错误: {e}")
        finally:
            del self.clients[client_id]
            writer.close()
            await writer.wait_closed()
            print(f"客户端 {client_id} 已断开")

    async def process_command(self, client_id, command_str):
        try:
            command_data = json.loads(command_str)
            command_type = command_data.get("command", "")
            print(f"收到命令 [{command_type}] 来自 {client_id}")

            # 内置命令处理
            if command_type == "__net__handshake":
                await self.send_command(self.clients[client_id], "__net__handshake", {"status": "ack"})
            elif command_type == "__net__fin":
                await self.send_command(self.clients[client_id], "__net__fin__ack", {})
            else:
                # 调用注册的自定义处理函数
                handler = self.command_handlers.get(command_type)
                if handler:
                    await handler(client_id, command_data)

        except json.JSONDecodeError:
            print(f"无效的JSON数据: {command_str}")

    async def send_command(self, writer, command_type, data):
        """向单个客户端发送命令"""
        command = {"command": command_type, **data}
        content = json.dumps(command) + "\n\t\n"  # 添加分隔符
        writer.write(content.encode())
        await writer.drain()

    async def broadcast(self, command_type, data, exclude_client=None):
        """广播命令给所有客户端（排除指定客户端）"""
        content = json.dumps({"command": command_type, **data}) + "\n\t\n"
        for cid, writer in self.clients.items():
            if cid != exclude_client:
                writer.write(content.encode())
                await writer.drain()

    def add_handler(self, command_type):
        """装饰器：注册命令处理函数"""
        def decorator(func):
            self.command_handlers[command_type] = func
            return func
        return decorator

# ------------------- 示例用法 -------------------
if __name__ == "__main__":
    server = GameServer()

    @server.add_handler("chat")  # 注册处理 "chat" 命令
    async def handle_chat(client_id, data):
        message = data.get("text", "")
        print(f"聊天消息来自 {client_id}: {message}")
        await server.broadcast("chat", {"text": message}, exclude_client=client_id)

    @server.add_handler("player_move")  # 注册处理 "player_move" 命令
    async def handle_move(client_id, data):
        x = data.get("x", 0)
        y = data.get("y", 0)
        print(f"玩家 {client_id} 移动到 ({x}, {y})")
        await server.broadcast("player_update", {"id": str(client_id), "x": x, "y": y})

    asyncio.run(server.start())