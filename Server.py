import socketserver
import json
import struct
import threading

# Global data structures
players = {}  # {player_id: {"x": x_pos, "y": y_pos}}
handlers = set()  # Set of active client handlers
data_lock = threading.Lock()  # Lock for thread-safe access
player_id_counter = 0  # For assigning unique player IDs

# Send a message with length prefix
def send_message(sock, message):
    json_str = json.dumps(message)
    length = len(json_str)
    sock.sendall(struct.pack('!I', length))  # 4-byte length
    sock.sendall(json_str.encode('utf-8'))

# Broadcast a message to all clients except the sender (optional)
def broadcast(message, exclude_handler=None):
    with data_lock:
        for handler in handlers.copy():
            if handler != exclude_handler:
                try:
                    send_message(handler.request, message)
                except:
                    # Remove handler if sending fails (client disconnected)
                    handlers.remove(handler)

# Client handler
class ClientHandler(socketserver.BaseRequestHandler):
    def handle(self):
        global player_id_counter
        sock = self.request

        # Assign a unique player ID
        with data_lock:
            player_id = f"player{player_id_counter}"
            player_id_counter += 1
            players[player_id] = {"x": 100, "y": 100}  # Default position
            handlers.add(self)

        # Send welcome message with player ID and current players
        welcome_msg = {
            "type": "welcome",
            "player_id": player_id,
            "players": [{"id": pid, "x": data["x"], "y": data["y"]} for pid, data in players.items()]
        }
        send_message(sock, welcome_msg)

        # Broadcast new player to others
        join_msg = {"type": "join", "player_id": player_id, "x": 100, "y": 100}
        broadcast(join_msg, self)

        # Main loop to handle client messages
        while True:
            try:
                # Receive length prefix
                length_data = sock.recv(4)
                if not length_data:
                    break  # Client disconnected
                length = struct.unpack('!I', length_data)[0]
                # Receive message
                json_str = sock.recv(length).decode('utf-8')
                message = json.loads(json_str)

                # Process message based on type
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
                        # Echo back to sender for consistency
                        send_message(sock, chat_msg)

            except:
                break  # Exit on any error (e.g., disconnect)

        # Client disconnected
        with data_lock:
            del players[player_id]
            handlers.remove(self)
            leave_msg = {"type": "leave", "player_id": player_id}
            broadcast(leave_msg)

# Start the server
server = socketserver.ThreadingTCPServer(("127.0.0.1", 5555), ClientHandler)
print("Server running on 127.0.0.1:5555...")
server.serve_forever()