## UTRS_TFT_Server
UTRS_TFT_Server is a game server implementation using Python's `socketserver`, `json`, `struct`, `threading` library. It handles client connections, processes commands, and broadcasts messages to connected clients.

# Project Structure
```
UTRS_TFT_Server/
├── LICENSE
├── README.md
├── Examples/
│   ├── TestingProject
│   ├── Images
├── scripts_abandoned/
│   ├── ...
├── Server.py
```

# Getting Started
**Prerequisites**
* Python 3.10+

**Installation**

1. Clone the repository:
```
git clone https://github.com/Bill092738/UTRS_TFT_Server.git
```

2. Open the GM project in the Example folder.

**Running the Server**

To start the server, run the following command:
```
python Server.py
```

**BUG**
Async - Networking event is firing, but the `async_load[? "type"]` value is `3`, not `64`. In GameMaker, the expected value for receiving network data over a TCP socket is `network_type_data`, which equals `64`. Receiving `3` instead suggests a different network event is occurring.

# License
MIT License
