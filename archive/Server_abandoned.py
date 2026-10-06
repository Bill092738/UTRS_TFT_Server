import socketserver
import json
import struct
import threading

# Global data structures
players = {}
handlers = set()
data_lock = threading.Lock()
player_id_counter = 0

def send_message(sock, message):
    json_str = json.dumps(message) + '\0'  # Add null terminator for GameMaker
    length = len(json_str)
    sock.sendall(struct.pack('!I', length))
    sock.sendall(json_str.encode('utf-8'))
    print(f"Sending to {sock}: {message}")

def broadcast(message, exclude_handler=None):
    with data_lock:
        for handler in handlers.copy():
            if handler != exclude_handler:
                try:
                    send_message(handler.request, message)
                except Exception as e:
                    print(f"Error sending to client: {e}")
                    handlers.remove(handler)

class ClientHandler(socketserver.BaseRequestHandler):
    def handle(self):
        global player_id_counter
        sock = self.request
        print(f"New client connected: {self.client_address}")

        with data_lock:
            player_id = f"player{player_id_counter}"
            player_id_counter += 1
            players[player_id] = {"x": 100, "y": 100}
            handlers.add(self)

        welcome_msg = {
            "type": "welcome",
            "player_id": player_id,
            "players": [{"id": pid, "x": data["x"], "y": data["y"]} 
                        for pid, data in players.items() if pid != player_id]
        }
        send_message(sock, welcome_msg)

        join_msg = {"type": "join", "player_id": player_id, "x": 100, "y": 100}
        broadcast(join_msg, self)

        while True:
            try:
                length_data = sock.recv(4)
                if not length_data:
                    break
                length = struct.unpack('!I', length_data)[0]
                json_str = sock.recv(length).decode('utf-8')
                try:
                    message = json.loads(json_str)
                    print(f"Received from {player_id}: {message}")
                except json.JSONDecodeError:
                    #print("Received malformed JSON")
                    continue

                with data_lock:
                    if message["type"] == "move":
                        players[player_id]["x"] = message["x"]
                        players[player_id]["y"] = message["y"]
                        move_msg = {
                            "type": "move",
                            "player_id": player_id,
                            "x": message["x"],
                            "y": message["y"]
                        }
                        broadcast(move_msg, self)
                    elif message["type"] == "chat":
                        chat_msg = {
                            "type": "chat",
                            "player_id": player_id,
                            "message": message["message"]
                        }
                        broadcast(chat_msg, self)
                        send_message(sock, chat_msg)

            except:
                break

        with data_lock:
            del players[player_id]
            handlers.remove(self)
            leave_msg = {"type": "leave", "player_id": player_id}
            broadcast(leave_msg)

server = socketserver.ThreadingTCPServer(("127.0.0.1", 5555), ClientHandler)
print("Server running on 127.0.0.1:5555...")
server.serve_forever()