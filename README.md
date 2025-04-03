# UTRS_TFT_Server

A simple TCP server and client implementation with both Java and GameMaker Studio clients.

## Project Structure

```
NST6/
├── src/
│   ├── App.java           # TCP Server implementation
│   ├── demoCli.java       # Java client implementation
│   ├── demoCli.gml        # GameMaker client implementation
│   └── translateGML.gml   # GameMaker coordinate translation utilities
```

## Features

- TCP Server with real-time client communication
- Support for multiple clients
- Position tracking system (-1000 to +1000 coordinate range)
- Broadcast messaging system
- Command parsing and validation
- Data persistence through save.txt

## Commands

1. **Move Command**
   ```
   Move(userID, currentPosition, destination)
   Example: Move(User1, (0,0), (1,1))
   ```

2. **Broadcast Command**
   ```
   Broadcast(userID, target, "message")
   Example: Broadcast(User1, all, "Hello World!")
   ```

3. **Help Command**
   ```
   Help()
   ```

## Setup Instructions

### Server (Java)
1. Compile App.java:
   ```bash
   javac App.java
   ```
2. Run the server:
   ```bash
   java App
   ```
The server will start listening on 127.0.0.1:23363

### Java Client
1. Compile demoCli.java:
   ```bash
   javac demoCli.java
   ```
2. Run the client:
   ```bash
   java demoCli
   ```

### GameMaker Studio 2 Client
1. Create a new GameMaker Studio 2 project
2. Import demoCli.gml and translateGML.gml
3. Create an obj_player object
4. Add network functionality using GMNet or similar extension
5. Implement the network sending guides as commented in the code

## Usage

### Java Client
- Type commands directly in the console
- Commands start with "/" (e.g., "/Move (1,1)")
- Use "/Help" to see available commands

### GameMaker Client
- Use arrow keys/WASD to move the player
- Press T to broadcast a message
- Press F1 for help
- Movement is automatically translated to server commands

## Technical Details

### Coordinate System
- Origin (0,0) at center
- Range: -1000 to +1000 on both axes
- New users spawn at (0,0)

### Data Persistence
- User positions saved in save.txt
- File loaded on server start
- Data saved on server shutdown

### Network Protocol
- TCP-based communication
- Text commands in specified format
- Real-time position updates
- Broadcast message support

## Requirements

- Java 8 or higher (Server & Java Client)
- GameMaker Studio 2 (GameMaker Client)
- Network extension for GameMaker (e.g., GMNet)

## Known Limitations

1. GameMaker implementation requires additional network extension
2. Coordinate system limited to -1000 to +1000
3. No encryption or authentication implemented

## Error Handling

The server implements various error checks:
- Invalid command format
- Out of bounds coordinates
- Invalid position updates
- Malformed messages

## Contributing

This is a demo project. Feel free to modify and expand upon it.

## License

MIT